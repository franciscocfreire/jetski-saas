package com.jetski.locacoes.internal;

import com.jetski.locacoes.internal.repository.AssinaturaCertificadoRepository;
import com.jetski.shared.security.SecretCipher;
import com.lowagie.text.pdf.PdfDate;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfPKCS7;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfSignature;
import com.lowagie.text.pdf.PdfSignatureAppearance;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.PdfString;
import com.lowagie.text.pdf.TSAClient;
import com.lowagie.text.pdf.TSAClientBouncyCastle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.asn1.x500.X500Name;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

/**
 * Assinatura digital PAdES do PDF emitido (Fase C2): torna o documento
 * <em>tamper-evident</em> (qualquer alteração posterior é detectada pelo próprio
 * arquivo, verificável no Adobe Reader), assinatura eletrônica avançada
 * (Lei 14.063/2020 art. 4º II). Usa um certificado auto-assinado da plataforma
 * (grátis; a validade da identidade aparece como "desconhecida" por não ser de uma
 * AC confiável, mas a integridade é garantida) e carimbo de tempo RFC 3161 (PAdES-T).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PadesSignatureService {

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final AssinaturaCertificadoRepository repository;
    private final SecretCipher secretCipher;

    private record Chave(PrivateKey pk, Certificate[] chain) {}

    /**
     * Assina o PDF em PAdES (SHA-256). Se {@code tsaUrl} for informado, embute um
     * carimbo de tempo; se o TSA falhar, assina sem carimbo (PAdES-BES). Nunca
     * altera o conteúdo visível do documento (assinatura invisível).
     */
    public byte[] assinar(byte[] pdf, String tsaUrl) {
        Chave chave = getOrCreate();
        try {
            return doAssinar(pdf, chave, tsaUrl);
        } catch (Exception e) {
            if (tsaUrl != null) {
                log.warn("PAdES com TSA falhou ({}); assinando sem carimbo: {}", tsaUrl, e.getMessage());
                try {
                    return doAssinar(pdf, chave, null);
                } catch (Exception e2) {
                    throw new IllegalStateException("Falha ao assinar PDF (PAdES)", e2);
                }
            }
            throw new IllegalStateException("Falha ao assinar PDF (PAdES)", e);
        }
    }

    private byte[] doAssinar(byte[] pdf, Chave chave, String tsaUrl) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfStamper stp = PdfStamper.createSignature(reader, baos, '\0', null, true);
        PdfSignatureAppearance sap = stp.getSignatureAppearance();
        String reason = "Documento emitido e assinado digitalmente pela plataforma";
        String location = "MeuJet";
        sap.setReason(reason);
        sap.setLocation(location);
        Calendar cal = Calendar.getInstance();
        sap.setSignDate(cal);
        // Assinatura invisível (não altera o layout do documento).

        PdfSignature dic = new PdfSignature(PdfName.ADOBE_PPKLITE, PdfName.ADBE_PKCS7_DETACHED);
        dic.setReason(reason);
        dic.setLocation(location);
        dic.setDate(new PdfDate(cal));
        sap.setCryptoDictionary(dic);

        TSAClient tsa = (tsaUrl != null && !tsaUrl.isBlank()) ? new TSAClientBouncyCastle(tsaUrl) : null;
        int estimated = 8192 + (tsa != null ? tsa.getTokenSizeEstimate() : 0) + 2048;
        HashMap<PdfName, Integer> exc = new HashMap<>();
        exc.put(PdfName.CONTENTS, estimated * 2 + 2);
        sap.preClose(exc);

        PdfPKCS7 sgn = new PdfPKCS7(chave.pk(), chave.chain(), null, "SHA-256", null, false);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        InputStream data = sap.getRangeStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = data.read(buf)) > 0) {
            md.update(buf, 0, n);
        }
        byte[] hash = md.digest();
        byte[] sh = sgn.getAuthenticatedAttributeBytes(hash, cal, null);
        sgn.update(sh, 0, sh.length);
        byte[] encoded = sgn.getEncodedPKCS7(hash, cal, tsa, null);

        if (encoded.length > estimated) {
            throw new IllegalStateException("Assinatura (" + encoded.length
                + ") maior que o espaço reservado (" + estimated + ")");
        }
        byte[] padded = new byte[estimated];
        System.arraycopy(encoded, 0, padded, 0, encoded.length);
        PdfDictionary d2 = new PdfDictionary();
        d2.put(PdfName.CONTENTS, new PdfString(padded).setHexWriting(true));
        sap.close(d2);
        return baos.toByteArray();
    }

    /** Carrega o certificado da plataforma; cria (auto-assinado) na primeira vez. */
    private synchronized Chave getOrCreate() {
        return repository.findFirstByOrderByCreatedAtDesc()
            .map(this::carregar)
            .orElseGet(this::gerarEArmazenar);
    }

    private Chave carregar(AssinaturaCertificado ent) {
        try {
            byte[] certDer = Base64.getDecoder().decode(ent.getCertPem());
            X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(certDer));
            byte[] keyDer = Base64.getDecoder().decode(secretCipher.decrypt(ent.getKeyPemEnc()));
            PrivateKey pk = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyDer));
            return new Chave(pk, new Certificate[]{cert});
        } catch (Exception e) {
            throw new IllegalStateException("Certificado de assinatura inválido", e);
        }
    }

    private Chave gerarEArmazenar() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();

            X500Name dn = new X500Name("CN=MeuJet Assinatura Digital,O=MeuJet");
            BigInteger serial = BigInteger.valueOf(Instant.now().toEpochMilli());
            Date from = Date.from(Instant.now().minus(1, ChronoUnit.DAYS));
            Date to = Date.from(Instant.now().plus(3650, ChronoUnit.DAYS));
            JcaX509v3CertificateBuilder cb =
                new JcaX509v3CertificateBuilder(dn, serial, from, to, dn, kp.getPublic());
            ContentSigner cs = new JcaContentSignerBuilder("SHA256withRSA").build(kp.getPrivate());
            X509Certificate cert = new JcaX509CertificateConverter()
                .getCertificate(cb.build(cs));

            AssinaturaCertificado ent = AssinaturaCertificado.builder()
                .id(UUID.randomUUID())
                .subject(dn.toString())
                .certPem(Base64.getEncoder().encodeToString(cert.getEncoded()))
                .keyPemEnc(secretCipher.encrypt(Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded())))
                .algoritmo("SHA256withRSA")
                .createdAt(Instant.now())
                .build();
            repository.save(ent);
            log.info("Certificado de assinatura PAdES gerado (subject={})", dn);
            return new Chave(kp.getPrivate(), new Certificate[]{cert});
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar o certificado de assinatura", e);
        }
    }
}
