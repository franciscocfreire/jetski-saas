package com.jetski.usuarios.internal;

import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.exception.NotFoundException;
import com.jetski.shared.security.PapelPlataforma;
import com.jetski.shared.security.TenantContext;
import com.jetski.usuarios.domain.Usuario;
import com.jetski.usuarios.event.OperadorPlataformaAlteradoEvent;
import com.jetski.usuarios.internal.repository.UsuarioGlobalRolesRepository;
import com.jetski.usuarios.internal.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Gestão dos OPERADORES DA PLATAFORMA (F2).
 *
 * <p>Antes disso, conceder acesso de plataforma exigia editar {@code PLATFORM_ADMIN_EMAILS}
 * no {@code .env} e reiniciar o backend, ou um {@code INSERT} manual no banco — sem trilha,
 * sem revisão. Esta é a superfície auditada que substitui o SQL.
 *
 * <p><strong>Alcance × poder:</strong> qualquer papel de plataforma implica
 * {@code unrestricted_access = true} (o {@code TenantFilter} precisa disso para o operador
 * enxergar empresas sem ser membro); o que cada papel PODE fazer é decisão do
 * {@code platform.rego}.
 *
 * <p><strong>Travas contra auto-bloqueio:</strong> não é possível revogar o próprio acesso
 * nem remover o último {@code PLATFORM_ADMIN}. Sem isso, um clique deixaria a plataforma
 * sem ninguém capaz de conceder acesso de volta — e o conserto seria SQL manual em produção.
 *
 * @since 0.9.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformOperadorService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioGlobalRolesRepository globalRolesRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * @param usuarioId  id interno
     * @param email      e-mail de login
     * @param nome       nome exibido
     * @param ativo      conta ativa
     * @param papeis     papéis de plataforma reconhecidos
     * @param concedidoEm quando o acesso foi concedido/alterado
     */
    public record Operador(
        UUID usuarioId, String email, String nome, boolean ativo,
        List<String> papeis, Instant concedidoEm) {}

    /** Operadores atuais, ordenados por e-mail. */
    @Transactional(readOnly = true)
    public List<Operador> listar() {
        List<Operador> operadores = new ArrayList<>();
        for (UsuarioGlobalRoles global : globalRolesRepository.findAll()) {
            List<String> papeis = papeisDePlataforma(global);
            if (papeis.isEmpty()) {
                continue;   // linha legada sem papel de plataforma: não é operador
            }
            usuarioRepository.findById(global.getUsuarioId()).ifPresent(u -> operadores.add(
                new Operador(u.getId(), u.getEmail(), u.getNome(),
                    Boolean.TRUE.equals(u.getAtivo()), papeis, global.getUpdatedAt())));
        }
        operadores.sort((a, b) -> a.email().compareToIgnoreCase(b.email()));
        return operadores;
    }

    /**
     * Concede/atualiza os papéis de plataforma de um usuário EXISTENTE.
     *
     * <p>Não cria conta de propósito: o acesso de plataforma é concedido a quem já se
     * cadastrou e ativou (mesma ordem documentada em SUPERADMIN.md — cadastrar, ativar,
     * promover). Criar o usuário aqui abriria caminho para conceder acesso a um e-mail
     * digitado errado.
     */
    @Transactional
    @CacheEvict(value = "tenant-access", allEntries = true)
    public Operador conceder(String email, List<String> papeisSolicitados) {
        Usuario usuario = usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new NotFoundException(
                "Nenhuma conta com o e-mail '" + email + "'. A pessoa precisa se cadastrar "
                + "e ativar a conta antes de receber acesso de plataforma."));
        return definirPapeis(usuario, papeisSolicitados);
    }

    /** Atualiza os papéis de um operador existente. */
    @Transactional
    @CacheEvict(value = "tenant-access", allEntries = true)
    public Operador atualizar(UUID usuarioId, List<String> papeisSolicitados) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new NotFoundException("Usuário não encontrado: " + usuarioId));
        return definirPapeis(usuario, papeisSolicitados);
    }

    /** Revoga TODO o acesso de plataforma (mantém a conta e os vínculos de empresa). */
    @Transactional
    @CacheEvict(value = "tenant-access", allEntries = true)
    public void revogar(UUID usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new NotFoundException("Usuário não encontrado: " + usuarioId));
        definirPapeis(usuario, List.of());
    }

    // ------------------------------------------------------------------ interno

    private Operador definirPapeis(Usuario usuario, List<String> papeisSolicitados) {
        List<String> validados = validar(papeisSolicitados);
        UsuarioGlobalRoles global = globalRolesRepository.findById(usuario.getId())
            .orElseGet(() -> UsuarioGlobalRoles.builder()
                .usuarioId(usuario.getId())
                .roles(new String[0])
                .unrestrictedAccess(false)
                .build());

        List<String> antes = papeisDePlataforma(global);
        garantirQueNaoSeAutoBloqueia(usuario.getId(), antes, validados);
        garantirQueSobraAdmin(usuario.getId(), antes, validados);

        // Preserva papéis NÃO-plataforma da linha (ex.: AUDITOR) — a tela mexe só nos PLATFORM_*
        Set<String> finais = new LinkedHashSet<>();
        for (String papel : nullSafe(global.getRoles())) {
            if (!PapelPlataforma.ehPapelDePlataforma(papel)) {
                finais.add(papel);
            }
        }
        finais.addAll(validados);

        global.setRoles(finais.toArray(String[]::new));
        // Alcance acompanha o poder: sem unrestricted o TenantFilter barraria o operador
        // antes mesmo de o platform.rego decidir a ação.
        global.setUnrestrictedAccess(!validados.isEmpty());

        if (finais.isEmpty()) {
            globalRolesRepository.delete(global);
        } else {
            globalRolesRepository.save(global);
        }

        UUID actor = TenantContext.getUsuarioId();
        eventPublisher.publishEvent(OperadorPlataformaAlteradoEvent.of(
            usuario.getId(), usuario.getEmail(), antes, validados, actor));
        log.warn("[PLATFORM] Papéis de plataforma alterados: alvo={} ({}), antes={}, depois={}, por={}",
            usuario.getId(), usuario.getEmail(), antes, validados, actor);

        return new Operador(usuario.getId(), usuario.getEmail(), usuario.getNome(),
            Boolean.TRUE.equals(usuario.getAtivo()), validados, Instant.now());
    }

    /** Nomes desconhecidos são erro de negócio, não silêncio: o rego negaria tudo. */
    private List<String> validar(List<String> solicitados) {
        if (solicitados == null) {
            return List.of();
        }
        List<String> validos = new ArrayList<>();
        for (String papel : solicitados) {
            PapelPlataforma reconhecido = PapelPlataforma.de(papel).orElseThrow(
                () -> new BusinessException("Papel de plataforma desconhecido: '" + papel
                    + "'. Válidos: " + Arrays.toString(PapelPlataforma.values())));
            if (!validos.contains(reconhecido.name())) {
                validos.add(reconhecido.name());
            }
        }
        return validos;
    }

    private void garantirQueNaoSeAutoBloqueia(UUID alvo, List<String> antes, List<String> depois) {
        UUID actor = TenantContext.getUsuarioId();
        if (actor == null || !actor.equals(alvo)) {
            return;
        }
        boolean eraAdmin = antes.contains(PapelPlataforma.PLATFORM_ADMIN.name());
        boolean continuaAdmin = depois.contains(PapelPlataforma.PLATFORM_ADMIN.name());
        if (eraAdmin && !continuaAdmin) {
            throw new BusinessException("Você não pode remover o seu próprio acesso de "
                + "administrador. Peça a outro administrador da plataforma.");
        }
    }

    private void garantirQueSobraAdmin(UUID alvo, List<String> antes, List<String> depois) {
        boolean eraAdmin = antes.contains(PapelPlataforma.PLATFORM_ADMIN.name());
        boolean continuaAdmin = depois.contains(PapelPlataforma.PLATFORM_ADMIN.name());
        if (!eraAdmin || continuaAdmin) {
            return;
        }
        long outrosAdmins = globalRolesRepository.findAll().stream()
            .filter(g -> !g.getUsuarioId().equals(alvo))
            .filter(g -> papeisDePlataforma(g).contains(PapelPlataforma.PLATFORM_ADMIN.name()))
            .count();
        if (outrosAdmins == 0) {
            throw new BusinessException("Este é o último administrador da plataforma. "
                + "Promova outro antes de revogar este acesso.");
        }
    }

    private List<String> papeisDePlataforma(UsuarioGlobalRoles global) {
        return PapelPlataforma.filtrar(Arrays.asList(nullSafe(global.getRoles())))
            .stream().map(Enum::name).toList();
    }

    private String[] nullSafe(String[] roles) {
        return roles != null ? roles : new String[0];
    }
}
