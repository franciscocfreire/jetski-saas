"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { useSession, signOut } from "next-auth/react";
import {
  Button,
  Card,
  Field,
  SectionTitle,
  inputCls,
  Badge,
} from "@/components/ui";
import { getSelf, updateSelf, ApiError, type CustomerSelf } from "@/lib/api";
import { Loader2, LogOut, MailWarning, Store, BadgeCheck } from "lucide-react";

/**
 * Perfil REAL (P0): dados da identidade global + lojas vinculadas, direto do
 * backend (/v1/customers/self). As demais telas de conta seguem mock até P1.
 */
export default function PerfilPage() {
  const { data: session, status } = useSession();
  const router = useRouter();

  const [self, setSelf] = useState<CustomerSelf | null>(null);
  const [nome, setNome] = useState("");
  const [carregando, setCarregando] = useState(true);
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [salvo, setSalvo] = useState(false);

  const carregar = useCallback(async (token: string) => {
    setCarregando(true);
    setErro(null);
    try {
      const dados = await getSelf(token);
      setSelf(dados);
      setNome(dados.nome ?? "");
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Não foi possível carregar o perfil.");
    } finally {
      setCarregando(false);
    }
  }, []);

  useEffect(() => {
    if (status === "unauthenticated") {
      router.replace("/login");
      return;
    }
    if (status === "authenticated" && session?.accessToken) {
      carregar(session.accessToken);
    }
  }, [status, session?.accessToken, carregar, router]);

  async function salvar() {
    if (!session?.accessToken) return;
    setSalvando(true);
    setErro(null);
    setSalvo(false);
    try {
      await updateSelf(session.accessToken, nome);
      setSalvo(true);
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Não foi possível salvar.");
    } finally {
      setSalvando(false);
    }
  }

  if (status === "loading" || (carregando && !erro)) {
    return (
      <div className="flex justify-center py-20 text-slate-400">
        <Loader2 className="animate-spin" />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-2xl">
      <SectionTitle sub="Seus dados e lojas vinculadas">Meu perfil</SectionTitle>

      {self && !self.emailVerified && (
        <div className="mb-4 flex items-start gap-2 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          <MailWarning size={16} className="mt-0.5 shrink-0" />
          <span>
            <strong>Verifique seu e-mail.</strong> Enviamos um link para{" "}
            {self.email}. Sem a verificação, suas reservas não ficam garantidas.
          </span>
        </div>
      )}

      {erro && (
        <div className="mb-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
          {erro}
        </div>
      )}

      <Card className="p-6">
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Nome completo">
            <input
              className={inputCls}
              value={nome}
              onChange={(e) => setNome(e.target.value)}
            />
          </Field>
          <Field label="E-mail">
            <div className="flex items-center gap-2">
              <input className={inputCls} value={self?.email ?? ""} disabled />
              {self?.emailVerified && (
                <span title="E-mail verificado">
                  <BadgeCheck size={18} className="shrink-0 text-emerald-600" />
                </span>
              )}
            </div>
          </Field>
        </div>
        <div className="mt-4 flex items-center gap-3">
          <Button onClick={salvar} disabled={salvando || nome.trim().length < 3}>
            {salvando && <Loader2 size={14} className="animate-spin" />}
            Salvar alterações
          </Button>
          {salvo && <span className="text-sm text-emerald-600">Salvo ✓</span>}
        </div>
      </Card>

      <Card className="mt-4 p-6">
        <h3 className="flex items-center gap-2 font-semibold text-ink-900">
          <Store size={18} /> Lojas vinculadas
        </h3>
        <p className="mt-1 text-sm text-slate-500">
          Sua conta é única — cada loja onde você aluga cria um vínculo aqui.
        </p>
        {self && self.lojas.length === 0 && (
          <p className="mt-3 rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-500">
            Nenhum vínculo ainda. Ele é criado na sua primeira reserva com uma
            loja.
          </p>
        )}
        <div className="mt-3 space-y-2">
          {self?.lojas.map((loja) => (
            <div
              key={loja.tenantId}
              className="flex items-center gap-2 rounded-lg bg-slate-50 px-3 py-2 text-sm"
            >
              <span className="flex-1 font-medium text-slate-700">
                {loja.nome}
              </span>
              <Badge tone="brand">{loja.slug}</Badge>
            </div>
          ))}
        </div>
      </Card>

      <div className="mt-6 text-center">
        <button
          onClick={() => signOut({ callbackUrl: "/login" })}
          className="inline-flex items-center gap-1 text-xs text-slate-400 hover:text-slate-600"
        >
          <LogOut size={12} /> Sair da conta
        </button>
      </div>
    </div>
  );
}
