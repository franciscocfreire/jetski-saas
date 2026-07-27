package jetski.platform

# =============================================================================
# Papéis de plataforma (F2) — separam ALCANCE de PODER.
#
# Até aqui `unrestricted_access = true` significava as duas coisas ao mesmo
# tempo: "acesso qualquer empresa" E "posso tudo". Com mais de um tipo de
# operador isso não se sustenta.
#
#   ALCANCE  → continua sendo `unrestricted_access` (setado para QUALQUER papel
#              de plataforma; é o que faz o TenantFilter/RLS deixarem o operador
#              enxergar empresas sem ser membro).
#   PODER    → passa a ser o papel em `usuario_global_roles.roles[]`, decidido
#              aqui.
#
# IMPORTANTE (gotcha do OPA): toda regra referenciada precisa de
# `default <regra> := false`. Regra undefined colapsa o `result` inteiro e a
# decisão vira `{}` — deny silencioso em TUDO.
#
# O método HTTP entra na decisão porque a ação sozinha não separa leitura de
# escrita: GET /v1/platform/creditos e POST /v1/platform/creditos/{id} produzem
# a mesma ação `platform:creditos` (o identificador é descartado por design).
# =============================================================================

import future.keywords.contains
import future.keywords.if
import future.keywords.in

default allow := false

# Aceita `role` (singular) E `roles[]` — mesma convenção do rbac.rego, que tem
# allow_rbac para as duas formas. O ABACAuthorizationInterceptor envia ambos.
papeis contains p if {
	p := object.get(input.user, "role", "")
	p != ""
}

papeis contains p if {
	some p in object.get(input.user, "roles", [])
}

metodo_http := upper(object.get(input.context, "method", ""))

default leitura := false

leitura if {
	metodo_http == "GET"
}

# -----------------------------------------------------------------------------
# Papéis
# -----------------------------------------------------------------------------

default tem_papel_plataforma := false

tem_papel_plataforma if {
	some p in papeis
	startswith(p, "PLATFORM_")
}

default eh_admin := false

eh_admin if {
	some p in papeis
	p == "PLATFORM_ADMIN"
}

default eh_suporte := false

eh_suporte if {
	some p in papeis
	p == "PLATFORM_SUPORTE"
}

default eh_financeiro := false

eh_financeiro if {
	some p in papeis
	p == "PLATFORM_FINANCEIRO"
}

# -----------------------------------------------------------------------------
# Leituras sensíveis: fora do "qualquer GET".
#
# O export é o arquivo COMPLETO da empresa (dados + arquivos); o comprovante é
# documento financeiro de terceiro. Nenhum dos dois é leitura de rotina.
# -----------------------------------------------------------------------------

leitura_sensivel contains "platform:tenants:exports:download"

leitura_sensivel contains "platform:creditos:compras:comprovante"

default acao_e_leitura_sensivel := false

acao_e_leitura_sensivel if {
	input.action in leitura_sensivel
}

# -----------------------------------------------------------------------------
# Conjuntos de ação por área
# -----------------------------------------------------------------------------

acoes_financeiras := {
	"platform:creditos", # lançar créditos (POST)
	"platform:creditos:config", # preço unitário (PUT)
	"platform:creditos:compras:aprovar",
	"platform:creditos:compras:rejeitar",
	"platform:creditos:compras:comprovante",
	"platform:faturas:gerar",
	"platform:faturas:confirmar",
	"platform:faturas:cancelar",
	"platform:tenants:plano", # trocar plano contratado
	"platform:planos:modulos", # oferta de módulos por plano
}

acoes_suporte := {
	"platform:tenants:approve",
	"platform:tenants:suspend",
	"platform:tenants:reactivate",
	"platform:tenants:habilitar-emissora",
	"platform:tenants:desabilitar-emissora",
	"platform:capitanias", # catálogo EAMA (POST)
	"platform:capitanias:atualizar",
	"platform:tenants:suporte", # abrir sessão de suporte numa empresa
	"platform:suporte", # revogar sessão (a leitura da trilha cai no ramo de GET)
}

# Destrutivas e de infraestrutura: SÓ admin. Nunca entram em suporte/financeiro.
acoes_exclusivas_admin := {
	"platform:tenants:reset",
	"platform:tenants:excluir",
	"platform:tenants:cancelar-exclusao",
	"platform:tenants:export",
	"platform:tenants:exports:download",
	"platform:secrets:reencrypt",
	"platform:operadores", # gestão de quem opera a plataforma
	"platform:operadores:papeis", # catálogo — toda a superfície /operadores é admin
	"platform:documentos:imagem-config",
	# Afrouxar o 2FA da porta da plataforma não é tarefa de suporte nem de
	# financeiro. A LEITURA cai no ramo de GET — ver como está configurado
	# ajuda qualquer operador a entender o próprio login.
	"platform:seguranca:2fa-console",
}

# -----------------------------------------------------------------------------
# Decisão
# -----------------------------------------------------------------------------

# ADMIN: tudo.
allow if {
	eh_admin
}

# Leitura de rotina: qualquer papel de plataforma, método GET, ação não sensível
# e fora da lista exclusiva de admin.
allow if {
	tem_papel_plataforma
	leitura
	not acao_e_leitura_sensivel
	not input.action in acoes_exclusivas_admin
}

# SUPORTE: ciclo de vida da empresa e catálogo EAMA. Sem destrutivo, sem financeiro.
allow if {
	eh_suporte
	input.action in acoes_suporte
}

# FINANCEIRO: créditos, faturas, plano e oferta. Sem destrutivo, sem operadores.
allow if {
	eh_financeiro
	input.action in acoes_financeiras
}

# -----------------------------------------------------------------------------
# Sessão de suporte (F3): operar uma EMPRESA a partir do console.
#
# Substitui o god mode implícito (era: qualquer ação de tenant liberada para
# quem tivesse unrestricted_access). Agora exige sessão declarada, e a sessão
# somente-leitura nega escrita de verdade — não é aviso de UI.
# -----------------------------------------------------------------------------

sessao := object.get(input.context, "support_session", null)

default em_sessao_de_suporte := false

em_sessao_de_suporte if {
	sessao != null
}

default sessao_somente_leitura := false

sessao_somente_leitura if {
	sessao != null
	object.get(sessao, "somente_leitura", false) == true
}

# Ação de TENANT dentro de sessão de suporte.
default allow_suporte := false

allow_suporte if {
	em_sessao_de_suporte
	tem_papel_plataforma
	not sessao_somente_leitura
}

allow_suporte if {
	em_sessao_de_suporte
	tem_papel_plataforma
	sessao_somente_leitura
	leitura # GET e só
}

# -----------------------------------------------------------------------------
# Permissões efetivas (menu do console) — mesma matriz, exposta para a UI.
# -----------------------------------------------------------------------------

permissoes contains "platform:*" if {
	eh_admin
}

permissoes contains acao if {
	eh_suporte
	some acao in acoes_suporte
}

permissoes contains acao if {
	eh_financeiro
	some acao in acoes_financeiras
}

permissoes contains "platform:read" if {
	tem_papel_plataforma
}
