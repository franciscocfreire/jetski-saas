"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { useSession } from "next-auth/react";
import {
  FileSignature,
  CheckCircle2,
  ShieldCheck,
  Loader2,
  MailCheck,
} from "lucide-react";
import {
  getReserva,
  getChecklist,
  getOtpStatus,
  enviarOtp,
  verificarOtp,
  assinarTermo,
  ApiError,
  type ReservaCliente,
  type OtpStatus,
} from "@/lib/api";
import { Button, Card, inputCls } from "@/components/ui";
import { SignaturePad } from "@/components/SignaturePad";

const CLAUSULAS = [
  "A moto aquática me é entregue em perfeitas condições de funcionamento e conservação.",
  "Sou responsável pela guarda, conservação e correta operação do equipamento durante o uso.",
  "Danos por negligência, imprudência, imperícia ou desrespeito às orientações são de minha responsabilidade.",
  "Em caso de colisão, abalroamento, encalhe ou choque, arco integralmente com os custos de reparo.",
  "O tombamento (virada) pode causar entrada de água no motor, gerando custos de manutenção.",
  "Autorizo a cobrança dos custos de inspeção/drenagem/reparo entre R$ 400,00 e R$ 2.000,00 em caso de virada por erro operacional.",
  "Possuo condições físicas e psicológicas adequadas e não estou sob efeito de álcool ou drogas.",
  "Comprometo-me a respeitar as orientações do instrutor e as normas da Autoridade Marítima Brasileira.",
];

/**
 * Assinatura REAL do termo: cláusulas + SignaturePad + OTP (se a loja exigir).
 * O aceite é registrado com evidências (IP, user-agent, hash, origem PORTAL).
 */
export default function TermosPage() {
  const { id } = useParams<{ id: string }>();
  const { data: session, status } = useSession();
  const router = useRouter();

  const [reserva, setReserva] = useState<ReservaCliente | null>(null);
  const [assinado, setAssinado] = useState(false);
  const [otp, setOtp] = useState<OtpStatus | null>(null);
  const [otpEnviado, setOtpEnviado] = useState<string | null>(null);
  const [codigo, setCodigo] = useState("");
  const [aceito, setAceito] = useState(false);
  const [assinatura, setAssinatura] = useState<string | null>(null);
  const [processando, setProcessando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const carregar = useCallback(async (token: string) => {
    try {
      const [r, c, o] = await Promise.all([
        getReserva(token, id),
        getChecklist(token, id),
        getOtpStatus(token, id),
      ]);
      setReserva(r);
      setAssinado(c.termosOk);
      setOtp(o);
    } catch {
      setErro("Reserva não encontrada.");
    }
  }, [id]);

  useEffect(() => {
    if (status === "unauthenticated") {
      router.replace("/login");
      return;
    }
    if (status === "authenticated" && session?.accessToken) {
      carregar(session.accessToken);
    }
  }, [status, session?.accessToken, carregar, router]);

  if (erro && !reserva) return <p className="py-20 text-center text-slate-400">{erro}</p>;
  if (!reserva) {
    return (
      <div className="flex justify-center py-20 text-slate-400">
        <Loader2 className="animate-spin" />
      </div>
    );
  }

  const precisaOtp = otp?.ativo && !otp?.verificado;
  const podeAssinar = aceito && !!assinatura && !precisaOtp;

  async function pedirCodigo() {
    if (!session?.accessToken) return;
    setProcessando(true);
    setErro(null);
    try {
      const envio = await enviarOtp(session.accessToken, id);
      setOtpEnviado(envio.destinoMascarado ?? envio.canal ?? "enviado");
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Não foi possível enviar o código.");
    } finally {
      setProcessando(false);
    }
  }

  async function confirmarCodigo() {
    if (!session?.accessToken) return;
    setProcessando(true);
    setErro(null);
    try {
      const ok = await verificarOtp(session.accessToken, id, codigo.trim());
      if (!ok) {
        setErro("Código inválido — confira e tente novamente.");
      } else {
        setOtp((o) => (o ? { ...o, verificado: true } : o));
      }
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Não foi possível verificar o código.");
    } finally {
      setProcessando(false);
    }
  }

  async function assinar() {
    if (!session?.accessToken || !assinatura) return;
    setProcessando(true);
    setErro(null);
    try {
      await assinarTermo(session.accessToken, id, assinatura);
      setAssinado(true);
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Não foi possível registrar o aceite.");
    } finally {
      setProcessando(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl">
      <Link href={`/conta/reservas/${id}`} className="text-sm text-slate-400 hover:text-slate-600">
        ← Voltar para a reserva
      </Link>
      <h1 className="mt-3 flex items-center gap-2 text-2xl font-bold text-ink-900">
        <FileSignature className="text-brand-600" /> Termo de responsabilidade
      </h1>

      {assinado ? (
        <Card className="mt-6 flex flex-col items-center gap-3 p-8 text-center">
          <CheckCircle2 className="text-emerald-500" size={44} />
          <h2 className="text-lg font-semibold text-ink-900">Termo assinado</h2>
          <p className="text-sm text-slate-500">
            Aceite eletrônico registrado com data, IP, hash do documento e sua assinatura.
          </p>
          <Button href={`/conta/reservas/${id}`} variant="outline">
            Voltar para a reserva
          </Button>
        </Card>
      ) : (
        <>
          <Card className="mt-6 p-6">
            <p className="text-sm text-slate-500">
              Termo de responsabilidade pelo uso de moto aquática —{" "}
              <b className="text-slate-700">{reserva.lojaNome}</b>
              {reserva.lojaCnpj && <> · CNPJ {reserva.lojaCnpj}</>}
            </p>
            <div className="mt-4 max-h-72 space-y-2 overflow-y-auto rounded-xl bg-slate-50 p-4 text-sm text-slate-700">
              {CLAUSULAS.map((c, i) => (
                <p key={i} className="flex gap-2">
                  <span className="font-semibold text-slate-400">{i + 1}.</span> {c}
                </p>
              ))}
            </div>
            <label className="mt-4 flex cursor-pointer items-start gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                className="mt-0.5"
                checked={aceito}
                onChange={(e) => setAceito(e.target.checked)}
              />
              Li e concordo com todas as cláusulas acima.
            </label>
          </Card>

          {otp?.ativo && (
            <Card className="mt-4 p-6">
              <h3 className="flex items-center gap-2 font-semibold text-ink-900">
                <MailCheck size={18} /> Confirmação por código
              </h3>
              {otp.verificado ? (
                <p className="mt-2 flex items-center gap-1 text-sm text-emerald-600">
                  <CheckCircle2 size={15} /> Código confirmado.
                </p>
              ) : (
                <>
                  <p className="mt-1 text-sm text-slate-500">
                    A loja exige confirmação por código antes da assinatura.
                  </p>
                  {otpEnviado ? (
                    <div className="mt-3 flex flex-wrap items-end gap-2">
                      <div className="flex-1">
                        <p className="mb-1 text-xs text-slate-500">
                          Código enviado para {otpEnviado}
                        </p>
                        <input
                          className={inputCls}
                          value={codigo}
                          onChange={(e) => setCodigo(e.target.value)}
                          placeholder="000000"
                          maxLength={8}
                          inputMode="numeric"
                        />
                      </div>
                      <Button onClick={confirmarCodigo} disabled={processando || codigo.trim().length < 4}>
                        {processando && <Loader2 size={14} className="animate-spin" />}
                        Confirmar
                      </Button>
                    </div>
                  ) : (
                    <Button className="mt-3" variant="outline" onClick={pedirCodigo} disabled={processando}>
                      {processando && <Loader2 size={14} className="animate-spin" />}
                      Enviar código
                    </Button>
                  )}
                </>
              )}
            </Card>
          )}

          <Card className="mt-4 p-6">
            <h3 className="font-semibold text-ink-900">Sua assinatura</h3>
            <div className="mt-3">
              <SignaturePad onDataUrlChange={setAssinatura} />
            </div>
          </Card>

          {erro && (
            <p className="mt-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{erro}</p>
          )}

          <Button
            className="mt-4 w-full gap-2"
            size="lg"
            onClick={assinar}
            disabled={!podeAssinar || processando}
          >
            {processando ? <Loader2 size={15} className="animate-spin" /> : <FileSignature size={15} />}
            Assinar eletronicamente
          </Button>
          <p className="mt-3 flex items-center justify-center gap-1 text-center text-xs text-slate-400">
            <ShieldCheck size={13} /> Registramos data, IP, dispositivo e o hash da assinatura.
          </p>
        </>
      )}
    </div>
  );
}
