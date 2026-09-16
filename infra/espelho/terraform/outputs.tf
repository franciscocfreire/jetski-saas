output "ip_publico" {
  description = "IP da VM (só SSH entra; a web passa pelo túnel)."
  value       = oci_core_instance.espelho.public_ip
}

output "ssh" {
  value = "ssh ubuntu@${oci_core_instance.espelho.public_ip}"
}

output "acompanhar_provisionamento" {
  description = "O build ARM do deploy.sh leva dezenas de minutos na primeira vez."
  value       = "ssh ubuntu@${oci_core_instance.espelho.public_ip} 'sudo tail -f /var/log/meujet-espelho.log'"
}

output "status_provisionamento" {
  value = "ssh ubuntu@${oci_core_instance.espelho.public_ip} 'sudo cat /var/lib/meujet-espelho/provisionado /var/lib/meujet-espelho/FALHOU 2>/dev/null'"
}

output "mailpit" {
  description = "Caixa de e-mail do espelho (links de ativação de conta)."
  value       = "ssh -L 8025:127.0.0.1:8025 ubuntu@${oci_core_instance.espelho.public_ip}  →  http://localhost:8025"
}

output "urls" {
  value = {
    site    = "https://www.${var.dominio}"
    painel  = "https://app.${var.dominio}"
    portal  = "https://cliente.${var.dominio}"
    console = "https://admin.${var.dominio}"
    sso     = "https://sso.${var.dominio}"
    grafana = "https://www.${var.dominio}/grafana"
  }
}

output "k6" {
  description = "Variáveis para os cenários de k6/ (ver k6/README.md)."
  value = {
    BASE_URL = "https://www.${var.dominio}/api"
    ISSUER   = "https://sso.${var.dominio}/realms/jetski-saas"
    APP_URL  = "https://app.${var.dominio}"
  }
}

output "tunel_id" {
  value = cloudflare_zero_trust_tunnel_cloudflared.espelho.id
}
