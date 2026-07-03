"use client";

import { useCallback, useEffect, useState } from "react";
import { useSession } from "next-auth/react";
import {
  CheckCircle2,
  Circle,
  ChevronDown,
  Loader2,
  UploadCloud,
  PlayCircle,
  FileCheck2,
  Landmark,
  IdCard,
  User,
  Copy,
  Check,
  Award,
} from "lucide-react";
import {
  getEmaEstado,
  getDadosPessoais,
  putDadosPessoais,
  uploadAnexoEma,
  putEmaFlags,
  gerarGruPix,
  verificarGru,
  enviarComprovanteGru,
  ApiError,
  type EmaEstado,
  type DadosPessoais,
  type EnderecoCliente,
} from "@/lib/api";
import { Button, Card, Field, inputCls } from "@/components/ui";
import { AddressForm, type Address } from "@/components/AddressForm";
import { PixQr } from "@/components/PixQr";
import { brl } from "@/lib/cn";

const VIDEOAULA_URL =
  "https://www.marinha.mil.br/dpc/sites/www.marinha.mil.br.dpc/files/videoaula-moto-aquatica.mp4";

/**
 * Caminho B — emissão da CHA-MTA-E pelo cliente (P3): dados pessoais,
 * documentos, videoaula, declarações e GRU. A emissão final e a demonstração
 * prática acontecem com a loja no dia.
 */
export function EmaWizard({ reservaId }: { reservaId: string }) {
  const { data: session } = useSession();
  const token = session?.accessToken;

  const [estado, setEstado] = useState<EmaEstado | null>(null);
  const [aberto, setAberto] = useState<number>(0);
  const [erro, setErro] = useState<string | null>(null);

  const recarregar = useCallback(async () => {
    if (!token) return;
    try {
      setEstado(await getEmaEstado(token, reservaId));
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Não foi possível carregar.");
    }
  }, [token, reservaId]);

  useEffect(() => {
    recarregar();
  }, [recarregar]);

  if (erro && !estado) return <p className="py-10 text-center text-slate-400">{erro}</p>;
  if (!estado || !token) {
    return (
      <div className="flex justify-center py-10 text-slate-400">
        <Loader2 className="animate-spin" />
      </div>
    );
  }

  const temIdentidade = estado.anexosPresentes.includes("IDENTIDADE");
  const temSelfie = estado.anexosPresentes.includes("SELFIE");
  const temComprovanteRes = estado.anexosPresentes.includes("COMPROVANTE_RESIDENCIA");
  const documentosOk = temIdentidade && temSelfie && (temComprovanteRes || estado.anexoResidencia);
  const declaracoesOk = estado.anexoSaude && estado.anexoRegras;

  const passos: { titulo: string; icone: React.ReactNode; ok: boolean }[] = [
    { titulo: "Dados pessoais", icone: <User size={16} />, ok: estado.dadosPessoaisCompletos },
    { titulo: "Documentos", icone: <IdCard size={16} />, ok: documentosOk },
    { titulo: "Videoaula da Marinha", icone: <PlayCircle size={16} />, ok: estado.videoaulaAssistida },
    { titulo: "Declarações", icone: <FileCheck2 size={16} />, ok: declaracoesOk },
    { titulo: "Taxa da Marinha (GRU)", icone: <Landmark size={16} />, ok: estado.gru.pago },
  ];

  if (estado.resolvida) {
    return (
      <Card className="flex flex-col items-center gap-3 p-8 text-center">
        <Award className="text-emerald-500" size={44} />
        <h2 className="text-lg font-semibold text-ink-900">CHA-MTA-E encaminhada!</h2>
        <p className="text-sm text-slate-500">
          GRU paga e requisitos enviados. A loja emite seus documentos e a
          demonstração prática acontece no embarque, com o instrutor.
        </p>
      </Card>
    );
  }

  return (
    <div className="space-y-3">
      {passos.map((p, i) => (
        <Card key={p.titulo} className="overflow-hidden">
          <button
            className="flex w-full items-center gap-3 p-4 text-left"
            onClick={() => setAberto(aberto === i ? -1 : i)}
          >
            {p.ok ? (
              <CheckCircle2 className="shrink-0 text-emerald-500" size={20} />
            ) : (
              <Circle className="shrink-0 text-slate-300" size={20} />
            )}
            <span className="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-slate-100 text-slate-600">
              {p.icone}
            </span>
            <span className="flex-1 font-semibold text-ink-900">
              {i + 1}. {p.titulo}
            </span>
            <ChevronDown
              size={16}
              className={`text-slate-400 transition ${aberto === i ? "rotate-180" : ""}`}
            />
          </button>
          {aberto === i && (
            <div className="border-t border-slate-100 p-4">
              {i === 0 && (
                <PassoDados token={token} reservaId={reservaId}
                  onSalvo={() => { recarregar(); setAberto(1); }} />
              )}
              {i === 1 && (
                <PassoDocumentos token={token} reservaId={reservaId} estado={estado}
                  onMudou={recarregar} onConcluido={() => setAberto(2)} />
              )}
              {i === 2 && (
                <PassoVideoaula token={token} reservaId={reservaId} assistida={estado.videoaulaAssistida}
                  onSalvo={() => { recarregar(); setAberto(3); }} />
              )}
              {i === 3 && (
                <PassoDeclaracoes token={token} reservaId={reservaId} estado={estado}
                  onSalvo={() => { recarregar(); setAberto(4); }} />
              )}
              {i === 4 && (
                <PassoGru token={token} reservaId={reservaId} estado={estado} onMudou={recarregar} />
              )}
            </div>
          )}
        </Card>
      ))}
    </div>
  );
}

// ============================ Passo 1 — Dados ============================

function PassoDados({ token, reservaId, onSalvo }:
  { token: string; reservaId: string; onSalvo: () => void }) {
  const [dados, setDados] = useState<DadosPessoais | null>(null);
  const [endereco, setEndereco] = useState<Address | null>(null);
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    getDadosPessoais(token, reservaId).then((d) => {
      setDados(d);
      if (d.endereco) setEndereco(d.endereco as Address);
    }).catch(() => setErro("Não foi possível carregar seus dados."));
  }, [token, reservaId]);

  if (!dados) return <Loader2 className="mx-auto animate-spin text-slate-400" />;

  const up = (patch: Partial<DadosPessoais>) => setDados({ ...dados, ...patch });

  async function salvar() {
    if (!dados) return;
    setSalvando(true);
    setErro(null);
    try {
      await putDadosPessoais(token, reservaId, {
        cpf: dados.cpf || undefined,
        rg: dados.rg, orgaoEmissor: dados.orgaoEmissor,
        nacionalidade: dados.nacionalidade || "Brasileira",
        naturalidade: dados.naturalidade,
        estrangeiro: dados.estrangeiro ?? false,
        dataNascimento: dados.dataNascimento || undefined,
        telefone: dados.telefone, whatsapp: dados.whatsapp,
        endereco: endereco ?? undefined,
      });
      onSalvo();
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Não foi possível salvar.");
    } finally {
      setSalvando(false);
    }
  }

  return (
    <div className="space-y-3">
      <p className="text-sm text-slate-500">
        Esses dados entram nos anexos oficiais da Marinha (NORMAM-212).
      </p>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="CPF">
          <input className={inputCls} value={dados.cpf ?? ""} placeholder="000.000.000-00"
            onChange={(e) => up({ cpf: e.target.value })} />
        </Field>
        <Field label="Data de nascimento">
          <input type="date" className={inputCls} value={dados.dataNascimento ?? ""}
            onChange={(e) => up({ dataNascimento: e.target.value })} />
        </Field>
        <Field label="RG / Identidade">
          <input className={inputCls} value={dados.rg ?? ""}
            onChange={(e) => up({ rg: e.target.value })} />
        </Field>
        <Field label="Órgão emissor">
          <input className={inputCls} value={dados.orgaoEmissor ?? ""} placeholder="SSP/UF"
            onChange={(e) => up({ orgaoEmissor: e.target.value })} />
        </Field>
        <Field label="Nacionalidade">
          <input className={inputCls} value={dados.nacionalidade ?? "Brasileira"}
            onChange={(e) => up({ nacionalidade: e.target.value })} />
        </Field>
        <Field label="Naturalidade (Cidade/UF)">
          <input className={inputCls} value={dados.naturalidade ?? ""} placeholder="Florianópolis/SC"
            onChange={(e) => up({ naturalidade: e.target.value })} />
        </Field>
        <Field label="Telefone/WhatsApp">
          <input className={inputCls} value={dados.whatsapp ?? dados.telefone ?? ""}
            onChange={(e) => up({ whatsapp: e.target.value })} />
        </Field>
        <label className="mt-6 flex items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" checked={dados.estrangeiro ?? false}
            onChange={(e) => up({ estrangeiro: e.target.checked })} />
          Sou estrangeiro(a) — usar passaporte / anexos em inglês
        </label>
      </div>
      <div>
        <p className="mb-2 mt-2 text-sm font-medium text-slate-700">Endereço</p>
        <AddressForm initial={endereco} onChange={setEndereco} />
      </div>
      {erro && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{erro}</p>}
      <Button className="w-full" onClick={salvar} disabled={salvando}>
        {salvando && <Loader2 size={14} className="animate-spin" />} Salvar e continuar
      </Button>
    </div>
  );
}

// ============================ Passo 2 — Documentos ============================

function UploadTile({ rotulo, presente, onFile }:
  { rotulo: string; presente: boolean; onFile: (dataUrl: string) => void }) {
  const [lendo, setLendo] = useState(false);
  return (
    <label className={`flex cursor-pointer flex-col items-center gap-1.5 rounded-xl border-2 border-dashed p-4 text-center text-sm ${
      presente ? "border-emerald-300 bg-emerald-50 text-emerald-700" : "border-slate-300 bg-slate-50 text-slate-600 hover:border-brand-400"
    }`}>
      {presente ? <CheckCircle2 size={22} /> : lendo ? <Loader2 size={22} className="animate-spin" /> : <UploadCloud size={22} className="text-slate-400" />}
      {rotulo}
      <span className="text-xs opacity-70">{presente ? "Enviado — toque para substituir" : "JPEG/PNG até 5 MB"}</span>
      <input type="file" accept="image/jpeg,image/png,image/webp" className="hidden"
        onChange={(e) => {
          const f = e.target.files?.[0];
          if (!f || f.size > 5 * 1024 * 1024) return;
          setLendo(true);
          const r = new FileReader();
          r.onload = () => { onFile(r.result as string); setLendo(false); };
          r.readAsDataURL(f);
        }} />
    </label>
  );
}

function PassoDocumentos({ token, reservaId, estado, onMudou, onConcluido }: {
  token: string; reservaId: string; estado: EmaEstado;
  onMudou: () => void; onConcluido: () => void;
}) {
  const [temComprovante, setTemComprovante] = useState(
    estado.anexosPresentes.includes("COMPROVANTE_RESIDENCIA") || !estado.anexoResidencia);
  const [erro, setErro] = useState<string | null>(null);

  async function enviar(tipo: "IDENTIDADE" | "SELFIE" | "COMPROVANTE_RESIDENCIA", dataUrl: string) {
    setErro(null);
    try {
      await uploadAnexoEma(token, reservaId, tipo, dataUrl);
      onMudou();
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Não foi possível enviar.");
    }
  }

  async function declararResidencia() {
    setErro(null);
    try {
      await putEmaFlags(token, reservaId, { anexoResidencia: true });
      onMudou();
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Não foi possível registrar.");
    }
  }

  return (
    <div className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-2">
        <UploadTile rotulo="Identidade (RG/CNH)" presente={estado.anexosPresentes.includes("IDENTIDADE")}
          onFile={(d) => enviar("IDENTIDADE", d)} />
        <UploadTile rotulo="Selfie segurando o documento" presente={estado.anexosPresentes.includes("SELFIE")}
          onFile={(d) => enviar("SELFIE", d)} />
      </div>

      <div className="rounded-xl bg-slate-50 p-4">
        <p className="text-sm font-medium text-slate-700">Comprovante de residência</p>
        <div className="mt-2 grid gap-2 sm:grid-cols-2">
          <button onClick={() => setTemComprovante(true)}
            className={`rounded-lg border p-2.5 text-left text-sm ${temComprovante ? "border-brand-500 bg-brand-50" : "border-slate-200"}`}>
            Tenho comprovante (conta de luz/água…)
          </button>
          <button onClick={() => { setTemComprovante(false); declararResidencia(); }}
            className={`rounded-lg border p-2.5 text-left text-sm ${!temComprovante ? "border-brand-500 bg-brand-50" : "border-slate-200"}`}>
            Não tenho — usar Declaração de Residência (Anexo 1-C)
          </button>
        </div>
        {temComprovante ? (
          <div className="mt-3">
            <UploadTile rotulo="Comprovante de residência"
              presente={estado.anexosPresentes.includes("COMPROVANTE_RESIDENCIA")}
              onFile={(d) => enviar("COMPROVANTE_RESIDENCIA", d)} />
          </div>
        ) : (
          <p className="mt-2 text-xs text-slate-500">
            {estado.anexoResidencia
              ? "✓ A Declaração 1-C será gerada com o endereço informado no passo 1."
              : "Selecionando esta opção, a Declaração 1-C é gerada com seu endereço."}
          </p>
        )}
      </div>

      {erro && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{erro}</p>}
      <Button className="w-full" variant="outline" onClick={onConcluido}>Continuar</Button>
    </div>
  );
}

// ============================ Passo 3 — Videoaula ============================

function PassoVideoaula({ token, reservaId, assistida, onSalvo }:
  { token: string; reservaId: string; assistida: boolean; onSalvo: () => void }) {
  const [marcada, setMarcada] = useState(assistida);
  const [salvando, setSalvando] = useState(false);

  async function salvar() {
    setSalvando(true);
    try {
      await putEmaFlags(token, reservaId, { videoaulaAssistida: true });
      onSalvo();
    } finally {
      setSalvando(false);
    }
  }

  return (
    <div className="space-y-3">
      <p className="text-sm text-slate-600">
        Assista à videoaula oficial da Marinha sobre condução de motos aquáticas
        (obrigatória para a CHA-MTA-E).
      </p>
      <a href={VIDEOAULA_URL} target="_blank" rel="noreferrer"
        className="flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 p-3 text-sm font-medium text-brand-700 hover:border-brand-400">
        <PlayCircle size={18} /> Abrir videoaula da Marinha
      </a>
      <label className="flex items-start gap-2 text-sm text-slate-700">
        <input type="checkbox" className="mt-0.5" checked={marcada}
          onChange={(e) => setMarcada(e.target.checked)} />
        Declaro que assisti à videoaula na íntegra.
      </label>
      <Button className="w-full" onClick={salvar} disabled={!marcada || salvando}>
        {salvando && <Loader2 size={14} className="animate-spin" />} Registrar e continuar
      </Button>
    </div>
  );
}

// ============================ Passo 4 — Declarações ============================

function PassoDeclaracoes({ token, reservaId, estado, onSalvo }:
  { token: string; reservaId: string; estado: EmaEstado; onSalvo: () => void }) {
  const [saude, setSaude] = useState(estado.anexoSaude);
  const [regras, setRegras] = useState(estado.anexoRegras);
  const [usaLentes, setUsaLentes] = useState(estado.usaLentes);
  const [usaAparelho, setUsaAparelho] = useState(estado.usaAparelho);
  const [salvando, setSalvando] = useState(false);

  async function salvar() {
    setSalvando(true);
    try {
      await putEmaFlags(token, reservaId, {
        anexoSaude: saude, anexoRegras: regras, usaLentes, usaAparelho,
      });
      onSalvo();
    } finally {
      setSalvando(false);
    }
  }

  return (
    <div className="space-y-4">
      <div className="rounded-xl bg-slate-50 p-4">
        <p className="text-sm font-medium text-slate-700">Autodeclaração de saúde (Anexo 5-C)</p>
        <label className="mt-2 flex items-start gap-2 text-sm text-slate-700">
          <input type="checkbox" className="mt-0.5" checked={saude} onChange={(e) => setSaude(e.target.checked)} />
          Declaro estar em boas condições físicas e mentais para conduzir moto aquática.
        </label>
        <div className="mt-2 grid gap-1.5 pl-6">
          <label className="flex items-center gap-2 text-sm text-slate-600">
            <input type="checkbox" checked={usaLentes} onChange={(e) => setUsaLentes(e.target.checked)} />
            Uso lentes corretivas (óculos/lentes)
          </label>
          <label className="flex items-center gap-2 text-sm text-slate-600">
            <input type="checkbox" checked={usaAparelho} onChange={(e) => setUsaAparelho(e.target.checked)} />
            Uso aparelho auditivo
          </label>
        </div>
      </div>

      <div className="rounded-xl bg-slate-50 p-4">
        <p className="text-sm font-medium text-slate-700">Ciência das regras (Anexo 5-B)</p>
        <label className="mt-2 flex items-start gap-2 text-sm text-slate-700">
          <input type="checkbox" className="mt-0.5" checked={regras} onChange={(e) => setRegras(e.target.checked)} />
          <span>
            Estou ciente das regras de condução: navegar só na área delimitada, entre o
            nascer e o pôr do sol, sem passageiros, máx. 20 nós (37 km/h), sem
            abastecer por conta própria, jamais sob álcool/entorpecentes, e das
            sanções LESTA/RLESTA e art. 299 do Código Penal.
          </span>
        </label>
      </div>

      <Button className="w-full" onClick={salvar} disabled={!saude || !regras || salvando}>
        {salvando && <Loader2 size={14} className="animate-spin" />} Registrar declarações
      </Button>
    </div>
  );
}

// ============================ Passo 5 — GRU ============================

function PassoGru({ token, reservaId, estado, onMudou }:
  { token: string; reservaId: string; estado: EmaEstado; onMudou: () => void }) {
  const gru = estado.gru;
  const [gerando, setGerando] = useState(false);
  const [verificando, setVerificando] = useState(false);
  const [copiado, setCopiado] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  async function gerar() {
    setGerando(true);
    setErro(null);
    try {
      const g = await gerarGruPix(token, reservaId);
      if (!g.sucesso) {
        setErro(g.erroMensagem ?? "Não foi possível gerar a GRU agora — envie o comprovante manual abaixo ou tente de novo.");
      }
      onMudou();
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Não foi possível gerar a GRU.");
    } finally {
      setGerando(false);
    }
  }

  async function verificar() {
    setVerificando(true);
    setMsg(null);
    try {
      const v = await verificarGru(token, reservaId);
      setMsg(v.pago ? "Pagamento confirmado! 🎉" : `Ainda não identificado (${v.situacao}).`);
      onMudou();
    } finally {
      setVerificando(false);
    }
  }

  async function comprovante(dataUrl: string) {
    setErro(null);
    try {
      await enviarComprovanteGru(token, reservaId, dataUrl);
      onMudou();
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Não foi possível enviar o comprovante.");
    }
  }

  if (gru.pago) {
    return (
      <p className="flex items-center gap-2 text-sm font-medium text-emerald-600">
        <CheckCircle2 size={16} /> GRU paga{gru.numero ? ` — nº ${gru.numero}` : ""}. Habilitação encaminhada!
      </p>
    );
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-slate-600">
        A taxa da Marinha (GRU) é obrigatória para emitir a CHA-MTA-E. Geramos o
        PIX oficial direto do sistema do governo.
      </p>

      {!gru.pixCopiaECola ? (
        <Button className="w-full gap-2" onClick={gerar} disabled={gerando}>
          {gerando ? <Loader2 size={15} className="animate-spin" /> : <Landmark size={15} />}
          {gerando ? "Gerando GRU no site da Marinha…" : "Gerar GRU com PIX"}
        </Button>
      ) : (
        <div className="flex flex-col items-center gap-3">
          {gru.valor != null && (
            <p className="text-sm">
              Valor: <b className="tabular-nums">{brl(gru.valor)}</b>
              {gru.numero && <span className="text-slate-400"> · GRU nº {gru.numero}</span>}
            </p>
          )}
          <PixQr payload={gru.pixCopiaECola} size={160} />
          <div className="flex w-full items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 p-2.5">
            <code className="flex-1 truncate text-[11px] text-slate-700">{gru.pixCopiaECola}</code>
            <Button size="sm" variant="outline" onClick={() => {
              navigator.clipboard?.writeText(gru.pixCopiaECola!);
              setCopiado(true);
              setTimeout(() => setCopiado(false), 1500);
            }}>
              {copiado ? <Check size={14} /> : <Copy size={14} />}
            </Button>
          </div>
          <Button className="w-full gap-2" variant="outline" onClick={verificar} disabled={verificando}>
            {verificando && <Loader2 size={14} className="animate-spin" />} Já paguei — verificar pagamento
          </Button>
          {msg && <p className="text-sm text-slate-600">{msg}</p>}
        </div>
      )}

      {erro && <p className="rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800">{erro}</p>}

      <details className="rounded-xl bg-slate-50 p-3 text-sm text-slate-600">
        <summary className="cursor-pointer font-medium">Paguei por fora / tenho o comprovante</summary>
        <div className="mt-3">
          <UploadTile rotulo="Comprovante da GRU (imagem ou PDF)" presente={gru.comprovanteDisponivel}
            onFile={comprovante} />
        </div>
      </details>
    </div>
  );
}
