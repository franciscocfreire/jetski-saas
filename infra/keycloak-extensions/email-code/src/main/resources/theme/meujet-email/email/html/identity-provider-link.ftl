<#--
  Confirmação de vinculação de conta de IdP (ex.: Google) a uma conta Meu Jet que já
  existe com o mesmo e-mail (first broker login → "verificar por e-mail").
  Substitui o template do tema base (texto cru, em inglês por locale do navegador).
-->
<#import "template.ftl" as layout>
<@layout.emailLayout>
<#assign idp = identityProviderDisplayName!"Google">
<#assign conta = (identityProviderContext.username)!"">
<@layout.titulo texto=msg("mjIdpLinkTitulo", idp)/>
<@layout.saudacao/>

<p>${msg("mjIdpLinkIntro", idp, realmName, conta)}</p>
<p>${msg("mjIdpLinkAcao", idp, realmName)}</p>

<@layout.botao link=link texto=msg("mjIdpLinkBotao")/>

<@layout.aviso>
    <p style="margin: 4px 0;">${msg("mjIdpLinkExpira", linkExpirationFormatter(linkExpiration))}</p>
    <p style="margin: 4px 0;">${msg("mjIdpLinkIgnorar")}</p>
</@layout.aviso>

<@layout.linkAlternativo link=link/>
</@layout.emailLayout>
