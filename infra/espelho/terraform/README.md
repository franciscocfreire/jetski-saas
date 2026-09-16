# Espelho de carga — Terraform

Cria o espelho descrito em [`../README.md`](../README.md): VM Oracle igual à de
produção, rede própria, túnel Cloudflare novo, DNS e ajustes da zona
`jetsave.com.br`. A VM se provisiona sozinha — o cloud-init clona o repositório e
chama [`../provisionar-vm.sh`](../provisionar-vm.sh), que instala o Docker nas
versões de produção, gera o `.env`, roda o preflight, o `deploy.sh` e o stack de
observabilidade.

**O Terraform só cuida da infraestrutura do espelho.** A aplicação continua no
Compose e no `deploy.sh`, e produção continua fora de qualquer state.

## O que ele cria

| Onde | O quê |
|---|---|
| OCI | compartimento `meujet-espelho` (a fronteira do state), VCN, internet gateway, rota, security list (**só SSH do seu IP** — a web entra pelo túnel, como em produção), sub-rede e a VM `VM.Standard.A1.Flex` 2 OCPU / 12 GB / 200 GB com Ubuntu 24.04 |
| Cloudflare | túnel `meujet-espelho`, rotas de `www`/`app`/`cliente`/`admin`/`sso` para `http://nginx:80`, os 5 CNAMEs e, na zona `jetsave.com.br`, Browser Integrity Check e Bot Fight Mode desligados (senão o k6 leva erro 1010) |

Não cria registro no apex: a zona tem MX nulo e SPF no apex (domínio sem e-mail), e
o apex só redirecionaria para `www`.

## Pré-requisitos (uma vez)

1. **Terraform ≥ 1.9** — ou sem instalar, pela imagem oficial:

   ```bash
   alias terraform='docker run --rm -it -u "$(id -u):$(id -g)" -e HOME="$HOME" \
     -v "$HOME/.oci:$HOME/.oci:ro" -v "$HOME/.ssh:$HOME/.ssh:ro" \
     -v "$PWD":/w -w /w -e CLOUDFLARE_API_TOKEN hashicorp/terraform:1.16.3'
   ```

   `~/.oci` e `~/.ssh` montados no **mesmo caminho** do host: o `~/.oci/config` guarda o
   caminho absoluto da chave, que não existiria dentro do container em outro lugar.
2. **Chave de API da OCI:** `oci setup config` (ou *Perfil → Tokens and keys → API keys*
   no console), gerando o `~/.oci/config`.
3. **Token de API da Cloudflare** (*My Profile → API Tokens → Create Token → Custom*),
   com o mínimo:
   - Account → **Cloudflare Tunnel: Edit**
   - Zone → **Zone: Read**, **DNS: Edit**, **Zone Settings: Edit**, **Bot Management: Edit**
   - Zone Resources → **somente `jetsave.com.br`**

   A permissão de túnel vale para a conta inteira — a Cloudflare não a restringe por
   zona — e por isso alcança também o túnel de produção. É isso que a pré-condição
   `recusa_gerenciar_o_tunel_de_producao` cobre. Se algum nome de permissão estiver
   diferente no painel, o `plan` falha dizendo qual falta.
4. **UUID do túnel de produção:** Zero Trust → Networks → Tunnels.
5. **Seu IP:** `curl -s https://ifconfig.me`.

## Uso

```bash
cd infra/espelho/terraform
cp terraform.tfvars.example terraform.tfvars   # preencha
export CLOUDFLARE_API_TOKEN=...                # nunca em arquivo

terraform init
terraform plan        # leia: só recursos "meujet-espelho" e da zona jetsave
terraform apply
```

O `apply` termina em poucos minutos, mas **a VM leva dezenas de minutos para ficar
pronta**: o `deploy.sh` compila o backend e os três frontends em ARM. Acompanhe:

```bash
terraform output -raw acompanhar_provisionamento | sh    # log ao vivo
terraform output -raw status_provisionamento | sh        # provisionado ou FALHOU (com a etapa)
```

Depois: passos 5 e 6 do [`../README.md`](../README.md) (primeiro operador e tenant de
carga) — o `terraform output` mostra URLs, o túnel para o Mailpit e as variáveis do k6.

> **`terraform destroy` pode parar no túnel** com *"This tunnel has active
> connections"* (código 1022): a VM some antes de a Cloudflare dar as conexões do
> `cloudflared` por encerradas. Não é erro de configuração — espere alguns minutos e
> rode `terraform destroy` de novo; ele só tenta o que sobrou (visto em 16/set/2026:
> 15 recursos no primeiro, túnel no segundo).

Ciclo de vida (ligar, desligar, recriar num commit, destruir): tabela em
[`../README.md` → Ciclo de vida](../README.md#ciclo-de-vida).

## Outra conta ou outra região

Nada no código amarra o espelho à tenancy ou à região de produção. Para subir em
outra conta OCI: um perfil próprio no `~/.oci/config` (`oci setup config`,
respondendo **Y** para *add a profile*) e, no `terraform.tfvars`, `oci_perfil`,
`tenancy_ocid` e `oci_regiao`. A parte Cloudflare não muda.

O que muda na **leitura dos resultados** quando a região não é `sa-saopaulo-1`:

- **Latência de rede.** O k6 rodando no Brasil atravessa Cloudflare → região
  remota → volta; nos EUA isso soma ~100–150 ms por ida e volta que produção não
  tem. A CPU é a mesma (Ampere A1), então a **capacidade do servidor** segue
  comparável — mas leia tempos pelo Grafana do espelho (medidos dentro da VM), não
  pelo P95 que o k6 calcula do lado do cliente.
- **Mais de um domínio de disponibilidade.** Se o `apply` falhar com *Out of host
  capacity*, tente `-var ad_indice=1` (ou 2).

Em conta **Always Free**, o espelho ocupa a cota inteira: 2 OCPU / 12 GB de A1 e
os 200 GB de block storage gratuitos (o disco de boot conta). Instância Always
Free ociosa pode ser recolhida pela Oracle — destrua ao fim de cada sessão em vez
de deixar parada.

## Testes

```bash
terraform init -backend=false && terraform test
```

Rodam **sem credenciais e sem criar nada** — os três providers são mockados. Cobrem a
paridade com produção (shape, disco, versões do Docker/Compose no cloud-init), o
roteamento (5 CNAMEs para o túnel do espelho, ingress com 404 no fim, nenhuma porta web
aberta) e as travas: domínio de produção ou do dev, SSH aberto para o mundo, túnel sem
"espelho" no nome, e-mail que injetaria YAML no cloud-init e túnel de produção no state.
A trava do túnel foi conferida por mutação: com a pré-condição neutralizada, o teste
reprova.

## Segurança do state

- **O state guarda o segredo e o token do túnel.** Fica local e o `.gitignore` o exclui —
  **o repositório é público**. Nunca force o add.
- **O Terraform grava o state com permissão 644** (legível por qualquer usuário da
  máquina). Depois de cada `apply`/`destroy`: `chmod 600 terraform.tfstate*` — ou rode
  com `umask 077`.
- Os segredos da aplicação (banco, Keycloak, NextAuth…) **não** passam pelo Terraform:
  são gerados dentro da VM pelo `gerar-env.sh`.
- O token do túnel vai para a VM pelo `user_data`, que fica legível no metadata da
  instância para quem tem acesso à tenancy. Aceitável: o token só dá acesso ao túnel do
  espelho.
- **Nunca `terraform import` de recurso de produção** neste diretório.
