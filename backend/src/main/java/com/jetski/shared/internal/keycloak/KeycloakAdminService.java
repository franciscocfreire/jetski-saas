package com.jetski.shared.internal.keycloak;

import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Serviço para integração com Keycloak Admin API.
 *
 * Responsabilidades:
 * - Criar usuários no Keycloak após ativação de convite
 * - Atribuir roles aos usuários
 * - Gerenciar credenciais (senha)
 *
 * @author Jetski Team
 */
@Slf4j
@Service
public class KeycloakAdminService {

    @Value("${keycloak.admin.server-url}")
    private String serverUrl;

    @Value("${keycloak.admin.realm}")
    private String adminRealm;

    @Value("${keycloak.admin.client-id}")
    private String clientId;

    @Value("${keycloak.admin.username}")
    private String username;

    @Value("${keycloak.admin.password}")
    private String password;

    @Value("${keycloak.admin.target-realm}")
    private String targetRealm;

    /**
     * Cria um usuário no Keycloak com senha temporária (Option 2 flow).
     *
     * Fluxo Option 2:
     * 1. Cria usuário no Keycloak COM senha TEMPORÁRIA
     * 2. Email VERIFICADO (emailVerified = true) - convite já validou
     * 3. COM required action UPDATE_PASSWORD - força troca no primeiro login
     * 4. Keycloak gerencia políticas de senha (comprimento, complexidade, etc)
     * 5. Usuário faz login com senha temporária, Keycloak força troca
     *
     * @param usuarioId UUID do usuário PostgreSQL
     * @param email Email do usuário
     * @param nome Nome completo do usuário
     * @param tenantId ID do tenant
     * @param roles Lista de roles a serem atribuídas
     * @param password Senha TEMPORÁRIA gerada pelo backend (validada contra hash)
     * @return Keycloak user ID (UUID) if successful, null otherwise
     */
    public String createUserWithPassword(UUID usuarioId, String email, String nome,
                                          UUID tenantId, List<String> roles, String password) {
        try (Keycloak keycloak = buildKeycloakClient()) {

            log.info("Criando usuário no Keycloak com senha temporária (Option 2): email={}, realm={}", email, targetRealm);

            RealmResource realmResource = keycloak.realm(targetRealm);
            UsersResource usersResource = realmResource.users();

            // 1. Criar representação do usuário
            UserRepresentation user = new UserRepresentation();
            user.setUsername(email);  // Username = email
            user.setEmail(email);
            user.setFirstName(extractFirstName(nome));
            user.setLastName(extractLastName(nome));
            user.setEnabled(true);
            user.setEmailVerified(true);  // Email já foi verificado no fluxo de convite

            // Adicionar atributos personalizados
            user.singleAttribute("tenant_id", tenantId.toString());
            user.singleAttribute("postgresql_user_id", usuarioId.toString());

            // COM required action UPDATE_PASSWORD - força troca de senha no primeiro login
            user.setRequiredActions(Collections.singletonList("UPDATE_PASSWORD"));

            // 2. Criar credencial de senha TEMPORÁRIA
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(true);  // Senha TEMPORÁRIA - Keycloak força troca no primeiro login
            user.setCredentials(Collections.singletonList(credential));

            // 3. Criar usuário no Keycloak
            Response response = usersResource.create(user);
            int status = response.getStatus();

            if (status != 201) {
                log.error("Falha ao criar usuário no Keycloak: status={}, email={}", status, email);
                response.close();
                return null;
            }

            // 4. Extrair ID do usuário criado do header Location
            String locationHeader = response.getHeaderString("Location");
            response.close();

            if (locationHeader == null || locationHeader.isEmpty()) {
                log.error("Header Location não encontrado na resposta de criação do usuário");
                return null;
            }

            String keycloakUserId = locationHeader.substring(locationHeader.lastIndexOf('/') + 1);
            log.info("Usuário criado no Keycloak com sucesso: keycloakId={}, postgresId={}, email={}",
                    keycloakUserId, usuarioId, email);

            // 5. Obter recurso do usuário criado
            UserResource userResource = usersResource.get(keycloakUserId);

            // 6. Atribuir roles ao usuário
            assignRolesToUser(realmResource, userResource, roles);

            log.info("Usuário criado e configurado com sucesso no Keycloak: keycloakId={}, postgresId={}, email={}, roles={}",
                    keycloakUserId, usuarioId, email, roles);

            return keycloakUserId;

        } catch (Exception e) {
            log.error("Erro ao criar usuário no Keycloak com senha: email={}, error={}", email, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Atribui roles realm-level ao usuário.
     *
     * @param realmResource Recurso do realm
     * @param userResource Recurso do usuário
     * @param roleNames Lista de nomes de roles
     */
    private void assignRolesToUser(RealmResource realmResource, UserResource userResource,
                                    List<String> roleNames) {
        try {
            // Buscar representações das roles
            List<RoleRepresentation> rolesToAdd = roleNames.stream()
                .map(roleName -> realmResource.roles().get(roleName).toRepresentation())
                .toList();

            // Atribuir roles ao usuário
            userResource.roles().realmLevel().add(rolesToAdd);

            log.info("Roles atribuídas ao usuário: userId={}, roles={}",
                     userResource.toRepresentation().getId(), roleNames);

        } catch (Exception e) {
            log.error("Erro ao atribuir roles ao usuário: roles={}, error={}", roleNames, e.getMessage(), e);
            throw new RuntimeException("Falha ao atribuir roles no Keycloak", e);
        }
    }

    /**
     * Extrai primeiro nome do nome completo.
     */
    private String extractFirstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "";
        }
        String[] parts = fullName.trim().split("\\s+");
        return parts[0];
    }

    /**
     * Extrai sobrenome do nome completo.
     */
    private String extractLastName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "";
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length > 1) {
            return String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length));
        }
        return "";
    }

    /**
     * Constrói cliente Keycloak Admin com credenciais configuradas.
     */
    private Keycloak buildKeycloakClient() {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(adminRealm)
                .clientId(clientId)
                .username(username)
                .password(password)
                .build();
    }
}
