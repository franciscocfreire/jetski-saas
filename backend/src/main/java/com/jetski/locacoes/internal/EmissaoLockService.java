package com.jetski.locacoes.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Advisory lock por reserva, para serializar emissões concorrentes da mesma reserva.
 *
 * <p>Bean próprio (e não um {@code @PersistenceContext} dentro do {@code EmissaoService})
 * porque {@code EmissaoServiceTest} monta o serviço pelo construtor: um EntityManager
 * injetado em campo ficaria nulo e todo teste quebraria com NPE. Aqui basta mockar.
 *
 * <p><b>Ordem dos locks:</b> este (reserva) é adquirido ANTES do lock por tenant do
 * {@code CreditoService}. Inverter a ordem em algum caller futuro produz deadlock
 * intermitente — mantenha reserva → tenant.
 */
@Service
public class EmissaoLockService {

    @PersistenceContext
    private EntityManager entityManager;

    /** Segura o lock até o fim da transação corrente. */
    public void lockReserva(UUID reservaId) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(?1, 42))")
            .setParameter(1, "emissao:" + reservaId)
            .getSingleResult();
    }
}
