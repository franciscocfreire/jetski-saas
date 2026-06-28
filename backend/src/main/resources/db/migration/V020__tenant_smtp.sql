-- V020: SMTP por tenant (cada empresa envia pelo próprio servidor/conta).
-- A senha é segredo: nunca é retornada pela API nem logada. Recomenda-se
-- criptografia em repouso / secrets manager em produção.
ALTER TABLE public.tenant
    ADD COLUMN IF NOT EXISTS smtp_host     varchar(255),
    ADD COLUMN IF NOT EXISTS smtp_port     integer,
    ADD COLUMN IF NOT EXISTS smtp_username varchar(255),
    ADD COLUMN IF NOT EXISTS smtp_password varchar(255),
    ADD COLUMN IF NOT EXISTS smtp_from     varchar(255),
    ADD COLUMN IF NOT EXISTS smtp_starttls boolean DEFAULT true;
