package com.jetski.locacoes.api;

import com.jetski.locacoes.internal.CustomerEmaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Caminho B (emissão da CHA-MTA-E) pelo cliente — P3 do portal.
 * Escopo /v1/customers/** (role CLIENTE, posse via vínculos, sem X-Tenant-Id).
 */
@RestController
@RequestMapping("/v1/customers/reservas/{id}/ema")
@RequiredArgsConstructor
@Tag(name = "Portal do Cliente — CHA-MTA-E", description = "Dados, documentos, declarações e GRU (caminho B)")
public class CustomerEmaController {

    private final CustomerEmaService customerEmaService;

    // ==================== Estado consolidado ====================

    @GetMapping
    @Operation(summary = "Estado do caminho B (dados, anexos, declarações, GRU)")
    public ResponseEntity<CustomerEmaService.EmaEstado> estado(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok(customerEmaService.estado(jwt.getSubject(), id));
    }

    // ==================== Dados pessoais ====================

    @GetMapping("/dados")
    @Operation(summary = "Dados pessoais do cliente nesta loja")
    public ResponseEntity<CustomerEmaService.DadosPessoais> dados(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok(customerEmaService.dadosPessoais(jwt.getSubject(), id));
    }

    public record AtualizarDadosRequest(
        @Size(max = 20) String cpf,
        @Size(max = 30) String rg,
        @Size(max = 20) String orgaoEmissor,
        @Size(max = 60) String nacionalidade,
        @Size(max = 80) String naturalidade,
        Boolean estrangeiro,
        LocalDate dataNascimento,
        @Size(max = 30) String telefone,
        @Size(max = 30) String whatsapp,
        CustomerEmaService.Endereco endereco
    ) {}

    @PutMapping("/dados")
    @Operation(summary = "Atualiza os dados pessoais exigidos pelos anexos NORMAM")
    public ResponseEntity<CustomerEmaService.DadosPessoais> atualizarDados(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody AtualizarDadosRequest r) {
        CustomerEmaService.AtualizarDadosCmd cmd = new CustomerEmaService.AtualizarDadosCmd(
            r.cpf(), r.rg(), r.orgaoEmissor(), r.nacionalidade(), r.naturalidade(),
            r.estrangeiro(), r.dataNascimento(), r.telefone(), r.whatsapp(), r.endereco());
        return ResponseEntity.ok(customerEmaService.atualizarDadosPessoais(jwt.getSubject(), id, cmd));
    }

    // ==================== Anexos ====================

    public record UploadAnexoRequest(
        @NotBlank String tipo,
        @NotBlank String conteudoBase64
    ) {}

    @GetMapping("/anexos")
    @Operation(summary = "Tipos de documento já anexados")
    public ResponseEntity<List<String>> anexos(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok(customerEmaService.anexos(jwt.getSubject(), id));
    }

    @GetMapping("/anexos/{tipo}")
    @Operation(summary = "Imagem do documento anexado (preview do próprio cliente)")
    public ResponseEntity<byte[]> anexoImagem(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable String tipo) {
        CustomerEmaService.AnexoImagem img = customerEmaService.lerAnexo(jwt.getSubject(), id, tipo);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(
                img.contentType() != null ? img.contentType() : "image/jpeg"))
            .body(img.bytes());
    }

    @PostMapping("/anexos")
    @Operation(summary = "Anexa documento (IDENTIDADE | SELFIE | COMPROVANTE_RESIDENCIA)")
    public ResponseEntity<List<String>> uploadAnexo(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody UploadAnexoRequest r) {
        return ResponseEntity.ok(
            customerEmaService.uploadAnexo(jwt.getSubject(), id, r.tipo(), r.conteudoBase64()));
    }

    // ==================== Videoaula / declarações ====================

    public record AtualizarEmaRequest(
        Boolean videoaulaAssistida, Boolean anexoSaude, Boolean anexoRegras,
        Boolean anexoResidencia, Boolean usaLentes, Boolean usaAparelho
    ) {}

    @PutMapping
    @Operation(summary = "Registra videoaula e declarações (5-B/5-C/1-C)")
    public ResponseEntity<CustomerEmaService.EmaEstado> atualizar(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @RequestBody AtualizarEmaRequest r) {
        CustomerEmaService.AtualizarEmaCmd cmd = new CustomerEmaService.AtualizarEmaCmd(
            r.videoaulaAssistida(), r.anexoSaude(), r.anexoRegras(),
            r.anexoResidencia(), r.usaLentes(), r.usaAparelho());
        return ResponseEntity.ok(customerEmaService.atualizarEma(jwt.getSubject(), id, cmd));
    }

    // ==================== GRU ====================

    public record ComprovanteGruRequest(@NotBlank String conteudoBase64) {}

    @PostMapping("/gru/pix")
    @Operation(summary = "Gera a GRU com PIX (idempotente — reaproveita se válida)")
    public ResponseEntity<CustomerEmaService.GruEstado> gruPix(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok(customerEmaService.gruGerarPix(jwt.getSubject(), id));
    }

    @PostMapping("/gru/boleto")
    @Operation(summary = "Gera a GRU em boleto (PDF)")
    public ResponseEntity<CustomerEmaService.GruEstado> gruBoleto(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok(customerEmaService.gruGerarBoleto(jwt.getSubject(), id));
    }

    @GetMapping(value = "/gru/boleto/download", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Baixa o PDF do boleto da GRU")
    public ResponseEntity<byte[]> gruBoletoDownload(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=gru-boleto.pdf")
            .body(customerEmaService.gruBoletoPdf(jwt.getSubject(), id));
    }

    @PostMapping("/gru/verificar")
    @Operation(summary = "Consulta o pagamento do PIX da GRU (PagTesouro)")
    public ResponseEntity<CustomerEmaService.GruVerificacao> gruVerificar(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok(customerEmaService.gruVerificarPagamento(jwt.getSubject(), id));
    }

    @PostMapping("/gru/comprovante")
    @Operation(summary = "Envia comprovante manual da GRU (imagem/PDF)")
    public ResponseEntity<CustomerEmaService.EmaEstado> gruComprovante(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody ComprovanteGruRequest r) {
        return ResponseEntity.ok(
            customerEmaService.gruComprovanteManual(jwt.getSubject(), id, r.conteudoBase64()));
    }
}
