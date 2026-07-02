-- OTP (código de confirmação) no aceite: evidência de que o cliente controla o
-- canal (e-mail/WhatsApp). Fase B do reforço jurídico da assinatura.
ALTER TABLE public.reserva_aceite
    ADD COLUMN IF NOT EXISTS otp_verificado boolean,
    ADD COLUMN IF NOT EXISTS otp_canal   varchar(20),
    ADD COLUMN IF NOT EXISTS otp_destino varchar(160);
