package com.jetski.tenant;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Papel da empresa na emissão à Marinha (EMISSAO_DELEGADA_SPEC §8.M) — API pública
 * do módulo tenant, para quem mostra a rede de emissão (switcher do backoffice,
 * console da plataforma) e para travas cadastrais.
 *
 * <p>Três estados, nesta precedência:
 * <ul>
 *   <li>{@link Papel#DELEGADA}: operadora de uma parceria em vigor (ATIVA ou
 *       BLOQUEADA). A capitania dela é a da EAMA e não se altera.</li>
 *   <li>{@link Papel#EMISSORA}: EAMA habilitada pelo Meu Jet.</li>
 *   <li>{@link Papel#NENHUM}: não é EAMA — pode nem estar em uma capitania.</li>
 * </ul>
 *
 * <p>RLS: {@code vinculo_emissao} só é visível às partes, e o projeto não tem bypass.
 * A leitura roda em transação própria com o {@code app.tenant_id} da empresa
 * consultada (mesmo padrão de {@link PlanoLimiteService#modulosDoPlano}) — serve a
 * chamadas sem tenant no contexto ({@code /v1/user/tenants}, rotas de plataforma).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PapelEmissaoService {

    public enum Papel { EMISSORA, DELEGADA, NENHUM }

    /**
     * @param emissoraTenantId EAMA da parceria em vigor (só na delegada)
     * @param vinculoStatus    ATIVO ou BLOQUEADO (só na delegada)
     */
    public record PapelEmissao(Papel papel, UUID emissoraTenantId, String vinculoStatus) {}

    private final EntityManager entityManager;

    /**
     * @param emissoraHabilitada {@code tenant.emissora_habilitada}, que o chamador já tem
     *                           carregado — evita reler a linha só para isso
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public PapelEmissao papelDe(UUID tenantId, boolean emissoraHabilitada) {
        try {
            entityManager.createNativeQuery("SELECT set_config('app.tenant_id', :tid, true)")
                .setParameter("tid", tenantId.toString())
                .getSingleResult();
            @SuppressWarnings("unchecked")
            List<Object[]> rows = entityManager.createNativeQuery(
                    "SELECT tenant_emissor_id, status FROM vinculo_emissao "
                    + "WHERE tenant_operador_id = :tid AND status IN ('ATIVO', 'BLOQUEADO') "
                    + "ORDER BY created_at DESC LIMIT 1")
                .setParameter("tid", tenantId)
                .getResultList();
            if (!rows.isEmpty()) {
                return new PapelEmissao(Papel.DELEGADA, (UUID) rows.get(0)[0], (String) rows.get(0)[1]);
            }
        } catch (Exception e) {
            log.warn("Papel de emissão do tenant {} indisponível (segue pelo cadastro): {}",
                tenantId, e.getMessage());
        }
        return new PapelEmissao(emissoraHabilitada ? Papel.EMISSORA : Papel.NENHUM, null, null);
    }
}
