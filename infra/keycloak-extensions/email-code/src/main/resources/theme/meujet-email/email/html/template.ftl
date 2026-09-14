<#--
  Layout de TODOS os e-mails HTML do Keycloak no tema meujet-email (vinculação de
  conta Google, redefinição de senha, verificação, código de acesso...). Mesma marca
  dos e-mails do backend (EmailTemplates.BRAND_HEADER): wordmark em texto, porque
  clientes de e-mail bloqueiam imagens.
-->
<#macro emailLayout>
<html lang="${locale.language}" dir="${(ltr)?then('ltr','rtl')}">
<head>
    <meta charset="UTF-8">
</head>
<body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; background-color: #FCFAF6; margin: 0;">
    <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
        <p style="font-family: Georgia, 'Times New Roman', serif; font-size: 20px;
                  letter-spacing: 5px; color: #12263F; margin: 0 0 4px 0;">
            MEU&nbsp;JET
        </p>
        <div style="height: 2px; width: 64px; background-color: #C9A24B; margin: 0 0 24px 0;"></div>

        <#nested>

        <hr style="border: none; border-top: 1px solid #E3D9C2; margin: 30px 0;">
        <p style="color: #999; font-size: 12px;">
            ${msg("mjEmailAssinatura")?no_esc}
        </p>
    </div>
</body>
</html>
</#macro>
