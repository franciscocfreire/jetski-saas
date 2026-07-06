"use client";

import { withBase } from "@/lib/base";
import { Suspense, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Anchor, CheckCircle2, KeyRound, Loader2, LogIn } from "lucide-react";
import { signIn } from "next-auth/react";
import { validarClaim, ApiError, type ClaimAtivacao } from "@/lib/api";
import { Button, Card, Field, inputCls } from "@/components/ui";

/**
 * Ativação da conta criada no balcão (claim-token): o cliente chega pelo link
 * do e-mail de convite e confirma com a senha temporária. O sucesso provisiona
 * (ou reutiliza) a identidade no Keycloak e vincula o cadastro da loja.
 */
export default function AtivarPage() {
  return (
    <Suspense fallback={<div className="py-24 text-center text-slate-400">Carregando…</div>}>
      <AtivarInner />
    </Suspense>
  );
}

function AtivarInner() {
  const params = useSearchParams();
  const token = params.get("token");
  const [senha, setSenha] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [resultado, setResultado] = useState<ClaimAtivacao | null>(null);
  const [jaAtiva, setJaAtiva] = useState(false);
  const [indo, setIndo] = useState(false);

  async function ativar() {
    if (!token || enviando) return;
    setErro(null);
    setEnviando(true);
    try {
      setResultado(await validarClaim(token, senha.trim()));
    } catch (e) {
      if (e instanceof ApiError) {
        // "Conta do cliente já está ativa" → só falta entrar
        if (e.message.toLowerCase().includes("já está ativa")) {
          setJaAtiva(true);
        } else {
          setErro(e.message);
        }
      } else {
        setErro("Não foi possível ativar a conta agora. Tente novamente.");
      }
    } finally {
      setEnviando(false);
    }
  }

  function entrar() {
    if (indo) return;
    setIndo(true);
    signIn("keycloak", { callbackUrl: withBase("/conta/perfil") });
  }

  if (!token) {
    return (
      <div className="mx-auto max-w-sm py-10 text-center">
        <h1 className="text-2xl font-bold text-ink-900">Link inválido</h1>
        <p className="mt-2 text-sm text-slate-500">
          Este link de ativação está incompleto. Abra o link exatamente como
          veio no e-mail ou peça um novo convite à loja.
        </p>
        <Link href="/login" className="mt-6 inline-block font-medium text-brand-600">
          Ir para o login
        </Link>
      </div>
    );
  }

  if (resultado || jaAtiva) {
    return (
      <div className="mx-auto max-w-sm py-10 text-center">
        <span className="mx-auto grid h-14 w-14 place-items-center rounded-2xl bg-emerald-100 text-emerald-700">
          <CheckCircle2 size={26} />
        </span>
        <h1 className="mt-4 text-2xl font-bold text-ink-900">
          {jaAtiva ? "Sua conta já está ativa" : "Conta ativada!"}
        </h1>
        <p className="mt-2 text-sm text-slate-500">
          {jaAtiva
            ? "É só entrar com seu e-mail (ou CPF) e sua senha."
            : resultado?.contaExistente
              ? "Você já tinha conta no Meu Jet — entre com a senha que você já usa. O cadastro da loja foi vinculado à sua conta."
              : "Entre com seu e-mail e a senha temporária do convite — no primeiro acesso você criará sua nova senha."}
        </p>
        <Button className="mt-6 w-full gap-2" size="lg" onClick={entrar} disabled={indo}>
          {indo ? <Loader2 size={16} className="animate-spin" /> : <LogIn size={16} />}
          {indo ? "Abrindo login seguro…" : "Entrar no portal"}
        </Button>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-sm py-6">
      <div className="flex flex-col items-center">
        <span className="grid h-12 w-12 place-items-center rounded-2xl bg-brand-600 text-white">
          <Anchor size={22} />
        </span>
        <h1 className="mt-3 text-2xl font-bold text-ink-900">Ativar sua conta</h1>
        <p className="text-center text-sm text-slate-500">
          A loja criou um cadastro para você. Confirme com a senha temporária
          que veio no e-mail do convite.
        </p>
      </div>

      <Card className="mt-6 p-6">
        <Field label="Senha temporária (enviada no e-mail)">
          <input
            className={inputCls}
            type="text"
            value={senha}
            onChange={(e) => setSenha(e.target.value)}
            autoComplete="one-time-code"
            autoFocus
          />
        </Field>

        {erro && (
          <p className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
            {erro} {erro.toLowerCase().includes("token") && "Peça um novo convite no balcão da loja."}
          </p>
        )}

        <Button
          className="mt-4 w-full gap-2"
          size="lg"
          onClick={ativar}
          disabled={senha.trim().length === 0 || enviando}
        >
          {enviando ? <Loader2 size={15} className="animate-spin" /> : <KeyRound size={15} />}
          Ativar conta
        </Button>
        <p className="mt-4 text-center text-sm text-slate-500">
          Já tem conta ativa?{" "}
          <Link href="/login" className="font-medium text-brand-600">
            Entrar
          </Link>
        </p>
      </Card>
    </div>
  );
}
