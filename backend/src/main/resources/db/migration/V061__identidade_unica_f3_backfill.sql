-- V061: Identidade única — F3: backfill do re-key dos dados globais.
-- Spec: IDENTIDADE_UNICA_SPEC.md §5/F3.
--
-- Liga customer_profile e customer_habilitacao à PESSOA quando o sub já tem
-- mapping. Em produção pré-lançamento isso é ~zero linhas; em dev cobre os
-- dados de teste. Idempotente (só preenche NULL).

UPDATE public.customer_profile p
   SET usuario_id = uip.usuario_id
  FROM public.usuario_identity_provider uip
 WHERE p.usuario_id IS NULL
   AND uip.provider = p.provider
   AND uip.provider_user_id = p.provider_user_id;

UPDATE public.customer_habilitacao h
   SET usuario_id = uip.usuario_id
  FROM public.usuario_identity_provider uip
 WHERE h.usuario_id IS NULL
   AND h.provider IS NOT NULL
   AND uip.provider = h.provider
   AND uip.provider_user_id = h.provider_user_id;
