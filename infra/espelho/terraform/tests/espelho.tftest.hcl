# Testes do Terraform do espelho — rodam SEM credenciais e sem criar nada:
# os três providers são mockados. Exercitam as travas (validações e
# pré-condições) e o cloud-init que chega na VM.
#
#   terraform init -backend=false && terraform test

mock_provider "oci" {
  mock_data "oci_identity_availability_domains" {
    defaults = {
      availability_domains = [{ compartment_id = "ocid1.tenancy.oc1..teste", id = "ad1", name = "iwtO:SA-SAOPAULO-1-AD-1" }]
    }
  }
  mock_data "oci_core_images" {
    defaults = {
      images = [{ id = "ocid1.image.oc1.sa-saopaulo-1.ubuntu2404", display_name = "Canonical-Ubuntu-24.04-aarch64" }]
    }
  }
}

mock_provider "cloudflare" {
  mock_data "cloudflare_zone" {
    defaults = {
      id      = "zona-jetsave"
      name    = "jetsave.com.br"
      account = { id = "conta-teste", name = "teste", type = "standard" }
    }
  }
  mock_data "cloudflare_zero_trust_tunnel_cloudflared_token" {
    defaults = {
      token = "VE9LRU4tRE8tVFVORUwtRE8tRVNQRUxITw=="
    }
  }
  mock_resource "cloudflare_zero_trust_tunnel_cloudflared" {
    defaults = {
      id = "11111111-2222-3333-4444-555555555555"
    }
  }
}

mock_provider "random" {}

variables {
  tenancy_ocid              = "ocid1.tenancy.oc1..teste"
  prod_tunnel_id            = "99999999-8888-7777-6666-555555555555"
  ssh_cidr_permitido        = "203.0.113.10/32"
  ssh_chave_publica_arquivo = "tests/fixtures/chave-teste.pub"
}

# ---------------------------------------------------------------------------
run "caminho_feliz_reproduz_producao" {
  # apply, não plan: o token do túnel vem de um data source que depende do
  # túnel a criar, e o Terraform só o lê no apply. Com os providers mockados,
  # "apply" não cria nada real.
  command = apply

  assert {
    condition     = oci_core_instance.espelho.shape == "VM.Standard.A1.Flex" && oci_core_instance.espelho.shape_config[0].ocpus == 2 && oci_core_instance.espelho.shape_config[0].memory_in_gbs == 12
    error_message = "A VM precisa ter a shape de produção (A1.Flex, 2 OCPU, 12 GB)."
  }

  assert {
    condition     = oci_core_instance.espelho.source_details[0].boot_volume_size_in_gbs == "200"
    error_message = "O disco precisa ter os 200 GB de produção (IOPS cresce com o tamanho)."
  }

  assert {
    condition     = oci_core_instance.espelho.state == "RUNNING"
    error_message = "Por padrão a VM sobe ligada."
  }

  assert {
    condition     = length(cloudflare_dns_record.espelho) == 6 && contains(keys(cloudflare_dns_record.espelho), "praia.jetsave.com.br") && !contains(keys(cloudflare_dns_record.espelho), "jetsave.com.br")
    error_message = "São 6 subdomínios (5 do nginx + a praia) e nenhum registro no apex (que tem MX nulo e SPF)."
  }

  assert {
    condition     = alltrue([for r in cloudflare_dns_record.espelho : r.proxied && r.type == "CNAME" && r.content == "11111111-2222-3333-4444-555555555555.cfargotunnel.com"])
    error_message = "Todo hostname deve ser CNAME proxied para o túnel do ESPELHO."
  }

  assert {
    condition     = length(cloudflare_zero_trust_tunnel_cloudflared_config.espelho.config.ingress) == 7 && cloudflare_zero_trust_tunnel_cloudflared_config.espelho.config.ingress[6].service == "http_status:404"
    error_message = "Ingress: 5 hostnames para o nginx, a praia e um 404 no fim."
  }

  assert {
    condition     = cloudflare_zero_trust_tunnel_cloudflared_config.espelho.config.ingress[5].hostname == "praia.jetsave.com.br" && cloudflare_zero_trust_tunnel_cloudflared_config.espelho.config.ingress[5].service == "http://praia:7331"
    error_message = "A praia vai direto ao container dela (fora do nginx), antes do 404."
  }

  assert {
    condition     = strcontains(base64decode(oci_core_instance.espelho.metadata.user_data), "CLOUDFLARE_TUNNEL_TOKEN=VE9LRU4tRE8tVFVORUwtRE8tRVNQRUxITw==")
    error_message = "O cloud-init precisa levar o token do túnel do espelho."
  }

  assert {
    condition     = strcontains(base64decode(oci_core_instance.espelho.metadata.user_data), "PROD_TUNNEL_ID=99999999-8888-7777-6666-555555555555") && strcontains(base64decode(oci_core_instance.espelho.metadata.user_data), "COMPOSE_VERSAO=5.1.4-1~ubuntu.24.04~noble")
    error_message = "O cloud-init precisa levar o UUID de produção (para o preflight) e o Compose de produção."
  }

  assert {
    condition     = length([for r in oci_core_security_list.espelho.ingress_security_rules : r if r.protocol == "6"]) == 1
    error_message = "Só uma regra TCP de entrada (SSH); a web entra pelo túnel."
  }
}

run "desligar_entre_rodadas" {
  command = plan

  variables {
    ligada = false
  }

  assert {
    condition     = oci_core_instance.espelho.state == "STOPPED"
    error_message = "ligada = false precisa parar a VM."
  }
}

# ---------------------------------------------------------------------------
# Travas
# ---------------------------------------------------------------------------

run "recusa_dominio_de_producao" {
  command = plan
  variables {
    dominio = "meujet.com.br"
  }
  expect_failures = [var.dominio]
}

run "recusa_subdominio_do_dev" {
  command = plan
  variables {
    dominio = "carga.pegaojet.com.br"
  }
  expect_failures = [var.dominio]
}

run "recusa_ssh_aberto_para_o_mundo" {
  command = plan
  variables {
    ssh_cidr_permitido = "0.0.0.0/0"
  }
  expect_failures = [var.ssh_cidr_permitido]
}

run "recusa_tunel_sem_espelho_no_nome" {
  command = plan
  variables {
    nome_tunel = "meujet-prod"
  }
  expect_failures = [var.nome_tunel]
}

run "recusa_email_que_injetaria_yaml" {
  command = plan
  variables {
    platform_admin_emails = "a@b.com\nruncmd: [rm]"
  }
  expect_failures = [var.platform_admin_emails]
}

run "recusa_gerenciar_o_tunel_de_producao" {
  # Simula um `terraform import` errado: o túnel no state é o de produção.
  # Num import, o ID já está no state e a pré-condição reprova no plan; num
  # plan de CRIAÇÃO o ID ainda é desconhecido e ela fica para o apply — por
  # isso o teste roda em apply (mockado: nada real é criado).
  command = apply
  # State próprio: os runs do arquivo compartilham state, e o túnel "criado"
  # no caminho feliz manteria o ID do espelho — o override nunca pegaria.
  state_key = "trava_tunel_producao"

  override_resource {
    target = cloudflare_zero_trust_tunnel_cloudflared.espelho
    values = {
      id = "99999999-8888-7777-6666-555555555555"
    }
  }

  expect_failures = [cloudflare_zero_trust_tunnel_cloudflared_config.espelho]
}

run "recusa_ad_indice_fora_da_regiao" {
  # O mock tem 1 domínio de disponibilidade; pedir o terceiro tem de reprovar
  # com a mensagem da pré-condição, não com erro genérico de índice.
  command = plan
  variables {
    ad_indice = 2
  }
  expect_failures = [oci_core_instance.espelho]
}

run "recusa_ad_indice_negativo" {
  command = plan
  variables {
    ad_indice = -1
  }
  expect_failures = [var.ad_indice]
}
