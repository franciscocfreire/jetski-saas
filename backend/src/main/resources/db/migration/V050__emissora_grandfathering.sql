-- =====================================================================
-- Emissão delegada — portão duplo da emissão PRÓPRIA (§8.K estrito):
-- a partir daqui, emitir documentação NORMAM em nome próprio exige
-- emissora_habilitada (portão cadastral), além do módulo no plano.
--
-- Grandfathering: lojas que JÁ emitem (têm documento_emitido) ou que já
-- estão configuradas para emitir (marinha_email preenchido) entram
-- habilitadas — nenhuma operação existente quebra no deploy. Empresas
-- novas passam pela validação do superadmin (habilitar-emissora).
-- =====================================================================

UPDATE public.tenant t
   SET emissora_habilitada = true
 WHERE emissora_habilitada = false
   AND (
        EXISTS (SELECT 1 FROM public.documento_emitido d WHERE d.tenant_id = t.id)
     OR t.marinha_email IS NOT NULL
   );
