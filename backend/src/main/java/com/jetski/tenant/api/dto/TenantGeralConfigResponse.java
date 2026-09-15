package com.jetski.tenant.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO: dados gerais/e-mail da empresa (tenant). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantGeralConfigResponse {
    private String slug;
    private String cnpj;
    private String razaoSocial;
    private String cidade;
    private String uf;
    private String whatsapp;
    private String marinhaEmail;
    private String emailRemetente;
    private String responsavelNome;
    private String telefone;
    private String emailOficial;
    private String pixChave;
    // SMTP (a senha NUNCA é retornada — só o indicador de configurado).
    private String smtpHost;
    private Integer smtpPort;
    private String smtpUsername;
    private String smtpFrom;
    private Boolean smtpStarttls;
    private boolean smtpConfigurado;
}
