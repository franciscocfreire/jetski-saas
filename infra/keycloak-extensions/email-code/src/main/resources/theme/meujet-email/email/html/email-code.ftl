<#import "template.ftl" as layout>
<@layout.emailLayout>
${kcSanitize(msg("mjEmailCodeBodyHtml", code, ttlMinutes))?no_esc}
</@layout.emailLayout>
