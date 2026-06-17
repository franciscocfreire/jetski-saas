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

    @Transactional(readOnly = true)
    public Optional<ReservaAceite> getUltimo(UUID reservaId) {
        return repository.findFirstByReservaIdOrderByAceitoEmDesc(reservaId);
    }

    @Transactional
    public ReservaAceite registrar(UUID reservaId, ReservaAceite.Metodo metodo,
                                   byte[] assinatura, String ip, String userAgent) {
        Reserva reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new NotFoundException("Reserva não encontrada: " + reservaId));

        boolean temAssinatura = assinatura != null && assinatura.length > 0;
        if (metodo == ReservaAceite.Metodo.SIGNATURE_PAD && !temAssinatura) {
            throw new BusinessException("Assinatura (imagem) é obrigatória para o método SIGNATURE_PAD");
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
            .origem("BALCAO")
            .build();

        ReservaAceite saved = repository.save(aceite);
        log.info("Aceite registrado: reservaId={}, metodo={}, s3Key={}", reservaId, metodo, s3Key);
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
