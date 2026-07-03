package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.domain.ReservaAceite;
import com.jetski.locacoes.internal.repository.ReservaAceiteRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.security.TenantContext;
import com.jetski.shared.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;

/**
 * Serviço de aceite/assinatura presencial (balcão).
 * Arquiva a imagem da assinatura no storage e grava as evidências
 * (operador, IP, user-agent, hash, origem=BALCAO).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AceiteService {

    private final ReservaAceiteRepository repository;
    private final ReservaRepository reservaRepository;
    private final StorageService storageService;
    private final AceiteOtpService otpService;

    @Transactional(readOnly = true)
    public Optional<ReservaAceite> getUltimo(UUID reservaId) {
        return repository.findFirstByReservaIdOrderByAceitoEmDesc(reservaId);
    }

    @Transactional
    public ReservaAceite registrar(UUID reservaId, ReservaAceite.Metodo metodo,
                                   byte[] assinatura, String ip, String userAgent) {
        return registrar(reservaId, metodo, assinatura, ip, userAgent, "BALCAO");
    }

    /**
     * Registra o aceite com origem explícita: BALCAO (staff, tablet) ou PORTAL
     * (cliente assina remotamente — P2). No portal não há operador
     * (TenantContext.usuarioId nulo) e a posse do cliente é garantida pelo
     * chamador (CustomerReservaService.localizar).
     */
    @Transactional
    public ReservaAceite registrar(UUID reservaId, ReservaAceite.Metodo metodo,
                                   byte[] assinatura, String ip, String userAgent, String origem) {
        Reserva reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new NotFoundException("Reserva não encontrada: " + reservaId));

        boolean temAssinatura = assinatura != null && assinatura.length > 0;
        if (metodo == ReservaAceite.Metodo.SIGNATURE_PAD && !temAssinatura) {
            throw new BusinessException("Assinatura (imagem) é obrigatória para o método SIGNATURE_PAD");
        }

        // OTP (Fase B): se o tenant exige, o código já deve ter sido verificado.
        AceiteOtpService.OtpStatus otp = otpService.status(reservaId);
        Boolean otpVerificado = null;
        String otpCanal = null, otpDestino = null;
        if (otp.ativo()) {
            String ver = otpService.verificacaoValida(reservaId);
            if (ver == null) {
                throw new BusinessException(
                    "Confirmação por código (OTP) pendente. Envie e valide o código antes de assinar.");
            }
            int sep = ver.indexOf('|');
            otpVerificado = true;
            otpCanal = sep > 0 ? ver.substring(0, sep) : ver;
            otpDestino = sep > 0 ? ver.substring(sep + 1) : null;
        }

        String s3Key = null;
        String hash = null;
        if (temAssinatura) {
            s3Key = String.format("%s/reserva/%s/assinatura-%d.png",
                    reserva.getTenantId(), reservaId, System.currentTimeMillis());
            storageService.putObject(s3Key, assinatura, "image/png");
            hash = sha256Hex(assinatura);
        }

        ReservaAceite aceite = ReservaAceite.builder()
            .tenantId(reserva.getTenantId())
            .reservaId(reservaId)
            .operadorId(TenantContext.getUsuarioId())
            .metodo(metodo)
            .assinaturaS3Key(s3Key)
            .hashSha256(hash)
            .ip(ip)
            .userAgent(userAgent)
            .origem(origem)
            .otpVerificado(otpVerificado)
            .otpCanal(otpCanal)
            .otpDestino(otpDestino)
            .build();

        ReservaAceite saved = repository.save(aceite);
        if (otp.ativo()) {
            otpService.consumir(reservaId);
        }
        log.info("Aceite registrado: reservaId={}, metodo={}, s3Key={}", reservaId, metodo, s3Key);

        // Termos assinados → a reserva deixa de ser rascunho e passa a valer (entra na
        // fila/agenda, pode embarcar). A emissão dos documentos NORMAM (que exige a GRU
        // paga) é um passo posterior — pode acontecer agora ou depois.
        if (reserva.getStatus() == Reserva.ReservaStatus.RASCUNHO) {
            reserva.setStatus(Reserva.ReservaStatus.PENDENTE);
            reservaRepository.save(reserva);
            log.info("Reserva {} confirmada pelos termos: RASCUNHO → PENDENTE", reservaId);
        }
        return saved;
    }

    private static String sha256Hex(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
