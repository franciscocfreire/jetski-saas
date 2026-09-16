# -----------------------------------------------------------------------------
# Cloudflare: túnel NOVO, rotas e DNS só da zona do espelho.
#
# O token de API tem acesso a túneis da CONTA inteira (a Cloudflare não escopa
# túnel por zona) — ou seja, tecnicamente alcança o túnel de produção. Por isso
# duas travas: este código nunca referencia o túnel de produção, e a config do
# túnel recusa aplicar se o túnel gerenciado aqui for o de produção (o que só
# aconteceria com um `terraform import` errado).
# -----------------------------------------------------------------------------

data "cloudflare_zone" "espelho" {
  filter = {
    name = var.dominio
  }

  lifecycle {
    postcondition {
      condition     = self.name == var.dominio
      error_message = "A zona encontrada na Cloudflare não é ${var.dominio}."
    }
  }
}

locals {
  cf_account_id = data.cloudflare_zone.espelho.account.id

  # Os hostnames que o nginx do espelho sabe rotear. Sem o apex: a zona tem MX
  # nulo e SPF no apex (domínio sem e-mail), com que um CNAME conflitaria, e o
  # apex só redirecionaria para www.
  hostnames = [for sub in ["www", "app", "cliente", "admin", "sso"] : "${sub}.${var.dominio}"]
}

resource "random_bytes" "segredo_tunel" {
  length = 32
}

resource "cloudflare_zero_trust_tunnel_cloudflared" "espelho" {
  account_id    = local.cf_account_id
  name          = var.nome_tunel
  config_src    = "cloudflare" # rotas gerenciadas pela API, como o túnel de produção
  tunnel_secret = random_bytes.segredo_tunel.base64
}

resource "cloudflare_zero_trust_tunnel_cloudflared_config" "espelho" {
  account_id = local.cf_account_id
  tunnel_id  = cloudflare_zero_trust_tunnel_cloudflared.espelho.id

  config = {
    # Mesmo destino de produção: tudo para o nginx, que roteia por hostname.
    ingress = concat(
      [for h in local.hostnames : { hostname = h, service = "http://nginx:80" }],
      [{ service = "http_status:404" }],
    )
  }

  lifecycle {
    precondition {
      condition     = cloudflare_zero_trust_tunnel_cloudflared.espelho.id != var.prod_tunnel_id
      error_message = "O túnel gerenciado por este Terraform é o de PRODUÇÃO. Nada foi alterado — confira o state (terraform state list)."
    }
  }
}

data "cloudflare_zero_trust_tunnel_cloudflared_token" "espelho" {
  account_id = local.cf_account_id
  tunnel_id  = cloudflare_zero_trust_tunnel_cloudflared.espelho.id
}

resource "cloudflare_dns_record" "espelho" {
  for_each = toset(local.hostnames)

  zone_id = data.cloudflare_zone.espelho.id
  name    = each.value
  type    = "CNAME"
  content = "${cloudflare_zero_trust_tunnel_cloudflared.espelho.id}.cfargotunnel.com"
  proxied = true
  ttl     = 1 # automático (obrigatório com proxied)
  comment = "espelho de carga — infra/espelho/terraform"
}

# O k6 não é navegador: Browser Integrity Check e Bot Fight Mode o bloqueiam
# (erro 1010). Como a zona é dedicada ao espelho, desligar na zona inteira é
# seguro — e evita o limite do plano gratuito, que não pula Bot Fight Mode por
# hostname.
resource "cloudflare_zone_setting" "browser_check" {
  zone_id    = data.cloudflare_zone.espelho.id
  setting_id = "browser_check"
  value      = "off"
}

resource "cloudflare_bot_management" "espelho" {
  zone_id    = data.cloudflare_zone.espelho.id
  fight_mode = false
}
