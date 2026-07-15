import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { keycloakify } from "keycloakify/vite-plugin";

// Tema de login "meujet": compila para UM jar (meujet-theme.jar) cobrindo o
// range que inclui o Keycloak 26 (target "all-other-versions"). O Dockerfile
// referencia esse nome fixo — mudou aqui, mude lá.
export default defineConfig({
    plugins: [
        react(),
        keycloakify({
            themeName: "meujet",
            accountThemeImplementation: "none",
            keycloakVersionTargets: {
                "22-to-25": false,
                "all-other-versions": "meujet-theme.jar"
            }
        })
    ]
});
