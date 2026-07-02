-- Certificado auto-assinado da plataforma para assinatura digital PAdES dos PDFs
-- emitidos (Fase C2). Chave privada armazenada cifrada (SecretCipher). Linha única
-- global (sem tenant_id) — identidade por tenant fica como refino futuro.
CREATE TABLE IF NOT EXISTS public.assinatura_certificado (
    id          uuid DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    subject     varchar(255) NOT NULL,
    cert_pem    text NOT NULL,
    key_pem_enc text NOT NULL,
    algoritmo   varchar(40) NOT NULL DEFAULT 'SHA256withRSA',
    created_at  timestamptz NOT NULL DEFAULT now()
);
