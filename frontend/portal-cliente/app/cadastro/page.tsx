"use client";

import { withBase } from "@/lib/base";
import { useState } from "react";
import Link from "next/link";
import { Anchor, MailCheck, Loader2 } from "lucide-react";
import { signIn } from "next-auth/react";
import { signup, ApiError } from "@/lib/api";
import { Button, Card, Field, inputCls } from "@/components/ui";

/**
 * Cadastro REAL: cria a identidade global no backend
 * (POST /v1/public/customers/signup) — o Keycloak envia o e-mail de verificação.
 */
export default function CadastroPage() {
  const [nome, setNome] = useState("");
  const [email, setEmail] = useState("");
  const [senha, setSenha] = useState("");
  const [confirmar, setConfirmar] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [criada, setCriada] = useState(false);
  const [indo, setIndo] = useState(false);

  const valido =
    nome.trim().length >= 3 &&
    /\S+@\S+\.\S+/.test(email) &&
    senha.length >= 8 &&
    senha === confirmar;

  async function cadastrar() {
    setErro(null);
    setEnviando(true);
    try {
      await signup(nome.trim(), email.trim(), senha);
      setCriada(true);
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Não foi possível criar a conta agora.");
    } finally {
      setEnviando(false);
    }
  }

  if (criada) {
    return (
      <div className="mx-auto max-w-sm py-10 text-center">
        <span className="mx-auto grid h-14 w-14 place-items-center rounded-2xl bg-emerald-100 text-emerald-700">
          <MailCheck size={26} />
        </span>
        <h1 className="mt-4 text-2xl font-bold text-ink-900">Conta criada!</h1>
        <p className="mt-2 text-sm text-slate-500">
          Enviamos um link de verificação para <strong>{email}</strong>. Você já
          pode entrar — a verificação libera todos os recursos da conta.
        </p>
        <Button
          className="mt-6 w-full"
          size="lg"
          onClick={() => { if (!indo) { setIndo(true); signIn("keycloak", { callbackUrl: withBase("/conta/perfil") }); } }}
          disabled={indo}
        >
          Entrar agora
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
        <h1 className="mt-3 text-2xl font-bold text-ink-900">Criar sua conta</h1>
        <p className="text-sm text-slate-500">
          Uma conta só — vale para todas as lojas do Meu Jet
        </p>
      </div>

      <Card className="mt-6 p-6">
        <div className="grid gap-3">
          <Field label="Nome completo">
            <input
              className={inputCls}
              value={nome}
              onChange={(e) => setNome(e.target.value)}
              autoComplete="name"
            />
          </Field>
          <Field label="E-mail">
            <input
              className={inputCls}
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="email"
            />
          </Field>
          <Field label="Senha (mín. 8 caracteres)">
            <input
              className={inputCls}
              type="password"
              value={senha}
              onChange={(e) => setSenha(e.target.value)}
              autoComplete="new-password"
            />
          </Field>
          <Field label="Confirmar senha">
            <input
              className={inputCls}
              type="password"
              value={confirmar}
              onChange={(e) => setConfirmar(e.target.value)}
              autoComplete="new-password"
            />
          </Field>
        </div>

        {confirmar.length > 0 && senha !== confirmar && (
          <p className="mt-2 text-xs text-red-600">As senhas não conferem.</p>
        )}
        {erro && (
          <p className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
            {erro}
          </p>
        )}

        <Button
          className="mt-4 w-full gap-2"
          size="lg"
          onClick={cadastrar}
          disabled={!valido || enviando}
        >
          {enviando && <Loader2 size={15} className="animate-spin" />}
          Criar conta
        </Button>
        <p className="mt-4 text-center text-sm text-slate-500">
          Já tem conta?{" "}
          <Link href="/login" className="font-medium text-brand-600">
            Entrar
          </Link>
        </p>
      </Card>
    </div>
  );
}
