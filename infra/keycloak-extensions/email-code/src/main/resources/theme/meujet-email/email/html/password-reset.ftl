<#--
  Redefinição de senha ("Esqueci minha senha" na tela de login).
  Substitui o template do tema base (texto genérico, link cru).
-->
<#import "template.ftl" as layout>
<@layout.emailLayout>
<@layout.titulo texto=msg("mjPasswordResetTitulo")/>
<@layout.saudacao/>

<p>${msg("mjPasswordResetIntro", realmName)}</p>
<p>${msg("mjPasswordResetAcao")}</p>

<@layout.botao link=link texto=msg("mjPasswordResetBotao")/>

<@layout.aviso>
    <p style="margin: 4px 0;">${msg("mjPasswordResetExpira", linkExpirationFormatter(linkExpiration))}</p>
    <p style="margin: 4px 0;">${msg("mjPasswordResetIgnorar")}</p>
</@layout.aviso>

<@layout.linkAlternativo link=link/>
</@layout.emailLayout>
