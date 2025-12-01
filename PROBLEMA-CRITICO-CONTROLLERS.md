# 🚨 PROBLEMA CRÍTICO: Controllers Não Sendo Mapeados

**Data**: 2025-11-08
**Severidade**: CRÍTICA
**Status**: IDENTIFICADO - Aguardando correção

---

## Resumo Executivo

**TODOS** os controllers da aplicação estão com beans criados, mas **os endpoints NÃO estão sendo registrados** pelo Spring MVC.

### Sintomas

- ✅ Beans dos controllers são criados (`fechamentoController`, `locacaoController`, etc.)
- ✅ Aplicação inicia sem erros
- ✅ Spring Actuator funciona (`/api/actuator/health`, etc.)
- ❌ **NENHUM endpoint de controller funciona** (todos retornam 404)
- ❌ Erro: `"No static resource v1/fechamentos/dia/consolidar."`
- ❌ RequestMappingHandlerMapping registra "110 mappings" mas nenhum dos nossos controllers

### Evidências

```bash
# FechamentoController - 404
POST http://localhost:8090/api/v1/fechamentos/dia/consolidar
→ {"status":404,"detail":"No static resource v1/fechamentos/dia/consolidar."}

# LocacaoController - 404
GET http://localhost:8090/api/v1/locacoes
→ {"status":404,"detail":"No static resource v1/locacoes."}

# Spring Actuator - OK
GET http://localhost:8090/api/actuator/health
→ {"status":"UP"}
```

---

## Logs de Diagnóstico

### 1. Beans Criados Corretamente

```
2025-11-08 08:27:12.878 DEBUG DefaultListableBeanFactory - Creating shared instance of singleton bean 'fechamentoController'
2025-11-08 08:27:12.968 DEBUG DefaultListableBeanFactory - Autowiring by type from bean name 'fechamentoController' via constructor to bean named 'fechamentoService'
2025-11-08 08:27:12.968 DEBUG DefaultListableBeanFactory - Autowiring by type from bean name 'fechamentoController' via constructor to bean named 'usuarioService'
```

✅ O bean `fechamentoController` foi criado com sucesso
✅ Dependências (`fechamentoService`, `usuarioService`) foram injetadas

### 2. Component Scan Funcionando

```
2025-11-08 08:27:07.655 TRACE ClassPathBeanDefinitionScanner - Scanning file [FechamentoController.class]
2025-11-08 08:27:07.655 DEBUG ClassPathBeanDefinitionScanner - Identified candidate component class: file [FechamentoController.class]
```

✅ FechamentoController foi identificado e escaneado

### 3. RequestMappingHandlerMapping

```
2025-11-08 08:27:13.984 DEBUG RequestMappingHandlerMapping - 110 mappings in 'requestMappingHandlerMapping'
```

❌ **110 mappings** registrados, mas **NENHUM** dos nossos controllers (@GetMapping, @PostMapping) aparece!

---

## Causa Raiz Provável

O problema está relacionado a **como o Spring MVC está processando os `@RequestMapping` annotations**.

### Hipóteses (em ordem de probabilidade):

1. **WebMvcConfigurer ou Interceptor bloqueando** o registro de mappings
   - Pode haver um `WebMvcConfigurer` custom que está interferindo
   - Algum interceptor ou filtro pode estar bloqueando o processamento

2. **Problema com `@RestController` annotation**
   - Controllers podem estar usando anotações incorretas
   - Pode haver conflito com alguma configuração custom

3. **RequestMappingHandlerMapping não processando controllers custom**
   - Configuração do Spring MVC pode estar sobrescrevendo o padrão
   - Algum `@EnableWebMvc` ou configuração manual pode estar interferindo

4. **Problema com context-path `/api`**
   - O `server.servlet.context-path=/api` pode estar causando conflito
   - Mensagem de erro mostra "v1/..." sem o prefixo "/api/"

---

## Próximos Passos

### 1. Verificar Configurações WebMvc

```bash
grep -r "@EnableWebMvc\|WebMvcConfigurer" backend/src/main/java/com/jetski --include="*.java"
```

### 2. Verificar se há Interceptor bloqueando

```bash
grep -r "addInterceptors\|InterceptorRegistry" backend/src/main/java/com/jetski --include="*.java"
```

### 3. Testar sem context-path

Temporariamente comentar em `application-local.yml`:
```yaml
server:
  port: 8090
  # servlet:
  #   context-path: /api  # ← Comentar para testar
```

### 4. Adicionar logs TRACE para RequestMappingHandlerMapping

Já adicionado em `application-local.yml`:
```yaml
logging:
  level:
    org.springframework.web.servlet.mvc.method.annotation: TRACE
    org.springframework.web.servlet.handler: TRACE
```

### 5. Criar um TestController mínimo

Criar um controller simples para testar se o problema é global:

```java
@RestController
@RequestMapping("/test")
public class TestController {
    @GetMapping("/hello")
    public String hello() {
        return "Hello World";
    }
}
```

---

## Impacto

### Endpoints Afetados (Todos!)

- ❌ `/api/v1/fechamentos/**` - Fechamento diário/mensal
- ❌ `/api/v1/locacoes/**` - Locações (check-in/check-out)
- ❌ `/api/v1/comissoes/**` - Comissões
- ❌ `/api/v1/combustivel/**` - Abastecimento
- ❌ `/api/v1/modelos/**` - Modelos de jetski
- ❌ `/api/v1/jetskis/**` - Jetskis
- ❌ `/api/v1/reservas/**` - Reservas
- ❌ `/api/v1/clientes/**` - Clientes
- ❌ `/api/v1/vendedores/**` - Vendedores
- ❌ ... (todos os outros controllers)

### Endpoints NÃO Afetados

- ✅ `/api/actuator/**` - Spring Actuator (funcionando)

---

## Configuração Atual

### Context Path
```yaml
server:
  port: 8090
  servlet:
    context-path: /api
```

### RequestMapping Pattern
```java
@RestController
@RequestMapping("/api/v1/fechamentos")  // ← Será /api/api/v1/... ?
public class FechamentoController { ... }
```

**⚠️ POSSÍVEL PROBLEMA**: Se o `context-path` já é `/api`, e o controller também tem `/api` no `@RequestMapping`, o path final seria `/api/api/v1/...`!

---

## Solução Proposta

### Opção 1: Remover `/api` do @RequestMapping

```java
@RestController
@RequestMapping("/v1/fechamentos")  // ← Sem /api (context-path já adiciona)
public class FechamentoController { ... }
```

### Opção 2: Remover context-path e manter `/api` nos controllers

```yaml
server:
  port: 8090
  # servlet:
  #   context-path: /api  # ← Remover
```

### Opção 3: Usar paths relativos

Manter context-path mas usar paths sem prefixo:
```java
@RestController
@RequestMapping("v1/fechamentos")  // ← Sem / no início
```

---

## Teste Rápido

Para testar se o problema é o prefixo `/api` duplicado:

```bash
# Se o context-path é /api e o controller tem @RequestMapping("/api/v1/...")
# Então o endpoint real seria:
curl http://localhost:8090/api/api/v1/fechamentos/dia/consolidar

# Ou talvez só:
curl http://localhost:8090/v1/fechamentos/dia/consolidar
```

---

## Referências

- **Logs completos**: `/tmp/spring-boot-local-trace.log`
- **Config**: `backend/src/main/resources/application-local.yml`
- **Controller exemplo**: `backend/src/main/java/com/jetski/fechamento/api/FechamentoController.java`
