# Sprint 3 - Fechamento Diário Operacional + Fotos de Painel

**Data:** 26 de Outubro de 2025
**Versão:** 0.8.0-SNAPSHOT
**Objetivo:** Implementar fechamento diário operacional por jetski com fotos obrigatórias de painel

---

## 1. CONTEXTO E MOTIVAÇÃO

### Problema Atual
O sistema atual permite check-in/check-out de locações individuais, mas **não há controle diário do jetski**:
- ❌ Não sabemos quando o jetski iniciou operações no dia
- ❌ Não sabemos quando o jetski encerrou operações
- ❌ Não há evidência fotográfica do horímetro início/fim do dia
- ❌ Não há validação se as locações batem com o total operado

### Fluxo Operacional Real

**Um jetski em um dia típico:**
```
08:00 - Jetski chega na marina
        📸 FOTO OBRIGATÓRIA do painel (horímetro: 1.234,5h)
        → Sistema: Abre FechamentoDiarioJetski

10:00-11:30 - Locação 1 (Cliente A, 1,5h)
12:00-13:00 - Locação 2 (Cliente B, 1,0h)
14:00-16:00 - Locação 3 (Cliente C, 2,0h)
16:30-17:00 - Locação 4 (Cliente D, 0,5h)

18:00 - Jetski retorna definitivamente
        📸 FOTO OBRIGATÓRIA do painel (horímetro: 1.239,5h)
        → Sistema: Fecha FechamentoDiarioJetski
        → Validação: 1.239,5 - 1.234,5 = 5,0h operadas ✅
```

### Regras de Negócio

**RN-FD01: Abertura Obrigatória**
- Todo jetski que vai operar no dia DEVE ter um FechamentoDiarioJetski aberto
- Abertura exige foto do painel (tipo: PAINEL_INICIO)
- Horímetro inicial registrado da leitura da foto

**RN-FD02: Fechamento Obrigatório**
- Todo jetski ao final do dia DEVE ser fechado
- Fechamento exige foto do painel (tipo: PAINEL_FIM)
- Horímetro final >= horímetro inicial
- Horas operadas = horímetro_fim - horímetro_inicio

**RN-FD03: Validação de Locações**
- Todas as locações do dia ficam vinculadas ao FechamentoDiarioJetski
- Soma das horas locadas ≈ horas operadas (tolerância de ±10%)
- Se divergência > 10%, gera alerta para revisão

**RN-FD04: Bloqueio Retroativo**
- Após fechamento, não é permitido criar/editar locações daquele dia
- Reabertura exige aprovação de gerente + justificativa

**RN-FD05: Fotos Durante Locação (OPCIONAIS)**
- Fotos durante check-in/check-out individual são opcionais
- Úteis para registro de danos, incidentes, etc.
- Não impedem check-in/check-out se ausentes

---

## 2. MODELO DE DADOS

### 2.1 Nova Entidade: FechamentoDiarioJetski

```java
package com.jetski.operacoes.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Fechamento Diário Operacional por Jetski
 *
 * Registra o ciclo operacional de um jetski em um dia específico.
 * Exige foto obrigatória do painel no início e fim do dia.
 *
 * Business Rules:
 * - RN-FD01: Abertura obrigatória com foto do painel
 * - RN-FD02: Fechamento obrigatório com foto do painel
 * - RN-FD03: Validação horas locadas vs. horas operadas
 * - RN-FD04: Bloqueio retroativo após fechamento
 */
@Entity
@Table(
    name = "fechamento_diario_jetski",
    indexes = {
        @Index(name = "idx_fdj_tenant_data", columnList = "tenant_id, data"),
        @Index(name = "idx_fdj_jetski_data", columnList = "jetski_id, data"),
        @Index(name = "idx_fdj_status", columnList = "status")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_fdj_jetski_data",
            columnNames = {"tenant_id", "jetski_id", "data"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FechamentoDiarioJetski {

    @Id
    @GeneratedValue(generator = "UUID")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "jetski_id", nullable = false)
    private UUID jetskiId;

    /**
     * Data do fechamento (único por jetski/dia)
     */
    @Column(name = "data", nullable = false)
    private LocalDate data;

    // ===================================================================
    // HORÍMETRO
    // ===================================================================

    /**
     * Leitura do horímetro no início do dia (da foto PAINEL_INICIO)
     */
    @Column(name = "horimetro_inicio", nullable = false, precision = 10, scale = 2)
    private BigDecimal horimetroInicio;

    /**
     * Leitura do horímetro no fim do dia (da foto PAINEL_FIM)
     * Null enquanto status = ABERTO
     */
    @Column(name = "horimetro_fim", precision = 10, scale = 2)
    private BigDecimal horimetroFim;

    /**
     * Total de horas operadas no dia
     * Calculado: horimetroFim - horimetroInicio
     */
    @Column(name = "horas_operadas", precision = 10, scale = 2)
    private BigDecimal horasOperadas;

    // ===================================================================
    // FOTOS OBRIGATÓRIAS
    // ===================================================================

    /**
     * FK para Foto tipo PAINEL_INICIO (obrigatória)
     */
    @Column(name = "foto_inicio_id", nullable = false)
    private UUID fotoInicioId;

    /**
     * FK para Foto tipo PAINEL_FIM (obrigatória ao fechar)
     */
    @Column(name = "foto_fim_id")
    private UUID fotoFimId;

    // ===================================================================
    // STATUS
    // ===================================================================

    /**
     * Status do fechamento diário
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FechamentoDiarioStatus status;

    // ===================================================================
    // CONSOLIDAÇÃO DE LOCAÇÕES
    // ===================================================================

    /**
     * Quantidade de locações realizadas no dia
     */
    @Column(name = "quantidade_locacoes")
    @Builder.Default
    private Integer quantidadeLocacoes = 0;

    /**
     * Total de minutos locados (soma de todas locações)
     */
    @Column(name = "minutos_locados")
    @Builder.Default
    private Integer minutosLocados = 0;

    /**
     * Total de horas locadas (calculado: minutosLocados / 60)
     */
    @Column(name = "horas_locadas", precision = 10, scale = 2)
    private BigDecimal horasLocadas;

    /**
     * Divergência entre horas operadas e horas locadas
     * Percentual: ((horasOperadas - horasLocadas) / horasOperadas) * 100
     */
    @Column(name = "divergencia_percentual", precision = 5, scale = 2)
    private BigDecimal divergenciaPercentual;

    // ===================================================================
    // TIMESTAMPS
    // ===================================================================

    @Column(name = "data_hora_abertura", nullable = false)
    private LocalDateTime dataHoraAbertura;

    @Column(name = "data_hora_fechamento")
    private LocalDateTime dataHoraFechamento;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ===================================================================
    // AUDITORIA
    // ===================================================================

    @Column(name = "aberto_por_usuario_id")
    private UUID abertoPorUsuarioId;

    @Column(name = "fechado_por_usuario_id")
    private UUID fechadoPorUsuarioId;

    /**
     * Observações do operador (opcional)
     */
    @Column(name = "observacoes", columnDefinition = "TEXT")
    private String observacoes;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ===================================================================
    // BUSINESS LOGIC HELPERS
    // ===================================================================

    public boolean isAberto() {
        return status == FechamentoDiarioStatus.ABERTO;
    }

    public boolean isFechado() {
        return status == FechamentoDiarioStatus.FECHADO;
    }

    /**
     * Calcula horas operadas (horimetroFim - horimetroInicio)
     */
    public void calcularHorasOperadas() {
        if (horimetroFim != null && horimetroInicio != null) {
            this.horasOperadas = horimetroFim.subtract(horimetroInicio);
        }
    }

    /**
     * Calcula horas locadas (minutosLocados / 60)
     */
    public void calcularHorasLocadas() {
        if (minutosLocados != null) {
            this.horasLocadas = BigDecimal.valueOf(minutosLocados)
                .divide(BigDecimal.valueOf(60), 2, BigDecimal.ROUND_HALF_UP);
        }
    }

    /**
     * Calcula divergência percentual entre horas operadas e locadas
     */
    public void calcularDivergencia() {
        if (horasOperadas != null && horasLocadas != null &&
            horasOperadas.compareTo(BigDecimal.ZERO) > 0) {

            BigDecimal diff = horasOperadas.subtract(horasLocadas);
            this.divergenciaPercentual = diff
                .divide(horasOperadas, 4, BigDecimal.ROUND_HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, BigDecimal.ROUND_HALF_UP);
        }
    }

    /**
     * Verifica se divergência está dentro da tolerância (±10%)
     */
    public boolean isDivergenciaAceitavel() {
        if (divergenciaPercentual == null) return true;
        return divergenciaPercentual.abs().compareTo(BigDecimal.valueOf(10)) <= 0;
    }
}
```

### 2.2 Enum: FechamentoDiarioStatus

```java
package com.jetski.operacoes.domain;

/**
 * Status do Fechamento Diário de Jetski
 */
public enum FechamentoDiarioStatus {
    /**
     * Dia operacional aberto, jetski em operação
     * - Foto inicial tirada
     * - Pode receber locações
     */
    ABERTO,

    /**
     * Dia operacional fechado
     * - Foto final tirada
     * - Não aceita mais locações
     * - Lock retroativo ativado
     */
    FECHADO
}
```

### 2.3 Refatoração: Foto (adicionar campos)

```java
// ADICIONAR à entidade Foto existente:

@Entity
@Table(name = "foto")
public class Foto {
    // ... campos existentes ...

    /**
     * FK para FechamentoDiarioJetski (se foto for PAINEL_INICIO ou PAINEL_FIM)
     */
    @Column(name = "fechamento_diario_jetski_id")
    private UUID fechamentoDiarioJetskiId;

    /**
     * Leitura do horímetro capturada na foto
     * Obrigatório para tipo PAINEL_INICIO e PAINEL_FIM
     */
    @Column(name = "horimetro_leitura", precision = 10, scale = 2)
    private BigDecimal horimetroLeitura;

    /**
     * Data/hora da captura da foto (do dispositivo)
     */
    @Column(name = "data_hora_captura")
    private LocalDateTime dataHoraCaptura;

    // ... demais campos ...
}
```

### 2.4 Refatoração: FotoTipo (adicionar novos tipos)

```java
public enum FotoTipo {
    // Existentes (vinculados a Locação - OPCIONAIS)
    CHECK_IN,      // Opcional
    CHECK_OUT,     // Opcional
    INCIDENTE,     // Opcional
    MANUTENCAO,    // Opcional

    // NOVOS (vinculados a FechamentoDiarioJetski - OBRIGATÓRIOS)
    PAINEL_INICIO, // Obrigatória - Abertura do dia
    PAINEL_FIM     // Obrigatória - Fechamento do dia
}
```

### 2.5 Refatoração: Locacao (adicionar FK)

```java
@Entity
@Table(name = "locacao")
public class Locacao {
    // ... campos existentes ...

    /**
     * FK para o FechamentoDiarioJetski do dia
     * Populado automaticamente no check-in
     */
    @Column(name = "fechamento_diario_jetski_id")
    private UUID fechamentoDiarioJetskiId;

    // ... demais campos ...
}
```

---

## 3. DATABASE MIGRATIONS

### V1012__create_fechamento_diario_jetski_table.sql

```sql
-- ============================================================================
-- Fechamento Diário Operacional por Jetski
-- ============================================================================

CREATE TABLE fechamento_diario_jetski (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    jetski_id                   UUID NOT NULL,
    data                        DATE NOT NULL,

    -- Horímetro
    horimetro_inicio            NUMERIC(10, 2) NOT NULL,
    horimetro_fim               NUMERIC(10, 2),
    horas_operadas              NUMERIC(10, 2),

    -- Fotos obrigatórias
    foto_inicio_id              UUID NOT NULL,
    foto_fim_id                 UUID,

    -- Status
    status                      VARCHAR(20) NOT NULL DEFAULT 'ABERTO',

    -- Consolidação de locações
    quantidade_locacoes         INTEGER DEFAULT 0,
    minutos_locados             INTEGER DEFAULT 0,
    horas_locadas               NUMERIC(10, 2),
    divergencia_percentual      NUMERIC(5, 2),

    -- Timestamps
    data_hora_abertura          TIMESTAMP NOT NULL,
    data_hora_fechamento        TIMESTAMP,
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP,

    -- Auditoria
    aberto_por_usuario_id       UUID,
    fechado_por_usuario_id      UUID,
    observacoes                 TEXT,

    -- Constraints
    CONSTRAINT ck_fdj_horimetro_valido CHECK (
        horimetro_fim IS NULL OR horimetro_fim >= horimetro_inicio
    ),
    CONSTRAINT ck_fdj_status CHECK (
        status IN ('ABERTO', 'FECHADO')
    )
);

-- Indexes
CREATE INDEX idx_fdj_tenant_data ON fechamento_diario_jetski(tenant_id, data);
CREATE INDEX idx_fdj_jetski_data ON fechamento_diario_jetski(jetski_id, data);
CREATE INDEX idx_fdj_status ON fechamento_diario_jetski(status);

-- Unique constraint: 1 fechamento por jetski/dia
CREATE UNIQUE INDEX uk_fdj_jetski_data
    ON fechamento_diario_jetski(tenant_id, jetski_id, data);

-- Foreign keys
ALTER TABLE fechamento_diario_jetski
    ADD CONSTRAINT fk_fdj_jetski FOREIGN KEY (jetski_id)
        REFERENCES jetski(id);

-- RLS (Row Level Security)
ALTER TABLE fechamento_diario_jetski ENABLE ROW LEVEL SECURITY;

CREATE POLICY fechamento_diario_jetski_tenant_isolation
    ON fechamento_diario_jetski
    USING (tenant_id = current_setting('app.tenant_id')::uuid);

COMMENT ON TABLE fechamento_diario_jetski IS
    'Fechamento diário operacional por jetski com fotos obrigatórias de painel';
```

### V1013__alter_foto_add_fechamento_fields.sql

```sql
-- ============================================================================
-- Adicionar campos para suporte a fotos de fechamento diário
-- ============================================================================

ALTER TABLE foto
    ADD COLUMN fechamento_diario_jetski_id UUID,
    ADD COLUMN horimetro_leitura NUMERIC(10, 2),
    ADD COLUMN data_hora_captura TIMESTAMP;

-- Index para busca por fechamento
CREATE INDEX idx_foto_fechamento
    ON foto(fechamento_diario_jetski_id)
    WHERE fechamento_diario_jetski_id IS NOT NULL;

-- Foreign key
ALTER TABLE foto
    ADD CONSTRAINT fk_foto_fechamento_diario
        FOREIGN KEY (fechamento_diario_jetski_id)
        REFERENCES fechamento_diario_jetski(id);

COMMENT ON COLUMN foto.horimetro_leitura IS
    'Leitura do horímetro capturada na foto (obrigatório para PAINEL_INICIO/FIM)';
COMMENT ON COLUMN foto.data_hora_captura IS
    'Data/hora da captura da foto pelo dispositivo';
```

### V1014__alter_locacao_add_fechamento_diario_fk.sql

```sql
-- ============================================================================
-- Vincular locações ao fechamento diário do jetski
-- ============================================================================

ALTER TABLE locacao
    ADD COLUMN fechamento_diario_jetski_id UUID;

-- Index
CREATE INDEX idx_locacao_fechamento
    ON locacao(fechamento_diario_jetski_id)
    WHERE fechamento_diario_jetski_id IS NOT NULL;

-- Foreign key
ALTER TABLE locacao
    ADD CONSTRAINT fk_locacao_fechamento_diario
        FOREIGN KEY (fechamento_diario_jetski_id)
        REFERENCES fechamento_diario_jetski(id);

COMMENT ON COLUMN locacao.fechamento_diario_jetski_id IS
    'FK para o fechamento diário do jetski (vínculo automático no check-in)';
```

---

## 4. API ENDPOINTS

### 4.1 FechamentoDiarioJetskiController

```java
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/fechamentos-diarios/jetskis")
public class FechamentoDiarioJetskiController {

    /**
     * Abre o dia operacional de um jetski
     * POST /api/v1/tenants/{tenantId}/fechamentos-diarios/jetskis
     *
     * Body: {
     *   "jetskiId": "uuid",
     *   "data": "2025-10-26",
     *   "horimetroInicio": 1234.5,
     *   "fotoInicioId": "uuid",  // Foto já uploadada tipo PAINEL_INICIO
     *   "observacoes": "string"
     * }
     */
    @PostMapping
    AbrirDiaResponse abrirDia(@PathVariable UUID tenantId,
                              @RequestBody AbrirDiaRequest request);

    /**
     * Fecha o dia operacional de um jetski
     * POST /api/v1/tenants/{tenantId}/fechamentos-diarios/jetskis/{id}/fechar
     *
     * Body: {
     *   "horimetroFim": 1239.5,
     *   "fotoFimId": "uuid",  // Foto já uploadada tipo PAINEL_FIM
     *   "observacoes": "string"
     * }
     */
    @PostMapping("/{id}/fechar")
    FecharDiaResponse fecharDia(@PathVariable UUID tenantId,
                                @PathVariable UUID id,
                                @RequestBody FecharDiaRequest request);

    /**
     * Lista fechamentos diários (filtros: data, jetskiId, status)
     * GET /api/v1/tenants/{tenantId}/fechamentos-diarios/jetskis
     */
    @GetMapping
    Page<FechamentoDiarioJetskiResponse> listar(@PathVariable UUID tenantId,
                                                 FechamentoDiarioFiltros filtros,
                                                 Pageable pageable);

    /**
     * Busca fechamento por ID
     * GET /api/v1/tenants/{tenantId}/fechamentos-diarios/jetskis/{id}
     */
    @GetMapping("/{id}")
    FechamentoDiarioJetskiResponse buscarPorId(@PathVariable UUID tenantId,
                                                @PathVariable UUID id);

    /**
     * Busca fechamento por jetski e data
     * GET /api/v1/tenants/{tenantId}/fechamentos-diarios/jetskis/buscar
     *   ?jetskiId={uuid}&data=2025-10-26
     */
    @GetMapping("/buscar")
    FechamentoDiarioJetskiResponse buscarPorJetskiEData(
        @PathVariable UUID tenantId,
        @RequestParam UUID jetskiId,
        @RequestParam LocalDate data);
}
```

---

## 5. WORKFLOW COMPLETO

### 5.1 Abertura do Dia

```
1. Operador tira foto do painel
   → POST /fotos/upload (tipo: PAINEL_INICIO, horimetroLeitura: 1234.5)
   → Retorna: fotoId

2. Sistema registra abertura
   → POST /fechamentos-diarios/jetskis
   {
     "jetskiId": "uuid",
     "data": "2025-10-26",
     "horimetroInicio": 1234.5,
     "fotoInicioId": "uuid"
   }
   → Cria FechamentoDiarioJetski com status=ABERTO
   → Jetski disponível para locações
```

### 5.2 Locações Durante o Dia

```
3. Check-in de locação
   → POST /locacoes/check-in/reserva
   → Sistema:
     - Busca FechamentoDiarioJetski ABERTO para aquele jetski/data
     - Vincula locacao.fechamento_diario_jetski_id
     - Foto é OPCIONAL (pode enviar ou não)

4. Check-out de locação
   → POST /locacoes/{id}/check-out
   → Sistema:
     - Atualiza locação
     - Incrementa fechamentoDiario.quantidadeLocacoes
     - Incrementa fechamentoDiario.minutosLocados
     - Foto é OPCIONAL
```

### 5.3 Fechamento do Dia

```
5. Operador tira foto do painel
   → POST /fotos/upload (tipo: PAINEL_FIM, horimetroLeitura: 1239.5)
   → Retorna: fotoId

6. Sistema registra fechamento
   → POST /fechamentos-diarios/jetskis/{id}/fechar
   {
     "horimetroFim": 1239.5,
     "fotoFimId": "uuid"
   }
   → Sistema:
     - Atualiza horimetroFim
     - Calcula horasOperadas (1239.5 - 1234.5 = 5.0h)
     - Calcula horasLocadas (minutosLocados / 60)
     - Calcula divergenciaPercentual
     - Se divergência > 10%, gera alerta
     - Altera status = FECHADO
     - Ativa bloqueio retroativo
```

---

## 6. VALIDAÇÕES E REGRAS

### Validações na Abertura
- ✅ Foto tipo PAINEL_INICIO obrigatória
- ✅ HorimetroLeitura da foto deve estar preenchido
- ✅ Não pode existir FechamentoDiarioJetski ABERTO para mesmo jetski/data
- ✅ Data não pode ser futura

### Validações no Fechamento
- ✅ Foto tipo PAINEL_FIM obrigatória
- ✅ HorimetroFim >= HorimetroInicio
- ✅ Todas as locações do dia devem estar finalizadas
- ✅ Status deve ser ABERTO

### Validações em Locação
- ✅ Ao criar locação, verifica se existe FechamentoDiarioJetski ABERTO
- ✅ Se não existir, retorna erro (jetski não está operando hoje)
- ✅ Se dia já foi fechado, não permite criar locação retroativa

---

## 7. PERMISSÕES RBAC

```rego
"OPERADOR": [
    "fechamento-diario:abrir",   // Abrir dia
    "fechamento-diario:fechar",  // Fechar dia
    "fechamento-diario:view",    // Ver fechamentos
    "foto:upload",               // Upload de fotos
    "foto:view"
],

"GERENTE": [
    "fechamento-diario:*",       // Todas operações
    "fechamento-diario:reabrir", // Reabrir dia fechado (excepcional)
    "foto:*"
],

"FINANCEIRO": [
    "fechamento-diario:view",    // Somente leitura
    "fechamento-diario:export"   // Exportar relatórios
]
```

---

## 8. TESTES BDD

### Cenário 1: Abertura Bem-Sucedida
```gherkin
Dado que hoje é 26/10/2025
E o jetski "YAMAHA-001" não possui fechamento aberto
Quando o operador tira foto do painel mostrando 1234.5h
E abre o dia operacional com essa foto
Então o sistema cria FechamentoDiarioJetski com status ABERTO
E horimetro_inicio = 1234.5
E foto_inicio_id está vinculada
```

### Cenário 2: Fechamento com Validação OK
```gherkin
Dado que o jetski "YAMAHA-001" tem fechamento aberto
E horimetro_inicio = 1234.5h
E teve 4 locações totalizando 300 minutos (5.0h)
Quando o operador tira foto do painel mostrando 1239.5h
E fecha o dia operacional
Então horasOperadas = 5.0h
E horasLocadas = 5.0h
E divergenciaPercentual = 0%
E status = FECHADO
```

### Cenário 3: Fechamento com Divergência
```gherkin
Dado que o jetski "YAMAHA-001" tem fechamento aberto
E horimetro_inicio = 1234.5h
E teve 3 locações totalizando 240 minutos (4.0h)
Quando o operador tira foto do painel mostrando 1239.5h
E fecha o dia operacional
Então horasOperadas = 5.0h
E horasLocadas = 4.0h
E divergenciaPercentual = 20%
E sistema gera alerta "Divergência acima de 10%"
E status = FECHADO (mesmo com divergência)
```

### Cenário 4: Bloqueio Retroativo
```gherkin
Dado que o jetski "YAMAHA-001" tem fechamento do dia 25/10 com status FECHADO
Quando operador tenta criar locação para 25/10
Então sistema retorna erro 400
E mensagem "Dia operacional já fechado. Não é possível criar locações retroativas."
```

---

## 9. SPRINT PLANNING

### Tasks Estimadas

**Backend (20 pontos)**
1. Criar entidade FechamentoDiarioJetski (2 pts)
2. Criar migrations V1012-V1014 (2 pts)
3. Adicionar campos em Foto (1 pt)
4. Adicionar PAINEL_INICIO/FIM em FotoTipo (1 pt)
5. Criar FechamentoDiarioJetskiService (5 pts)
6. Criar FechamentoDiarioJetskiController (3 pts)
7. Integrar check-in/check-out com fechamento diário (3 pts)
8. Testes unitários + integração (3 pts)

**Postman (3 pontos)**
9. Endpoints de abertura/fechamento (2 pts)
10. Jornada completa (abrir → locações → fechar) (1 pt)

**Documentação (2 pontos)**
11. Atualizar README com novo fluxo (1 pt)
12. Swagger/OpenAPI (1 pt)

**Total: 25 story points**
**Duração estimada: 1,5-2 semanas**

---

## 10. PRÓXIMOS PASSOS

Após Sprint 3, o sistema terá:
- ✅ Controle diário completo de cada jetski
- ✅ Fotos obrigatórias de painel (evidência auditável)
- ✅ Validação de divergências operacionais
- ✅ Bloqueio retroativo para integridade financeira

**Sprint 4 será:** Combustível + RN03 (3 modos de cobrança)

---

**Fim da Especificação Sprint 3**
