package com.jetski.modulith;

import com.jetski.JetskiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Testes de verificação da estrutura modular usando Spring Modulith.
 *
 * <p>Este teste garante que:
 * <ul>
 *   <li>A estrutura de módulos está correta</li>
 *   <li>Não há dependências cíclicas entre módulos</li>
 *   <li>Pacotes "internal" não são acessados por outros módulos</li>
 *   <li>Dependências declaradas em @ApplicationModule são respeitadas</li>
 * </ul>
 *
 * @author Jetski Team
 * @since 0.1.0
 */
class ModuleStructureTest {

    /**
     * Verifica a estrutura completa dos módulos.
     *
     * <p>Spring Modulith escaneia a aplicação e valida:
     * <ul>
     *   <li>Módulos estão em subpacotes diretos de JetskiApplication</li>
     *   <li>Não há dependências cíclicas</li>
     *   <li>Pacotes "internal" não são acessados de fora</li>
     *   <li>Allowlist de dependências (@ApplicationModule.allowedDependencies) é respeitada</li>
     * </ul>
     *
     * <p>Se este teste falhar, significa que há violação de limites modulares.
     */
    @Test
    void shouldVerifyModularStructure() {
        ApplicationModules modules = ApplicationModules.of(JetskiApplication.class);

        // Valida estrutura modular e dependências
        modules.verify();

        // Imprime estrutura de módulos no console (útil para debug)
        System.out.println("\n=== Estrutura de Módulos Detectada ===");
        modules.forEach(module -> {
            System.out.println("\nMódulo: " + module.getName());
            System.out.println("  Display Name: " + module.getDisplayName());
            System.out.println("  Base Package: " + module.getBasePackage());
        });
    }

    /**
     * Verifica que não há dependências cíclicas entre módulos.
     *
     * <p>Dependências cíclicas (A -> B -> A) são anti-patterns em arquitetura
     * modular e devem ser evitadas. Se este teste falhar, refatore os módulos
     * para quebrar o ciclo (ex: extrair código comum para um módulo compartilhado).
     */
    @Test
    void shouldNotHaveCyclicDependencies() {
        ApplicationModules modules = ApplicationModules.of(JetskiApplication.class);

        // verify() já valida ciclos, mas este teste torna explícito
        modules.verify();
    }

    /**
     * Gera documentação da estrutura modular.
     *
     * <p>Cria arquivos de documentação em target/modulith-docs/:
     * <ul>
     *   <li>components.puml - Diagrama PlantUML dos módulos</li>
     *   <li>module-structure.adoc - Documentação AsciiDoc</li>
     * </ul>
     *
     * <p>Execute este teste para atualizar a documentação após mudanças estruturais.
     */
    @Test
    void shouldGenerateModuleDocumentation() {
        ApplicationModules modules = ApplicationModules.of(JetskiApplication.class);

        new Documenter(modules)
            .writeDocumentation()
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml();

        System.out.println("\n✅ Documentação gerada em: target/spring-modulith-docs/");
    }

    /**
     * Verifica que os módulos foram detectados corretamente.
     *
     * <p>Garante que Spring Modulith detectou os módulos esperados:
     * "shared" e "usuarios".
     */
    @Test
    void shouldDetectExpectedModules() {
        ApplicationModules modules = ApplicationModules.of(JetskiApplication.class);

        // Verifica que os módulos esperados existem
        boolean hasShared = modules.getModuleByName("shared").isPresent();
        boolean hasUsuarios = modules.getModuleByName("usuarios").isPresent();

        System.out.println("\n=== Módulos Detectados ===");
        System.out.println("shared: " + (hasShared ? "✅" : "❌"));
        System.out.println("usuarios: " + (hasUsuarios ? "✅" : "❌"));

        if (!hasShared) {
            throw new AssertionError("Módulo 'shared' não foi detectado");
        }
        if (!hasUsuarios) {
            throw new AssertionError("Módulo 'usuarios' não foi detectado");
        }
    }

    /**
     * Verifica informações sobre o módulo "shared".
     */
    @Test
    void shouldDescribeSharedModule() {
        ApplicationModules modules = ApplicationModules.of(JetskiApplication.class);

        modules.getModuleByName("shared").ifPresent(sharedModule -> {
            System.out.println("\n=== Módulo 'shared' ===");
            System.out.println("Display Name: " + sharedModule.getDisplayName());
            System.out.println("Base Package: " + sharedModule.getBasePackage());
        });
    }

    /**
     * Verifica informações sobre o módulo "usuarios".
     */
    @Test
    void shouldDescribeUsuariosModule() {
        ApplicationModules modules = ApplicationModules.of(JetskiApplication.class);

        modules.getModuleByName("usuarios").ifPresent(usuariosModule -> {
            System.out.println("\n=== Módulo 'usuarios' ===");
            System.out.println("Display Name: " + usuariosModule.getDisplayName());
            System.out.println("Base Package: " + usuariosModule.getBasePackage());
        });
    }
}
