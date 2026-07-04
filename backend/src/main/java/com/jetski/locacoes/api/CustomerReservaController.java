package com.jetski.locacoes.api;

import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.internal.CustomerReservaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reservas do cliente final (portal, P1) — escopo /v1/customers/** (role
 * CLIENTE, sem X-Tenant-Id; a loja vem no payload/vínculo).
 */
@Slf4j
@RestController
@RequestMapping("/v1/customers/reservas")
@RequiredArgsConstructor
@Tag(name = "Portal do Cliente — Reservas", description = "Reserva online, checklist e comprovante de pagamento")
public class CustomerReservaController {

    private final CustomerReservaService customerReservaService;

    public record CriarReservaRequest(
        @NotBlank String lojaSlug,
        @NotNull UUID modeloId,
        @NotNull @Future LocalDateTime dataInicio,
        @NotNull @Future LocalDateTime dataFimPrevista,
        @Size(max = 1000) String observacoes,
        String pagamentoTipo,
        @Size(max = 20) String cpf,
        @Size(max = 30) String telefone,
        /** true = cliente já tem CHA (sem GRU); false = loja emite via EMA. */
        Boolean possuiCha
    ) {}

    public record AnexarComprovanteRequest(
        @NotBlank String tipo,
        @NotNull @DecimalMin("0.01") BigDecimal valorInformado,
        @NotBlank String contentType,
        @NotBlank String dataBase64
    ) {}

    @PostMapping
    @Operation(summary = "Cria reserva na loja (1º contato cria/vincula o Cliente)")
    public ResponseEntity<CustomerReservaService.ReservaCliente> criar(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CriarReservaRequest request) {
        CustomerReservaService.CriarCmd cmd = new CustomerReservaService.CriarCmd(
            request.lojaSlug().trim(),
            request.modeloId(),
            request.dataInicio(),
            request.dataFimPrevista(),
            request.observacoes(),
            parseTipo(request.pagamentoTipo()),
            request.cpf(),
            request.telefone(), request.possuiCha());
        CustomerReservaService.ReservaCliente criada = customerReservaService.criar(
            jwt.getSubject(),
            jwt.getClaimAsString("email"),
            jwt.getClaimAsString("name"),
            cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(criada);
    }

    @GetMapping
    @Operation(summary = "Minhas reservas (todas as lojas vinculadas)")
    public ResponseEntity<List<CustomerReservaService.ReservaCliente>> minhas(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(customerReservaService.minhasReservas(jwt.getSubject()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhe da reserva (inclui PIX com valor exato)")
    public ResponseEntity<CustomerReservaService.ReservaCliente> detalhe(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        return ResponseEntity.ok(customerReservaService.detalhe(jwt.getSubject(), id));
    }

    @GetMapping("/{id}/checklist")
    @Operation(summary = "Prontidão da reserva (pagamento / habilitação / termos / e-mail)")
    public ResponseEntity<CustomerReservaService.Checklist> checklist(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        boolean emailVerified = Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"));
        return ResponseEntity.ok(customerReservaService.checklist(jwt.getSubject(), id, emailVerified));
    }

    @PostMapping("/{id}/comprovante")
    @Operation(summary = "Anexa comprovante PIX (leva o pagamento para EM_ANALISE)")
    public ResponseEntity<CustomerReservaService.ReservaCliente> comprovante(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody AnexarComprovanteRequest request) {
        CustomerReservaService.AnexarComprovanteCmd cmd = new CustomerReservaService.AnexarComprovanteCmd(
            parseTipo(request.tipo()),
            request.valorInformado(),
            request.contentType(),
            request.dataBase64());
        return ResponseEntity.ok(customerReservaService.anexarComprovante(jwt.getSubject(), id, cmd));
    }

    // ==================== Termos / aceite (P2) ====================

    public record AssinarTermoRequest(@NotBlank String assinaturaBase64) {}

    public record OtpVerificarRequest(@NotBlank @Size(min = 4, max = 8) String codigo) {}

    @GetMapping("/{id}/aceite/otp")
    @Operation(summary = "Status do OTP do aceite (ativo? verificado?)")
    public ResponseEntity<com.jetski.locacoes.internal.AceiteOtpService.OtpStatus> otpStatus(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok(customerReservaService.otpStatus(jwt.getSubject(), id));
    }

    @PostMapping("/{id}/aceite/otp/enviar")
    @Operation(summary = "Envia o código OTP (e-mail/WhatsApp conforme a loja)")
    public ResponseEntity<com.jetski.locacoes.internal.AceiteOtpService.EnvioResultado> otpEnviar(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok(customerReservaService.otpEnviar(jwt.getSubject(), id));
    }

    @PostMapping("/{id}/aceite/otp/verificar")
    @Operation(summary = "Verifica o código OTP digitado")
    public ResponseEntity<Map<String, Boolean>> otpVerificar(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody OtpVerificarRequest request) {
        boolean ok = customerReservaService.otpVerificar(jwt.getSubject(), id, request.codigo().trim());
        return ResponseEntity.ok(Map.of("verificado", ok));
    }

    @PostMapping("/{id}/aceite")
    @Operation(summary = "Assina o termo remotamente (SignaturePad; OTP se a loja exigir)")
    public ResponseEntity<Map<String, String>> assinar(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody AssinarTermoRequest request,
            jakarta.servlet.http.HttpServletRequest http) {
        String ip = http.getHeader("X-Forwarded-For") != null
            ? http.getHeader("X-Forwarded-For").split(",")[0].trim()
            : http.getRemoteAddr();
        customerReservaService.assinarTermo(jwt.getSubject(), id,
            request.assinaturaBase64(), ip, http.getHeader("User-Agent"));
        return ResponseEntity.ok(Map.of("message", "Termo assinado com sucesso"));
    }

    // ==================== Habilitação — caminho A (P2) ====================

    public record EnviarChaRequest(
        @Size(max = 40) String categoria,
        @NotBlank @Size(max = 60) String numero,
        java.time.LocalDate validade,
        String fotoBase64
    ) {}

    @GetMapping("/{id}/habilitacao")
    @Operation(summary = "Estado da habilitação da reserva (visão do cliente)")
    public ResponseEntity<CustomerReservaService.HabilitacaoCliente> habilitacao(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok(customerReservaService.habilitacao(jwt.getSubject(), id));
    }

    @PostMapping("/{id}/habilitacao/cha")
    @Operation(summary = "Envia a própria CHA (caminho A) + foto do documento")
    public ResponseEntity<CustomerReservaService.HabilitacaoCliente> enviarCha(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody EnviarChaRequest request) {
        CustomerReservaService.EnviarChaCmd cmd = new CustomerReservaService.EnviarChaCmd(
            request.categoria(), request.numero(), request.validade(), request.fotoBase64());
        return ResponseEntity.ok(customerReservaService.enviarCha(jwt.getSubject(), id, cmd));
    }

    private Reserva.PagamentoTipo parseTipo(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            return null;
        }
        try {
            return Reserva.PagamentoTipo.valueOf(tipo.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new com.jetski.shared.exception.BusinessException(
                "Tipo de pagamento inválido — use SINAL ou TOTAL");
        }
    }
}
