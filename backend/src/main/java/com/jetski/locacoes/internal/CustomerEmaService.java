package com.jetski.locacoes.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.ClienteAnexo;
import com.jetski.locacoes.domain.Reserva;
import com.jetski.locacoes.domain.ReservaHabilitacao;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Caminho B (CHA-MTA-E via EMA) pelo CLIENTE no portal — P3.
 *
 * Reusa o maquinário do balcão (ClienteAnexoService, HabilitacaoService,
 * GruService) atrás do escopo /v1/customers/**: a posse/tenant vêm de
 * CustomerReservaService.localizar (vínculos + set_config transaction-local).
 * A emissão final continua com o staff (consome créditos da plataforma).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerEmaService {

    private final CustomerReservaService customerReservaService;
    private final ClienteRepository clienteRepository;
    private final ClienteAnexoService clienteAnexoService;
    private final HabilitacaoService habilitacaoService;
    private final GruService gruService;
    private final CustomerProfileService customerProfileService;
    private final GruAutoEmissaoService gruAutoEmissaoService;
    private final ObjectMapper objectMapper;

    // ============================ Dados pessoais ============================

    public record Endereco(String cep, String logradouro, String numero,
                           String complemento, String bairro, String cidade, String uf) {}

    public record DadosPessoais(
        String nome, String cpf, String rg, String orgaoEmissor,
        String nacionalidade, String naturalidade, Boolean estrangeiro,
        LocalDate dataNascimento, String telefone, String whatsapp,
        Endereco endereco, boolean completos) {}

    @Transactional(readOnly = true)
    public DadosPessoais dadosPessoais(String sub, UUID reservaId) {
        Cliente c = clienteDaReserva(sub, reservaId);
        return toDadosPessoais(c);
    }

    public record AtualizarDadosCmd(
        String cpf, String rg, String orgaoEmissor,
        String nacionalidade, String naturalidade, Boolean estrangeiro,
        LocalDate dataNascimento, String telefone, String whatsapp,
        Endereco endereco) {}

    /**
     * O cliente completa os próprios dados exigidos pelos anexos NORMAM.
     * CPF só pode ser DEFINIDO (nunca trocado) — troca exige o staff.
     */
    @Transactional
    public DadosPessoais atualizarDadosPessoais(String sub, UUID reservaId, AtualizarDadosCmd cmd) {
        Cliente c = clienteDaReserva(sub, reservaId);

        if (cmd.cpf() != null && !cmd.cpf().isBlank()) {
            String novo = cmd.cpf().trim();
            if (c.getDocumento() == null || c.getDocumento().isBlank()) {
                clienteRepository.findByDocumento(novo).ifPresent(outro -> {
                    if (!outro.getId().equals(c.getId())) {
                        throw new BusinessException(
                            "Este CPF já está em outro cadastro desta loja — fale com a loja para vincular.");
                    }
                });
                c.setDocumento(novo);
            } else if (!c.getDocumento().equals(novo)) {
                throw new BusinessException(
                    "O CPF do cadastro não pode ser alterado pelo portal — fale com a loja.");
            }
        }

        if (cmd.rg() != null) c.setRg(blankToNull(cmd.rg()));
        if (cmd.orgaoEmissor() != null) c.setOrgaoEmissor(blankToNull(cmd.orgaoEmissor()));
        if (cmd.nacionalidade() != null) c.setNacionalidade(blankToNull(cmd.nacionalidade()));
        if (cmd.naturalidade() != null) c.setNaturalidade(blankToNull(cmd.naturalidade()));
        if (cmd.estrangeiro() != null) c.setEstrangeiro(cmd.estrangeiro());
        if (cmd.dataNascimento() != null) c.setDataNascimento(cmd.dataNascimento());
        if (cmd.telefone() != null) c.setTelefone(blankToNull(cmd.telefone()));
        if (cmd.whatsapp() != null) c.setWhatsapp(blankToNull(cmd.whatsapp()));
        if (cmd.endereco() != null) {
            try {
                c.setEnderecoJson(objectMapper.writeValueAsString(cmd.endereco()));
            } catch (Exception e) {
                throw new BusinessException("Endereço inválido");
            }
        }

        clienteRepository.save(c);
        // write-through da IDENTIDADE para o perfil global (endereço/telefone
        // permanecem só na loja — decisão de produto)
        customerProfileService.absorverIdentidade(sub, c.getNome(), c);
        log.info("Dados pessoais atualizados pelo cliente no portal: cliente={}", c.getId());

        // retry da auto-emissão: se o pagamento já foi confirmado e a GRU
        // ainda não saiu (faltavam dados), agora ela sai sozinha
        CustomerReservaService.Localizada loc = customerReservaService.localizar(sub, reservaId);
        if (Boolean.TRUE.equals(loc.reserva().getSinalPago())) {
            try {
                gruAutoEmissaoService.emitirSeAplicavel(loc.reserva().getTenantId(), reservaId);
            } catch (Exception e) {
                log.warn("Retry de auto-GRU falhou (reserva={}): {}", reservaId, e.getMessage());
            }
        }
        return toDadosPessoais(c);
    }

    // ============================ Anexos (documentos) ============================

    @Transactional(readOnly = true)
    public List<String> anexos(String sub, UUID reservaId) {
        Cliente c = clienteDaReserva(sub, reservaId);
        return clienteAnexoService.listar(c.getId()).stream()
            .map(a -> a.getTipo().name())
            .toList();
    }

    @Transactional
    public List<String> uploadAnexo(String sub, UUID reservaId, String tipo, String conteudoBase64) {
        ClienteAnexo.Tipo t = ClienteAnexoService.parseTipoPortal(tipo);
        Cliente c = clienteDaReserva(sub, reservaId);
        clienteAnexoService.salvar(c.getId(), t, conteudoBase64, "PORTAL", sub);
        log.info("Anexo {} enviado pelo cliente no portal: cliente={}", t, c.getId());
        return anexos(sub, reservaId);
    }

    /** Imagem do anexo do PRÓPRIO cliente (preview no portal). */
    @Transactional(readOnly = true)
    public ClienteAnexoService.AnexoImagem lerAnexo(String sub, UUID reservaId, String tipo) {
        ClienteAnexo.Tipo t = ClienteAnexoService.parseTipoPortal(tipo);
        Cliente c = clienteDaReserva(sub, reservaId);
        ClienteAnexo anexo = clienteAnexoService.buscar(c.getId(), t)
            .orElseThrow(() -> new com.jetski.shared.exception.NotFoundException(
                "Documento ainda não anexado"));
        return new ClienteAnexoService.AnexoImagem(
            clienteAnexoService.lerImagem(anexo), anexo.getContentType());
    }

    // ============================ Flags EMA (videoaula/declarações) ============================

    public record AtualizarEmaCmd(
        Boolean videoaulaAssistida, Boolean anexoSaude, Boolean anexoRegras,
        Boolean anexoResidencia, Boolean usaLentes, Boolean usaAparelho) {}

    @Transactional
    public EmaEstado atualizarEma(String sub, UUID reservaId, AtualizarEmaCmd cmd) {
        CustomerReservaService.Localizada l = customerReservaService.localizar(sub, reservaId);

        ReservaHabilitacao dados = ReservaHabilitacao.builder()
            .via(ReservaHabilitacao.Via.EMA)
            .videoaulaEm(Boolean.TRUE.equals(cmd.videoaulaAssistida()) ? Instant.now() : null)
            .anexoSaude(cmd.anexoSaude())
            .anexoRegras(cmd.anexoRegras())
            .anexoResidencia(cmd.anexoResidencia())
            .usaLentes(cmd.usaLentes())
            .usaAparelho(cmd.usaAparelho())
            .build();
        habilitacaoService.registrar(l.reserva().getId(), dados);
        return estado(sub, reservaId);
    }

    // ============================ GRU ============================

    public record GruEstado(
        String numero, BigDecimal valor, boolean pago,
        String pixCopiaECola, Instant pixExpiracao,
        boolean boletoDisponivel, boolean comprovanteDisponivel,
        boolean sucesso, boolean reaproveitada, String erroMensagem) {}

    @Transactional
    public GruEstado gruGerarPix(String sub, UUID reservaId) {
        CustomerReservaService.Localizada l = customerReservaService.localizar(sub, reservaId);
        GruService.GruGeracao g = gruService.gerarGru(l.reserva().getId());
        return toGruEstado(g.habilitacao(), g.sucesso(), g.reaproveitada(), g.erroMensagem());
    }

    @Transactional
    public GruEstado gruGerarBoleto(String sub, UUID reservaId) {
        CustomerReservaService.Localizada l = customerReservaService.localizar(sub, reservaId);
        GruService.BoletoGeracao b = gruService.gerarBoleto(l.reserva().getId());
        return toGruEstado(b.habilitacao(), b.sucesso(), b.reaproveitada(), b.erroMensagem());
    }

    public record GruVerificacao(boolean pago, String situacao, boolean comprovanteDisponivel) {}

    @Transactional
    public GruVerificacao gruVerificarPagamento(String sub, UUID reservaId) {
        CustomerReservaService.Localizada l = customerReservaService.localizar(sub, reservaId);
        GruService.VerificacaoPagamento v = gruService.verificarPagamento(l.reserva().getId());
        return new GruVerificacao(v.pago(), v.situacao(), v.comprovanteDisponivel());
    }

    @Transactional
    public EmaEstado gruComprovanteManual(String sub, UUID reservaId, String conteudoBase64) {
        CustomerReservaService.Localizada l = customerReservaService.localizar(sub, reservaId);
        byte[] bytes = decodeDataUrl(conteudoBase64);
        boolean ehImagem = !ehPdf(bytes);
        gruService.registrarComprovanteManual(l.reserva().getId(), bytes, ehImagem);
        return estado(sub, reservaId);
    }

    @Transactional(readOnly = true)
    public byte[] gruBoletoPdf(String sub, UUID reservaId) {
        CustomerReservaService.Localizada l = customerReservaService.localizar(sub, reservaId);
        return gruService.baixarBoletoPdf(l.reserva().getId());
    }

    // ============================ Estado consolidado ============================

    public record EmaEstado(
        boolean dadosPessoaisCompletos,
        List<String> anexosPresentes,
        boolean videoaulaAssistida,
        boolean anexoSaude, boolean anexoRegras, boolean anexoResidencia,
        boolean usaLentes, boolean usaAparelho,
        GruEstado gru, boolean resolvida) {}

    @Transactional(readOnly = true)
    public EmaEstado estado(String sub, UUID reservaId) {
        CustomerReservaService.Localizada l = customerReservaService.localizar(sub, reservaId);
        Cliente c = clienteRepository.findById(l.reserva().getClienteId())
            .orElseThrow(() -> new BusinessException("Cliente não encontrado"));
        Optional<ReservaHabilitacao> hab = habilitacaoService.getByReserva(l.reserva().getId());

        List<String> anexos = clienteAnexoService.listar(c.getId()).stream()
            .map(a -> a.getTipo().name())
            .toList();

        return new EmaEstado(
            toDadosPessoais(c).completos(),
            anexos,
            hab.map(h -> h.getVideoaulaEm() != null).orElse(false),
            hab.map(h -> Boolean.TRUE.equals(h.getAnexoSaude())).orElse(false),
            hab.map(h -> Boolean.TRUE.equals(h.getAnexoRegras())).orElse(false),
            hab.map(h -> Boolean.TRUE.equals(h.getAnexoResidencia())).orElse(false),
            hab.map(h -> Boolean.TRUE.equals(h.getUsaLentes())).orElse(false),
            hab.map(h -> Boolean.TRUE.equals(h.getUsaAparelho())).orElse(false),
            hab.map(h -> toGruEstado(h, true, false, null))
                .orElse(new GruEstado(null, null, false, null, null, false, false, true, false, null)),
            hab.map(h -> Boolean.TRUE.equals(h.getResolvida())).orElse(false));
    }

    // ============================ Internos ============================

    private Cliente clienteDaReserva(String sub, UUID reservaId) {
        CustomerReservaService.Localizada l = customerReservaService.localizar(sub, reservaId);
        return clienteRepository.findById(l.reserva().getClienteId())
            .orElseThrow(() -> new BusinessException("Cliente não encontrado"));
    }

    private DadosPessoais toDadosPessoais(Cliente c) {
        Endereco endereco = null;
        if (c.getEnderecoJson() != null && !c.getEnderecoJson().isBlank()) {
            try {
                endereco = objectMapper.readValue(c.getEnderecoJson(), Endereco.class);
            } catch (Exception ignored) {
                // endereço em formato legado — cliente preenche de novo
            }
        }
        boolean completos = notBlank(c.getNome()) && notBlank(c.getDocumento())
            && notBlank(c.getRg()) && notBlank(c.getNacionalidade())
            && notBlank(c.getNaturalidade())
            && endereco != null && notBlank(endereco.cep()) && notBlank(endereco.logradouro());
        return new DadosPessoais(
            c.getNome(), c.getDocumento(), c.getRg(), c.getOrgaoEmissor(),
            c.getNacionalidade(), c.getNaturalidade(), c.getEstrangeiro(),
            c.getDataNascimento(), c.getTelefone(), c.getWhatsapp(),
            endereco, completos);
    }

    private GruEstado toGruEstado(ReservaHabilitacao h, boolean sucesso,
                                  boolean reaproveitada, String erroMensagem) {
        return new GruEstado(
            h.getGruNumero(), h.getGruValor(), Boolean.TRUE.equals(h.getGruPago()),
            h.getGruPixCopiaECola(), h.getGruPixExpiracao(),
            h.getGruPdfS3Key() != null, h.getGruComprovanteS3Key() != null,
            sucesso, reaproveitada, erroMensagem);
    }

    private static byte[] decodeDataUrl(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new BusinessException("Arquivo é obrigatório");
        }
        String dados = base64.contains(",") ? base64.substring(base64.indexOf(',') + 1) : base64;
        try {
            byte[] bytes = java.util.Base64.getDecoder().decode(dados);
            if (bytes.length == 0 || bytes.length > 8L * 1024 * 1024) {
                throw new BusinessException("Arquivo deve ter até 8 MB");
            }
            return bytes;
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Arquivo inválido");
        }
    }

    private static boolean ehPdf(byte[] bytes) {
        return bytes.length > 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
