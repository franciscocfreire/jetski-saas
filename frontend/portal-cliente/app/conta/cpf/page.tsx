"use client";

import { Suspense, useCallback, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useSession } from "next-auth/react";
import {
  CheckCircle2,
  IdCard,
  Loader2,
  MailCheck,
  ShieldAlert,
} from "lucide-react";
import { Button, Card, Field, SectionTitle, inputCls } from "@/components/ui";
import { useToast } from "@/components/Toast";
import { maskCpf } from "@/lib/masks";
import { sairDaConta } from "@/lib/logout";
import {
  ApiError,
  enviarCpfMerge,
  getSelf,
  isCpfEmUso,
  updateSelf,
  verificarCpfMerge,
  type CustomerSelf,
} from "@/lib/api";

/**
 * Gate de CPF + unificação de contas: pede o CPF no primeiro acesso; se ele
 * já pertence a outra conta (ex.: cadastro antigo por e-mail/senha e agora a
 * pessoa entrou com Google), oferece o merge verificado por OTP — o código
 * vai para o e-mail da conta dona do CPF, nunca revelado em claro.
 */
export default function CpfPage() {
  return (
    <Suspense fallback={<div className="py-24 text-center text-slate-400">Carregando…</div>}>
      <CpfInner />
    </Suspense>
  );
}

type Etapa = "form" | "colisao" | "codigoEnviado" | "indisponivel" | "sucesso";

function CpfInner() {
  const { data: session, status } = useSession();
  const router = useRouter();
  const params = useSearchParams();
  const { toast } = useToast();

  const next = params.get("next") ?? "/conta/perfil";

  const [self, setSelf] = useState<CustomerSelf | null>(null);
  const [etapa, setEtapa] = useState<Etapa>("form");
  const [cpf, setCpf] = useState("");
  const [codigo, setCodigo] = useState("");
  const [emailMascarado, setEmailMascarado] = useState("");
  const [motivoIndisponivel, setMotivoIndisponivel] = useState("");
  const [ocupado, setOcupado] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    if (status === "unauthenticated") {
      router.replace("/login");
      return;
    }
    if (status === "authenticated" && session?.accessToken) {
      getSelf(session.accessToken)
        .then((dados) => {
          // Já resolvido (CPF definido ou estrangeiro) — segue para o destino
          if (dados.identidade?.cpf || dados.identidade?.estrangeiro === true) {
            router.replace(next);
            return;
          }
          setSelf(dados);
        })
        .catch(() => router.replace(next));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, session?.accessToken]);

  const salvarCpf = useCallback(async () => {
    if (!session?.accessToken || !self) return;
    setOcupado(true);
    setErro(null);
    try {
      await updateSelf(session.accessToken, self.nome, { cpf });
      toast("CPF salvo");
      router.replace(next);
    } catch (e) {
      if (isCpfEmUso(e)) {
        setEtapa("colisao");
      } else {
        setErro(e instanceof ApiError ? e.message : "Não foi possível salvar o CPF.");
      }
    } finally {
      setOcupado(false);
    }
  }, [session?.accessToken, self, cpf, next, router, toast]);

  async function declararEstrangeiro() {
    if (!session?.accessToken || !self) return;
    setOcupado(true);
    setErro(null);
    try {
      await updateSelf(session.accessToken, self.nome, { estrangeiro: true });
      toast("Perfil atualizado");
      router.replace(next);
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Não foi possível atualizar o perfil.");
    } finally {
      setOcupado(false);
    }
  }

  async function enviarCodigo() {
    if (!session?.accessToken) return;
    setOcupado(true);
    setErro(null);
    try {
      const envio = await enviarCpfMerge(session.accessToken, cpf);
      if (envio.disponivel) {
        setEmailMascarado(envio.emailMascarado ?? "e-mail cadastrado");
        setCodigo("");
        setEtapa("codigoEnviado");
      } else {
        setMotivoIndisponivel(envio.mensagem);
        setEtapa("indisponivel");
      }
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Não foi possível enviar o código.";
      setErro(msg);
      toast(msg, "erro");
    } finally {
      setOcupado(false);
    }
  }

  async function confirmarCodigo() {
    if (!session?.accessToken) return;
    setOcupado(true);
    setErro(null);
    try {
      const r = await verificarCpfMerge(session.accessToken, cpf, codigo.trim());
      if (r.mergeConcluido) {
        setEtapa("sucesso");
      } else {
        setErro(r.mensagem ?? "Código incorreto — confira e tente de novo.");
      }
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Não foi possível confirmar o código.");
    } finally {
      setOcupado(false);
    }
  }

  // Pós-merge: sessão atual pertence à conta descartada — logout federado
  useEffect(() => {
    if (etapa !== "sucesso") return;
    const t = setTimeout(() => sairDaConta(session?.idToken), 3000);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [etapa]);

  if (status === "loading" || (!self && etapa === "form")) {
    return (
      <div className="flex justify-center py-20 text-slate-400">
        <Loader2 className="animate-spin" />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-md">
      <SectionTitle sub="Ele vale para todas as lojas — e você também poderá entrar com ele">
        Confirme seu CPF
      </SectionTitle>

      {erro && (
        <div className="mb-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{erro}</div>
      )}

      {etapa === "form" && (
        <Card className="p-6">
          <p className="flex items-start gap-2 text-sm text-slate-600">
            <IdCard size={18} className="mt-0.5 shrink-0 text-brand-600" />
            Para usar o portal, precisamos do CPF do seu cadastro. Ele é pedido
            uma única vez.
          </p>
          <div className="mt-4">
            <Field label="CPF">
              <input
                className={inputCls}
                inputMode="numeric"
                value={maskCpf(cpf)}
                onChange={(e) => setCpf(maskCpf(e.target.value))}
                placeholder="000.000.000-00"
                autoFocus
              />
            </Field>
          </div>
          <Button
            className="mt-4 w-full"
            size="lg"
            onClick={salvarCpf}
            disabled={ocupado || cpf.replace(/\D/g, "").length !== 11}
          >
            {ocupado && <Loader2 size={14} className="animate-spin" />}
            Salvar CPF
          </Button>
          <button
            type="button"
            onClick={declararEstrangeiro}
            disabled={ocupado}
            className="mt-3 w-full text-center text-sm text-slate-500 underline-offset-2 hover:underline"
          >
            Sou estrangeiro(a), não tenho CPF
          </button>
        </Card>
      )}

      {etapa === "colisao" && (
        <Card className="p-6">
          <div className="flex items-start gap-2 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
            <ShieldAlert size={16} className="mt-0.5 shrink-0" />
            <span>
              <strong>Este CPF já está em outra conta do Meu Jet.</strong> Se
              essa conta é sua, podemos unificar as duas: enviaremos um código
              para o e-mail dela.
            </span>
          </div>
          <Button className="mt-4 w-full" size="lg" onClick={enviarCodigo} disabled={ocupado}>
            {ocupado && <Loader2 size={14} className="animate-spin" />}
            Enviar código
          </Button>
          <button
            type="button"
            onClick={() => { setEtapa("form"); setErro(null); }}
            className="mt-3 w-full text-center text-sm text-slate-500 underline-offset-2 hover:underline"
          >
            Digitei o CPF errado — voltar
          </button>
        </Card>
      )}

      {etapa === "codigoEnviado" && (
        <Card className="p-6">
          <p className="flex items-start gap-2 text-sm text-slate-600">
            <MailCheck size={18} className="mt-0.5 shrink-0 text-brand-600" />
            <span>
              Enviamos um código de 6 dígitos para{" "}
              <strong>{emailMascarado}</strong>. Ele vale por 10 minutos.
            </span>
          </p>
          <div className="mt-4">
            <Field label="Código de 6 dígitos">
              <input
                className={`${inputCls} text-center text-lg tracking-[0.6em]`}
                inputMode="numeric"
                maxLength={6}
                value={codigo}
                onChange={(e) => setCodigo(e.target.value.replace(/\D/g, ""))}
                placeholder="••••••"
                autoFocus
              />
            </Field>
          </div>
          <Button
            className="mt-4 w-full"
            size="lg"
            onClick={confirmarCodigo}
            disabled={ocupado || codigo.length !== 6}
          >
            {ocupado && <Loader2 size={14} className="animate-spin" />}
            Confirmar código
          </Button>
          <button
            type="button"
            onClick={enviarCodigo}
            disabled={ocupado}
            className="mt-3 w-full text-center text-sm text-slate-500 underline-offset-2 hover:underline"
          >
            Reenviar código
          </button>
        </Card>
      )}

      {etapa === "indisponivel" && (
        <Card className="p-6">
          <div className="flex items-start gap-2 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
            <ShieldAlert size={16} className="mt-0.5 shrink-0" />
            <span>{motivoIndisponivel}</span>
          </div>
          <p className="mt-3 text-center text-xs text-slate-400">
            Se precisar de ajuda, fale com a loja.
          </p>
          <button
            type="button"
            onClick={() => { setEtapa("form"); setErro(null); }}
            className="mt-3 w-full text-center text-sm text-slate-500 underline-offset-2 hover:underline"
          >
            Voltar
          </button>
        </Card>
      )}

      {etapa === "sucesso" && (
        <Card className="p-6 text-center">
          <CheckCircle2 size={40} className="mx-auto text-emerald-600" />
          <h3 className="mt-3 font-semibold text-ink-900">Contas unificadas!</h3>
          <p className="mt-1 text-sm text-slate-600">
            Por segurança, saia e entre novamente com o Google — você verá todo
            o seu histórico.
          </p>
          <Button className="mt-4 w-full" size="lg" onClick={() => sairDaConta(session?.idToken)}>
            Entrar novamente
          </Button>
        </Card>
      )}
    </div>
  );
}
