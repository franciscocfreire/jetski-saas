package jetski.platform_test

import future.keywords.if
import future.keywords.in

import data.jetski.authorization
import data.jetski.platform

# =============================================================================
# Matriz papel × ação das rotas /v1/platform/**.
#
# O que estes testes travam:
#  - operador de plataforma não-admin NÃO herda destrutivo nem financeiro;
#  - "somente leitura" é de verdade (o método HTTP separa, porque a ação não separa);
#  - papel de EMPRESA continua sem acesso nenhum a platform:*;
#  - god mode em ações de tenant é exclusivo do PLATFORM_ADMIN.
# =============================================================================

operador(papel, acao, metodo) := {
	"action": acao,
	"user": {
		"id": "op",
		"roles": [papel],
		"role": papel,
		"unrestricted_access": true,
	},
	"resource": {},
	"context": {"method": metodo},
}

empresa(acao, metodo) := {
	"action": acao,
	"user": {
		"id": "u",
		"roles": ["ADMIN_TENANT"],
		"role": "ADMIN_TENANT",
		"tenant_id": "11111111-1111-1111-1111-111111111111",
	},
	"resource": {"tenant_id": "11111111-1111-1111-1111-111111111111"},
	"context": {"method": metodo},
}

# ---------------------------------------------------------------- ADMIN: tudo

test_admin_pode_destrutivo if {
	platform.allow with input as operador("PLATFORM_ADMIN", "platform:tenants:reset", "POST")
}

test_admin_pode_excluir if {
	platform.allow with input as operador("PLATFORM_ADMIN", "platform:tenants:excluir", "POST")
}

test_admin_pode_operadores if {
	platform.allow with input as operador("PLATFORM_ADMIN", "platform:operadores", "PUT")
}

test_admin_pode_financeiro if {
	platform.allow with input as operador("PLATFORM_ADMIN", "platform:faturas:confirmar", "POST")
}

# -------------------------------------------------------------------- SUPORTE

test_suporte_pode_aprovar_empresa if {
	platform.allow with input as operador("PLATFORM_SUPORTE", "platform:tenants:approve", "POST")
}

test_suporte_pode_suspender if {
	platform.allow with input as operador("PLATFORM_SUPORTE", "platform:tenants:suspend", "POST")
}

test_suporte_pode_ler if {
	platform.allow with input as operador("PLATFORM_SUPORTE", "platform:tenants", "GET")
}

test_suporte_nao_reseta if {
	not platform.allow with input as operador("PLATFORM_SUPORTE", "platform:tenants:reset", "POST")
}

test_suporte_nao_exclui if {
	not platform.allow with input as operador("PLATFORM_SUPORTE", "platform:tenants:excluir", "POST")
}

test_suporte_nao_lanca_creditos if {
	not platform.allow with input as operador("PLATFORM_SUPORTE", "platform:creditos", "POST")
}

test_suporte_nao_confirma_fatura if {
	not platform.allow with input as operador("PLATFORM_SUPORTE", "platform:faturas:confirmar", "POST")
}

test_suporte_nao_gere_operadores if {
	not platform.allow with input as operador("PLATFORM_SUPORTE", "platform:operadores", "PUT")
}

test_suporte_nao_recifra_segredos if {
	not platform.allow with input as operador("PLATFORM_SUPORTE", "platform:secrets:reencrypt", "POST")
}

# Afrouxar o 2FA da porta da plataforma é decisão de admin — inclusive a leitura,
# para não divergir do menu do console, que só mostra /configuracoes ao admin.
test_admin_muda_2fa_do_console if {
	platform.allow with input as operador("PLATFORM_ADMIN", "platform:seguranca:2fa-console", "PUT")
}

test_suporte_nao_muda_2fa_do_console if {
	not platform.allow with input as operador("PLATFORM_SUPORTE", "platform:seguranca:2fa-console", "PUT")
}

test_financeiro_nao_muda_2fa_do_console if {
	not platform.allow with input as operador("PLATFORM_FINANCEIRO", "platform:seguranca:2fa-console", "PUT")
}

test_leitura_nao_ve_2fa_do_console if {
	not platform.allow with input as operador("PLATFORM_LEITURA", "platform:seguranca:2fa-console", "GET")
}

# ----------------------------------------------------------------- FINANCEIRO

test_financeiro_lanca_creditos if {
	platform.allow with input as operador("PLATFORM_FINANCEIRO", "platform:creditos", "POST")
}

test_financeiro_aprova_compra if {
	platform.allow with input as operador("PLATFORM_FINANCEIRO", "platform:creditos:compras:aprovar", "POST")
}

test_financeiro_confirma_fatura if {
	platform.allow with input as operador("PLATFORM_FINANCEIRO", "platform:faturas:confirmar", "POST")
}

test_financeiro_troca_plano if {
	platform.allow with input as operador("PLATFORM_FINANCEIRO", "platform:tenants:plano", "POST")
}

test_financeiro_nao_aprova_empresa if {
	not platform.allow with input as operador("PLATFORM_FINANCEIRO", "platform:tenants:approve", "POST")
}

test_financeiro_nao_reseta if {
	not platform.allow with input as operador("PLATFORM_FINANCEIRO", "platform:tenants:reset", "POST")
}

test_financeiro_nao_gere_operadores if {
	not platform.allow with input as operador("PLATFORM_FINANCEIRO", "platform:operadores", "PUT")
}

# -------------------------------------------------------------------- LEITURA

test_leitura_le_empresas if {
	platform.allow with input as operador("PLATFORM_LEITURA", "platform:tenants", "GET")
}

test_leitura_le_faturas if {
	platform.allow with input as operador("PLATFORM_LEITURA", "platform:faturas:pendentes", "GET")
}

# O ponto central da F2: a MESMA ação, método diferente, decisão diferente.
# GET /v1/platform/creditos (saldos) e POST /v1/platform/creditos/{id} (lançar)
# colapsam em "platform:creditos" — sem o método não dava para separar.
test_leitura_le_saldos_mas_nao_lanca if {
	platform.allow with input as operador("PLATFORM_LEITURA", "platform:creditos", "GET")
	not platform.allow with input as operador("PLATFORM_LEITURA", "platform:creditos", "POST")
}

test_leitura_nao_altera_preco if {
	platform.allow with input as operador("PLATFORM_LEITURA", "platform:creditos:config", "GET")
	not platform.allow with input as operador("PLATFORM_LEITURA", "platform:creditos:config", "PUT")
}

test_leitura_nao_baixa_export if {
	not platform.allow with input as operador("PLATFORM_LEITURA", "platform:tenants:exports:download", "GET")
}

test_leitura_nao_ve_comprovante if {
	not platform.allow with input as operador("PLATFORM_LEITURA", "platform:creditos:compras:comprovante", "GET")
}

test_leitura_nao_gere_operadores if {
	not platform.allow with input as operador("PLATFORM_LEITURA", "platform:operadores", "GET")
}

test_leitura_nao_aprova_nada if {
	not platform.allow with input as operador("PLATFORM_LEITURA", "platform:tenants:approve", "POST")
}

# Suporte e financeiro também não baixam o arquivo completo da empresa
test_suporte_nao_baixa_export if {
	not platform.allow with input as operador("PLATFORM_SUPORTE", "platform:tenants:exports:download", "GET")
}

# ...mas o financeiro precisa do comprovante para conferir o PIX
test_financeiro_ve_comprovante if {
	platform.allow with input as operador("PLATFORM_FINANCEIRO", "platform:creditos:compras:comprovante", "GET")
}

# ------------------------------------------------- Papel de empresa: nada aqui

test_admin_tenant_sem_acesso_platform if {
	not platform.allow with input as empresa("platform:tenants", "GET")
	not platform.allow with input as empresa("platform:tenants:approve", "POST")
	not platform.allow with input as empresa("platform:tenants:reset", "POST")
}

test_sem_papel_nenhum_negado if {
	not platform.allow with input as {
		"action": "platform:tenants",
		"user": {"id": "x", "roles": []},
		"resource": {},
		"context": {"method": "GET"},
	}
}

# ------------------------------- Sessão de suporte (F3): substitui o god mode

# Operador COM sessão, mas a empresa vem da sessão — não de header escolhido.
com_sessao(papel, acao, metodo, leitura) := {
	"action": acao,
	"user": {"id": "op", "roles": [papel], "role": papel, "unrestricted_access": true},
	"resource": {},
	"context": {
		"method": metodo,
		"support_session": {"id": "s1", "somente_leitura": leitura},
	},
}

# O ponto central da F3: SEM sessão, nem o admin opera a empresa.
test_sem_sessao_nem_admin_opera_tenant if {
	not authorization.allow with input as operador("PLATFORM_ADMIN", "modelo:list", "GET")
	not authorization.allow with input as operador("PLATFORM_ADMIN", "locacao:checkin", "POST")
}

test_com_sessao_escrita_opera if {
	authorization.allow with input as com_sessao("PLATFORM_ADMIN", "locacao:checkin", "POST", false)
}

test_com_sessao_escrita_le_tambem if {
	authorization.allow with input as com_sessao("PLATFORM_SUPORTE", "modelo:list", "GET", false)
}

# Somente-leitura é negação de verdade, não aviso de UI.
test_sessao_leitura_le_mas_nao_escreve if {
	authorization.allow with input as com_sessao("PLATFORM_ADMIN", "modelo:list", "GET", true)
	not authorization.allow with input as com_sessao("PLATFORM_ADMIN", "locacao:checkin", "POST", true)
}

test_sessao_leitura_nega_delete if {
	not authorization.allow with input as com_sessao("PLATFORM_ADMIN", "jetski:delete", "DELETE", true)
}

# Sessão não transforma papel de EMPRESA em operador de plataforma.
test_sessao_sem_papel_de_plataforma_nao_vale if {
	not platform.allow_suporte with input as {
		"action": "locacao:checkin",
		"user": {"id": "u", "roles": ["ADMIN_TENANT"], "role": "ADMIN_TENANT"},
		"resource": {},
		"context": {"method": "POST", "support_session": {"id": "s1", "somente_leitura": false}},
	}
}

# Sessão de suporte NÃO libera ações de plataforma (são governadas pela matriz).
test_sessao_nao_libera_acao_de_plataforma if {
	not authorization.allow with input as com_sessao("PLATFORM_LEITURA", "platform:tenants:reset", "POST", false)
}
