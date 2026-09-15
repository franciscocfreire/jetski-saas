"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Briefcase, ChevronRight, ExternalLink, IdCard, Loader2, LogOut, ShieldCheck, Store, User } from "lucide-react";
import { Badge, Button, Card } from "@/components/ui";
import {
  getAnexosLoja, getCredentials, getHabilitacoes,
  type HabilitacaoTemporaria, type SecondFactorCredential,
} from "@/lib/api";
import { sairDaConta } from "@/lib/logout";
import { usePerfil } from "@/components/perfil/usePerfil";
import { acaoSeguranca, fmtDataBr } from "@/components/perfil/secoes";
import {
  cpfMascarado, dadosCompletos, fmtData, iniciais, passosCadastro, tiposPendentes,
} from "@/components/perfil/completude";

const PAPEIS_STAFF = ["ADMIN_TENANT", "GERENTE", "OPERADOR", "FINANCEIRO", "MECANICO", "VENDEDOR", "PLATFORM_ADMIN"];

/**
 * Perfil — hub (redesenho B, set/2026): faixa de identidade com o checklist
 * para emissão + um resumo por seção. A edição vive nas subpáginas
 * /conta/perfil/{dados,documentos,seguranca,lojas}.
 */
export default function PerfilPage() {
  const { session, token, self, erro, carregando } = usePerfil();
  const [habs, setHabs] = useState<HabilitacaoTemporaria[]>([]);
  const [fatores, setFatores] = useState<SecondFactorCredential[] | null>(null);
  // tenantId → tipos enviados; null = lista daquela loja não carregou
  const [anexos, setAnexos] = useState<Record<string, string[] | null> | null>(null);

  useEffect(() => {
    if (!token) return;
    getHabilitacoes(token).then(setHabs).catch(() => { /* resumo sem habilitação */ });
    getCredentials(token).then(setFatores).catch(() => setFatores([]));
  }, [token]);

  const lojaIds = self?.lojas.map((l) => l.tenantId).join(",");
  useEffect(() => {
    if (!token || !self) return;
    Promise.all(
      self.lojas.map((l) =>
        getAnexosLoja(token, l.tenantId)
          .then((tipos) => [l.tenantId, tipos] as const)
          .catch(() => [l.tenantId, null] as const),
      ),
    ).then((pares) => setAnexos(Object.fromEntries(pares)));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, lojaIds]);

  if (carregando) {
    return (
      <div className="flex justify-center py-20 text-slate-400">
        <Loader2 className="animate-spin" />
      </div>
    );
  }
  if (!self) {
    return (
      <div className="mx-auto max-w-2xl rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
        {erro ?? "Não foi possível carregar o perfil."}
      </div>
    );
  }

  const ident = self.identidade ?? {};
  const cpf = cpfMascarado(ident.cpf);
  const passos = anexos ? passosCadastro(self, anexos) : null;
  const feitos = passos?.filter((p) => p.ok).length ?? 0;
  const proximo = passos?.find((p) => !p.ok);
  const pendentes = anexos ? tiposPendentes(self, anexos) : [];
  const habPrincipal = habs.find((h) => h.vigente) ?? habs[0];
  const doisFatores = fatores === null ? null : fatores.length > 0;
  const staff = session?.roles?.some((r) => PAPEIS_STAFF.includes(r));

  const statusDados = dadosCompletos(self)
    ? <Status tom="green">Completo</Status>
    : <Status tom="amber">Incompleto</Status>;
  const statusDocs = self.lojas.length === 0
    ? <Status tom="slate">Na 1ª reserva</Status>
    : anexos === null
      ? null
      : pendentes.length > 0
        ? <Status tom="amber">{pendentes.length} pendente{pendentes.length > 1 ? "s" : ""}</Status>
        : <Status tom="green">Completo</Status>;
  const statusSeg = doisFatores === null
    ? null
    : doisFatores
      ? <Status tom="green">2FA ativado</Status>
      : <Status tom="slate">2FA desativado</Status>;

  return (
    <div className="mx-auto max-w-5xl space-y-5 md:space-y-6">
      {/* Faixa de identidade — navy é a âncora da marca */}
      <div className="relative overflow-hidden rounded-[20px] bg-brand-900 px-5 py-6 text-[#efeae0] md:px-8 md:py-7">
        <svg
          aria-hidden
          className="pointer-events-none absolute -bottom-8 -right-10 hidden opacity-[.18] md:block"
          width="420" height="260" viewBox="0 0 64 40" fill="none" strokeLinecap="round" strokeWidth="2"
        >
          <path d="M5 15.5 C 15 15.5, 19 6, 30 6 C 39.5 6, 42 12.5, 59 10.5" stroke="#C9A24B" />
          <path d="M5 29 C 13 29, 18.5 20.5, 28 20.5 C 37 20.5, 42.5 27.5, 59 25" stroke="#F8F4EA" />
        </svg>
        <div className="relative flex flex-col gap-5 md:flex-row md:items-center md:gap-6">
          <div className="flex min-w-0 flex-1 items-center gap-4">
            <div className="grid h-14 w-14 shrink-0 place-items-center rounded-full border-2 border-gold-500 font-display text-xl font-semibold md:h-[72px] md:w-[72px] md:text-[26px]">
              {iniciais(self.nome)}
            </div>
            <div className="min-w-0">
              <h1 className="font-display text-[21px] font-semibold leading-tight md:text-3xl">
                {self.nome}
              </h1>
              <p className="mt-1 flex flex-wrap gap-x-3 text-sm text-[#b9c4d2]">
                <span className="truncate">
                  {self.email}
                  {self.emailVerified ? " · verificado" : ""}
                </span>
                {cpf && <span>CPF {cpf}</span>}
              </p>
            </div>
          </div>

          {passos && (
            <div className="md:w-[300px]">
              <div className="flex justify-between text-[13px]">
                <span>{proximo ? "Cadastro para emissão" : "Pronto para emitir sua habilitação"}</span>
                <span className="font-semibold tabular-nums">
                  {feitos} de {passos.length}
                </span>
              </div>
              <div className="mt-2 h-1.5 rounded-full bg-[#efeae0]/15">
                <div
                  className="h-1.5 rounded-full bg-gold-300 transition-[width]"
                  style={{ width: `${(feitos / passos.length) * 100}%` }}
                />
              </div>
              {proximo && (
                <Link href={proximo.href} className="mt-2 hidden text-[13px] text-[#b9c4d2] hover:text-white md:block">
                  {proximo.acao} →
                </Link>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Próximo passo — no celular vira um aviso com ação */}
      {proximo && (
        <div className="flex items-center gap-3 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3.5 md:hidden">
          <p className="flex-1 text-sm font-medium text-amber-900">{proximo.acao}</p>
          <Button size="sm" href={proximo.href}>Resolver</Button>
        </div>
      )}

      {/* Celular: lista de seções */}
      <Card className="divide-y divide-slate-100 overflow-hidden md:hidden">
        <LinhaSecao href="/conta/perfil/dados" icone={<User size={20} />} titulo="Dados pessoais"
          detalhe="Nome, nascimento, documento" status={statusDados} />
        <LinhaSecao href="/conta/perfil/documentos" icone={<IdCard size={20} />} titulo="Documentos e habilitações"
          detalhe={habPrincipal ? `CHA ${habPrincipal.vigente ? `vigente até ${fmtDataBr(habPrincipal.validaAte)}` : "expirada"}` : "Fotos e CHA-MTA-E"}
          status={statusDocs} />
        <LinhaSecao href="/conta/perfil/seguranca" icone={<ShieldCheck size={20} />} titulo="Segurança"
          detalhe="Verificação em duas etapas" status={statusSeg} />
        <LinhaSecao href="/conta/perfil/lojas" icone={<Store size={20} />} titulo="Lojas vinculadas"
          detalhe={self.lojas.length ? self.lojas.map((l) => l.nome).join(", ") : "Nenhuma ainda"}
          status={<span className="text-xs font-semibold text-slate-500">{self.lojas.length}</span>} />
      </Card>

      {/* Desktop: cartões resumo */}
      <div className="hidden gap-5 md:grid md:grid-cols-2">
        <CartaoSecao icone={<User size={20} />} titulo="Dados pessoais" status={statusDados}
          rodape={<Link href="/conta/perfil/dados">Editar dados →</Link>}>
          <dl className="grid grid-cols-2 gap-x-5 gap-y-3.5">
            <Dado rotulo="Nascimento" valor={fmtData(ident.dataNascimento)} />
            <Dado rotulo="RG" valor={ident.rg ? [ident.rg, ident.orgaoEmissor].filter(Boolean).join(" · ") : null} />
            <Dado rotulo="Nacionalidade" valor={ident.nacionalidade} />
            <Dado rotulo="Naturalidade" valor={ident.naturalidade} />
          </dl>
        </CartaoSecao>

        <CartaoSecao icone={<IdCard size={20} />} titulo="Documentos e habilitações" status={statusDocs}
          rodape={<Link href="/conta/perfil/documentos">{pendentes.length ? "Enviar documentos →" : "Ver documentos →"}</Link>}>
          {self.lojas.length === 0 ? (
            <p className="text-sm text-slate-500">
              As fotos dos documentos são pedidas por loja, na sua primeira reserva.
            </p>
          ) : (
            <div className="grid grid-cols-3 gap-2.5">
              <TileDoc rotulo="Identidade" ok={anexos ? !pendentes.includes("IDENTIDADE") : null} />
              <TileDoc rotulo="Selfie" ok={anexos ? !pendentes.includes("SELFIE") : null} />
              <TileDoc rotulo="Comprovante" opcional
                ok={anexos ? self.lojas.every((l) => anexos[l.tenantId]?.includes("COMPROVANTE_RESIDENCIA")) : null} />
            </div>
          )}
          {habPrincipal && (
            <div className="mt-3 flex items-center justify-between gap-3 rounded-xl border border-slate-200 px-3.5 py-2.5">
              <div className="min-w-0">
                <p className="truncate text-sm font-medium text-ink-900">CHA-MTA-E · {habPrincipal.lojaNome}</p>
                <p className="text-xs text-slate-500">
                  {habPrincipal.vigente ? "Válida" : "Expirou"} até {fmtDataBr(habPrincipal.validaAte)} · GRU {habPrincipal.gruNumero}
                </p>
              </div>
              <Badge tone={!habPrincipal.vigente ? "slate" : habPrincipal.confirmada ? "green" : "amber"}>
                {!habPrincipal.vigente ? "Expirada" : habPrincipal.confirmada ? "Vigente" : "Aguardando Marinha"}
              </Badge>
            </div>
          )}
        </CartaoSecao>

        <CartaoSecao icone={<ShieldCheck size={20} />} titulo="Segurança" status={statusSeg}
          rodape={<Link href="/conta/perfil/seguranca">Gerenciar segurança →</Link>}>
          {doisFatores ? (
            <p className="text-sm leading-relaxed text-slate-600">
              Seu login pede um segundo fator: {fatores!.length} cadastrado{fatores!.length > 1 ? "s" : ""}.
            </p>
          ) : (
            <>
              <p className="text-sm leading-relaxed text-slate-600">
                Hoje seu login pede só a primeira etapa. Adicione um app autenticador
                ou uma passkey para proteger suas reservas e documentos.
              </p>
              <div className="mt-4 flex flex-wrap gap-2.5">
                <Button onClick={() => acaoSeguranca("CONFIGURE_TOTP", true, "/conta/perfil/seguranca")}>
                  Ativar verificação
                </Button>
                <Button variant="outline" onClick={() => acaoSeguranca("webauthn-register", true, "/conta/perfil/seguranca")}>
                  Usar passkey
                </Button>
              </div>
            </>
          )}
        </CartaoSecao>

        <CartaoSecao icone={<Store size={20} />} titulo="Lojas vinculadas"
          status={<span className="text-sm text-slate-500">{self.lojas.length} loja{self.lojas.length === 1 ? "" : "s"}</span>}
          rodape={self.lojas.length ? <Link href="/conta/perfil/lojas">Editar contatos →</Link> : undefined}>
          {self.lojas.length === 0 ? (
            <p className="text-sm text-slate-500">
              Sua conta é única — o vínculo com uma loja é criado na primeira reserva.
            </p>
          ) : (
            <ul className="divide-y divide-slate-100">
              {self.lojas.slice(0, 3).map((l) => {
                const tel = l.telefone ?? l.whatsapp;
                return (
                  <li key={l.tenantId} className="flex items-center gap-3 py-2.5 first:pt-0">
                    <span className="grid h-9 w-9 shrink-0 place-items-center rounded-[10px] bg-brand-50 text-[13px] font-semibold text-brand-600">
                      {iniciais(l.nome)}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-ink-900">{l.nome}</p>
                      <p className="text-xs text-slate-500">{tel ? `Contato ${tel}` : "Sem telefone nesta loja"}</p>
                    </div>
                  </li>
                );
              })}
              {self.lojas.length > 3 && (
                <li className="pt-2.5 text-xs text-slate-500">+ {self.lojas.length - 3} loja(s)</li>
              )}
            </ul>
          )}
        </CartaoSecao>
      </div>

      {staff && (
        <Card className="flex flex-wrap items-center gap-3 p-5">
          <Briefcase size={18} className="text-brand-600" />
          <p className="flex-1 text-sm text-slate-600">
            Sua conta também tem papel de equipe — o painel da loja fica no Backoffice.
          </p>
          {/* <a> puro: URL absoluta do host, fora do basePath /portal */}
          <a
            href="/dashboard"
            className="inline-flex items-center gap-2 rounded-xl border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            Abrir o Backoffice <ExternalLink size={14} />
          </a>
        </Card>
      )}

      <div className="text-center">
        <button
          onClick={() => sairDaConta()}
          className="inline-flex items-center gap-1 text-xs text-slate-400 hover:text-slate-600"
        >
          <LogOut size={12} /> Sair da conta
        </button>
      </div>
    </div>
  );
}

function Status({ tom, children }: { tom: "green" | "amber" | "slate"; children: React.ReactNode }) {
  const ponto = { green: "bg-emerald-500", amber: "bg-amber-500", slate: "bg-slate-400" }[tom];
  return (
    <Badge tone={tom} className="shrink-0">
      <span className={`h-1.5 w-1.5 rounded-full ${ponto}`} />
      {children}
    </Badge>
  );
}

function CartaoSecao({ icone, titulo, status, rodape, children }: {
  icone: React.ReactNode; titulo: string; status?: React.ReactNode;
  rodape?: React.ReactNode; children: React.ReactNode;
}) {
  return (
    <Card className="flex flex-col p-6">
      <div className="flex items-center gap-2.5">
        <span className="text-brand-600">{icone}</span>
        <h3 className="flex-1 font-semibold text-ink-900">{titulo}</h3>
        {status}
      </div>
      <div className="mt-4 flex-1">{children}</div>
      {rodape && (
        <div className="mt-4 border-t border-slate-100 pt-3.5 text-sm font-medium text-brand-500 hover:[&_a]:text-brand-600">
          {rodape}
        </div>
      )}
    </Card>
  );
}

function LinhaSecao({ href, icone, titulo, detalhe, status }: {
  href: string; icone: React.ReactNode; titulo: string; detalhe: string; status?: React.ReactNode;
}) {
  return (
    <Link href={href} className="flex min-h-[64px] items-center gap-3 px-4 py-3 active:bg-slate-50">
      <span className="shrink-0 text-brand-600">{icone}</span>
      <span className="min-w-0 flex-1">
        <span className="block text-[15px] font-medium text-ink-900">{titulo}</span>
        <span className="block truncate text-xs text-slate-500">{detalhe}</span>
      </span>
      {status}
      <ChevronRight size={16} className="shrink-0 text-slate-400" />
    </Link>
  );
}

function Dado({ rotulo, valor }: { rotulo: string; valor?: string | null }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-slate-500">{rotulo}</dt>
      <dd className={`truncate text-sm font-medium ${valor ? "text-ink-900" : "text-slate-400"}`}>
        {valor || "Não informado"}
      </dd>
    </div>
  );
}

/** ok: true enviado · false faltando · null carregando */
function TileDoc({ rotulo, ok, opcional }: { rotulo: string; ok: boolean | null; opcional?: boolean }) {
  if (ok) {
    return (
      <div className="rounded-xl bg-emerald-50 px-3 py-2.5 text-[13px] text-emerald-700">
        <p className="font-semibold">{rotulo}</p>
        <p>Enviado</p>
      </div>
    );
  }
  return (
    <div className="rounded-xl border-[1.5px] border-dashed border-slate-300 px-3 py-2 text-[13px] text-slate-700">
      <p className="font-semibold">{rotulo}</p>
      <p className={ok === null ? "text-slate-400" : opcional ? "text-slate-500" : "text-amber-700"}>
        {ok === null ? "…" : opcional ? "Opcional" : "Pendente"}
      </p>
    </div>
  );
}
