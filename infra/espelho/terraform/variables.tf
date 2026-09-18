# -----------------------------------------------------------------------------
# Obrigatórias
# -----------------------------------------------------------------------------

variable "tenancy_ocid" {
  description = "OCID da tenancy. O compartimento do espelho é criado sob ela."
  type        = string

  validation {
    condition     = startswith(var.tenancy_ocid, "ocid1.tenancy.")
    error_message = "tenancy_ocid precisa ser um OCID de tenancy (ocid1.tenancy...)."
  }
}

variable "prod_tunnel_id" {
  description = <<-EOT
    UUID do túnel Cloudflare de PRODUÇÃO (Zero Trust → Networks → Tunnels).
    Não é usado para criar nada: é a referência que as travas comparam — o
    Terraform recusa aplicar se o túnel gerenciado aqui for o de produção, e
    o preflight da VM faz a mesma checagem com o token.
  EOT
  type        = string

  validation {
    condition     = can(regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", var.prod_tunnel_id))
    error_message = "prod_tunnel_id precisa ser o UUID do túnel de produção (formato 8-4-4-4-12, minúsculo)."
  }
}

variable "ssh_cidr_permitido" {
  description = "Único CIDR que chega na porta 22 da VM (ex.: \"203.0.113.10/32\" — o seu IP)."
  type        = string

  validation {
    condition     = can(cidrhost(var.ssh_cidr_permitido, 0)) && var.ssh_cidr_permitido != "0.0.0.0/0"
    error_message = "ssh_cidr_permitido precisa ser um CIDR válido e não pode ser 0.0.0.0/0."
  }
}

variable "ssh_chave_publica_arquivo" {
  description = "Caminho da chave pública SSH autorizada na VM (ex.: ~/.ssh/id_ed25519.pub)."
  type        = string
}

# -----------------------------------------------------------------------------
# Com padrão — o padrão é o que torna o espelho IGUAL a produção
# -----------------------------------------------------------------------------

variable "dominio" {
  description = "Domínio do espelho. Precisa ser uma zona da conta Cloudflare e estar no nginx.conf."
  type        = string
  default     = "jetsave.com.br"

  validation {
    condition     = !contains(["meujet.com.br", "pegaojet.com.br"], var.dominio) && !endswith(var.dominio, ".meujet.com.br") && !endswith(var.dominio, ".pegaojet.com.br")
    error_message = "O espelho não pode usar o domínio de produção (meujet) nem o do dev (pegaojet)."
  }
}

variable "rotas_extras" {
  description = "Subdomínio → serviço na rede do espelho, fora do nginx (ex.: a Praia Sintética). Cada um vira rota no túnel e CNAME."
  type        = map(string)
  default     = { praia = "http://praia:7331" }

  validation {
    condition     = alltrue([for sub, s in var.rotas_extras : !contains(["www", "app", "cliente", "admin", "sso"], sub) && can(regex("^http://[a-z0-9-]+:[0-9]+$", s))])
    error_message = "Rota extra não pode repetir um hostname do nginx e o serviço é http://<container>:<porta> na rede do espelho."
  }
}

variable "nome_tunel" {
  description = "Nome do túnel Cloudflare do espelho."
  type        = string
  default     = "meujet-espelho"

  validation {
    condition     = strcontains(var.nome_tunel, "espelho")
    error_message = "O nome do túnel precisa conter \"espelho\" — é o que o distingue do de produção no painel."
  }
}

variable "ligada" {
  description = "false desliga a VM (terraform apply -var ligada=false). Parada, a Oracle cobra só o disco."
  type        = bool
  default     = true
}

variable "oci_regiao" {
  description = "Região da VM. A de produção é sa-saopaulo-1."
  type        = string
  default     = "sa-saopaulo-1"
}

variable "ad_indice" {
  description = <<-EOT
    Domínio de disponibilidade da VM (0, 1, 2…). São Paulo tem um só; Ashburn
    tem três. Se a criação falhar com "Out of host capacity" — comum para A1,
    ainda mais no Free Tier —, tente outro índice.
  EOT
  type        = number
  default     = 0

  validation {
    condition     = var.ad_indice >= 0 && floor(var.ad_indice) == var.ad_indice
    error_message = "ad_indice precisa ser um inteiro a partir de 0."
  }
}

variable "oci_perfil" {
  description = "Perfil do ~/.oci/config com a chave de API."
  type        = string
  default     = "DEFAULT"
}

variable "ocpus" {
  description = "OCPUs. Produção: 2. Mudar invalida a comparação com produção."
  type        = number
  default     = 2
}

variable "memoria_gb" {
  description = "Memória em GB. Produção: 12. Mudar invalida a comparação com produção."
  type        = number
  default     = 12
}

variable "disco_gb" {
  description = <<-EOT
    Volume de boot em GB. Produção: 200. Na OCI o IOPS e a vazão do volume
    crescem com o tamanho — um disco menor mede outro Postgres.
  EOT
  type        = number
  default     = 200
}

variable "repo_url" {
  description = "Repositório clonado na VM."
  type        = string
  default     = "https://github.com/franciscocfreire/jetski-saas.git"
}

variable "git_ref" {
  description = <<-EOT
    Branch, tag ou commit a provisionar. Para comparar com produção, use o
    mesmo commit que está no ar lá. O cloud-init só roda na CRIAÇÃO da VM, e o
    Terraform ignora mudanças de metadata para não recriar um espelho em uso
    sem querer: para aplicar um git_ref novo, recrie explicitamente com
    `terraform apply -replace=oci_core_instance.espelho -var git_ref=...`.
  EOT
  type        = string
  default     = "main"

  validation {
    condition     = can(regex("^[A-Za-z0-9._/-]+$", var.git_ref))
    error_message = "git_ref só aceita letras, números, ., _, / e -."
  }
}

variable "platform_admin_emails" {
  description = "E-mails promovidos a operador de plataforma no boot (ver README, \"Primeiro operador\")."
  type        = string
  default     = ""

  validation {
    # Vai para dentro do cloud-init (YAML): uma quebra de linha aqui injetaria chaves.
    condition     = can(regex("^[A-Za-z0-9.,@+_-]*$", var.platform_admin_emails))
    error_message = "platform_admin_emails: e-mails separados por vírgula, sem espaços nem quebras de linha."
  }
}

# Versões EXATAS do Docker de produção (dpkg em 16/set/2026). Atualize junto
# com produção, senão o espelho mede outro comportamento de deploy.
variable "docker_ce_versao" {
  type    = string
  default = "5:29.6.0-1~ubuntu.24.04~noble"
}

variable "containerd_versao" {
  type    = string
  default = "2.2.4-1~ubuntu.24.04~noble"
}

variable "buildx_versao" {
  type    = string
  default = "0.34.1-1~ubuntu.24.04~noble"
}

variable "compose_versao" {
  type    = string
  default = "5.1.4-1~ubuntu.24.04~noble"
}
