# Versões conferidas no registry em 16/set/2026. O provider da Cloudflare
# mudou quase todos os nomes de recurso na v5 — não "relaxe" para ~> 4.
terraform {
  required_version = ">= 1.9"

  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "~> 9.2"
    }
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "~> 5.25"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.9"
    }
  }
}

# Credenciais NUNCA em arquivo versionado nem em tfvars:
# - OCI: perfil do ~/.oci/config (`oci setup config`).
# - Cloudflare: variável de ambiente CLOUDFLARE_API_TOKEN.
provider "oci" {
  region              = var.oci_regiao
  config_file_profile = var.oci_perfil
}

provider "cloudflare" {}
