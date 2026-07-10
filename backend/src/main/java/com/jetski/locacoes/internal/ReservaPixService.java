package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.locacoes.internal.repository.ReservaRepository;
import com.jetski.shared.email.EmailService;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.pix.BrCodePix;
import com.jetski.shared.security.TenantContext;
import com.jetski.tenant.TenantQueryService;
import com.jetski.tenant.domain.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * PIX da cobrança presencial do balcão: gera o "copia e cola" (BR Code, chave
 * estática da loja) com o valor digitado no passo de Pagamento e o envia ao
 * cliente por e-mail quando solicitado. Nada é persistido — o recebimento
 * continua sendo confirmado manualmente via registrar-pagamento (folio).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservaPixService {

    private final ReservaRepository reservaRepository;
    private final ClienteRepository clienteRepository;
    private final TenantQueryService tenantQueryService;
    private final EmailService emailService;

    /** PIX gerado para a cobrança (o copia-e-cola é o próprio conteúdo do QR). */
    public record PixCobranca(String chave, String copiaECola, BigDecimal valor) {}

    /**
     * Gera o BR Code do valor a cobrar. Exige a chave PIX da loja configurada
     * (Configurações → Geral); recebedor = razão social, cidade do tenant.
     */
    @Transactional(readOnly = true)
    public PixCobranca gerar(UUID reservaId, BigDecimal valor) {
        if (valor == null || valor.signum() <= 0) {
            throw new BusinessException("Valor da cobrança PIX deve ser maior que zero");
        }
        Reserva reserva = findReserva(reservaId);
        Tenant tenant = tenantQueryService.findById(reserva.getTenantId());
        if (tenant == null || tenant.getPixChave() == null || tenant.getPixChave().isBlank()) {
            throw new BusinessException(
                "A loja ainda não configurou a chave PIX (Configurações → Geral)");
        }
        String cidade = tenant.getCidade() != null && !tenant.getCidade().isBlank()
            ? tenant.getCidade() : "Brasil";
        BigDecimal exato = valor.setScale(2, RoundingMode.HALF_UP);
        String copiaECola = BrCodePix.gerar(
            tenant.getPixChave().trim(), exato, tenant.getRazaoSocial(), cidade);
        return new PixCobranca(tenant.getPixChave().trim(), copiaECola, exato);
    }

    /**
     * Envia o copia-e-cola por e-mail ao cliente da reserva.
     *
     * @return o e-mail de destino (para feedback ao operador)
     */
    @Transactional(readOnly = true)
    public String enviarEmail(UUID reservaId, BigDecimal valor) {
        PixCobranca pix = gerar(reservaId, valor);
        Reserva reserva = findReserva(reservaId);
        Cliente cliente = clienteRepository.findById(reserva.getClienteId())
            .orElseThrow(() -> new NotFoundException("Cliente não encontrado: " + reserva.getClienteId()));
        String email = cliente.getEmail();
        if (email == null || email.isBlank()) {
            throw new BusinessException("Cliente sem e-mail cadastrado — atualize a ficha do cliente");
        }

        Tenant tenant = tenantQueryService.findById(reserva.getTenantId());
        String loja = tenant != null ? tenant.getRazaoSocial() : "";
        String valorBr = pix.valor().toPlainString().replace('.', ',');

        StringBuilder b = new StringBuilder();
        b.append("<p>Olá, ").append(cliente.getNome()).append("!</p>");
        b.append("<p>Segue o PIX para o pagamento do seu passeio:</p><ul>");
        b.append("<li><b>Valor:</b> R$ ").append(valorBr).append("</li>");
        b.append("<li><b>Recebedor:</b> ").append(loja).append("</li>");
        b.append("</ul>");
        b.append("<p><b>PIX copia-e-cola:</b></p><p style=\"word-break:break-all;font-family:monospace\">")
         .append(pix.copiaECola()).append("</p>");
        b.append("<p>Abra o app do seu banco, escolha PIX &gt; Pix Copia e Cola e cole o código acima.</p>");
        b.append("<p>").append(loja).append("</p>");

        emailService.sendEmail(email, "PIX para pagamento — " + loja, b.toString());
        log.info("PIX copia-e-cola enviado por e-mail: reserva={}, valor={}", reservaId, pix.valor());
        return email;
    }

    /** Lookup tenant-scoped explícito (regra 1 — nunca confiar só na RLS). */
    private Reserva findReserva(UUID reservaId) {
        return reservaRepository.findById(reservaId)
            .filter(r -> r.getTenantId().equals(TenantContext.getTenantId()))
            .orElseThrow(() -> new NotFoundException("Reserva não encontrada: " + reservaId));
    }
}
