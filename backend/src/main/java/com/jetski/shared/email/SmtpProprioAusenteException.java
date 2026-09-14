package com.jetski.shared.email;

import java.util.UUID;

/**
 * O e-mail exige o SMTP próprio do remetente ({@link EmailService.Remetente#exigeSmtpProprio()})
 * e o tenant não tem host + usuário + senha configurados. Nada foi enviado — e de propósito
 * não houve fallback para o SMTP da plataforma.
 */
public class SmtpProprioAusenteException extends RuntimeException {

    private final UUID tenantId;

    public SmtpProprioAusenteException(UUID tenantId, String nome) {
        super("EAMA emissora" + (nome != null && !nome.isBlank() ? " " + nome : "")
            + " sem SMTP próprio configurado: o ofício à Capitania só é enviado pelo e-mail da EAMA");
        this.tenantId = tenantId;
    }

    public UUID getTenantId() {
        return tenantId;
    }
}
