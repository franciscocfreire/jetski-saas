"use client";

import { withBase } from "@/lib/base";
import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { useSession } from "next-auth/react";
import { sairDaConta } from "@/lib/logout";
import {
  Button,
  Card,
  Field,
  SectionTitle,
  inputCls,
  Badge,
} from "@/components/ui";
import { getSelf, updateSelf, updateContatoLoja, ApiError, type CustomerSelf, type IdentidadeCliente, type VinculoLoja } from "@/lib/api";
import { Loader2, LogOut, MailWarning, Store, BadgeCheck, IdCard, Briefcase, ExternalLink } from "lucide-react";
import { maskCpf } from "@/lib/masks";
import { PhoneInput } from "@/components/PhoneInput";
import { useToast } from "@/components/Toast";

/**
 * Perfil REAL (P0): dados da identidade global + lojas vinculadas, direto do
 * backend (/v1/customers/self). As demais telas de conta seguem mock até P1.
 */
export default function PerfilPage() {
  const { data: session, status } = useSession();
  const router = useRouter();

  const { toast } = useToast();
  const [self, setSelf] = useState<CustomerSelf | null>(null);
  const [nome, setNome] = useState("");
  const [ident, setIdent] = useState<IdentidadeCliente>({});
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
      setIdent(dados.identidade ?? {});
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
      await updateSelf(session.accessToken, nome, ident);
      setSalvo(true);
      toast("Perfil salvo");
      const dados = await getSelf(session.accessToken);
      setSelf(dados);
      setIdent(dados.identidade ?? {});
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : "Não foi possível salvar.";
      setErro(msg);
      toast(msg, "erro");
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
          <IdCard size={18} /> Documento de identidade
        </h3>
        <p className="mt-1 text-sm text-slate-500">
          Vale para todas as lojas — endereço e telefone são pedidos por loja,
          no fluxo da reserva.
        </p>
        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          <Field
            label="CPF"
            hint={self?.identidade?.cpf ? "Definido uma única vez — fale com a loja para corrigir" : "Você também poderá entrar com o CPF"}
          >
            <input
              className={inputCls}
              inputMode="numeric"
              value={maskCpf(ident.cpf ?? "")}
              onChange={(e) => setIdent({ ...ident, cpf: maskCpf(e.target.value) })}
              placeholder="000.000.000-00"
              disabled={!!self?.identidade?.cpf}
            />
          </Field>
          <Field label="Data de nascimento">
            <input
              type="date"
              className={inputCls}
              value={ident.dataNascimento ?? ""}
              onChange={(e) => setIdent({ ...ident, dataNascimento: e.target.value })}
            />
          </Field>
          <Field label="RG / Identidade">
            <input
              className={inputCls}
              value={ident.rg ?? ""}
              onChange={(e) => setIdent({ ...ident, rg: e.target.value })}
            />
          </Field>
          <Field label="Órgão emissor">
            <input
              className={inputCls}
              value={ident.orgaoEmissor ?? ""}
              onChange={(e) => setIdent({ ...ident, orgaoEmissor: e.target.value })}
              placeholder="SSP/UF"
            />
          </Field>
          <Field label="Nacionalidade">
            <input
              className={inputCls}
              value={ident.nacionalidade ?? ""}
              onChange={(e) => setIdent({ ...ident, nacionalidade: e.target.value })}
              placeholder="Brasileira"
            />
          </Field>
          <Field label="Naturalidade (Cidade/UF)">
            <input
              className={inputCls}
              value={ident.naturalidade ?? ""}
              onChange={(e) => setIdent({ ...ident, naturalidade: e.target.value })}
            />
          </Field>
        </div>
        <label className="mt-3 flex items-center gap-2 text-sm text-slate-700">
          <input
            type="checkbox"
            checked={ident.estrangeiro ?? false}
            onChange={(e) => setIdent({ ...ident, estrangeiro: e.target.checked })}
          />
          Sou estrangeiro(a)
        </label>
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
            <LojaRow
              key={loja.tenantId}
              loja={loja}
              token={session?.accessToken}
            />
          ))}
        </div>
      </Card>

      {session?.roles?.some((r) =>
        ["ADMIN_TENANT", "GERENTE", "OPERADOR", "FINANCEIRO", "MECANICO", "VENDEDOR", "PLATFORM_ADMIN"].includes(r)
      ) && (
        <Card className="mt-4 p-6">
          <h3 className="flex items-center gap-2 font-semibold text-ink-900">
            <Briefcase size={18} /> Acesso da equipe
          </h3>
          <p className="mt-1 text-sm text-slate-500">
            Sua conta também tem papel de staff — o painel da loja fica no
            Backoffice.
          </p>
          {/* <a> puro: URL absoluta do host, fora do basePath /portal */}
          <a
            href="/dashboard"
            className="mt-3 inline-flex items-center gap-2 rounded-xl border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            Abrir o Backoffice <ExternalLink size={14} />
          </a>
        </Card>
      )}

      <div className="mt-6 text-center">
        <button
          onClick={() => sairDaConta(session?.idToken)}
          className="inline-flex items-center gap-1 text-xs text-slate-400 hover:text-slate-600"
        >
          <LogOut size={12} /> Sair da conta
        </button>
      </div>
    </div>
  );
}

/** Linha da loja com telefone/WhatsApp editável — contato é POR LOJA. */
function LojaRow({ loja, token }: { loja: VinculoLoja; token?: string }) {
  const { toast } = useToast();
  const [tel, setTel] = useState(loja.telefone ?? loja.whatsapp ?? "");
  const [salvandoTel, setSalvandoTel] = useState(false);
  const [okTel, setOkTel] = useState(false);
  const original = loja.telefone ?? loja.whatsapp ?? "";

  async function salvarTel() {
    if (!token) return;
    setSalvandoTel(true);
    setOkTel(false);
    try {
      await updateContatoLoja(token, loja.tenantId, tel);
      setOkTel(true);
      toast(`Contato salvo na ${loja.nome}`);
    } catch {
      // mantém o valor digitado; usuário tenta de novo
    } finally {
      setSalvandoTel(false);
    }
  }

  return (
    <div className="rounded-lg bg-slate-50 px-3 py-2 text-sm">
      <div className="flex items-center gap-2">
        <span className="flex-1 font-medium text-slate-700">{loja.nome}</span>
        <Badge tone="brand">{loja.slug}</Badge>
      </div>
      <div className="mt-2 flex items-center gap-2">
        <PhoneInput
          className="max-w-[300px] flex-1"
          value={tel}
          onChange={(v) => { setTel(v); setOkTel(false); }}
        />
        {tel !== original && !okTel && (
          <Button size="sm" variant="outline" onClick={salvarTel} disabled={salvandoTel}>
            {salvandoTel ? <Loader2 size={13} className="animate-spin" /> : "Salvar"}
          </Button>
        )}
        {okTel && <span className="text-xs text-emerald-600">Salvo ✓</span>}
      </div>
    </div>
  );
}
