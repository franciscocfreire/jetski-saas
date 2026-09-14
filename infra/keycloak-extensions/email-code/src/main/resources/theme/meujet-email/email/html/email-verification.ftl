<#--
  Verificação de e-mail (required action VERIFY_EMAIL / cadastro).
  Substitui o template do tema base (texto genérico, link cru).
-->
<#import "template.ftl" as layout>
<@layout.emailLayout>
<@layout.titulo texto=msg("mjEmailVerificationTitulo")/>
<@layout.saudacao/>

<p>${msg("mjEmailVerificationIntro", realmName)}</p>

<@layout.botao link=link texto=msg("mjEmailVerificationBotao")/>

<@layout.aviso>
    <p style="margin: 4px 0;">${msg("mjEmailVerificationExpira", linkExpirationFormatter(linkExpiration))}</p>
    <p style="margin: 4px 0;">${msg("mjEmailVerificationIgnorar", realmName)}</p>
</@layout.aviso>

<@layout.linkAlternativo link=link/>
</@layout.emailLayout>
