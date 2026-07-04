---
name: nova-migration
description: Criar uma alteração de schema no jetski do jeito certo — migration Flyway + bloco idempotente no reset-ambiente-dev.sh + aplicação no banco dev via psql. Use sempre que for criar/alterar tabela, coluna, índice ou RLS.
---

# Nova migration (Flyway + reset script)

Toda alteração de schema neste projeto exige **três passos**. Pular qualquer um causa "relation does not exist" em dev (o backend dev NÃO roda Flyway no boot).

## 1. Migration Flyway
- Descobrir o próximo número: `ls backend/src/main/resources/db/migration/ | sort | tail -3`
- Criar `V0XX__descricao_curta.sql` seguindo o padrão dos arquivos existentes.
- Tabela nova operacional: incluir `tenant_id UUID NOT NULL`, habilitar RLS com policy `tenant_id = current_setting('app.tenant_id')::uuid`, e índice composto `(tenant_id, fk)`.
- `GRANT` apropriado para `jetski_app` (o app não é superuser em prod).

## 2. Bloco idempotente no reset-ambiente-dev.sh
- Adicionar o SQL equivalente em forma idempotente (`CREATE TABLE IF NOT EXISTS`, `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`, `DO $$ ... $$` para policies) na seção de schema do `reset-ambiente-dev.sh`, junto dos blocos das migrations anteriores.
- Esta é regra do CLAUDE.md do projeto — o reset script é o mecanismo real de schema em dev.

## 3. Aplicar no banco dev AGORA
Sem reset completo:
```bash
docker compose exec -T postgres psql -U jetski -d jetski_dev <<'SQL'
-- colar aqui o bloco idempotente do passo 2
SQL
```
Verificar: `docker compose exec -T postgres psql -U jetski -d jetski_dev -c "\d nome_da_tabela"`

## Checklist final
- [ ] `V0XX__*.sql` criado e numerado sem conflito
- [ ] Bloco idempotente no `reset-ambiente-dev.sh`
- [ ] Aplicado no `jetski_dev` via psql (ou reset completo rodado)
- [ ] RLS + índice `(tenant_id, ...)` se tabela operacional
- [ ] Lembrar: merge na main com CI verde → CD aplica a migration em PROD automaticamente
