#!/usr/bin/env bash
###############################################################################
# Bootstrap do SERVIDOR (one-time) — Ubuntu 24.04 ARM64 (Oracle Cloud)
# Instala Docker + compose plugin e prepara o diretório do projeto.
# Rodar UMA vez no servidor como o usuário ubuntu:
#   curl -fsSL https://raw.githubusercontent.com/<owner>/<repo>/main/infra/prod/server-bootstrap.sh | bash
# ou copiar e executar: bash server-bootstrap.sh <git-url>
###############################################################################
set -euo pipefail
REPO_URL="${1:-}"
TARGET="${2:-$HOME/jetski}"

echo "==> Instalando Docker Engine + compose plugin (aarch64)..."
if ! command -v docker >/dev/null 2>&1; then
  sudo apt-get update -y
  sudo apt-get install -y ca-certificates curl git
  sudo install -m 0755 -d /etc/apt/keyrings
  sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  sudo chmod a+r /etc/apt/keyrings/docker.asc
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
    | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
  sudo apt-get update -y
  sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  sudo usermod -aG docker "$USER"
  echo "==> Docker instalado. Faça logout/login (ou: newgrp docker) para usar sem sudo."
fi

docker --version || true
docker compose version || true

if [ -n "$REPO_URL" ]; then
  if [ ! -d "$TARGET/.git" ]; then
    echo "==> Clonando $REPO_URL em $TARGET..."
    git clone "$REPO_URL" "$TARGET"
  else
    echo "==> Repo já existe em $TARGET (pull)..."
    git -C "$TARGET" pull --ff-only
  fi
  echo "==> Próximos passos:"
  echo "    cd $TARGET"
  echo "    cp .env.prod.example .env && nano .env   # preencha os segredos"
  echo "    ./deploy.sh"
else
  echo "==> Sem git-url. Clone manualmente e siga o DEPLOY.md."
fi
