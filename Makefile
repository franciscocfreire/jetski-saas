.PHONY: help up down restart logs ps clean setup-keycloak

help: ## Mostrar esta ajuda
	@echo "Jetski SaaS - Comandos Disponíveis:"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

up: ## Subir todos os serviços (PostgreSQL + Redis + Keycloak)
	@echo "🚀 Subindo serviços..."
	docker-compose up -d
	@echo "⏳ Aguardando Keycloak ficar pronto..."
	@bash infra/wait-for-keycloak.sh
	@echo "✅ Serviços rodando!"
	@echo ""
	@echo "📍 URLs:"
	@echo "   PostgreSQL: localhost:5432 (user: jetski, pass: dev123, db: jetski_dev)"
	@echo "   Redis:      localhost:6379"
	@echo "   Keycloak:   http://localhost:8080 (admin/admin)"
	@echo ""
	@echo "🔐 Usuários de teste:"
	@echo "   admin@acme.com / admin123 (ADMIN_TENANT, GERENTE)"
	@echo "   operador@acme.com / operador123 (OPERADOR)"

down: ## Parar todos os serviços
	@echo "🛑 Parando serviços..."
	docker-compose down

restart: ## Reiniciar todos os serviços
	@echo "🔄 Reiniciando serviços..."
	docker-compose restart

logs: ## Ver logs de todos os serviços
	docker-compose logs -f

logs-api: ## Ver logs apenas da API (quando estiver rodando)
	docker-compose logs -f api

logs-keycloak: ## Ver logs do Keycloak
	docker-compose logs -f keycloak

logs-postgres: ## Ver logs do PostgreSQL
	docker-compose logs -f postgres

ps: ## Listar status dos serviços
	docker-compose ps

clean: ## Parar e remover todos os containers e volumes (CUIDADO!)
	@echo "⚠️  Isso vai remover TODOS os dados. Confirma? [y/N] " && read ans && [ $${ans:-N} = y ]
	docker-compose down -v
	@echo "🗑️  Volumes removidos"

setup-keycloak: ## Configurar Keycloak com realm e usuários
	@echo "🔧 Configurando Keycloak..."
	@bash infra/setup-keycloak.sh
	@echo "✅ Keycloak configurado!"

test-db: ## Testar conexão com PostgreSQL
	@echo "🔍 Testando conexão com PostgreSQL..."
	docker-compose exec postgres psql -U jetski -d jetski_dev -c "SELECT version();"

test-redis: ## Testar conexão com Redis
	@echo "🔍 Testando conexão com Redis..."
	docker-compose exec redis redis-cli ping

test-keycloak: ## Testar Keycloak health
	@echo "🔍 Testando Keycloak..."
	@curl -f http://localhost:8080/health/ready && echo "✅ Keycloak OK" || echo "❌ Keycloak não está pronto"

test-all: test-db test-redis test-keycloak ## Testar todos os serviços

shell-postgres: ## Abrir shell do PostgreSQL
	docker-compose exec postgres psql -U jetski -d jetski_dev

shell-redis: ## Abrir shell do Redis
	docker-compose exec redis redis-cli

backend-build: ## Build do backend (Maven)
	cd backend && mvn clean package -DskipTests

backend-test: ## Rodar testes do backend
	cd backend && mvn test

backend-run: ## Rodar backend localmente (requer serviços up)
	cd backend && mvn spring-boot:run
