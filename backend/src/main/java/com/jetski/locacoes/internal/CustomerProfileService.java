package com.jetski.locacoes.internal;

import com.jetski.locacoes.domain.Cliente;
import com.jetski.locacoes.domain.CustomerProfile;
import com.jetski.locacoes.event.ClienteIdentidadeSincronizadaEvent;
import com.jetski.locacoes.internal.repository.ClienteRepository;
import com.jetski.locacoes.internal.repository.CustomerProfileRepository;
import com.jetski.shared.exception.BusinessException;
import com.jetski.shared.security.DuplicateUserException;
import com.jetski.shared.security.TenantContext;
import com.jetski.shared.security.UserProvisioningService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Identidade GLOBAL do cliente do portal: CPF/RG/nascimento seguem a pessoa
 * e hidratam o Cliente de cada loja. Endereço/telefones/anexos permanecem
 * tenant-scoped (decisão de produto).
 *
 * CPF é define-only pelo portal, único entre contas (índice parcial no banco
 * + username do Keycloak) e sincronizado com o Keycloak para permitir login
 * por e-mail OU CPF.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerProfileService {

    private static final String PROVIDER = "keycloak";

    private final CustomerProfileRepository repository;
    private final CustomerAccountService customerAccountService;
    private final ClienteRepository clienteRepository;
    private final UserProvisioningService userProvisioningService;
    private final EntityManager entityManager;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    // ============================ Consulta (get-or-backfill) ============================

    /**
     * Perfil do sub — cria com backfill lazy dos Clientes já vinculados
     * (vínculo mais recente primeiro; primeiro valor não-nulo vence).
     * Cobre clientes pré-existentes sem migração de dados.
     */
    @Transactional
    public CustomerProfile obter(String sub, String nomeJwt) {
        Optional<CustomerProfile> existente =
            repository.findByProviderAndProviderUserId(PROVIDER, sub);
        if (existente.isPresent()) {
            return existente.get();
        }

        CustomerProfile profile = CustomerProfile.builder()
            .provider(PROVIDER)
            .providerUserId(sub)
            .nome(nomeJwt)
            .build();

        // Backfill dos vínculos existentes (mais recente primeiro)
        var vinculos = customerAccountService.vinculos(sub);
        for (int i = vinculos.size() - 1; i >= 0; i--) {
            var v = vinculos.get(i);
            fixarTenant(v.getTenantId());
            clienteRepository.findById(v.getClienteId()).ifPresent(c -> absorver(profile, c));
        }

        CustomerProfile salvo = repository.save(profile);
        if (salvo.getCpf() != null) {
            sincronizarCpfKeycloak(sub, salvo.getCpf());
        }
        log.info("Perfil global criado (backfill de {} vínculo(s)): sub={}, temCpf={}",
            vinculos.size(), sub, salvo.getCpf() != null);
        return salvo;
    }

    // ============================ Atualização ============================

    public record AtualizarCmd(
        String cpf, String rg, String orgaoEmissor,
        String nacionalidade, String naturalidade,
        Boolean estrangeiro, LocalDate dataNascimento) {}

    @Transactional
    public CustomerProfile atualizar(String sub, String nomeJwt, AtualizarCmd cmd) {
        CustomerProfile p = obter(sub, nomeJwt);

        if (cmd.cpf() != null && !cmd.cpf().isBlank()) {
            definirCpf(p, cmd.cpf());
        }

        // Detecção de mudança REAL por campo (o portal envia a identidade
        // inteira em todo Salvar — sem diff, propagação/auditoria disparariam
        // a cada clique). CPF (define-once) e nome (fluxo próprio) ficam fora.
        java.util.List<String> alterados = new java.util.ArrayList<>();
        if (cmd.rg() != null && !java.util.Objects.equals(blankToNull(cmd.rg()), p.getRg())) {
            p.setRg(blankToNull(cmd.rg())); alterados.add("rg");
        }
        if (cmd.orgaoEmissor() != null
                && !java.util.Objects.equals(blankToNull(cmd.orgaoEmissor()), p.getOrgaoEmissor())) {
            p.setOrgaoEmissor(blankToNull(cmd.orgaoEmissor())); alterados.add("orgaoEmissor");
        }
        if (cmd.nacionalidade() != null
                && !java.util.Objects.equals(blankToNull(cmd.nacionalidade()), p.getNacionalidade())) {
            p.setNacionalidade(blankToNull(cmd.nacionalidade())); alterados.add("nacionalidade");
        }
        if (cmd.naturalidade() != null
                && !java.util.Objects.equals(blankToNull(cmd.naturalidade()), p.getNaturalidade())) {
            p.setNaturalidade(blankToNull(cmd.naturalidade())); alterados.add("naturalidade");
        }
        if (cmd.estrangeiro() != null && !cmd.estrangeiro().equals(p.getEstrangeiro())) {
            p.setEstrangeiro(cmd.estrangeiro()); alterados.add("estrangeiro");
        }
        if (cmd.dataNascimento() != null && !cmd.dataNascimento().equals(p.getDataNascimento())) {
            p.setDataNascimento(cmd.dataNascimento()); alterados.add("dataNascimento");
        }

        CustomerProfile salvo = repository.save(p);
        if (!alterados.isEmpty()) {
            propagarIdentidade(sub, salvo, List.copyOf(alterados));
        }
        return salvo;
    }

    /**
     * O cliente é a fonte da própria identidade: edições no perfil global
     * sobrescrevem os campos correspondentes nos Clientes das lojas VINCULADAS
     * (a correção vale para a próxima emissão). CPF/nome ficam fora.
     *
     * <p>ATENÇÃO (RLS): {@code fixarTenant} é transaction-local e o Hibernate
     * adia UPDATEs para o flush — o {@code saveAndFlush} POR TENANT é
     * obrigatório, senão os UPDATEs dos tenants anteriores seriam filtrados
     * pela RLS do último tenant fixado (perdidos silenciosamente).
     */
    private void propagarIdentidade(String sub, CustomerProfile p, java.util.List<String> campos) {
        for (var v : customerAccountService.vinculos(sub)) {
            fixarTenant(v.getTenantId());
            clienteRepository.findById(v.getClienteId()).ifPresent(c -> {
                for (String campo : campos) {
                    switch (campo) {
                        case "rg" -> c.setRg(p.getRg());
                        case "orgaoEmissor" -> c.setOrgaoEmissor(p.getOrgaoEmissor());
                        case "nacionalidade" -> c.setNacionalidade(p.getNacionalidade());
                        case "naturalidade" -> c.setNaturalidade(p.getNaturalidade());
                        case "estrangeiro" -> c.setEstrangeiro(p.getEstrangeiro());
                        case "dataNascimento" -> c.setDataNascimento(p.getDataNascimento());
                        default -> { /* campo desconhecido — ignora */ }
                    }
                }
                clienteRepository.saveAndFlush(c); // CRÍTICO: flush antes do próximo set_config
                eventPublisher.publishEvent(ClienteIdentidadeSincronizadaEvent.of(
                    v.getTenantId(), v.getClienteId(), campos, sub));
            });
        }
        log.info("Identidade propagada do perfil para as lojas vinculadas: sub={}, campos={}",
            sub, campos);
    }

    /**
     * CPF define-only: grava se ainda não existe; troca é bloqueada (staff).
     * Unicidade global: índice parcial no banco + username do Keycloak.
     */
    @Transactional
    public void definirCpf(CustomerProfile p, String cpfInformado) {
        String cpf = cpfInformado.trim();
        if (p.getCpf() != null && !p.getCpf().isBlank()) {
            if (normalizar(p.getCpf()).equals(normalizar(cpf))) {
                return; // mesmo CPF — nada a fazer
            }
            throw new BusinessException(
                "O CPF do seu cadastro não pode ser alterado pelo portal — fale com a loja.");
        }

        repository.findByCpf(cpf).ifPresent(outro -> {
            if (!outro.getId().equals(p.getId())) {
                throw new BusinessException(
                    "Este CPF já está vinculado a outra conta — fale com a loja para regularizar.");
            }
        });

        p.setCpf(cpf);
        repository.save(p);
        sincronizarCpfKeycloak(p.getProviderUserId(), cpf);
        log.info("CPF definido no perfil global: sub={}", p.getProviderUserId());
    }

    // ============================ Hidratação p/ Cliente da loja ============================

    /** Copia APENAS os campos de identidade faltantes do perfil para o Cliente da loja. */
    public void hidratarIdentidade(Cliente c, CustomerProfile p) {
        if (isBlank(c.getDocumento()) && !isBlank(p.getCpf())) c.setDocumento(p.getCpf());
        if (isBlank(c.getNome()) && !isBlank(p.getNome())) c.setNome(p.getNome());
        if (isBlank(c.getRg()) && !isBlank(p.getRg())) c.setRg(p.getRg());
        if (isBlank(c.getOrgaoEmissor()) && !isBlank(p.getOrgaoEmissor())) c.setOrgaoEmissor(p.getOrgaoEmissor());
        if (isBlank(c.getNacionalidade()) && !isBlank(p.getNacionalidade())) c.setNacionalidade(p.getNacionalidade());
        if (isBlank(c.getNaturalidade()) && !isBlank(p.getNaturalidade())) c.setNaturalidade(p.getNaturalidade());
        if (c.getEstrangeiro() == null) c.setEstrangeiro(p.getEstrangeiro());
        if (c.getDataNascimento() == null && p.getDataNascimento() != null) {
            c.setDataNascimento(p.getDataNascimento());
        }
    }

    /** Espelha a identidade do Cliente da loja para o perfil (write-through do EMA). */
    @Transactional
    public void absorverIdentidade(String sub, String nomeJwt, Cliente c) {
        CustomerProfile p = obter(sub, nomeJwt);
        if (!isBlank(c.getDocumento()) && isBlank(p.getCpf())) {
            definirCpf(p, c.getDocumento());
        }
        absorver(p, c);
        repository.save(p);
    }

    // ============================ Internos ============================

    private void absorver(CustomerProfile p, Cliente c) {
        if (isBlank(p.getNome()) && !isBlank(c.getNome())) p.setNome(c.getNome());
        if (isBlank(p.getCpf()) && !isBlank(c.getDocumento())) p.setCpf(c.getDocumento());
        if (isBlank(p.getRg()) && !isBlank(c.getRg())) p.setRg(c.getRg());
        if (isBlank(p.getOrgaoEmissor()) && !isBlank(c.getOrgaoEmissor())) p.setOrgaoEmissor(c.getOrgaoEmissor());
        if (isBlank(p.getNacionalidade()) && !isBlank(c.getNacionalidade())) p.setNacionalidade(c.getNacionalidade());
        if (isBlank(p.getNaturalidade()) && !isBlank(c.getNaturalidade())) p.setNaturalidade(c.getNaturalidade());
        if (Boolean.TRUE.equals(c.getEstrangeiro())) p.setEstrangeiro(true);
        if (p.getDataNascimento() == null && c.getDataNascimento() != null) {
            p.setDataNascimento(c.getDataNascimento());
        }
    }

    /** Username do Keycloak vira o CPF (dígitos) — login por e-mail OU CPF. Best-effort. */
    private void sincronizarCpfKeycloak(String sub, String cpf) {
        try {
            userProvisioningService.definirCpf(sub, normalizar(cpf));
        } catch (DuplicateUserException e) {
            throw new BusinessException(
                "Este CPF já está vinculado a outra conta — fale com a loja para regularizar.");
        } catch (Exception e) {
            // não bloqueia a operação de negócio; login por CPF fica pendente
            log.warn("Falha ao sincronizar CPF no Keycloak (login por CPF adiado): sub={}, err={}",
                sub, e.getMessage());
        }
    }

    private void fixarTenant(UUID tenantId) {
        entityManager.createNativeQuery("SELECT set_config('app.tenant_id', :tid, true)")
            .setParameter("tid", tenantId.toString())
            .getSingleResult();
        TenantContext.setTenantId(tenantId);
    }

    private static String normalizar(String cpf) {
        return cpf == null ? "" : cpf.replaceAll("\\D", "");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
