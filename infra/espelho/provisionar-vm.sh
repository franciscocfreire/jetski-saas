#!/usr/bin/env bash
# =============================================================================
# Provisiona a VM do ESPELHO de dentro dela. Chamado pelo cloud-init que o
# Terraform (infra/espelho/terraform) injeta — roda como root, uma vez.
#
# Não substitui nada do fluxo de produção: instala o Docker nas MESMAS versões
# de produção, gera o .env com segredos criados aqui dentro, passa pelo
# preflight e roda o deploy.sh e o stack de observabilidade — os mesmos
# comandos de sempre, só que sem ninguém digitando.
#
# Entrada: /etc/meujet-espelho/entrada.env (0600, escrito pelo cloud-init).
# Log:     /var/log/meujet-espelho.log
# Estado:  /var/lib/meujet-espelho/{provisionado,FALHOU}
#
# Acompanhar de fora:
#   ssh ubuntu@<ip> 'sudo tail -f /var/log/meujet-espelho.log'
# =============================================================================
set -Eeuo pipefail
umask 077

ENTRADA=/etc/meujet-espelho/entrada.env
ESTADO=/var/lib/meujet-espelho
LOG=/var/log/meujet-espelho.log
USUARIO=ubuntu
CASA=/home/$USUARIO
REPO=$CASA/jetski

mkdir -p "$ESTADO"
touch "$LOG" && chmod 600 "$LOG"
exec > >(tee -a "$LOG") 2>&1

falhou() {
  local linha="$1"
  echo "!!!! provisionamento FALHOU na linha $linha (etapa: ${ETAPA:-?}) — ver $LOG"
  printf 'etapa=%s\nlinha=%s\nquando=%s\n' "${ETAPA:-?}" "$linha" "$(date -Is)" > "$ESTADO/FALHOU"
}
trap 'falhou $LINENO' ERR

etapa() { ETAPA="$*"; echo; echo "==== [$(date -Is)] $* ===="; }

# Roda como o usuário ubuntu, com o ambiente que o deploy.sh espera
# (HOME certo e o grupo docker, que o runuser carrega via initgroups).
como_ubuntu() { runuser -u "$USUARIO" -- env HOME="$CASA" "$@"; }

[ -f "$ENTRADA" ] || { echo "sem $ENTRADA — este script é para a VM do espelho" >&2; exit 1; }
set -a; . "$ENTRADA"; set +a
: "${DOMINIO:?}" "${CLOUDFLARE_TUNNEL_TOKEN:?}" "${ESPELHO_TUNNEL_ID:?}" "${PROD_TUNNEL_ID:?}"
: "${DOCKER_CE_VERSAO:?}" "${CONTAINERD_VERSAO:?}" "${BUILDX_VERSAO:?}" "${COMPOSE_VERSAO:?}"

rm -f "$ESTADO/FALHOU"

# A persona operadora de plataforma (sintetico/catalogo/e3a.json). Entra em
# PLATFORM_ADMIN_EMAILS desde o primeiro boot: é o próprio mecanismo de bootstrap da
# plataforma — o boot do backend promove quem está na lista E já tem conta.
OPERADORA_SINTETICA=$(sed -n '/"operadorPlataforma"/,/}/s/.*"email": *"\([^"]*\)".*/\1/p' "$REPO/sintetico/catalogo/e3a.json" 2>/dev/null | head -1)
: "${OPERADORA_SINTETICA:?não achei o e-mail da operadora em sintetico/catalogo/e3a.json}"
NODE_IMAGEM=node:24-alpine   # Node 24 roda TypeScript direto; o semeador não tem dependências

semeador() { # semeador <comando>  — container efêmero, rede do host (Mailpit em 127.0.0.1)
  docker run --rm --network host \
    -v "$REPO/sintetico:/app:ro" -v "$ESTADO:/estado" \
    -e DOMINIO="$DOMINIO" "$NODE_IMAGEM" node /app/src/semeador.ts "$1"
}

# ---------------------------------------------------------------------------
etapa "1/7 Docker nas mesmas versões de produção"
# Mesma origem do infra/prod/server-bootstrap.sh, mas com versão FIXA: a
# diferença de versão do Compose entre dev e produção já derrubou o SSO a cada
# deploy (o digest da imagem mudava e o Keycloak era recriado). Um espelho com
# outro Compose mediria outro comportamento de deploy.
if ! dpkg-query -W -f='${Version}' docker-ce 2>/dev/null | grep -qx "$DOCKER_CE_VERSAO"; then
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
    > /etc/apt/sources.list.d/docker.list
  chmod 644 /etc/apt/sources.list.d/docker.list
  apt-get update -y
  DEBIAN_FRONTEND=noninteractive apt-get install -y --allow-downgrades \
    "docker-ce=$DOCKER_CE_VERSAO" "docker-ce-cli=$DOCKER_CE_VERSAO" \
    "containerd.io=$CONTAINERD_VERSAO" "docker-buildx-plugin=$BUILDX_VERSAO" \
    "docker-compose-plugin=$COMPOSE_VERSAO"
fi
# Sem hold, um apt upgrade qualquer tira o espelho da paridade em silêncio.
apt-mark hold docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
usermod -aG docker "$USUARIO"
docker version --format 'engine {{.Server.Version}}'
docker compose version

# ---------------------------------------------------------------------------
etapa "2/7 Repositório"
git config --system --add safe.directory "$REPO"
git -C "$REPO" log --oneline -1

# ---------------------------------------------------------------------------
etapa "3/7 .env do espelho (segredos gerados aqui dentro)"
# Os segredos nascem na VM e nunca passam pelo Terraform nem pelo state dele.
# Do lado de fora só chegam o token/UUID do túnel e o UUID do túnel de produção.
if [ -f "$REPO/.env" ]; then
  echo ".env já existe — mantido (o gerar-env.sh se recusa a sobrescrever)."
else
  como_ubuntu \
    CLOUDFLARE_TUNNEL_TOKEN="$CLOUDFLARE_TUNNEL_TOKEN" \
    ESPELHO_TUNNEL_ID="$ESPELHO_TUNNEL_ID" \
    PROD_TUNNEL_ID="$PROD_TUNNEL_ID" \
    PLATFORM_ADMIN_EMAILS="${PLATFORM_ADMIN_EMAILS:+$PLATFORM_ADMIN_EMAILS,}$OPERADORA_SINTETICA" \
    bash "$REPO/infra/espelho/gerar-env.sh" "$DOMINIO"
fi

# ---------------------------------------------------------------------------
etapa "4/7 Preflight"
como_ubuntu bash -c "cd '$REPO' && ./infra/espelho/preflight.sh"

# ---------------------------------------------------------------------------
etapa "5/7 deploy.sh (build das imagens em ARM — demora)"
# NO_PULL: o cloud-init já clonou no commit pedido; um pull aqui trocaria o
# código por baixo do que o Terraform declarou.
como_ubuntu bash -c "cd '$REPO' && NO_PULL=1 ./deploy.sh"

# ---------------------------------------------------------------------------
etapa "6/7 Observabilidade (o instrumento do teste)"
como_ubuntu bash -c "cd '$REPO' && docker compose --env-file .env -f infra/observability/docker-compose.observability.yml up -d"

# ---------------------------------------------------------------------------
etapa "7/7 Personas sintéticas (plataforma, empresas de carga, EAMA + delegadas, equipe, clientes)"
# Tudo pelas APIs reais (ECOSSISTEMA_SINTETICO_SPEC.md, fase E3a). O túnel acabou
# de subir: espera o endereço público responder antes de começar.
for i in $(seq 1 60); do
  [ "$(curl -s -o /dev/null -w '%{http_code}' "https://www.$DOMINIO/api/actuator/health")" = "200" ] && break
  sleep 5
done
semeador bootstrap
# A operadora agora tem conta; é o BOOT do backend que a promove (PlatformAdminSeeder).
como_ubuntu bash -c "cd '$REPO' && docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.espelho.yml restart backend"
for i in $(seq 1 60); do
  [ "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8090/api/actuator/health)" = "200" ] && break
  sleep 5
done
semeador semear
# Prova de vida dos fakes (E2): uma GRU de ponta a ponta — gerar, pagar, confirmar, boleto.
# Se a Marinha/PagTesouro sintéticos não estiverem de pé, o espelho não está pronto.
semeador provar-gru
# Prova da E3b: uma emissão PRÓPRIA (EAMA) e uma DELEGADA, até o ofício à Capitania no Mailpit.
semeador provar-emissao
semeador resumo

printf 'commit=%s\nquando=%s\ndominio=%s\n' \
  "$(git -C "$REPO" rev-parse HEAD)" "$(date -Is)" "$DOMINIO" > "$ESTADO/provisionado"
echo
echo "==== espelho provisionado: https://www.$DOMINIO ===="
