package br.com.jetski.keycloak.emailcode;

import org.keycloak.credential.CredentialModel;
import org.keycloak.util.JsonSerialization;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Modelo do "dispositivo confiável" persistido como credential custom
 * ({@code mj-trusted-device}) no Keycloak. Sem CredentialProvider: os
 * authenticators leem/escrevem o CredentialModel direto (secretData guarda só o
 * hash do token do cookie; credentialData guarda expiração/uso/UA).
 */
public final class TrustedDevice {

    public static final String TYPE = "mj-trusted-device";
    public static final String COOKIE = "MJ_TRUSTED_DEVICE";
    /** auth note: login veio por device confiável (check pulou o 2FA). */
    public static final String NOTE_SKIP = "MJ_TD_SKIP";
    /** tipos que contam como "tem 2FA" (mesma lista do backend). */
    public static final java.util.Set<String> TIPOS_2FA =
            java.util.Set.of("otp", "webauthn", "webauthn-passwordless");
    /** 30 dias. */
    public static final long TTL_SECONDS = 30L * 24 * 60 * 60;
    /** token do cookie: 256 bits em hex. */
    public static final int TOKEN_BYTES = 32;

    private TrustedDevice() {
    }

    /** Monta o CredentialModel a persistir. */
    public static CredentialModel novo(String tokenHash, String label, long nowEpochSec, String ua) {
        CredentialModel cm = new CredentialModel();
        cm.setType(TYPE);
        cm.setUserLabel(label);
        cm.setCreatedDate(nowEpochSec * 1000);
        cm.setSecretData(writeJson(Map.of("tokenHash", tokenHash)));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("expiresAt", nowEpochSec + TTL_SECONDS);
        data.put("lastUsedAt", nowEpochSec);
        data.put("ua", ua == null ? "" : ua);
        cm.setCredentialData(writeJson(data));
        return cm;
    }

    public static String tokenHash(CredentialModel cm) {
        return asMap(cm.getSecretData()).getOrDefault("tokenHash", "").toString();
    }

    public static long expiresAt(CredentialModel cm) {
        Object v = asMap(cm.getCredentialData()).get("expiresAt");
        return v instanceof Number ? ((Number) v).longValue() : 0L;
    }

    /** Atualiza lastUsedAt in-place (preserva os demais campos). */
    public static void touch(CredentialModel cm, long nowEpochSec) {
        Map<String, Object> data = asMap(cm.getCredentialData());
        data.put("lastUsedAt", nowEpochSec);
        cm.setCredentialData(writeJson(data));
    }

    private static Map<String, Object> asMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = JsonSerialization.readValue(json, Map.class);
            return m == null ? new LinkedHashMap<>() : m;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static String writeJson(Map<String, ?> m) {
        try {
            return JsonSerialization.writeValueAsString(m);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao serializar trusted-device", e);
        }
    }
}
