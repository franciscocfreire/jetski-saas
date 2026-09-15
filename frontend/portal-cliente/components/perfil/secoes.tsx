"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { signIn } from "next-auth/react";
import { ArrowLeft, Award, Copy, FileDown, KeyRound, Loader2, MonitorSmartphone, ShieldCheck, Smartphone, Trash2 } from "lucide-react";
import { withBase } from "@/lib/base";
import { Badge, Button, Card, SectionTitle } from "@/components/ui";
import {
  getAnexoLoja, getAnexosLoja, getCredentials, getHabilitacaoDocumento, getHabilitacoes,
  getTrustedDevices, revokeDevice, updateContatoLoja, uploadAnexoLoja, ApiError,
  type HabilitacaoTemporaria, type SecondFactorCredential, type TrustedDevice, type VinculoLoja,
} from "@/lib/api";
import { UploadTile } from "@/components/UploadTile";
import { PhoneInput } from "@/components/PhoneInput";
import { useToast } from "@/components/Toast";

/** Moldura das subpáginas do Perfil: voltar ao hub + título. */
export function PerfilSubpagina({ titulo, sub, carregando, erro, children }: {
  titulo: string; sub?: string; carregando: boolean; erro?: string | null;
  children: React.ReactNode;
}) {
  return (
    <div className="mx-auto max-w-2xl">
      <Link
        href="/conta/perfil"
        className="mb-3 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-ink-900"
      >
        <ArrowLeft size={14} /> Perfil
      </Link>
      <SectionTitle sub={sub}>{titulo}</SectionTitle>
      {erro && (
        <div className="mb-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{erro}</div>
      )}
      {carregando ? (
        <div className="flex justify-center py-20 text-slate-400">
          <Loader2 className="animate-spin" />
        </div>
      ) : (
        <div className="space-y-4">{children}</div>
      )}
    </div>
  );
}

/**
 * Ações de 2FA via Keycloak (AIA). Ações que reduzem segurança ou cadastram
 * fator levam max_age=0 → reautenticação (step-up). Volta para callbackPath.
 */
export function acaoSeguranca(kcAction: string, stepUp: boolean, callbackPath: string) {
  return signIn(
    "keycloak",
    { callbackUrl: withBase(callbackPath) },
    stepUp ? { kc_action: kcAction, max_age: "0" } : { kc_action: kcAction },
  );
}

/** Linha da loja com telefone/WhatsApp editável — contato é POR LOJA. */
export function LojaRow({ loja, token }: { loja: VinculoLoja; token?: string }) {
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
export function DocumentosLoja({ loja, token, mostrarCabecalho }: {
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
        <UploadTile rotulo="Comprovante de residência (opcional)"
          presente={presentes.includes("COMPROVANTE_RESIDENCIA")}
          previewUrl={previews.COMPROVANTE_RESIDENCIA}
          onFile={(d) => enviar("COMPROVANTE_RESIDENCIA", d)} />
      </div>
    </div>
  );
}

export const fmtDataBr = (iso: string) =>
  new Date(iso.length === 10 ? iso + "T12:00:00" : iso).toLocaleDateString("pt-BR");

/** Habilitações temporárias (CHA-MTA-E) emitidas — validade 30 dias, GRU como referência na Marinha. */
export function MinhasHabilitacoes({ token }: { token: string }) {
  const { toast } = useToast();
  const [itens, setItens] = useState<HabilitacaoTemporaria[]>([]);

  useEffect(() => {
    getHabilitacoes(token)
      .then(setItens)
      .catch(() => { /* sem lista — card não renderiza */ });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (itens.length === 0) return null;

  return (
    <Card className="p-6">
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
                Emitida em {fmtDataBr(h.emitidaEm)} ·{" "}
                {h.vigente ? `válida até ${fmtDataBr(h.validaAte)}` : `expirou em ${fmtDataBr(h.validaAte)}`}
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
 */
export function SegurancaCard({ token, callbackPath }: { token: string; callbackPath: string }) {
  const [fatores, setFatores] = useState<SecondFactorCredential[] | null>(null);

  useEffect(() => {
    getCredentials(token).then(setFatores).catch(() => setFatores([]));
  }, [token]);

  const acao = (kcAction: string, stepUp = false) => acaoSeguranca(kcAction, stepUp, callbackPath);

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
    <Card className="p-6">
      <div className="flex items-center justify-between gap-2">
        <h3 className="flex items-center gap-2 font-semibold text-ink-900">
          <ShieldCheck size={18} /> Verificação em duas etapas
        </h3>
        {fatores !== null &&
          (ativo ? <Badge tone="green">Ativado</Badge> : <Badge tone="slate">Desativado</Badge>)}
      </div>
      <p className="mt-1 text-sm text-slate-500">
        Opcional: além do código por e-mail, senha ou Google, pedimos um fator
        só seu — app autenticador ou passkey.
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
            <Button variant="outline" onClick={() => acao("CONFIGURE_TOTP", true)}>
              <Smartphone size={16} className="mr-2" /> Adicionar app autenticador
            </Button>
            <Button variant="outline" onClick={() => acao("webauthn-register", true)}>
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
        <div className="mt-4 flex flex-wrap gap-2">
          <Button onClick={() => acao("CONFIGURE_TOTP", true)}>
            <Smartphone size={16} className="mr-2" /> Ativar com app autenticador
          </Button>
          <Button variant="outline" onClick={() => acao("webauthn-register", true)}>
            <KeyRound size={16} className="mr-2" /> Ativar com passkey
          </Button>
        </div>
      )}
    </Card>
  );
}

/**
 * Dispositivos confiáveis (trusted device): navegadores onde o 2FA foi
 * dispensado por 30 dias. Revogar = DELETE simples (aumento de segurança,
 * sem step-up); some da lista e volta a pedir 2FA no próximo login.
 */
export function DispositivosCard({ token }: { token: string }) {
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

  // sem dispositivos → não renderiza (não polui a página)
  if (devices !== null && devices.length === 0) return null;

  return (
    <Card className="p-6">
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
