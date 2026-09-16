# -----------------------------------------------------------------------------
# OCI: tudo do espelho vive num compartimento próprio. É a fronteira que impede
# este state de tocar em produção — nada aqui referencia recurso de produção, e
# um `terraform destroy` só alcança o que está dentro do compartimento.
# -----------------------------------------------------------------------------

resource "oci_identity_compartment" "espelho" {
  compartment_id = var.tenancy_ocid
  name           = "meujet-espelho"
  description    = "Espelho de testes de carga do Meu Jet (infra/espelho). Descartável."
  enable_delete  = true
}

locals {
  compartimento = oci_identity_compartment.espelho.id
  tags = {
    projeto  = "meujet"
    ambiente = "espelho"
    gerencia = "terraform:infra/espelho/terraform"
  }
}

data "oci_identity_availability_domains" "ads" {
  compartment_id = var.tenancy_ocid
}

# A imagem Ubuntu 24.04 ARM mais recente da região — a mesma família da VM de
# produção. "24.04" exato exclui a variante Minimal.
data "oci_core_images" "ubuntu" {
  compartment_id           = var.tenancy_ocid
  operating_system         = "Canonical Ubuntu"
  operating_system_version = "24.04"
  shape                    = "VM.Standard.A1.Flex"
  sort_by                  = "TIMECREATED"
  sort_order               = "DESC"
}

resource "oci_core_vcn" "espelho" {
  compartment_id = local.compartimento
  display_name   = "meujet-espelho"
  cidr_blocks    = ["10.42.0.0/16"]
  dns_label      = "espelho"
  freeform_tags  = local.tags
}

# Só saída para a internet (git clone, apt, imagens, cloudflared). Nenhuma porta
# web entra: o tráfego público chega pelo túnel, que é conexão de SAÍDA — igual
# a produção.
resource "oci_core_internet_gateway" "espelho" {
  compartment_id = local.compartimento
  vcn_id         = oci_core_vcn.espelho.id
  display_name   = "meujet-espelho"
  enabled        = true
  freeform_tags  = local.tags
}

resource "oci_core_route_table" "espelho" {
  compartment_id = local.compartimento
  vcn_id         = oci_core_vcn.espelho.id
  display_name   = "meujet-espelho"
  freeform_tags  = local.tags

  route_rules {
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_internet_gateway.espelho.id
  }
}

resource "oci_core_security_list" "espelho" {
  compartment_id = local.compartimento
  vcn_id         = oci_core_vcn.espelho.id
  display_name   = "meujet-espelho"
  freeform_tags  = local.tags

  egress_security_rules {
    destination = "0.0.0.0/0"
    protocol    = "all"
  }

  # SSH só do CIDR informado.
  ingress_security_rules {
    source   = var.ssh_cidr_permitido
    protocol = "6" # TCP
    tcp_options {
      min = 22
      max = 22
    }
  }

  # ICMP "fragmentation needed": sem ele, conexões grandes travam por MTU.
  ingress_security_rules {
    source   = "0.0.0.0/0"
    protocol = "1" # ICMP
    icmp_options {
      type = 3
      code = 4
    }
  }
}

resource "oci_core_subnet" "espelho" {
  compartment_id    = local.compartimento
  vcn_id            = oci_core_vcn.espelho.id
  display_name      = "meujet-espelho"
  cidr_block        = "10.42.1.0/24"
  dns_label         = "publica"
  route_table_id    = oci_core_route_table.espelho.id
  security_list_ids = [oci_core_security_list.espelho.id]
  freeform_tags     = local.tags
}

resource "oci_core_instance" "espelho" {
  compartment_id = local.compartimento
  # try(): índice fora do intervalo cai na pré-condição abaixo, com mensagem clara,
  # em vez de um "index out of range" genérico.
  availability_domain = try(data.oci_identity_availability_domains.ads.availability_domains[var.ad_indice].name, "")
  display_name        = "meujet-espelho"
  shape               = "VM.Standard.A1.Flex"
  state               = var.ligada ? "RUNNING" : "STOPPED"
  freeform_tags       = local.tags

  shape_config {
    ocpus         = var.ocpus
    memory_in_gbs = var.memoria_gb
  }

  source_details {
    source_type             = "image"
    source_id               = data.oci_core_images.ubuntu.images[0].id
    boot_volume_size_in_gbs = var.disco_gb
  }

  create_vnic_details {
    subnet_id        = oci_core_subnet.espelho.id
    assign_public_ip = "true"
    hostname_label   = "espelho"
  }

  metadata = {
    ssh_authorized_keys = trimspace(file(pathexpand(var.ssh_chave_publica_arquivo)))
    user_data = base64encode(templatefile("${path.module}/cloud-init.yaml.tftpl", {
      dominio               = var.dominio
      tunnel_token          = data.cloudflare_zero_trust_tunnel_cloudflared_token.espelho.token
      tunnel_id             = cloudflare_zero_trust_tunnel_cloudflared.espelho.id
      prod_tunnel_id        = var.prod_tunnel_id
      platform_admin_emails = var.platform_admin_emails
      repo_url              = var.repo_url
      git_ref               = var.git_ref
      docker_ce_versao      = var.docker_ce_versao
      containerd_versao     = var.containerd_versao
      buildx_versao         = var.buildx_versao
      compose_versao        = var.compose_versao
    }))
  }

  lifecycle {
    # Recriar a VM é SEMPRE decisão explícita (-replace), nunca efeito colateral:
    # - imagem Ubuntu nova publicada pela Oracle;
    # - metadata (cloud-init, git_ref, chave SSH): na OCI qualquer mudança aqui
    #   FORÇA a recriação da instância. Visto em 16/set/2026: só atualizar o
    #   template no repositório fazia um `apply` destruir o espelho em uso, com
    #   operador, tenant de carga e dados sintéticos dentro.
    ignore_changes = [source_details[0].source_id, metadata]

    # A exceção: túnel recriado = token novo. A VM com o token antigo ficaria sem
    # ingress, então ela é recriada junto.
    replace_triggered_by = [cloudflare_zero_trust_tunnel_cloudflared.espelho]

    precondition {
      condition     = length(data.oci_core_images.ubuntu.images) > 0
      error_message = "Nenhuma imagem Canonical Ubuntu 24.04 para VM.Standard.A1.Flex nesta região."
    }

    precondition {
      condition     = var.ad_indice < length(data.oci_identity_availability_domains.ads.availability_domains)
      error_message = "ad_indice fora do intervalo: esta região tem ${length(data.oci_identity_availability_domains.ads.availability_domains)} domínio(s) de disponibilidade (índices a partir de 0)."
    }
  }

  # A config do túnel precisa existir antes do cloudflared subir na VM, senão
  # ele conecta e não roteia nada até a próxima atualização de configuração.
  depends_on = [cloudflare_zero_trust_tunnel_cloudflared_config.espelho]
}
