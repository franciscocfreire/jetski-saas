-- V019: e-mail remetente por tenant (identidade da empresa nos envios / responder-para).
ALTER TABLE public.tenant ADD COLUMN IF NOT EXISTS email_remetente varchar(255);
