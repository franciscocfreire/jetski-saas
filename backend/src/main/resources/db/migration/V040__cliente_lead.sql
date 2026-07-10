-- =====================================================================
-- Captura de leads pelo backoffice (menu Clientes)
-- - origem 'LEAD': cliente registrado por um operador fora do balcão
--   (captação na praia); balcão continua 'BALCAO', auto-cadastro 'PORTAL'.
-- - observacoes: notas livres do staff (o backoffice já exibia o campo).
-- - capturado_por: usuário do staff que registrou o lead/pré-conta
--   (base p/ métrica de conversão e comissão de captação futuras).
-- =====================================================================

ALTER TABLE public.cliente
    ADD COLUMN observacoes    text,
    ADD COLUMN capturado_por  uuid;

ALTER TABLE public.cliente
    ADD CONSTRAINT cliente_capturado_por_fk
        FOREIGN KEY (capturado_por) REFERENCES public.usuario (id);

ALTER TABLE public.cliente DROP CONSTRAINT IF EXISTS cliente_origem_check;
ALTER TABLE public.cliente
    ADD CONSTRAINT cliente_origem_check
        CHECK ((origem)::text = ANY (ARRAY['PORTAL'::text, 'BALCAO'::text, 'LEAD'::text]));
