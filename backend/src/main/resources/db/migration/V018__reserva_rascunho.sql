-- V018: estado RASCUNHO da reserva (atendimento de balcão em preenchimento).
-- O balcão cria a reserva como RASCUNHO (sem cobrança, não bloqueia jetski) e a
-- emissão a transiciona para PENDENTE/CONFIRMADA. EM_EDICAO é estado de tela
-- (frontend), não persistido — por isso NÃO entra no CHECK.

ALTER TABLE public.reserva DROP CONSTRAINT IF EXISTS reserva_status_check;

ALTER TABLE public.reserva
    ADD CONSTRAINT reserva_status_check
    CHECK ((status)::text = ANY (ARRAY[
        'RASCUNHO'::varchar,
        'PENDENTE'::varchar,
        'CONFIRMADA'::varchar,
        'CANCELADA'::varchar,
        'FINALIZADA'::varchar,
        'EXPIRADA'::varchar
    ]::text[]));
