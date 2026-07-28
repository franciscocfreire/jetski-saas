"use client";

import { useState, useTransition } from "react";
import { AlertTriangle, Download, History } from "lucide-react";
import { Botao } from "@/components/Acao";
import {
  cancelarExclusao,
  excluirEmpresa,
  exportarEmpresa,
  importarEmpresa,
  previewImport,
  resetarEmpresa,
} from "@/lib/actions";
import type {
  ImportPreview,
  ImportResult,
  ImportUpload,
  ResetNivel,
  ResetResult,
  TenantExport,
} from "@/lib/types";
import { dataCurta } from "@/lib/platform";

const NIVEIS: { valor: ResetNivel; rotulo: string; descricao: string }[] = [
  {
    valor: "OPERACIONAL",
    rotulo: "Operacional",
    descricao: "Locações, reservas, clientes, fechamentos, comissões.",
  },
  {
    valor: "FROTA",
    rotulo: "+ Frota",
    descricao: "Tudo do operacional e mais jetskis, modelos e manutenção.",
  },
  {
    valor: "TOTAL",
    rotulo: "Total",
    descricao: "Tudo acima e mais usuários, configurações e branding.",
  },
];

/**
 * Zona de perigo. Três garantias que o backend impõe e a UI espelha:
 *  - reset e exclusão exigem o SLUG digitado (não é confirm() genérico);
 *  - o reset gera um export automático antes de apagar;
 *  - créditos, metering, faturas e auditoria nunca são apagados.
 */
export function ZonaDePerigo({
  tenantId,
  slug,
  exclusaoAgendadaEm,
  exports,
}: {
  tenantId: string;
  slug: string;
  exclusaoAgendadaEm: string | null;
  exports: TenantExport[];
}) {
  return (
    <section className="rounded-lg border-2 border-red-200 bg-red-50/40">
      <header className="flex items-center gap-2 border-b border-red-200 px-5 py-4">
        <AlertTriangle className="h-5 w-5 text-red-700" />
        <h2 className="font-display text-lg text-red-900">Dados e LGPD</h2>
      </header>

      <div className="space-y-6 px-5 py-5">
        <Exportar tenantId={tenantId} exports={exports} />
        <Restaurar tenantId={tenantId} slug={slug} exports={exports} />
        <Resetar tenantId={tenantId} slug={slug} />
        <Excluir
          tenantId={tenantId}
          slug={slug}
          exclusaoAgendadaEm={exclusaoAgendadaEm}
        />
      </div>
    </section>
  );
}

function Exportar({ tenantId, exports }: { tenantId: string; exports: TenantExport[] }) {
  const [pendente, iniciar] = useTransition();
  const [erro, setErro] = useState<string | null>(null);
  const [gerado, setGerado] = useState<TenantExport | null>(null);

  const lista = gerado ? [gerado, ...exports] : exports;

  return (
    <div>
      <h3 className="font-medium text-ink-900">Exportar arquivamento</h3>
      <p className="mt-0.5 text-sm text-ink-500">
        .zip com os dados (JSON de todas as tabelas) e os arquivos do storage. Não apaga nada.
      </p>
      <div className="mt-3">
        <Botao
          disabled={pendente}
          onClick={() => {
            setErro(null);
            iniciar(async () => {
              const r = await exportarEmpresa(tenantId);
              if (!r.ok) setErro(r.erro);
              else setGerado(r.dados as TenantExport);
            });
          }}
        >
          {pendente ? "Gerando… (pode demorar)" : "Gerar export"}
        </Botao>
      </div>
      {erro && <p className="mt-1 text-xs text-red-700">{erro}</p>}

      {lista.length > 0 && (
        <ul className="mt-3 space-y-1 text-sm">
          {lista.map((e) => (
            <li key={e.key} className="flex flex-wrap items-center gap-2">
              <a
                href={`/api/download?tenantId=${tenantId}&key=${encodeURIComponent(e.key)}`}
                className="inline-flex items-center gap-1 text-brand-700 hover:underline"
              >
                <Download className="h-3.5 w-3.5" />
                {e.key.split("/").pop()}
              </a>
              {e.bytes != null && (
                <span className="text-xs text-ink-300">
                  {(e.bytes / 1024 / 1024).toFixed(1)} MB · {e.tabelas} tabelas · {e.arquivos} arquivos
                </span>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/**
 * Restauração a partir de um export: escolher a origem (export listado ou
 * upload de um .zip baixado antes) → analisar (dry-run) → confirmar pelo slug.
 * O backend gera um export de segurança do estado atual antes de apagar.
 */
function Restaurar({
  tenantId,
  slug,
  exports,
}: {
  tenantId: string;
  slug: string;
  exports: TenantExport[];
}) {
  const [keySelecionada, setKeySelecionada] = useState("");
  const [subindo, setSubindo] = useState(false);
  const [analisando, iniciarAnalise] = useTransition();
  const [importando, iniciarImport] = useTransition();
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [confirmacao, setConfirmacao] = useState("");
  const [ignorarDesconhecidas, setIgnorarDesconhecidas] = useState(false);
  const [resultado, setResultado] = useState<ImportResult | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  const escolherKey = (key: string) => {
    setKeySelecionada(key);
    setPreview(null);
    setResultado(null);
    setErro(null);
  };

  const subirZip = async (arquivo: File) => {
    setSubindo(true);
    setErro(null);
    try {
      const form = new FormData();
      form.append("arquivo", arquivo);
      const res = await fetch(`/api/upload?tenantId=${tenantId}`, {
        method: "POST",
        body: form,
      });
      const corpo = await res.json().catch(() => null);
      if (!res.ok) {
        setErro(corpo?.message ?? corpo?.erro ?? `Falha no upload (${res.status})`);
        return;
      }
      escolherKey((corpo as ImportUpload).key);
    } finally {
      setSubindo(false);
    }
  };

  const analisar = () => {
    setErro(null);
    setPreview(null);
    iniciarAnalise(async () => {
      const r = await previewImport(tenantId, keySelecionada);
      if (!r.ok) setErro(r.erro);
      else setPreview(r.dados);
    });
  };

  const tabelasDoPreview = preview
    ? Array.from(
        new Set([
          ...Object.keys(preview.linhasNoZip),
          ...Object.keys(preview.linhasAtuais),
        ]),
      ).sort()
    : [];
  const temDesconhecidas =
    preview?.avisos.some((a) => a.includes("não existem mais no sistema")) ?? false;

  return (
    <div className="border-t border-red-200 pt-5">
      <h3 className="flex items-center gap-1.5 font-medium text-ink-900">
        <History className="h-4 w-4" /> Restaurar de um export
      </h3>
      <p className="mt-0.5 text-sm text-ink-500">
        Volta a empresa ao estado do zip: <strong>substitui</strong> dados e arquivos
        atuais (um export de segurança é gerado antes). Membros voltam ao estado do
        export; créditos, faturas, metering e auditoria nunca são tocados.
      </p>

      <div className="mt-3 flex flex-wrap items-center gap-2">
        <select
          value={exports.some((e) => e.key === keySelecionada) ? keySelecionada : ""}
          onChange={(e) => escolherKey(e.target.value)}
          className="max-w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm"
        >
          <option value="">
            {exports.length > 0 ? "Escolher um export…" : "Nenhum export disponível"}
          </option>
          {exports.map((e) => (
            <option key={e.key} value={e.key}>
              {e.key.split("/").pop()}
            </option>
          ))}
        </select>
        <span className="text-xs text-ink-300">ou</span>
        <label className="cursor-pointer rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50">
          {subindo ? "Enviando…" : "Enviar .zip baixado"}
          <input
            type="file"
            accept=".zip,application/zip"
            className="hidden"
            disabled={subindo}
            onChange={(e) => {
              const f = e.target.files?.[0];
              if (f) subirZip(f);
              e.target.value = "";
            }}
          />
        </label>
        <Botao disabled={!keySelecionada || analisando} onClick={analisar}>
          {analisando ? "Analisando…" : "Analisar"}
        </Botao>
      </div>
      {keySelecionada && (
        <p className="mt-1 break-all text-xs text-ink-300">{keySelecionada}</p>
      )}

      {preview && (
        <div className="mt-3 rounded-md border border-slate-200 bg-white p-3">
          <div className="text-xs text-ink-500">
            Export de <strong>{dataCurta(preview.geradoEm)}</strong> ·{" "}
            {preview.arquivosNoZip} arquivo(s) no storage
          </div>
          {preview.avisos.length > 0 && (
            <ul className="mt-2 space-y-1">
              {preview.avisos.map((a) => (
                <li key={a} className="text-xs text-amber-700">
                  ⚠ {a}
                </li>
              ))}
            </ul>
          )}
          <div className="mt-2 max-h-56 overflow-y-auto">
            <table className="w-full text-xs">
              <thead className="text-left text-ink-300">
                <tr>
                  <th className="py-1 pr-2 font-normal">Tabela</th>
                  <th className="py-1 pr-2 font-normal">No zip</th>
                  <th className="py-1 font-normal">Hoje (será substituído)</th>
                </tr>
              </thead>
              <tbody>
                {tabelasDoPreview
                  .filter(
                    (t) =>
                      (preview.linhasNoZip[t] ?? 0) > 0 ||
                      (preview.linhasAtuais[t] ?? 0) > 0,
                  )
                  .map((t) => (
                    <tr key={t} className="border-t border-slate-100">
                      <td className="py-1 pr-2">{t}</td>
                      <td className="py-1 pr-2">{preview.linhasNoZip[t] ?? 0}</td>
                      <td className="py-1">{preview.linhasAtuais[t] ?? 0}</td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>

          {temDesconhecidas && (
            <label className="mt-2 flex items-start gap-2 text-xs">
              <input
                type="checkbox"
                checked={ignorarDesconhecidas}
                onChange={(e) => setIgnorarDesconhecidas(e.target.checked)}
                className="mt-0.5"
              />
              <span>Ignorar as tabelas que não existem mais (esses dados não voltam)</span>
            </label>
          )}

          <div className="mt-3 flex flex-wrap items-center gap-2">
            <input
              value={confirmacao}
              onChange={(e) => setConfirmacao(e.target.value)}
              placeholder={`digite "${slug}" para confirmar`}
              className="w-64 rounded-md border border-slate-300 px-2 py-1.5 text-sm"
            />
            <Botao
              variante="perigo"
              disabled={confirmacao !== slug || importando}
              onClick={() => {
                setErro(null);
                iniciarImport(async () => {
                  const r = await importarEmpresa(
                    tenantId,
                    keySelecionada,
                    confirmacao,
                    ignorarDesconhecidas,
                  );
                  if (!r.ok) setErro(r.erro);
                  else {
                    setResultado(r.dados);
                    setPreview(null);
                    setConfirmacao("");
                  }
                });
              }}
            >
              {importando
                ? "Restaurando… (pode demorar)"
                : "Restaurar (substitui os dados atuais)"}
            </Botao>
          </div>
        </div>
      )}

      {erro && <p className="mt-2 text-xs text-red-700">{erro}</p>}
      {resultado && (
        <div className="mt-2 space-y-1 text-xs text-emerald-700">
          <p>
            Restauração concluída — {resultado.totalLinhas} linhas em{" "}
            {Object.keys(resultado.inseridos).length} tabelas,{" "}
            {resultado.arquivosRestaurados} arquivo(s).
          </p>
          <p className="break-all text-ink-500">
            Export de segurança do estado anterior: {resultado.exportSegurancaKey}
          </p>
          {resultado.avisos.map((a) => (
            <p key={a} className="text-amber-700">
              ⚠ {a}
            </p>
          ))}
        </div>
      )}
    </div>
  );
}

function Resetar({ tenantId, slug }: { tenantId: string; slug: string }) {
  const [nivel, setNivel] = useState<ResetNivel>("OPERACIONAL");
  const [confirmacao, setConfirmacao] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [feito, setFeito] = useState<ResetResult | null>(null);
  const [pendente, iniciar] = useTransition();

  return (
    <div className="border-t border-red-200 pt-5">
      <h3 className="font-medium text-ink-900">Resetar dados</h3>
      <p className="mt-0.5 text-sm text-ink-500">
        Um export automático é gerado antes de apagar. Créditos, metering, faturas e
        auditoria são sempre preservados.
      </p>

      <div className="mt-3 space-y-2">
        {NIVEIS.map((n) => (
          <label key={n.valor} className="flex items-start gap-2 text-sm">
            <input
              type="radio"
              name="nivel"
              checked={nivel === n.valor}
              onChange={() => setNivel(n.valor)}
              className="mt-1"
            />
            <span>
              <span className="font-medium">{n.rotulo}</span>
              <span className="block text-xs text-ink-500">{n.descricao}</span>
            </span>
          </label>
        ))}
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-2">
        <input
          value={confirmacao}
          onChange={(e) => setConfirmacao(e.target.value)}
          placeholder={`digite "${slug}" para confirmar`}
          className="w-64 rounded-md border border-slate-300 px-2 py-1.5 text-sm"
        />
        <Botao
          variante="perigo"
          disabled={confirmacao !== slug || pendente}
          onClick={() => {
            setErro(null);
            iniciar(async () => {
              const r = await resetarEmpresa(tenantId, nivel, confirmacao);
              if (!r.ok) setErro(r.erro);
              else {
                setFeito(r.dados as ResetResult);
                setConfirmacao("");
              }
            });
          }}
        >
          {pendente ? "Resetando…" : `Resetar (${nivel})`}
        </Botao>
      </div>
      {erro && <p className="mt-1 text-xs text-red-700">{erro}</p>}
      {feito && (
        <p className="mt-2 text-xs text-emerald-700">
          Reset {feito.nivel} concluído — {feito.totalLinhas} linhas apagadas.
        </p>
      )}
    </div>
  );
}

function Excluir({
  tenantId,
  slug,
  exclusaoAgendadaEm,
}: {
  tenantId: string;
  slug: string;
  exclusaoAgendadaEm: string | null;
}) {
  const [modo, setModo] = useState<"CARENCIA" | "IMEDIATO">("CARENCIA");
  const [confirmacao, setConfirmacao] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [pendente, iniciar] = useTransition();

  if (exclusaoAgendadaEm) {
    return (
      <div className="border-t border-red-200 pt-5">
        <h3 className="font-medium text-ink-900">Exclusão agendada</h3>
        <p className="mt-0.5 text-sm text-ink-500">
          Expurgo em {dataCurta(exclusaoAgendadaEm)}. Cancelar mantém a empresa suspensa,
          sem apagar nada.
        </p>
        <div className="mt-3">
          <Botao
            disabled={pendente}
            onClick={() => {
              setErro(null);
              iniciar(async () => {
                const r = await cancelarExclusao(tenantId);
                if (!r.ok) setErro(r.erro);
              });
            }}
          >
            {pendente ? "…" : "Cancelar exclusão"}
          </Botao>
        </div>
        {erro && <p className="mt-1 text-xs text-red-700">{erro}</p>}
      </div>
    );
  }

  return (
    <div className="border-t border-red-200 pt-5">
      <h3 className="font-medium text-ink-900">Excluir empresa</h3>
      <p className="mt-0.5 text-sm text-ink-500">
        O expurgo deixa tombstone: o slug é liberado e os dados sensíveis são anonimizados.
        Ledger, metering e auditoria permanecem.
      </p>

      <div className="mt-3 space-y-2 text-sm">
        <label className="flex items-start gap-2">
          <input
            type="radio"
            checked={modo === "CARENCIA"}
            onChange={() => setModo("CARENCIA")}
            className="mt-1"
          />
          <span>
            <span className="font-medium">Com carência (30 dias)</span>
            <span className="block text-xs text-ink-500">
              Suspende agora, expurga depois. Cancelável a qualquer momento.
            </span>
          </span>
        </label>
        <label className="flex items-start gap-2">
          <input
            type="radio"
            checked={modo === "IMEDIATO"}
            onChange={() => setModo("IMEDIATO")}
            className="mt-1"
          />
          <span>
            <span className="font-medium">Imediato</span>
            <span className="block text-xs text-ink-500">
              Expurga agora. Sem volta.
            </span>
          </span>
        </label>
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-2">
        <input
          value={confirmacao}
          onChange={(e) => setConfirmacao(e.target.value)}
          placeholder={`digite "${slug}" para confirmar`}
          className="w-64 rounded-md border border-slate-300 px-2 py-1.5 text-sm"
        />
        <Botao
          variante="perigo"
          disabled={confirmacao !== slug || pendente}
          onClick={() => {
            setErro(null);
            iniciar(async () => {
              const r = await excluirEmpresa(tenantId, modo, confirmacao);
              if (!r.ok) setErro(r.erro);
              else setConfirmacao("");
            });
          }}
        >
          {pendente ? "Excluindo…" : modo === "CARENCIA" ? "Agendar exclusão" : "Excluir agora"}
        </Botao>
      </div>
      {erro && <p className="mt-1 text-xs text-red-700">{erro}</p>}
    </div>
  );
}
