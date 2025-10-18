# Keycloak com PostgreSQL Local

## 📋 Pré-requisitos

- PostgreSQL 16 instalado e rodando
- Keycloak 26.4.1 em `/home/franciscocfreire/apps/keycloak-26.4.1`

---

## 🚀 Setup Rápido

### Passo 1: Criar Database no PostgreSQL

Usando o PostgreSQL do Docker Compose (porta 5432):

```bash
cd /home/franciscocfreire/repos/jetski/backend

# Criar database e usuário
PGPASSWORD=dev123 psql -h localhost -p 5432 -U postgres <<'EOF'
CREATE USER keycloak WITH PASSWORD 'keycloak123';
CREATE DATABASE keycloak OWNER keycloak;
GRANT ALL PRIVILEGES ON DATABASE keycloak TO keycloak;
\c keycloak
GRANT ALL ON SCHEMA public TO keycloak;
EOF
```

**OU** se estiver usando PostgreSQL local (porta 5433):

```bash
# Ajuste o usuário/senha conforme sua instalação
psql -h localhost -p 5433 -U postgres <<'EOF'
CREATE USER keycloak WITH PASSWORD 'keycloak123';
CREATE DATABASE keycloak OWNER keycloak;
GRANT ALL PRIVILEGES ON DATABASE keycloak TO keycloak;
\c keycloak
GRANT ALL ON SCHEMA public TO keycloak;
EOF
```

---

### Passo 2: Build Keycloak com PostgreSQL Driver

```bash
cd /home/franciscocfreire/apps/keycloak-26.4.1

# Build com suporte a PostgreSQL
./bin/kc.sh build --db=postgres
```

---

### Passo 3: Iniciar Keycloak em Modo Desenvolvimento com PostgreSQL

```bash
cd /home/franciscocfreire/apps/keycloak-26.4.1

# Definir variáveis de ambiente
export KC_DB=postgres
export KC_DB_URL='jdbc:postgresql://localhost:5432/keycloak'
export KC_DB_USERNAME=keycloak
export KC_DB_PASSWORD=keycloak123
export KEYCLOAK_ADMIN=admin
export KEYCLOAK_ADMIN_PASSWORD=admin

# Iniciar em dev mode
./bin/kc.sh start-dev \
  --http-port=8081 \
  > /tmp/keycloak-postgres.log 2>&1 &

# Aguardar inicialização
echo "Aguardando Keycloak iniciar..."
sleep 10
tail -f /tmp/keycloak-postgres.log
```

**Pressione Ctrl+C quando ver**: `Listening on: http://0.0.0.0:8081`

---

### Passo 4: Configurar Realm (Executar Setup Script)

```bash
cd /home/franciscocfreire/repos/jetski/infra/keycloak-setup
bash setup-keycloak-local.sh
```

---

## 🏭 Modo Produção (Recomendado)

Para produção, use `start` em vez de `start-dev`:

```bash
cd /home/franciscocfreire/apps/keycloak-26.4.1

export KC_DB=postgres
export KC_DB_URL='jdbc:postgresql://localhost:5432/keycloak'
export KC_DB_USERNAME=keycloak
export KC_DB_PASSWORD=keycloak123
export KEYCLOAK_ADMIN=admin
export KEYCLOAK_ADMIN_PASSWORD=admin
export KC_HOSTNAME=localhost
export KC_HTTP_ENABLED=true

./bin/kc.sh start \
  --http-enabled=true \
  --http-port=8081 \
  --hostname-strict=false \
  --proxy-headers=xforwarded \
  > /tmp/keycloak-postgres.log 2>&1 &
```

---

## 🔍 Verificar Status

```bash
# Ver logs
tail -f /tmp/keycloak-postgres.log

# Verificar se está rodando
curl http://localhost:8081/health

# Verificar database
PGPASSWORD=keycloak123 psql -h localhost -p 5432 -U keycloak -d keycloak -c "\dt"
```

---

## 🛑 Parar Keycloak

```bash
# Encontrar PID
ps aux | grep keycloak | grep -v grep

# Matar processo (substitua PID pelo número)
kill <PID>

# OU forçar
pkill -f keycloak
```

---

## 📊 Vantagens do PostgreSQL vs H2

| Aspecto | H2 (Dev Mode) | PostgreSQL |
|---------|---------------|------------|
| Persistência | ❌ Volátil | ✅ Persistente |
| User Attributes | ❌ Bugs conhecidos | ✅ Funciona |
| Performance | ⚠️ Limitada | ✅ Excelente |
| Produção | ❌ Não recomendado | ✅ Recomendado |
| Multi-instância | ❌ Não | ✅ Sim |

---

## 🔧 Troubleshooting

### Erro: "Connection refused"
```bash
# Verificar se PostgreSQL está rodando
pg_isready -h localhost -p 5432

# Se não estiver, iniciar via Docker Compose
cd /home/franciscocfreire/repos/jetski/backend
docker-compose up -d postgres
```

### Erro: "Database does not exist"
```bash
# Recriar database
PGPASSWORD=dev123 psql -h localhost -p 5432 -U postgres <<'EOF'
DROP DATABASE IF EXISTS keycloak;
CREATE DATABASE keycloak OWNER keycloak;
EOF
```

### Erro: "Role 'keycloak' does not exist"
```bash
# Criar usuário
PGPASSWORD=dev123 psql -h localhost -p 5432 -U postgres <<'EOF'
CREATE USER keycloak WITH PASSWORD 'keycloak123';
GRANT ALL PRIVILEGES ON DATABASE keycloak TO keycloak;
EOF
```

---

## 🎯 Script de Inicialização Completo

Salve como `/tmp/start-keycloak-postgres.sh`:

```bash
#!/bin/bash

cd /home/franciscocfreire/apps/keycloak-26.4.1

# Parar instância anterior
pkill -f keycloak 2>/dev/null

echo "Iniciando Keycloak com PostgreSQL..."

KC_DB=postgres \
KC_DB_URL='jdbc:postgresql://localhost:5432/keycloak' \
KC_DB_USERNAME=keycloak \
KC_DB_PASSWORD=keycloak123 \
KEYCLOAK_ADMIN=admin \
KEYCLOAK_ADMIN_PASSWORD=admin \
./bin/kc.sh start-dev \
  --http-port=8081 \
  > /tmp/keycloak-postgres.log 2>&1 &

echo "Aguardando inicialização..."
for i in {1..30}; do
  if curl -s http://localhost:8081/health >/dev/null 2>&1; then
    echo "✓ Keycloak iniciado com sucesso!"
    echo "  URL: http://localhost:8081"
    echo "  Admin: admin / admin"
    echo "  Logs: tail -f /tmp/keycloak-postgres.log"
    exit 0
  fi
  sleep 2
done

echo "✗ Timeout aguardando Keycloak"
tail -20 /tmp/keycloak-postgres.log
exit 1
```

**Uso**:
```bash
chmod +x /tmp/start-keycloak-postgres.sh
/tmp/start-keycloak-postgres.sh
```

---

## 📝 Notas Importantes

1. **Password do PostgreSQL**: Ajuste `dev123` conforme sua configuração
2. **Porta do PostgreSQL**:
   - Docker Compose: `5432`
   - Local: geralmente `5433` ou `5432`
3. **User Attributes**: Com PostgreSQL, os attributes devem funcionar corretamente (sem os bugs do H2)
4. **Backup**: Faça backup do realm periodicamente:
   ```bash
   cd /home/franciscocfreire/apps/keycloak-26.4.1
   ./bin/kc.sh export --dir /tmp/keycloak-export --realm jetski-saas
   ```

---

## ✅ Verificar Migração Bem-Sucedida

```bash
# 1. Keycloak está usando PostgreSQL?
tail -100 /tmp/keycloak-postgres.log | grep -i "postgres\|database"

# 2. Tabelas criadas no PostgreSQL?
PGPASSWORD=keycloak123 psql -h localhost -p 5432 -U keycloak -d keycloak \
  -c "SELECT count(*) as total_tables FROM information_schema.tables WHERE table_schema = 'public';"

# Deve mostrar ~100+ tabelas do Keycloak

# 3. Realm existe?
curl -s http://localhost:8081/realms/jetski-saas | grep -q "jetski-saas" && echo "✓ Realm OK" || echo "✗ Realm não encontrado"
```
