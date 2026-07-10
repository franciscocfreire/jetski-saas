package com.jetski.tenant.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO: atualizar dados gerais/e-mail da empresa (tenant). Campos opcionais. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantGeralConfigRequest {

    @Size(max = 200)
    private String razaoSocial;

    @Size(max = 100)
    private String cidade;

    @Email(message = "E-mail da Marinha inválido")
    @Size(max = 255)
    private String marinhaEmail;

    @Email(message = "E-mail remetente inválido")
    @Size(max = 255)
    private String emailRemetente;

    /** Chave PIX da empresa — destino do sinal das reservas do portal. */
    @Size(max = 140)
    private String pixChave;

    // SMTP próprio da empresa (envio "from" real). Senha só é gravada se enviada.
    @Size(max = 255)
    private String smtpHost;
    private Integer smtpPort;
    @Size(max = 255)
    private String smtpUsername;
    @Size(max = 255)
    private String smtpPassword;
    @Size(max = 255)
    private String smtpFrom;
    private Boolean smtpStarttls;
}
