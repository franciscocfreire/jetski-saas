# Imagem do Keycloak COM o tema de login "meujet" (Keycloakify).
# Multi-stage: builda o JAR do tema (Node + Maven — o keycloakify empacota o
# theme via mvn) e copia para /opt/keycloak/providers/ da imagem oficial.
# Multi-arch: funciona no dev x86 e no servidor Oracle ARM (build nativo).

# ---- stage 1: build do tema ----
FROM node:20-bookworm AS theme-build
RUN apt-get update \
 && apt-get install -y --no-install-recommends maven \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /theme
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# tsc + vite build + keycloakify build (o keycloakify empacota o dist/ do vite)
RUN npm run build-keycloak-theme

# ---- stage 2: Keycloak + tema como provider ----
FROM quay.io/keycloak/keycloak:26.4
# Nome do JAR fixado em vite.config.ts (keycloakVersionTargets) — mudou lá, mude aqui.
COPY --from=theme-build /theme/dist_keycloak/meujet-theme.jar /opt/keycloak/providers/
# Sem `kc.sh build` aqui: os composes usam `start-dev`, que re-augmenta os
# providers no boot. Se um dia migrarem para `start --optimized`, adicionar:
#   RUN /opt/keycloak/bin/kc.sh build
