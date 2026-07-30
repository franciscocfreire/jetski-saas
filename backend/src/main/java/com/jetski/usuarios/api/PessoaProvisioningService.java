package com.jetski.usuarios.api;

import com.jetski.usuarios.domain.Usuario;
import com.jetski.usuarios.domain.event.PessoaProvisionadaEvent;
import com.jetski.usuarios.internal.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Provisão da PESSOA (identidade única, F0 — IDENTIDADE_UNICA_SPEC §4/D4).
 *
 * <p>Garante que um sub autenticado do Keycloak exista como {@code usuario}
 * (raiz única da pessoa) com o vínculo em {@code usuario_identity_provider}.
 * Idempotente: chamado a cada ativação de claim e primeira reserva logada.
 *
 * <p><b>Contrato de segurança (princípio 4 da spec)</b>: NUNCA roda por
 * matching espontâneo de e-mail. O chamador precisa ter em mãos o sub
 * AUTENTICADO e um e-mail cuja posse foi comprovada no fluxo (senha temporária
 * do claim entregue ao e-mail, ou verify-email/IdP verificado do próprio
 * token). A reutilização de um {@code usuario} existente com o mesmo e-mail é
 * segura porque o realm não permite e-mails duplicados: a conta autenticada É
 * a conta daquele e-mail — é a acumulação de papéis prevista na regra de
 * identidade única (ex.: staff virando cliente), não um link cego.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PessoaProvisioningService {

    private static final String PROVIDER = "keycloak";

    private final UsuarioRepository usuarioRepository;
    private final IdentityProviderMappingService mappingService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * @param providerUserId sub autenticado do Keycloak
     * @param email          e-mail com posse comprovada pelo fluxo chamador
     * @param nome           nome para criação (ignorado se a pessoa já existe)
     * @param origem         rótulo do fluxo (CLAIM, RESERVA_PORTAL) — vai para a trilha
     * @return id do usuario (a pessoa)
     */
    @Transactional
    public UUID provisionarPessoa(String providerUserId, String email, String nome, String origem) {
        // tryResolve (Optional), NÃO resolveUsuarioId: o contrato deste lança
        // NotFoundException (pensado para o TenantFilter) e, atravessando o
        // proxy @Transactional, marcaria a transação como rollback-only mesmo
        // com catch. Ausência aqui é o caminho normal de provisionamento.
        var mapeado = mappingService.tryResolveUsuarioId(PROVIDER, providerUserId);
        if (mapeado.isPresent()) {
            return mapeado.get();
        }

        // Sem mapping para o sub: reusa o usuario do MESMO e-mail (acumulação de
        // papéis — o realm garante 1 conta por e-mail) ou cria a pessoa agora.
        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
        boolean novo = usuario == null;
        if (novo) {
            usuario = usuarioRepository.save(Usuario.builder()
                .email(email)
                .nome(nome)
                .ativo(true)
                // posse do e-mail comprovada pelo fluxo chamador (contrato acima)
                .emailVerified(true)
                .emailVerifiedAt(Instant.now())
                .build());
        }

        mappingService.linkProvider(usuario.getId(), PROVIDER, providerUserId);
        eventPublisher.publishEvent(PessoaProvisionadaEvent.of(
            usuario.getId(), providerUserId, novo, origem));
        log.info("[IDENTIDADE] Pessoa provisionada: usuarioId={}, novo={}, origem={}",
            usuario.getId(), novo, origem);
        return usuario.getId();
    }
}
