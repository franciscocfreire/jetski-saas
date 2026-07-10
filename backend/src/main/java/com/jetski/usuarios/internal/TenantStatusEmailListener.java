package com.jetski.usuarios.internal;

import com.jetski.shared.email.EmailService;
import com.jetski.shared.security.TenantContext;
import com.jetski.tenant.domain.event.TenantStatusChangedEvent;
import com.jetski.tenant.domain.event.TrialExpiringEvent;
import com.jetski.usuarios.internal.repository.MembroRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Aviso por e-mail aos ADMIN_TENANT quando a plataforma muda o status da empresa
 * (aprovada/suspensa/reativada) — cumpre a promessa da tela de gate ("você receberá
 * um aviso assim que a empresa for liberada").
 *
 * <p>Roda APÓS o commit e em thread própria (padrão ClaimAutoConviteListener):
 * o envio jamais atrasa/derruba a ação do super admin, e nunca sai de transação revertida.
 * Best-effort — falha vira log, nunca exceção.
 *
 * <p>Empresa aprovada ANTES do admin ativar a conta ainda não tem Membro — esse caso é
 * coberto pelo listener do módulo signup (aviso ao e-mail do tenant_signup pendente).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantStatusEmailListener {

    private static final String ADMIN_ROLE = "ADMIN_TENANT";

    private final MembroRepository membroRepository;
    private final EmailService emailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTenantStatusChanged(TenantStatusChangedEvent event) {
        try {
            // Thread async nasce sem contexto: o TenantAwareDataSource lê o
            // ThreadLocal a cada getConnection() para satisfazer a RLS.
            TenantContext.setTenantId(event.tenantId());
            List<String> emails = membroRepository
                .findActiveMemberEmailsByTenantIdAndRole(event.tenantId(), ADMIN_ROLE);
            if (emails.isEmpty()) {
                log.info("Aviso de status sem destinatários (nenhum ADMIN_TENANT ativo): tenant={}, acao={}",
                    event.tenantId(), event.acao());
                return;
            }
            for (String to : emails) {
                try {
                    emailService.sendTenantStatusNotification(
                        to, event.acao(), event.razaoSocial(), event.motivo());
                } catch (Exception e) {
                    log.warn("Falha (ignorada) ao avisar admin {} sobre {} da empresa {}: {}",
                        to, event.acao(), event.slug(), e.getMessage());
                }
            }
            log.info("Admins avisados sobre {}: tenant={}, destinatarios={}",
                event.acao(), event.tenantId(), emails.size());
        } catch (Exception e) {
            // best-effort: a mudança de status JÁ está commitada
            log.warn("Aviso de status de tenant falhou (best-effort): tenant={}, acao={}, {}",
                event.tenantId(), event.acao(), e.getMessage());
        } finally {
            TenantContext.clear(); // thread de pool é reutilizada
        }
    }

    /** Aviso de trial vencendo (D-3/D-1, publicado pelo job de expiração). */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTrialExpiring(TrialExpiringEvent event) {
        try {
            TenantContext.setTenantId(event.tenantId());
            List<String> emails = membroRepository
                .findActiveMemberEmailsByTenantIdAndRole(event.tenantId(), ADMIN_ROLE);
            String dataFim = event.dataFim().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            for (String to : emails) {
                try {
                    emailService.sendTrialWarningNotification(
                        to, event.razaoSocial(), event.diasRestantes(), dataFim);
                } catch (Exception e) {
                    log.warn("Falha (ignorada) ao avisar admin {} sobre trial vencendo da empresa {}: {}",
                        to, event.slug(), e.getMessage());
                }
            }
            log.info("Aviso de trial vencendo enviado: tenant={}, faltam {} dias, destinatarios={}",
                event.tenantId(), event.diasRestantes(), emails.size());
        } catch (Exception e) {
            log.warn("Aviso de trial vencendo falhou (best-effort): tenant={}, {}",
                event.tenantId(), e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }
}
