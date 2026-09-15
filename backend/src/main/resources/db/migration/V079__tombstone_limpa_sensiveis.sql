-- V079: limpeza dos dados sensíveis das empresas JÁ excluídas (tombstone).
--
-- O expurgo (TenantExclusaoService) zerava SMTP/contatos com um UPDATE via JDBC logo
-- depois de salvar a entidade Tenant — e o flush do Hibernate no commit regravava a
-- linha inteira com os valores carregados, desfazendo a limpeza. As empresas excluídas
-- até aqui ficaram com senha de SMTP, contatos e branding. O código agora zera pela
-- entidade; esta migration corrige o passado. Idempotente: só toca status EXCLUIDO.
UPDATE public.tenant
   SET smtp_host = NULL,
       smtp_username = NULL,
       smtp_password = NULL,
       smtp_from = NULL,
       email_remetente = NULL,
       whatsapp = NULL,
       marinha_email = NULL,
       branding = NULL,
       pix_chave = NULL,
       exibir_no_marketplace = false,
       emissora_habilitada = false
 WHERE status = 'EXCLUIDO';

-- Convites de parceria pendentes com empresa excluída (dos dois lados) não podem ser aceitos
UPDATE public.vinculo_emissao v
   SET status = 'REVOGADO', revogado_em = now(), updated_at = now()
  FROM public.tenant t
 WHERE v.status = 'CONVIDADO'
   AND t.status = 'EXCLUIDO'
   AND t.id IN (v.tenant_emissor_id, v.tenant_operador_id);
