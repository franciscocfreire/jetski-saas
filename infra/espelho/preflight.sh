#!/usr/bin/env bash
# =============================================================================
# Preflight do ESPELHO — reprova o .env antes de qualquer container subir.
#
# Chamado pelo deploy.sh quando MEUJET_AMBIENTE=espelho. Também roda sozinho:
#   ./infra/espelho/preflight.sh            # lê ./.env
#
# Cada checagem aqui existe por um estrago concreto que um espelho montado a
# partir da configuração de produção causaria. Falha = sai 1 e diz por quê.
# Somente leitura: não escreve nada, não sobe nada.
# =============================================================================
set -uo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$RAIZ/.env}"

VERMELHO='\033[0;31m'; AMARELO='\033[1;33m'; VERDE='\033[0;32m'; NC='\033[0m'
falhas=0
reprova() { echo -e "${VERMELHO}  ✗${NC} $*"; falhas=$((falhas + 1)); }
avisa()   { echo -e "${AMARELO}  !${NC} $*"; }
ok()      { echo -e "${VERDE}  ✓${NC} $*"; }

[ -f "$ENV_FILE" ] || { echo "preflight: $ENV_FILE não existe" >&2; exit 1; }
set -a; . "$ENV_FILE"; set +a

echo "preflight do espelho ($ENV_FILE)"

# --- 0. É mesmo um espelho, e não estamos na VM de produção ------------------
[ "${MEUJET_AMBIENTE:-}" = "espelho" ] \
  && ok "MEUJET_AMBIENTE=espelho" \
  || reprova "MEUJET_AMBIENTE não é 'espelho' (valor: '${MEUJET_AMBIENTE:-}')"

# A VM de produção tem o timer de backup instalado pelo deploy.sh; o espelho
# nunca instala. Achar o timer aqui é o sinal mais barato de "máquina errada".
MARCADOR_PRODUCAO="${MARCADOR_PRODUCAO:-/etc/systemd/system/meujet-backup.timer}"
if [ -f "$MARCADOR_PRODUCAO" ]; then
  reprova "esta máquina tem o timer de backup de PRODUÇÃO ($MARCADOR_PRODUCAO) — é a VM de produção?"
else
  ok "sem timer de backup de produção nesta máquina"
fi

# --- 1. Nenhum placeholder esquecido -----------------------------------------
if grep -nE '__[A-Z_]+__|troque|cole-o-token' "$ENV_FILE" | grep -vE '^[0-9]+:\s*#' >/dev/null; then
  reprova "há valores de modelo não preenchidos no .env:"
  grep -nE '__[A-Z_]+__|troque|cole-o-token' "$ENV_FILE" | grep -vE '^[0-9]+:\s*#' | sed 's/=.*/=…/; s/^/      /'
else
  ok "nenhum placeholder no .env"
fi

# --- 2. Hostnames fora de produção e do dev ----------------------------------
# Subdomínio de meujet.com.br não funciona para o espelho (nginx roteia por
# server_name fixo, a vitrine captura *.meujet.com.br, e o certificado
# universal da Cloudflare não cobre dois níveis) — e, pior, pode colidir com
# um hostname real. pegaojet.com.br é o dev.
for var in PUBLIC_URL APP_PUBLIC_URL PORTAL_PUBLIC_URL CONSOLE_PUBLIC_URL SSO_PUBLIC_URL; do
  valor="${!var:-}"
  host="${valor#*://}"; host="${host%%/*}"
  if [ -z "$host" ]; then
    reprova "$var vazio"
  elif [[ "$host" == meujet.com.br || "$host" == *.meujet.com.br ]]; then
    reprova "$var aponta para PRODUÇÃO ($host)"
  elif [[ "$host" == pegaojet.com.br || "$host" == *.pegaojet.com.br ]]; then
    reprova "$var aponta para o DEV ($host)"
  fi
done

# O domínio do espelho precisa estar no nginx.conf e no middleware da vitrine,
# senão cliente./admin./sso. caem no servidor padrão e o portal e o SSO
# respondem conteúdo errado — sem erro nenhum, só comportamento estranho.
dominio="${SSO_PUBLIC_URL#*://sso.}"; dominio="${dominio%%/*}"
if [ -n "$dominio" ] && [ "$dominio" != "$SSO_PUBLIC_URL" ]; then
  faltando=()
  for sub in cliente admin sso; do
    grep -qE "server_name[^;]*\b${sub}\.${dominio//./\\.}\b" "$RAIZ/infra/nginx/nginx.conf" || faltando+=("${sub}.${dominio}")
  done
  if [ ${#faltando[@]} -gt 0 ]; then
    reprova "infra/nginx/nginx.conf não roteia: ${faltando[*]} (adicione aos server_name, como o pegaojet)"
  else
    ok "nginx.conf conhece cliente/admin/sso.${dominio}"
  fi
  base="${dominio%%.*}"
  if grep -qE "HOST_VITRINE.*\b${base}\b" "$RAIZ/frontend/jetski-backoffice/middleware.ts"; then
    ok "middleware da vitrine conhece ${dominio}"
  else
    avisa "frontend/jetski-backoffice/middleware.ts não inclui '${base}' em HOST_VITRINE (só afeta a vitrine por subdomínio)"
  fi
else
  reprova "SSO_PUBLIC_URL precisa ser https://sso.<dominio> (valor: '${SSO_PUBLIC_URL:-}')"
fi

# --- 3. Túnel Cloudflare próprio ---------------------------------------------
# Com o token de produção, o cloudflared do espelho entra como RÉPLICA do túnel
# de produção e a Cloudflare distribui tráfego real de meujet.com.br para cá.
# O token é base64 de um JSON {"a": conta, "t": túnel, "s": segredo}.
tunel=$(printf '%s' "${CLOUDFLARE_TUNNEL_TOKEN:-}" | base64 -d 2>/dev/null \
        | sed -n 's/.*"t" *: *"\([0-9a-fA-F-]*\)".*/\1/p')
if [ -z "$tunel" ]; then
  reprova "não consegui ler o UUID do túnel no CLOUDFLARE_TUNNEL_TOKEN (token ausente ou em formato inesperado)"
else
  if [ -n "${PROD_TUNNEL_ID:-}" ] && [ "$tunel" = "$PROD_TUNNEL_ID" ]; then
    reprova "CLOUDFLARE_TUNNEL_TOKEN é do túnel de PRODUÇÃO ($tunel)"
  fi
  if [ "$tunel" = "${ESPELHO_TUNNEL_ID:-}" ]; then
    ok "token do túnel confere com ESPELHO_TUNNEL_ID ($tunel)"
  else
    reprova "o token é do túnel $tunel, mas ESPELHO_TUNNEL_ID='${ESPELHO_TUNNEL_ID:-}' — confira no painel que é o túnel do espelho e preencha"
  fi
  [ -z "${PROD_TUNNEL_ID:-}" ] && avisa "PROD_TUNNEL_ID vazio — preencha para o preflight recusar o túnel de produção por comparação"
fi

# --- 4. E-mail só para o Mailpit ---------------------------------------------
[ "${PLATFORM_SMTP_HOST:-}" = "mailpit" ] \
  && ok "SMTP da plataforma = mailpit" \
  || reprova "PLATFORM_SMTP_HOST='${PLATFORM_SMTP_HOST:-}' — no espelho tem de ser 'mailpit'"
for var in GMAIL_USER GMAIL_APP_PASSWORD PLATFORM_SMTP_PASSWORD PLATFORM_SMTP_USERNAME; do
  [ -n "${!var:-}" ] && reprova "$var preenchido — o espelho não pode ter credencial de e-mail real (cota do Gmail é da operação)"
done

# --- 5. Backup sem destino off-site ------------------------------------------
# backup.sh faz `rclone sync`: apontado para o remoto de produção, APAGA do
# destino os backups que não existem neste espelho.
if [ -n "${BACKUP_RCLONE_REMOTE:-}" ]; then
  reprova "BACKUP_RCLONE_REMOTE='${BACKUP_RCLONE_REMOTE}' — o rclone sync do espelho apagaria backups desse destino"
else
  ok "sem destino de backup off-site"
fi
if command -v rclone >/dev/null 2>&1 && [ -n "$(rclone listremotes 2>/dev/null)" ]; then
  avisa "há remotos rclone configurados nesta máquina ($(rclone listremotes | tr '\n' ' ')) — config copiada de produção? Remova."
fi

# --- 6. Login social: Google real desligado, Google sintético com segredos próprios --
if [ "${GOOGLE_IDP_ENABLED:-false}" = "true" ] || [ -n "${GOOGLE_CLIENT_ID:-}" ]; then
  reprova "login Google configurado — o client OAuth é de produção e o redirect URI não é deste domínio"
else
  ok "login Google real desligado"
fi
# O alias `google` do espelho aponta para o realm google-sintetico do próprio Keycloak
# (fase E7). Sem os segredos, o deploy pularia a config e o login social ficaria no
# Google nativo desabilitado — as provas do semeador falhariam tarde e de forma confusa.
for var in GOOGLE_SINTETICO_BROKER_SECRET GOOGLE_SINTETICO_SEMEADOR_SECRET; do
  [ -n "${!var:-}" ] || reprova "$var vazio — o Google sintético do espelho precisa dele (openssl rand -base64 32 | tr -d '=+/')"
done
[ "${GOOGLE_SINTETICO_BROKER_SECRET:-a}" != "${GOOGLE_SINTETICO_SEMEADOR_SECRET:-b}" ] \
  && ok "segredos do Google sintético presentes e distintos" \
  || reprova "GOOGLE_SINTETICO_BROKER_SECRET e GOOGLE_SINTETICO_SEMEADOR_SECRET iguais — o semeador não pode ser o broker"

# --- 7. Sistemas externos: fakes + sumidouro de DNS ---------------------------
# Um CPF sintético de DV válido pode ser de uma pessoa real (não existe faixa de
# teste). Ele não pode chegar à Marinha nem ao Tesouro: as bases da GRU têm de
# apontar para o serviço de fakes E os domínios reais têm de estar afundados.
# Confere o compose RENDERIZADO quando há docker (é o que vai rodar de fato);
# sem docker, cai para o texto da camada do espelho.
CAMADA="$RAIZ/docker-compose.espelho.yml"
if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  alvo=$(cd "$RAIZ" && docker compose --env-file "$ENV_FILE" -f docker-compose.yml \
         -f docker-compose.prod.yml -f docker-compose.espelho.yml config 2>/dev/null \
         | awk '/^  backend:/{f=1;next} f&&/^  [a-z]/{f=0} f')
  origem="compose renderizado"
else
  alvo=$(cat "$CAMADA" 2>/dev/null)
  origem="docker-compose.espelho.yml (sem docker para renderizar)"
fi
faltam=()
for dominio_real in dpc1.marinha.mil.br pagtesouro.tesouro.gov.br freetsa.org; do
  grep -qE "${dominio_real//./\\.}[=:]127\.0\.0\.1" <<<"$alvo" || faltam+=("sumidouro de $dominio_real")
done
for base in marinha-base pagtesouro-base; do
  grep -qE "jetski\.gru\.${base}[\\\\\"]*:[\\\\\"]*http://fakes-externos[:/]" <<<"$alvo" || faltam+=("jetski.gru.$base → fakes-externos")
done
if [ ${#faltam[@]} -gt 0 ]; then
  reprova "proteção dos sistemas externos incompleta no backend ($origem): ${faltam[*]}"
else
  ok "Marinha/PagTesouro/TSA: bases nos fakes e domínios reais afundados ($origem)"
fi

# --- 8. O serviço de fakes não pode ficar exposto ------------------------------
# O /_controle paga GRU e injeta falha sem autenticação: só pode ser publicado em
# loopback (e nunca entrar no nginx/túnel, o que a checagem 4 já cobre por hostname).
if grep -q '^  fakes-externos:' "$CAMADA" 2>/dev/null; then
  expostas=$(awk '/^  fakes-externos:/{f=1;next} f&&/^  [a-z]/{f=0} f&&/^ +- "[^"]*:[0-9]+"/ && !/"127\.0\.0\.1:/' "$CAMADA")
  if [ -n "$expostas" ]; then
    reprova "fakes-externos publicado fora do loopback: $(tr -s ' \n' ' ' <<<"$expostas")"
  else
    ok "fakes-externos publicado só em 127.0.0.1"
  fi
else
  reprova "docker-compose.espelho.yml sem o serviço fakes-externos — a emissão de GRU não teria para onde ir"
fi

# --- 9. nginx do espelho vem do arquivo GERADO (limites por IP) --------------
# A camada monta infra/espelho/nginx.espelho.conf, que o deploy.sh gera a partir do
# nginx.conf de produção (nginx-limites.sh). Se a montagem sumir, o espelho volta a
# medir o nginx em vez da aplicação.
case "${ESPELHO_LIMITES:-frouxos}" in
  frouxos|reais) ;;
  *) reprova "ESPELHO_LIMITES='${ESPELHO_LIMITES}' — use frouxos ou reais" ;;
esac
if [ -d "$RAIZ/infra/espelho/nginx.espelho.conf" ]; then
  # Um `up` antes do nginx-limites.sh faz o Docker criar um DIRETÓRIO no lugar do arquivo.
  reprova "infra/espelho/nginx.espelho.conf é um diretório (nginx subiu antes de gerar o arquivo) — rmdir e rode o deploy de novo"
elif grep -q 'infra/espelho/nginx.espelho.conf:/etc/nginx/nginx.conf' "$CAMADA" 2>/dev/null && [ -x "$RAIZ/infra/espelho/nginx-limites.sh" ]; then
  ok "nginx do espelho montado do arquivo gerado (limites: ${ESPELHO_LIMITES:-frouxos})"
else
  reprova "docker-compose.espelho.yml não monta infra/espelho/nginx.espelho.conf (ou falta nginx-limites.sh)"
fi

echo
if [ "$falhas" -gt 0 ]; then
  echo -e "${VERMELHO}preflight REPROVADO: ${falhas} problema(s). Nada foi alterado.${NC}"
  exit 1
fi
echo -e "${VERDE}preflight aprovado.${NC}"
