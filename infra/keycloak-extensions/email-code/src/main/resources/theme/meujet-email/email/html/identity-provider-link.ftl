<#--
  Confirmação de vinculação de conta de IdP (ex.: Google) a uma conta Meu Jet que já
  existe com o mesmo e-mail (first broker login → "verificar por e-mail").
  Substitui o template do tema base (texto cru, em inglês por locale do navegador).
  Visual no FTL e mensagens em texto puro escapadas: o kcSanitize do base remove
  estilos inline, então o botão não pode viver dentro da mensagem.
-->
<#import "template.ftl" as layout>
<@layout.emailLayout>
<#assign idp = identityProviderDisplayName!"Google">
<#assign conta = (identityProviderContext.username)!"">
<h2 style="color: #1E4266; margin: 0 0 16px 0;">${msg("mjIdpLinkTitulo", idp)}</h2>

<p>${msg("mjIdpLinkIntro", idp, realmName, conta)}</p>

<p>${msg("mjIdpLinkAcao", idp, realmName)}</p>

<p style="text-align: center; margin: 30px 0;">
    <a href="${link}"
       style="background-color: #1E4266; color: #ffffff; padding: 12px 24px;
              text-decoration: none; border-radius: 8px; display: inline-block; font-weight: bold;">
        ${msg("mjIdpLinkBotao")}
    </a>
</p>

<div style="background-color: #FBF7EE; border-left: 4px solid #B78934; padding: 12px 15px; margin: 20px 0;">
    <p style="margin: 4px 0;">${msg("mjIdpLinkExpira", linkExpirationFormatter(linkExpiration))}</p>
    <p style="margin: 4px 0;">${msg("mjIdpLinkIgnorar")}</p>
</div>

<p style="color: #666; font-size: 12px;">
    ${msg("mjIdpLinkFallback")}<br>
    <a href="${link}" style="color: #1E4266; word-break: break-all;">${link}</a>
</p>
</@layout.emailLayout>
