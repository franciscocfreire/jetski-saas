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
import { getSelf, updateSelf, updateContatoLoja, getAnexosLoja, getAnexoLoja, uploadAnexoLoja, getHabilitacoes, getHabilitacaoDocumento, getCredentials, getTrustedDevices, revokeDevice, ApiError, isCpfEmUso, type CustomerSelf, type IdentidadeCliente, type VinculoLoja, type HabilitacaoTemporaria, type SecondFactorCredential, type TrustedDevice } from "@/lib/api";
import { UploadTile } from "@/components/UploadTile";
import { Award, Copy, FileDown, Loader2, LogOut, MailWarning, Store, BadgeCheck, IdCard, Briefcase, ExternalLink, ShieldCheck, Smartphone, KeyRound, MonitorSmartphone, Trash2 } from "lucide-react";
import { signIn } from "next-auth/react";
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
      if (isCpfEmUso(e)) {
        // CPF pertence a outra conta → fluxo de unificação em /conta/cpf
        router.push("/conta/cpf");
        return;
      }
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

      {session?.accessToken && (
        <SegurancaCard token={session.accessToken} />
      )}

      {session?.accessToken && (
        <DispositivosCard token={session.accessToken} />
      )}

      {session?.accessToken && (
        <MinhasHabilitacoes token={session.accessToken} />
      )}

      {self && self.lojas.length > 0 && session?.accessToken && (
        <Card className="mt-4 p-6">
          <h3 className="flex items-center gap-2 font-semibold text-ink-900">
            <IdCard size={18} /> Meus documentos
          </h3>
          <p className="mt-1 text-sm text-slate-500">
            Estas fotos são usadas apenas para a emissão da sua habilitação
            (NORMAM-212/DPC) e ficam visíveis só para você e para a loja.
          </p>
          <div className="mt-3 space-y-4">
            {self.lojas.map((loja) => (
              <DocumentosLoja
                key={loja.tenantId}
                loja={loja}
                token={session.accessToken}
                mostrarCabecalho={self.lojas.length > 1}
              />
            ))}
          </div>
        </Card>
      )}

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

/** Documentos (fotos) do cliente NESTA loja — anexos são tenant-scoped. */
function DocumentosLoja({ loja, token, mostrarCabecalho }: {
  loja: VinculoLoja; token: string; mostrarCabecalho: boolean;
}) {
  const { toast } = useToast();
  const [presentes, setPresentes] = useState<string[]>([]);
  const [previews, setPreviews] = useState<Record<string, string>>({});

  useEffect(() => {
    getAnexosLoja(token, loja.tenantId)
      .then(setPresentes)
      .catch(() => { /* sem lista — tiles ficam vazios */ });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loja.tenantId]);

  useEffect(() => {
    (["IDENTIDADE", "SELFIE", "COMPROVANTE_RESIDENCIA"] as const).forEach((tipo) => {
      if (presentes.includes(tipo) && !previews[tipo]) {
        getAnexoLoja(token, loja.tenantId, tipo)
          .then((url) => setPreviews((p) => ({ ...p, [tipo]: url })))
          .catch(() => { /* sem preview — o tile mostra só o check */ });
      }
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [presentes]);

  async function enviar(tipo: "IDENTIDADE" | "SELFIE" | "COMPROVANTE_RESIDENCIA", dataUrl: string) {
    try {
      const tipos = await uploadAnexoLoja(token, loja.tenantId, tipo, dataUrl);
      setPresentes(tipos);
      setPreviews((p) => ({ ...p, [tipo]: dataUrl }));
      toast("Documento atualizado.");
    } catch (e) {
      toast(e instanceof ApiError ? e.message : "Não foi possível enviar.", "erro");
    }
  }

  return (
    <div>
      {mostrarCabecalho && (
        <p className="mb-2 text-sm font-medium text-slate-700">{loja.nome}</p>
      )}
      <div className="grid gap-3 sm:grid-cols-3">
        <UploadTile rotulo="Identidade (RG/CNH)" presente={presentes.includes("IDENTIDADE")}
          previewUrl={previews.IDENTIDADE}
          onFile={(d) => enviar("IDENTIDADE", d)} />
        <UploadTile rotulo="Selfie com documento" presente={presentes.includes("SELFIE")}
          previewUrl={previews.SELFIE} camera="user"
          onFile={(d) => enviar("SELFIE", d)} />
        <UploadTile rotulo="Comprovante de residência"
          presente={presentes.includes("COMPROVANTE_RESIDENCIA")}
          previewUrl={previews.COMPROVANTE_RESIDENCIA}
          onFile={(d) => enviar("COMPROVANTE_RESIDENCIA", d)} />
      </div>
    </div>
  );
}

/** Habilitações temporárias (CHA-MTA-E) emitidas — validade 30 dias, GRU como referência na Marinha. */
function MinhasHabilitacoes({ token }: { token: string }) {
  const { toast } = useToast();
  const [itens, setItens] = useState<HabilitacaoTemporaria[]>([]);

  useEffect(() => {
    getHabilitacoes(token)
      .then(setItens)
      .catch(() => { /* sem lista — card não renderiza */ });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (itens.length === 0) return null;

  const fmt = (iso: string) =>
    new Date(iso.length === 10 ? iso + "T12:00:00" : iso).toLocaleDateString("pt-BR");

  return (
    <Card className="mt-4 p-6">
      <h3 className="flex items-center gap-2 font-semibold text-ink-900">
        <Award size={18} /> Minhas habilitações
      </h3>
      <p className="mt-1 text-sm text-slate-500">
        Habilitações temporárias (CHA-MTA-E) valem 30 dias a partir da emissão.
        Use o número da GRU para consultar o estado junto à Marinha.
      </p>
      <div className="mt-3 space-y-2">
        {itens.map((h) => (
          <div key={h.reservaId}
            className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-slate-200 px-4 py-3">
            <div className="min-w-0">
              <p className="text-sm font-medium text-ink-900">{h.lojaNome}</p>
              <p className="text-xs text-slate-500">
                Emitida em {fmt(h.emitidaEm)} ·{" "}
                {h.vigente ? `válida até ${fmt(h.validaAte)}` : `expirou em ${fmt(h.validaAte)}`}
              </p>
              <div className="mt-1 flex flex-wrap items-center gap-3">
                <button
                  type="button"
                  onClick={() => {
                    navigator.clipboard.writeText(h.gruNumero);
                    toast("Número da GRU copiado");
                  }}
                  className="flex items-center gap-1 text-xs font-medium text-brand-600"
                >
                  <Copy size={12} /> GRU {h.gruNumero}
                </button>
                {h.confirmada && (
                  <button
                    type="button"
                    onClick={async () => {
                      try {
                        const url = await getHabilitacaoDocumento(token, h.reservaId);
                        window.open(url, "_blank");
                      } catch {
                        toast("Não foi possível baixar a confirmação.", "erro");
                      }
                    }}
                    className="flex items-center gap-1 text-xs font-medium text-brand-600"
                  >
                    <FileDown size={12} /> Baixar confirmação (PDF)
                  </button>
                )}
              </div>
              {h.vigente && !h.confirmada && (
                <p className="mt-1 text-xs text-amber-600">
                  A loja ainda aguarda a confirmação da Marinha — quando chegar, esta
                  habilitação poderá ser usada em novas reservas.
                </p>
              )}
            </div>
            <Badge tone={!h.vigente ? "slate" : h.confirmada ? "green" : "amber"}>
              {!h.vigente ? "Expirada" : h.confirmada ? "Confirmada · Vigente" : "Aguardando confirmação"}
            </Badge>
          </div>
        ))}
      </div>
    </Card>
  );
}

/**
 * Verificação em duas etapas (identidade única no Keycloak). Toggle explícito:
 * cadastrar um fator ativa; "Desativar" remove todos (RA custom mj-2fa-disable).
 * Ações que reduzem segurança (remover fator, desativar) levam max_age=0 → o
 * Keycloak reautentica e desafia o próprio fator (step-up).
 */
function SegurancaCard({ token }: { token: string }) {
  const [fatores, setFatores] = useState<SecondFactorCredential[] | null>(null);

  useEffect(() => {
    getCredentials(token).then(setFatores).catch(() => setFatores([]));
  }, [token]);

  const acao = (kcAction: string, stepUp = false) =>
    signIn(
      "keycloak",
      { callbackUrl: withBase("/conta/perfil") },
      stepUp ? { kc_action: kcAction, max_age: "0" } : { kc_action: kcAction },
    );

  const desativar = () => {
    if (
      window.confirm(
        "Desativar a verificação em duas etapas? Todos os fatores serão removidos e você precisará confirmar sua identidade agora.",
      )
    ) {
      acao("mj-2fa-disable", true);
    }
  };

  const rotulo = (tipo: string) =>
    tipo === "otp" ? "Aplicativo autenticador" : "Passkey / chave de segurança";

  const ativo = !!fatores && fatores.length > 0;

  return (
    <Card className="mt-4 p-6">
      <div className="flex items-center justify-between gap-2">
        <h3 className="flex items-center gap-2 font-semibold text-ink-900">
          <ShieldCheck size={18} /> Segurança
        </h3>
        {fatores !== null &&
          (ativo ? <Badge tone="green">Ativado</Badge> : <Badge tone="slate">Desativado</Badge>)}
      </div>
      <p className="mt-1 text-sm text-slate-500">
        Verificação em duas etapas (opcional): além do código por e-mail, senha
        ou Google, pedimos um fator só seu — app autenticador ou passkey.
      </p>
      {fatores === null ? (
        <div className="mt-3 flex items-center gap-2 text-sm text-slate-400">
          <Loader2 size={16} className="animate-spin" /> Carregando…
        </div>
      ) : ativo ? (
        <>
          <ul className="mt-3 space-y-2">
            {fatores.map((f) => (
              <li
                key={f.id}
                className="flex items-center justify-between rounded-xl border border-slate-200 px-4 py-3"
              >
                <div className="flex items-center gap-3">
                  {f.type === "otp" ? (
                    <Smartphone size={18} className="text-slate-400" />
                  ) : (
                    <KeyRound size={18} className="text-slate-400" />
                  )}
                  <div>
                    <p className="text-sm font-medium text-ink-900">
                      {f.userLabel || rotulo(f.type)}
                    </p>
                    <p className="text-xs text-slate-500">
                      {rotulo(f.type)}
                      {f.createdDate
                        ? ` · desde ${new Date(f.createdDate).toLocaleDateString("pt-BR")}`
                        : ""}
                    </p>
                  </div>
                </div>
                {/* remover fator = downgrade → step-up */}
                <Button variant="outline" onClick={() => acao(`delete_credential:${f.id}`, true)}>
                  Remover
                </Button>
              </li>
            ))}
          </ul>
          <div className="mt-4 flex flex-wrap gap-2">
            <Button variant="outline" onClick={() => acao("CONFIGURE_TOTP")}>
              <Smartphone size={16} className="mr-2" /> Adicionar app autenticador
            </Button>
            <Button variant="outline" onClick={() => acao("webauthn-register")}>
              <KeyRound size={16} className="mr-2" /> Adicionar passkey
            </Button>
            <Button
              variant="outline"
              className="border-rose-300 text-rose-700 hover:bg-rose-50"
              onClick={desativar}
            >
              Desativar verificação em duas etapas
            </Button>
          </div>
        </>
      ) : (
        <>
          <p className="mt-3 text-sm text-slate-500">
            Desativada — seu login usa só a primeira etapa. Ative para exigir também
            um aplicativo autenticador ou uma passkey.
          </p>
          <div className="mt-4 flex flex-wrap gap-2">
            <Button onClick={() => acao("CONFIGURE_TOTP")}>
              <Smartphone size={16} className="mr-2" /> Ativar com app autenticador
            </Button>
            <Button variant="outline" onClick={() => acao("webauthn-register")}>
              <KeyRound size={16} className="mr-2" /> Ativar com passkey
            </Button>
          </div>
        </>
      )}
    </Card>
  );
}

/**
 * Dispositivos confiáveis (trusted device): navegadores onde o 2FA foi
 * dispensado por 30 dias. Revogar = DELETE simples (aumento de segurança,
 * sem step-up); some da lista e volta a pedir 2FA no próximo login.
 */
function DispositivosCard({ token }: { token: string }) {
  const [devices, setDevices] = useState<TrustedDevice[] | null>(null);
  const [revogando, setRevogando] = useState<string | null>(null);
  const { toast } = useToast();

  const carregar = useCallback(() => {
    getTrustedDevices(token).then(setDevices).catch(() => setDevices([]));
  }, [token]);

  useEffect(() => {
    carregar();
  }, [carregar]);

  async function revogar(id: string) {
    setRevogando(id);
    try {
      await revokeDevice(token, id);
      toast("Dispositivo revogado");
      carregar();
    } catch {
      toast("Não foi possível revogar", "erro");
    } finally {
      setRevogando(null);
    }
  }

  // sem dispositivos → não renderiza (não polui o perfil)
  if (devices !== null && devices.length === 0) return null;

  return (
    <Card className="mt-4 p-6">
      <h3 className="flex items-center gap-2 font-semibold text-ink-900">
        <MonitorSmartphone size={18} /> Dispositivos confiáveis
      </h3>
      <p className="mt-1 text-sm text-slate-500">
        Navegadores onde você marcou &quot;não pedir a verificação&quot;. Revogue os que
        não reconhece — voltam a pedir o código.
      </p>
      {devices === null ? (
        <div className="mt-3 flex items-center gap-2 text-sm text-slate-400">
          <Loader2 size={16} className="animate-spin" /> Carregando…
        </div>
      ) : (
        <ul className="mt-3 space-y-2">
          {devices.map((d) => (
            <li
              key={d.id}
              className="flex items-center justify-between rounded-xl border border-slate-200 px-4 py-3"
            >
              <div className="flex items-center gap-3">
                <MonitorSmartphone size={18} className="text-slate-400" />
                <div>
                  <p className="text-sm font-medium text-ink-900">{d.userLabel || "Navegador"}</p>
                  <p className="text-xs text-slate-500">
                    {d.createdDate ? `desde ${new Date(d.createdDate).toLocaleDateString("pt-BR")}` : ""}
                    {d.lastUsedAt ? ` · último uso ${new Date(d.lastUsedAt * 1000).toLocaleDateString("pt-BR")}` : ""}
                  </p>
                </div>
              </div>
              <Button variant="outline" disabled={revogando === d.id} onClick={() => revogar(d.id)}>
                <Trash2 size={16} className="mr-2" /> Revogar
              </Button>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
