-- V072: "Reservar Agora" vira módulo próprio (RESERVA_ONLINE), separado da Loja online.
--
-- Até aqui LOJA_ONLINE liberava a vitrine própria E a reserva com sinal PIX pelo
-- portal, e o botão "Reservar Agora" do marketplace aparecia para todo mundo.
-- Agora a reserva (botão no marketplace/vitrine, disponibilidade pública e criação
-- da reserva no portal) é o módulo RESERVA_ONLINE; LOJA_ONLINE fica só a vitrine.
--
-- Planos que já tinham Loja online continuam reservando: ganham RESERVA_ONLINE.
-- modulos NULL (todos) segue liberando tudo.

UPDATE public.plano
   SET modulos = modulos || '["RESERVA_ONLINE"]'::jsonb
 WHERE modulos @> '["LOJA_ONLINE"]'::jsonb
   AND NOT modulos @> '["RESERVA_ONLINE"]'::jsonb;
