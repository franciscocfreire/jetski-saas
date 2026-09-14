<#--
  Layout de TODOS os e-mails HTML do Keycloak no tema meujet-email (vinculação de
  conta Google, redefinição de senha, verificação, código de acesso...). Mesma marca
  dos e-mails do backend (EmailTemplates.BRAND_HEADER): wordmark em texto, porque
  clientes de e-mail bloqueiam imagens.

  E-mail HTML do Keycloak tem auto-escape: ${...} já sai escapado; ?no_esc só onde a
  mensagem traz HTML de propósito. Estilo fica aqui (inline), nunca nas mensagens —
  o kcSanitize dos templates base remove atributos style.
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

<#-- Título do e-mail -->
<#macro titulo texto>
<h2 style="color: #1E4266; margin: 0 0 16px 0;">${texto}</h2>
</#macro>

<#-- "Olá, Nome!" quando o Keycloak conhece o primeiro nome; senão "Olá!" -->
<#macro saudacao>
<#assign primeiroNome = (user.firstName)!"">
<p><#if primeiroNome?has_content>${msg("mjSaudacao", primeiroNome)}<#else>${msg("mjSaudacaoSemNome")}</#if></p>
</#macro>

<#-- Botão de ação principal -->
<#macro botao link texto>
<p style="text-align: center; margin: 30px 0;">
    <a href="${link}"
       style="background-color: #1E4266; color: #ffffff; padding: 12px 24px;
              text-decoration: none; border-radius: 8px; display: inline-block; font-weight: bold;">
        ${texto}
    </a>
</p>
</#macro>

<#-- Caixa de aviso (validade / "se não foi você") -->
<#macro aviso>
<div style="background-color: #FBF7EE; border-left: 4px solid #B78934; padding: 12px 15px; margin: 20px 0;">
    <#nested>
</div>
</#macro>

<#-- Endereço por extenso para quando o botão não abre -->
<#macro linkAlternativo link>
<p style="color: #666; font-size: 12px;">
    ${msg("mjLinkAlternativo")}<br>
    <a href="${link}" style="color: #1E4266; word-break: break-all;">${link}</a>
</p>
</#macro>
