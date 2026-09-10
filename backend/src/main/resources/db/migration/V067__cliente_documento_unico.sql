-- V067: unicidade do documento por loja.
--
-- Fecha o que a V066 deixou aberto de propósito. Lá o valor foi tipificado e
-- normalizado, mas a garantia de "uma pessoa, uma ficha" ficou dependendo da
-- disciplina do código — e são quatro caminhos que gravam cliente (balcão,
-- portal, perfil, merge de CPF). Basta um esquecer a normalização para a
-- duplicata voltar a nascer, e ela nasce calada: não quebra nada, só divide o
-- histórico da pessoa em duas fichas e afrouxa a trava anti-takeover.
--
-- O índice não veio junto da V066 porque normalizar podia revelar duplicatas
-- que a formatação escondia, e um CREATE UNIQUE INDEX otimista derrubaria o
-- deploy. A V066 emitiu o aviso, a produção foi consultada e voltou sem
-- nenhuma colisão — daí este passo agora ser seguro.
--
-- Se algum ambiente ainda tiver colisão, esta migration FALHA de propósito:
-- é melhor o deploy parar aqui do que seguir com a garantia pela metade. O
-- bloco abaixo diz exatamente quais fichas conciliar antes de repetir.

DO $$
DECLARE
    colisoes int;
    exemplo  text;
BEGIN
    SELECT count(*) INTO colisoes FROM (
        SELECT tenant_id, documento_tipo, documento
          FROM public.cliente
         WHERE documento IS NOT NULL AND btrim(documento) <> ''
         GROUP BY 1, 2, 3
        HAVING count(*) > 1
    ) d;

    IF colisoes > 0 THEN
        SELECT string_agg(format('tenant=%s %s=%s (%s fichas)',
                                 tenant_id, coalesce(documento_tipo, '?'), documento, n), '; ')
          INTO exemplo
          FROM (
            SELECT tenant_id, documento_tipo, documento, count(*) n
              FROM public.cliente
             WHERE documento IS NOT NULL AND btrim(documento) <> ''
             GROUP BY 1, 2, 3
            HAVING count(*) > 1
             LIMIT 10
          ) x;

        RAISE EXCEPTION
            'V067: % grupo(s) de clientes com o mesmo documento na mesma loja. '
            'Concilie as fichas (qual fica, o que acontece com as reservas da outra) '
            'antes de aplicar o índice único. Exemplos: %', colisoes, exemplo;
    END IF;
END $$;

-- Parcial: ficha sem documento é legítima (lead capturado na praia, pré-conta
-- criada só com nome e telefone) e não deve competir por unicidade.
CREATE UNIQUE INDEX IF NOT EXISTS ux_cliente_tenant_documento
    ON public.cliente (tenant_id, documento_tipo, documento)
 WHERE documento IS NOT NULL AND btrim(documento) <> '';

COMMENT ON INDEX public.ux_cliente_tenant_documento IS
    'Uma ficha por documento por loja. O dedupe do balcão e a trava anti-takeover '
    'do criarPreConta passam a ter garantia de banco, não só de código.';
