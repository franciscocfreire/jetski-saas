# Tema de login "meujet" (Keycloakify)

Tema de login do Keycloak com a identidade Meu Jet (BRAND.md na raiz do repo),
escrito em React/TypeScript e compilado para um JAR FreeMarker padrão pelo
[Keycloakify](https://keycloakify.dev) v11.

- **Iterar no visual**: `npm run storybook` (mocks de todas as telas ejetadas).
- **Testar num Keycloak real**: `npm run start-keycloak` (sobe um container com
  hot-reload do tema; escolha a versão 26).
- **Build do JAR**: `npm run build-keycloak-theme` (exige Maven >= 3.1.1 + JDK
  no PATH) → `dist_keycloak/meujet-theme.jar`.
- **Como o JAR chega ao Keycloak**: o `Dockerfile` deste diretório é a imagem
  do serviço `keycloak` dos composes (multi-stage: builda o JAR e copia para
  `/opt/keycloak/providers/`). Rebuild em dev: `./rebuild.sh keycloak`.
- **Ativação**: `"loginTheme": "meujet"` no `infra/keycloak-realm.json` (realm
  novo) e convergido pelo `infra/prod/configure-keycloak-client.sh` (realm
  existente, todo deploy).

Regra de marca: a tela de login é SEMPRE Meu Jet — sem white-label por tenant.
Cadastro de cliente é no portal (`registrationAllowed` fica `false`).
