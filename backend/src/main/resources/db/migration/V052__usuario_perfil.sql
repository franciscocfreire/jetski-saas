-- V052: perfil self-service do usuário staff (backoffice /dashboard/perfil).
--
-- A tabela usuario é GLOBAL (sem tenant_id, sem RLS) — o usuário pode pertencer
-- a vários tenants via membro. Telefone e avatar são atributos da pessoa, não
-- de uma loja; o avatar vive no storage sob prefixo global usuarios/{id}/...
-- (fora do export/expurgo por tenant, by design).
ALTER TABLE public.usuario ADD COLUMN IF NOT EXISTS telefone varchar(30);
ALTER TABLE public.usuario ADD COLUMN IF NOT EXISTS avatar_key varchar(255);
ALTER TABLE public.usuario ADD COLUMN IF NOT EXISTS avatar_content_type varchar(100);
