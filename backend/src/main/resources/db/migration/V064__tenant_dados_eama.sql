-- V064: Dados do EAMA exigidos no ofício à Capitania (NORMAM-212/DPC, item 5.4.2 e Anexo 5-A).
-- O e-mail de solicitação de emissão da CHA-MTA-E é assinado pelo responsável do EAMA
-- e traz telefone e e-mail oficial (o declarado no Anexo 5-A). Nada disso existia no
-- cadastro da empresa (só whatsapp/email_remetente). Todos opcionais: a emissão segue
-- best-effort e a assinatura omite o que não foi informado.

ALTER TABLE public.tenant
    ADD COLUMN IF NOT EXISTS responsavel_nome varchar(120),
    ADD COLUMN IF NOT EXISTS telefone         varchar(30),
    ADD COLUMN IF NOT EXISTS email_oficial    varchar(255);

COMMENT ON COLUMN public.tenant.responsavel_nome IS 'Responsável pelo EAMA (assina o ofício à Capitania — Anexo 5-A NORMAM-212)';
COMMENT ON COLUMN public.tenant.telefone         IS 'Telefone institucional do EAMA (Anexo 5-A)';
COMMENT ON COLUMN public.tenant.email_oficial    IS 'E-mail oficial do EAMA declarado no Anexo 5-A — Reply-To do e-mail à Capitania';
