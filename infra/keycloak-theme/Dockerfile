# Imagem do Keycloak COM o tema de login "meujet" (Keycloakify) e o SPI de
# login por código de e-mail (infra/keycloak-extensions/email-code).
# Contexto de build = ./infra (ver docker-compose.yml) para enxergar os dois
# diretórios — por isso os COPY são prefixados com keycloak-theme/ etc.
# Multi-arch: funciona no dev x86 e no servidor Oracle ARM (build nativo).

# ---- stage 0: build do SPI de código por e-mail ----
FROM maven:3.9-eclipse-temurin-21 AS spi-build
WORKDIR /spi
COPY keycloak-extensions/email-code/pom.xml .
RUN mvn -q -B dependency:go-offline
COPY keycloak-extensions/email-code/src ./src
# package roda os testes unitários do CodeChallenge — build falha se quebrarem
RUN mvn -q -B package

# ---- stage 1: build do tema ----
FROM node:20-bookworm AS theme-build
RUN apt-get update \
 && apt-get install -y --no-install-recommends maven \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /theme
COPY keycloak-theme/package.json keycloak-theme/package-lock.json ./
RUN npm ci
COPY keycloak-theme/ .
# tsc + vite build + keycloakify build (o keycloakify empacota o dist/ do vite)
RUN npm run build-keycloak-theme

# ---- stage 2: Keycloak + tema + SPI como providers ----
FROM quay.io/keycloak/keycloak:26.4
# Nome do JAR fixado em vite.config.ts (keycloakVersionTargets) — mudou lá, mude aqui.
COPY --from=theme-build /theme/dist_keycloak/meujet-theme.jar /opt/keycloak/providers/
# SPI do login por código (versão do Keycloak pinada no pom.xml do módulo —
# mudou a tag da base image aqui, mude keycloak.version lá).
COPY --from=spi-build /spi/target/meujet-email-code-spi.jar /opt/keycloak/providers/
# Sem `kc.sh build` aqui: os composes usam `start-dev`, que re-augmenta os
# providers no boot. Se um dia migrarem para `start --optimized`, adicionar:
#   RUN /opt/keycloak/bin/kc.sh build
