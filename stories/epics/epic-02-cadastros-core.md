---
epic_id: EPIC-02
title: Cadastros Core
status: TODO
priority: HIGH
start_date: 2025-01-29
target_date: 2025-02-11
owner: Team Backend + Frontend
dependencies: [EPIC-01]
---

# EPIC-02: Cadastros Core

## Objetivo

Implementar CRUD completo das entidades fundamentais do sistema (Modelo, Jetski, Vendedor, Cliente) com validações de negócio, isolamento multi-tenant e interface web funcional.

## Escopo

### Incluído
- [ ] Entidades JPA: `Modelo`, `Jetski`, `Vendedor`, `Cliente` com auditoria
- [ ] Repositories JPA com queries otimizadas por `tenant_id`
- [ ] Services com regras de negócio e validações
- [ ] REST Controllers com OpenAPI documentation
- [ ] DTOs request/response com MapStruct
- [ ] Validações: Bean Validation + regras customizadas
- [ ] Testes unitários e de integração (Testcontainers)
- [ ] Páginas CRUD no frontend (Next.js + shadcn/ui)
- [ ] Validação de unicidade de série/placa de jetski por tenant
- [ ] Controle de status do jetski (disponível/locado/manutenção/indisponível)

### Excluído (Out of Scope)
- Funcionalidades de reserva/locação (será EPIC-03)
- Políticas complexas de comissão/combustível (será refinado em EPIC-03)
- Importação em massa via CSV

## Histórias Relacionadas

### Backend
- `backend/stories/story-006-modelo-entity-crud.md` (8 pts)
- `backend/stories/story-007-jetski-entity-crud.md` (5 pts)
- `backend/stories/story-008-vendedor-entity-crud.md` (5 pts)
- `backend/stories/story-009-cliente-entity-crud.md` (3 pts)

### Frontend
- `frontend/stories/story-004-modelos-page.md` (5 pts)
- `frontend/stories/story-005-jetskis-page.md` (5 pts)
- `frontend/stories/story-006-vendedores-page.md` (3 pts)
- `frontend/stories/story-007-clientes-page.md` (3 pts)

**Total estimado:** 37 story points (~2 sprints)

## Critérios de Aceite

- [ ] CRUD completo para Modelo, Jetski, Vendedor e Cliente via API REST
- [ ] Todas as entidades incluem `tenant_id` e são filtradas por RLS
- [ ] Validações impedem cadastro de dados inválidos (CNPJ, email, preços negativos)
- [ ] Jetski com série duplicada é rejeitado dentro do mesmo tenant (mas permitido entre tenants)
- [ ] Interface web permite criar, editar, listar e excluir todas as entidades
- [ ] Documentação OpenAPI acessível em `/swagger-ui.html`
- [ ] Testes comprovam isolamento multi-tenant em todos os CRUDs
- [ ] Cobertura de testes > 80%

## Riscos

**Risco Médio:**
- **Complexidade das validações de CNPJ/CPF**: Validações específicas do Brasil.
  - **Mitigação**: Usar biblioteca validada (Hibernate Validator + custom validators)

**Risco Baixo:**
- **Performance de listagens com muitos registros**: Paginação pode ser necessária.
  - **Mitigação**: Implementar paginação desde o início (Spring Data Pageable)

## Dependências

- EPIC-01 concluído (RLS e autenticação funcionando)

## Métricas de Sucesso

- Tempo de resposta para listagem de 1000 registros: < 200ms (P95)
- Todas as validações de negócio cobertas por testes
- Interface web responsiva e funcional em mobile

## Notas

### Validações Importantes

**Modelo:**
- Preço base/hora > 0
- Tolerância em minutos >= 0
- Caução >= 0

**Jetski:**
- Série única por tenant
- Horímetro >= 0
- Status deve ser um dos valores válidos (enum)

**Vendedor:**
- CNPJ ou CPF válido (validação com dígito verificador)
- Percentual de comissão entre 0 e 100

**Cliente:**
- CPF ou CNPJ válido
- Email válido (se fornecido)

### Modelo de Dados

```sql
CREATE TABLE modelo (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenant(id),
  nome TEXT NOT NULL,
  fabricante TEXT,
  potencia_hp INT,
  preco_base_hora NUMERIC(10,2) NOT NULL CHECK (preco_base_hora > 0),
  tolerancia_min INT DEFAULT 5 CHECK (tolerancia_min >= 0),
  caucao NUMERIC(10,2) DEFAULT 0 CHECK (caucao >= 0),
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_modelo_tenant ON modelo(tenant_id);
```

## Changelog

- 2025-01-15: Épico criado
