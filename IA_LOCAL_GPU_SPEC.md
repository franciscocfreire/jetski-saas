# IA Local (máquina RTX 5080) — Spec de configuração

> Status: **A EXECUTAR** (escrita em 14/set/2026). Documento para ser executado **por um
> agente do Claude Code rodando na própria máquina da RTX 5080**, em ordem. Passos marcados
> **[MANUAL]** precisam do usuário (painel Cloudflare, BIOS, login do Windows). Ao terminar,
> o agente preenche a §12 (registro de instalação) e abre PR em rascunho.
>
> Contexto: servidor de inferência do **assistente de cadastro por IA**, piloto com IA local.
> Regras de identidade/segurança do agente em `AGENTE_IDENTIDADE_SPEC.md` (especialmente A8:
> a máquina de IA **nunca** recebe credencial de usuário).

---

## 1. Papel da máquina e fronteiras

- **Faz:** servir um modelo aberto com chamada de ferramentas (Ollama) para o backend do Meu Jet
  (dev e prod), e servir de laboratório para o conjunto de avaliação (F4).
- **Não faz:** nada de negócio. Não acessa banco, Keycloak, MinIO nem o repositório em runtime.
  Não guarda prompts. Não recebe token de usuário — só o segredo do gateway e o token do túnel.
- **Quem chama:** apenas o backend (container `backend` do dev e da produção Oracle), pela
  internet, via **Cloudflare Tunnel + Cloudflare Access (service token) + bearer do gateway**.
- **Pior caso se a máquina for comprometida:** respostas/propostas erradas, que ainda passam
  por validação do backend e confirmação humana. Por isso nada de credenciais aqui.

```
backend (dev WSL / prod Oracle)
   │  HTTPS  + CF-Access-Client-Id/Secret + Authorization: Bearer <IA_LOCAL_TOKEN>
   ▼
Cloudflare Access ──► Cloudflare Tunnel
                           │ (conexão de saída; nenhuma porta aberta no roteador)
                           ▼
            ┌──────────── máquina RTX 5080 (docker compose) ────────────┐
            │ cloudflared ──► gateway (nginx :8080) ──► ollama (:11434)  │
            │                  bearer + allowlist        GPU, sem porta   │
            │                  de endpoints              publicada na LAN │
            └────────────────────────────────────────────────────────────┘
```

---

## 2. Levantamento inicial (o agente executa e registra na §12)

Rodar e anotar a saída resumida:

| Item | Comando | Esperado |
|---|---|---|
| SO | `systeminfo` (PowerShell) ou `cat /etc/os-release` | Windows 11 + WSL2, ou Linux |
| GPU + driver | `nvidia-smi` | RTX 5080, 16 GB, driver recente (anotar versão) |
| WSL (se Windows) | `wsl --version` | WSL2 atualizado |
| Docker | `docker version` e `docker compose version` | **Compose v2 na versão mais nova** (regra do usuário; nunca `docker-compose`) |
| GPU no Docker | `docker run --rm --gpus all nvidia/cuda:12.8.0-base-ubuntu24.04 nvidia-smi` (confirmar tag atual) | lista a RTX 5080 |
| RAM / CPU | `free -g`, `nproc` (WSL) | anotar |
| Disco | `df -h` / Explorer | **≥ 60 GB livres** para imagens e modelos |
| Rede | `curl -sI https://www.cloudflare.com` | saída para internet ok |

Se `--gpus all` falhar:
- **Windows + Docker Desktop:** driver NVIDIA instalado **no Windows** (não dentro do WSL),
  Docker Desktop com engine WSL2, reiniciar Docker Desktop.
- **Linux nativo:** instalar NVIDIA Container Toolkit e
  `sudo nvidia-ctk runtime configure --runtime=docker && sudo systemctl restart docker`.

Não seguir adiante sem GPU visível dentro de container.

---

## 3. Host sempre ligado

Objetivo: após queda de energia, update ou reboot, o serviço volta sozinho em ≤ 5 min.

**Windows (caminho provável):**
1. Energia: `powercfg /change standby-timeout-ac 0`, `powercfg /change hibernate-timeout-ac 0`,
   `powercfg /hibernate off`; plano "Alto desempenho" ou equilibrado sem suspensão.
2. **[MANUAL] BIOS:** "Restore on AC Power Loss" = **Power On**.
3. **[MANUAL] Login automático** do usuário que roda o Docker Desktop (Docker Desktop precisa de
   sessão). Usar conta local dedicada se possível. Com login automático, **BitLocker ligado**
   é obrigatório (os modelos não são sensíveis, mas os tokens do `.env` são).
4. Docker Desktop: "Start Docker Desktop when you sign in" = on; recursos da VM WSL com folga
   (se preciso, `.wslconfig` com `memory=` adequado — anotar).
5. Windows Update: horário ativo cobrindo 08h–22h; reinício automático só de madrugada.
   Anotar o critério escolhido.
6. **[MANUAL] Recomendado:** nobreak. Sem nobreak, aceitar que queda de luz derruba o assistente
   (o backend mostra "assistente indisponível").
7. Driver NVIDIA: preferir a linha estável (Studio). Não atualizar sem refazer a §9.

**Linux nativo:** `systemctl enable docker`, BIOS igual ao item 2, updates automáticos com
reboot agendado de madrugada.

Todos os serviços do compose usam `restart: unless-stopped`.

---

## 4. Estrutura no repositório (entregável do agente)

Criar em branch própria (sugestão `feat/ia-local-gpu`), **sem tocar no backend**:

```
infra/ia-local/
├── README.md                 # resumo operacional + link para esta spec
├── docker-compose.yml        # ollama + gateway + cloudflared
├── .env.example              # nomes das variáveis, sem valores reais
├── gateway/nginx.conf        # bearer + allowlist de endpoints + health
├── modelfiles/               # um Modelfile por modelo candidato (num_ctx, temperatura)
└── scripts/
    ├── pull-models.sh        # baixa modelos pinados e cria os derivados
    ├── smoke-test.sh         # §9: saúde, segurança, tool call, desempenho
    └── status.sh             # nvidia-smi + ollama ps + health
```

`.env` real **nunca** vai para o git (conferir `.gitignore`). Merge na `main` dispara CI/CD
(regra 5 do `CLAUDE.md`): abrir PR em rascunho e deixar o merge para o usuário.

---

## 5. Compose

Requisitos (o agente escreve o arquivo seguindo isto):

- **`ollama`**
  - imagem `ollama/ollama:<versão estável atual>` — **pinar a tag** (nunca `latest`) e anotar na §12;
  - GPU via `deploy.resources.reservations.devices` (`driver: nvidia`, `count: all`,
    `capabilities: [gpu]`);
  - volume nomeado para `/root/.ollama`;
  - **sem `ports:`** (não publicar na LAN). Para debug local, no máximo
    `127.0.0.1:11434:11434` comentado;
  - ambiente (confirmar nomes no `envconfig` da versão pinada):

    | Variável | Valor inicial | Por quê |
    |---|---|---|
    | `OLLAMA_HOST` | `0.0.0.0:11434` | escuta só na rede do compose |
    | `OLLAMA_CONTEXT_LENGTH` | `16384` | padrão 4096 é curto para ferramentas + histórico |
    | `OLLAMA_KEEP_ALIVE` | `-1` | modelo sempre carregado (sem latência de carga) |
    | `OLLAMA_MAX_LOADED_MODELS` | `1` | 16 GB: um modelo por vez |
    | `OLLAMA_NUM_PARALLEL` | `2` | 2 conversas simultâneas; medir VRAM |
    | `OLLAMA_FLASH_ATTENTION` | `1` | menos VRAM por contexto |
    | `OLLAMA_KV_CACHE_TYPE` | `q8_0` | KV cache quantizado, cabe o contexto de 16k |
    | `OLLAMA_DEBUG` | ausente/`0` | **debug loga prompts** (dados pessoais) |

  - healthcheck: `ollama list` (ou `curl` em `/api/version`, se houver curl na imagem).
- **`gateway`** (`nginx:<versão estável pinada>`)
  - escuta `8080` só na rede interna do compose;
  - exige `Authorization: Bearer ${IA_LOCAL_TOKEN}`. Sem ele, `401`. Comparação exata, via
    `map`/template gerado no start a partir do env;
  - **allowlist** (qualquer outro caminho → `403`):
    - `POST /v1/chat/completions` (principal);
    - `POST /api/chat` (nativo, alternativa);
    - `GET /v1/models`, `GET /api/tags`, `GET /api/ps`, `GET /api/version`;
    - `GET /health`, sem bearer: responde `200 ok` se o upstream responde `/api/version`, sem
      detalhes.
  - **bloqueados explicitamente:** `/api/pull`, `/api/push`, `/api/delete`, `/api/create`,
    `/api/copy`, `/api/blobs`, `/api/embed*` (a API do Ollama não tem autenticação; esses
    endpoints administram a máquina);
  - `client_max_body_size 2m`; `proxy_read_timeout 180s`; `proxy_buffering off` (streaming);
  - `access_log` com método, caminho, status, tempo e bytes — **sem corpo, sem headers de
    autorização**; `error_log` em `warn`.
- **`cloudflared`** (`cloudflare/cloudflared:<versão pinada>`)
  - `tunnel --no-autoupdate run --token ${CLOUDFLARE_TUNNEL_TOKEN}`;
  - rede do compose; ingress aponta para `http://gateway:8080`.

---

## 6. Cloudflare [MANUAL, com apoio do agente]

1. **Zero Trust → Networks → Tunnels → Create tunnel** `meujet-ia-local` (tipo cloudflared).
   Copiar o token para `CLOUDFLARE_TUNNEL_TOKEN` no `.env` local.
2. **Public hostnames** do túnel → serviço `http://gateway:8080`:
   - dev: `ia.pegaojet.com.br`;
   - prod: `ia.meujet.com.br`.
   (Nomes sugeridos; se mudar, anotar na §12.)
3. **Access → Applications → Self-hosted** cobrindo os dois hostnames, política
   **Service Auth** que só aceita os service tokens:
   - `meujet-backend-dev`;
   - `meujet-backend-prod`.

   Nenhuma política de login por e-mail (ninguém acessa por navegador).
4. **Service tokens:** criar os dois acima e guardar Client ID/Secret no gerenciador de
   segredos. Vão para o `.env` do backend dev e do prod, **não** para esta máquina.
5. **WAF / Bot:** garantir que as chamadas do backend não caiam em desafio ou bloqueio. Já
   houve erro 1010 no e2e com cliente não-navegador. Se preciso, regra de skip para os
   hostnames `ia.*` quando os headers do Access estiverem presentes.
6. Gerar `IA_LOCAL_TOKEN` forte (`openssl rand -hex 32`) e colocar no `.env` desta máquina
   **e** no `.env` do backend. É uma segunda camada caso uma regra do Access seja alterada.

---

## 7. Modelos

1. Candidatos a avaliar (confirmar tags atuais em `ollama.com/library` antes de baixar):
   - `qwen3:14b` (Q4, ~9 GB) — referência de tool calling em 12–16 GB;
   - `gemma4:12b` (~7,6 GB);
   - a variante **Qwen3.5** que caiba com folga em 16 GB, com 16k de contexto (anotar a tag).
2. `pull-models.sh`:
   - baixa os candidatos;
   - registra o **digest** de cada um (`ollama list` / `ollama show`) na §12;
   - cria derivados via Modelfile: `meujet-assistente-<modelo>` com
     `PARAMETER num_ctx 16384`, `PARAMETER temperature 0.2`.

   O backend chama sempre o nome derivado, e trocar o modelo base fica transparente. A API
   compatível com OpenAI não aceita contexto por requisição, por isso o Modelfile.
3. Orçamento de VRAM (16 GB), com `ollama ps` confirmando **100% GPU**:
   - modelo de ~14B Q4: ~9 GB;
   - KV cache q8 com 16k de contexto × 2 paralelos: ~2–3 GB;
   - total ~12 GB (sobra de segurança).

   Se sair "CPU/GPU" misto, reduzir para `OLLAMA_NUM_PARALLEL=1` ou o contexto para `12288`,
   e anotar.
4. Modelo padrão do piloto: o que vencer a §9.3 (desempate: acerto > tokens/s). Definir em
   `IA_LOCAL_MODELO_PADRAO` no README e na §12.

---

## 8. Contrato com o backend (para a implementação futura, F1)

Esta máquina **não** implementa o backend; isto é o que o backend vai consumir:

- **Base URL:** `https://ia.pegaojet.com.br/v1` (dev) e `https://ia.meujet.com.br/v1` (prod).
- **Headers obrigatórios:** `CF-Access-Client-Id`, `CF-Access-Client-Secret` e
  `Authorization: Bearer <IA_LOCAL_TOKEN>`.
- **Endpoint:** `POST /v1/chat/completions` com `model`, `messages` e `tools`:
  - **não usar `tool_choice`** (não suportado pelo Ollama; o prompt instrui quando usar ferramentas);
  - turnos com ferramentas em **`stream: false`** (há relato de `tool_calls` em streaming não
    reconhecidos com Gemma 4 na API compatível);
  - streaming só para o texto final, se o teste §9.3 passar com stream.
- **Timeouts no backend:** conexão 5 s, leitura 120 s. Falha → "assistente indisponível" sem
  travar a tela. Health em `GET https://ia.<dominio>/health` (com headers do Access).
- **Variáveis previstas no backend** (nomes a confirmar na F1):
  - `JETSKI_ASSISTENTE_PROVEDOR=LOCAL_OLLAMA`;
  - `JETSKI_ASSISTENTE_BASE_URL`, `JETSKI_ASSISTENTE_MODELO`, `JETSKI_ASSISTENTE_TOKEN`;
  - `JETSKI_ASSISTENTE_CF_ACCESS_CLIENT_ID`, `JETSKI_ASSISTENTE_CF_ACCESS_CLIENT_SECRET`.

  **Gotcha conhecido:** no compose, variável definida e vazia engole o fallback do Spring
  (já quebrou e-mails em prod). Usar default explícito ou não declarar.
- **Dados:** o prompt pode conter nome, CPF e RG de instrutor digitados pelo dono. A máquina
  não persiste prompts (sem debug, log sem corpo). Essa é a razão LGPD da escolha local.

---

## 9. Testes de aceite (`scripts/smoke-test.sh`)

O script roda **na máquina** (via rede do compose) e **de fora** (via hostname, com os
headers). Tudo precisa passar. Registrar a saída na §12.

### 9.1 Saúde
- `docker compose ps`: os três serviços `healthy`/`running`.
- `nvidia-smi` dentro do container `ollama` mostra a RTX 5080.
- `ollama ps` mostra o modelo padrão carregado com **100% GPU**.

### 9.2 Segurança
- Da LAN (outra máquina ou `curl http://<ip-da-máquina>:11434/api/version`): **falha de conexão**.
- Via hostname sem headers do Access: **403** (ou redirect do Access). Nunca 200.
- Com headers do Access, sem bearer: **401**.
- Com Access + bearer: `POST /api/pull`, `/api/delete` e `/api/create` → **403**.
- Com Access + bearer: `GET /v1/models` → **200**.
- Logs do gateway e do Ollama após uma chamada com CPF fictício (`123.456.789-09`) **não contêm**
  o CPF (`docker compose logs | grep -c 123.456.789-09` = 0).

### 9.3 Chamada de ferramentas (por modelo candidato)
- Mensagem de sistema curta (assistente de cadastro, pt-BR). Ferramentas `buscar_modelos(nome)`
  e `propor_cadastro(itens)`, com schema de modelo (`nome` obrigatório, `precoBaseHora`
  obrigatório, `capacidadePessoas`, `potenciaHp`, `incluiCombustivel`) e jetski (`modelo`,
  `serie` obrigatórios, `ano`).
- Casos (10 execuções cada, `stream: false`):
  1. "Cadastra o modelo Sea-Doo Spark Trixx, 2 pessoas, 90 hp, R$ 280 a hora, combustível
     incluso." → `tool_calls` com JSON válido, `nome` contendo "Spark Trixx", `precoBaseHora=280`,
     `capacidadePessoas=2`.
  2. "Coloca os jets de série YDV12345 e YDV12346, ano 2024, no modelo Spark Trixx." → chama
     `buscar_modelos` antes de propor, ou propõe 2 jetskis com séries corretas.
  3. "Cadastra um modelo novo." (falta preço) → **não** chama `propor_cadastro`; pergunta o
     que falta.
- **Critério por modelo:**
  - caso 1 ≥ 9/10 com argumentos corretos;
  - caso 2 ≥ 8/10;
  - caso 3 ≥ 8/10 perguntando;
  - zero JSON inválido.
- Repetir o caso 1 com `stream: true` e anotar se os `tool_calls` chegam corretamente.

### 9.4 Desempenho
- Geração **≥ 40 tokens/s** no modelo padrão (usar `eval_count / eval_duration` do `/api/chat`
  nativo).
- Primeira resposta com modelo quente em **< 3 s** para uma pergunta curta.
- Duas requisições simultâneas completam sem erro e sem sair de 100% GPU.

### 9.5 Resiliência
- **[MANUAL] reiniciar a máquina.** Sem nenhuma ação humana além do login automático, o
  `GET /health` via hostname volta a 200 em ≤ 5 min.
- `docker compose restart ollama` → health volta sozinho.

---

## 10. Operação (runbook curto no README)

- **Status:** `scripts/status.sh`.
- **Parar o assistente em emergência:**
  - do lado do backend (kill switch, `AGENTE_IDENTIDADE_SPEC.md` A9), ou
  - aqui, `docker compose stop cloudflared` (corta o acesso externo sem derrubar o modelo).
- **Trocar modelo:**
  1. baixar e criar o derivado;
  2. rodar §9.3 e §9.4;
  3. atualizar `IA_LOCAL_MODELO_PADRAO` e avisar para mudar `JETSKI_ASSISTENTE_MODELO` no backend.

  Nova versão principal do agente invalida pré-autorizações (spec de identidade).
- **Atualizar Ollama, driver ou nginx:** só com pin novo + §9 completa + registro na §12.
- **Rotacionar segredos:**
  1. novo `IA_LOCAL_TOKEN` ou novo service token do Access;
  2. atualizar backend dev/prod;
  3. revogar o antigo no Cloudflare.
- **Disco:** `docker system df`; remover modelos não usados só via `docker compose exec ollama
  ollama rm <modelo>` (nunca pela API externa, que está bloqueada).

---

## 11. Fora do escopo desta spec

- Implementação no backend (módulo `assistente`, porta `ProvedorIA`, executor): F1 do projeto,
  em outra branch, por outro agente.
- Fundação de identidade (I1–I5) e step-up delegado (I6–I8): `AGENTE_IDENTIDADE_SPEC.md`.
- Conjunto de avaliação completo com 40–60 pedidos reais (F4): esta spec só faz o smoke de 3 casos.
- Fallback para a API do Claude (possível pela porta de provedor, não ativado no piloto).
- Monitoração no Grafana de prod: alerta a partir do health, feito do lado do backend/observabilidade.

---

## 12. Registro de instalação (preencher ao executar)

| Item | Valor |
|---|---|
| Data da instalação | |
| Hostname / SO / versão | |
| CPU / RAM / disco livre | |
| Driver NVIDIA / CUDA reportado | |
| WSL / Docker Desktop / Docker Compose | |
| Imagem `ollama/ollama` (tag pinada) | |
| Imagem `nginx` / `cloudflared` (tags) | |
| Hostnames do túnel | |
| Modelos candidatos (tag + digest) | |
| Resultado §9.3 por modelo (casos 1/2/3, stream) | |
| Tokens/s e latência (§9.4) | |
| VRAM em uso com 2 paralelos | |
| **Modelo padrão escolhido** | |
| Teste de reboot (§9.5): tempo até health 200 | |
| Desvios desta spec e motivo | |
