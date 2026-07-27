"use client";

import { useState, useTransition } from "react";
import { Botao } from "@/components/Acao";
import { salvarCapitania, salvarImagemConfig, salvarModulosDoPlano } from "@/lib/actions";
import type { ImagemPreset, ModuloCatalogo, PlanoInfo, PlatformCapitania } from "@/lib/types";
import { BRL } from "@/lib/platform";

/** `plano.modulos` é um jsonb serializado como texto; null = todos liberados. */
function parseModulos(bruto?: string | null): string[] | null {
  if (bruto == null) return null;
  try {
    const v = JSON.parse(bruto);
    return Array.isArray(v) ? v.map(String) : null;
  } catch {
    return null;
  }
}

export function ModulosPorPlano({
  planos,
  catalogo,
}: {
  planos: PlanoInfo[];
  catalogo: ModuloCatalogo[];
}) {
  return (
    <div className="space-y-6">
      {planos.map((p) => (
        <EditorPlano key={p.id} plano={p} catalogo={catalogo} />
      ))}
    </div>
  );
}

function EditorPlano({ plano, catalogo }: { plano: PlanoInfo; catalogo: ModuloCatalogo[] }) {
  const inicial = parseModulos(plano.modulos);
  const [marcados, setMarcados] = useState<string[]>(inicial ?? []);
  const [erro, setErro] = useState<string | null>(null);
  const [ok, setOk] = useState(false);
  const [pendente, iniciar] = useTransition();

  const semRestricao = marcados.length === 0;

  return (
    <div className="rounded-md border border-slate-200 p-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="font-medium text-ink-900">
          {plano.nome}{" "}
          <span className="text-sm font-normal text-ink-500">
            {BRL.format(plano.precoMensal)}/mês
          </span>
        </h3>
        {semRestricao && (
          <span className="text-xs text-ink-500">sem restrição — todos os módulos</span>
        )}
      </div>

      <div className="mt-3 grid gap-2 sm:grid-cols-2">
        {catalogo.map((m) => (
          <label key={m.key} className="flex items-start gap-2 text-sm">
            <input
              type="checkbox"
              checked={marcados.includes(m.key)}
              onChange={(e) =>
                setMarcados((atual) =>
                  e.target.checked
                    ? [...atual, m.key]
                    : atual.filter((k) => k !== m.key),
                )
              }
              className="mt-1"
            />
            <span>
              <span className="font-medium">{m.rotulo}</span>
              <span className="block text-xs text-ink-500">{m.descricao}</span>
            </span>
          </label>
        ))}
      </div>

      <div className="mt-3 flex items-center gap-2">
        <Botao
          variante="primaria"
          disabled={pendente}
          onClick={() => {
            setErro(null);
            setOk(false);
            iniciar(async () => {
              const r = await salvarModulosDoPlano(plano.id, marcados);
              if (!r.ok) setErro(r.erro);
              else setOk(true);
            });
          }}
        >
          {pendente ? "…" : "Salvar módulos"}
        </Botao>
        {erro && <span className="text-xs text-red-700">{erro}</span>}
        {ok && <span className="text-xs text-emerald-700">Salvo.</span>}
      </div>
    </div>
  );
}

export function Capitanias({ capitanias }: { capitanias: PlatformCapitania[] }) {
  const [editando, setEditando] = useState<string | null>(null);
  const [criando, setCriando] = useState(false);

  return (
    <div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[36rem] text-sm">
          <thead>
            <tr className="border-b border-slate-200 text-left text-xs uppercase tracking-wide text-ink-300">
              <th className="px-3 py-2 font-medium">Código</th>
              <th className="px-3 py-2 font-medium">Nome</th>
              <th className="px-3 py-2 font-medium">UF</th>
              <th className="px-3 py-2 font-medium">E-mail oficial</th>
              <th className="px-3 py-2 font-medium">Ativa</th>
              <th className="px-3 py-2" />
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {capitanias.map((c) =>
              editando === c.id ? (
                <tr key={c.id}>
                  <td colSpan={6} className="px-3 py-3">
                    <FormCapitania inicial={c} aoFechar={() => setEditando(null)} />
                  </td>
                </tr>
              ) : (
                <tr key={c.id}>
                  <td className="px-3 py-2.5 font-medium">{c.codigo}</td>
                  <td className="px-3 py-2.5">{c.nome}</td>
                  <td className="px-3 py-2.5">{c.uf ?? "—"}</td>
                  <td className="px-3 py-2.5">{c.emailOficial ?? "—"}</td>
                  <td className="px-3 py-2.5">{c.ativa ? "sim" : "não"}</td>
                  <td className="px-3 py-2.5 text-right">
                    <Botao onClick={() => setEditando(c.id)}>Editar</Botao>
                  </td>
                </tr>
              ),
            )}
          </tbody>
        </table>
      </div>

      <div className="mt-4">
        {criando ? (
          <FormCapitania inicial={null} aoFechar={() => setCriando(false)} />
        ) : (
          <Botao onClick={() => setCriando(true)}>Nova capitania</Botao>
        )}
      </div>
    </div>
  );
}

function FormCapitania({
  inicial,
  aoFechar,
}: {
  inicial: PlatformCapitania | null;
  aoFechar: () => void;
}) {
  const [codigo, setCodigo] = useState(inicial?.codigo ?? "");
  const [nome, setNome] = useState(inicial?.nome ?? "");
  const [uf, setUf] = useState(inicial?.uf ?? "");
  const [email, setEmail] = useState(inicial?.emailOficial ?? "");
  const [ativa, setAtiva] = useState(inicial?.ativa ?? true);
  const [erro, setErro] = useState<string | null>(null);
  const [pendente, iniciar] = useTransition();

  return (
    <div className="rounded-md border border-slate-200 bg-slate-50 p-3">
      <div className="flex flex-wrap items-center gap-2">
        <input
          value={codigo}
          onChange={(e) => setCodigo(e.target.value.toUpperCase())}
          placeholder="CPSP"
          className="w-24 rounded-md border border-slate-300 px-2 py-1.5 text-sm"
        />
        <input
          value={nome}
          onChange={(e) => setNome(e.target.value)}
          placeholder="Capitania dos Portos de São Paulo"
          className="w-72 rounded-md border border-slate-300 px-2 py-1.5 text-sm"
        />
        <input
          value={uf}
          onChange={(e) => setUf(e.target.value.toUpperCase().slice(0, 2))}
          placeholder="SP"
          className="w-16 rounded-md border border-slate-300 px-2 py-1.5 text-sm"
        />
        <input
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="e-mail oficial"
          className="w-64 rounded-md border border-slate-300 px-2 py-1.5 text-sm"
        />
        <label className="flex items-center gap-1.5 text-sm">
          <input type="checkbox" checked={ativa} onChange={(e) => setAtiva(e.target.checked)} />
          ativa
        </label>
        <Botao
          variante="primaria"
          disabled={!codigo.trim() || !nome.trim() || pendente}
          onClick={() => {
            setErro(null);
            iniciar(async () => {
              const r = await salvarCapitania(inicial?.id ?? null, {
                codigo: codigo.trim(),
                nome: nome.trim(),
                uf: uf.trim() || null,
                emailOficial: email.trim() || null,
                ativa,
              });
              if (!r.ok) setErro(r.erro);
              else aoFechar();
            });
          }}
        >
          {pendente ? "…" : "Salvar"}
        </Botao>
        <Botao onClick={aoFechar} disabled={pendente}>
          Cancelar
        </Botao>
      </div>
      {erro && <p className="mt-1 text-xs text-red-700">{erro}</p>}
    </div>
  );
}

export function ImagemConfig({ tipos }: { tipos: Record<string, ImagemPreset> }) {
  const [estado, setEstado] = useState(tipos);
  const [erro, setErro] = useState<string | null>(null);
  const [ok, setOk] = useState(false);
  const [pendente, iniciar] = useTransition();

  const chaves = Object.keys(estado);

  return (
    <div>
      <div className="space-y-3">
        {chaves.map((k) => (
          <div key={k} className="flex flex-wrap items-center gap-3 text-sm">
            <span className="w-56 font-medium">{k.replace(/_/g, " ")}</span>
            <label className="flex items-center gap-1.5">
              <span className="text-xs text-ink-500">lado máx. (px)</span>
              <input
                type="number"
                min={400}
                max={4000}
                value={estado[k].maxDimensao}
                onChange={(e) =>
                  setEstado((s) => ({
                    ...s,
                    [k]: { ...s[k], maxDimensao: Number(e.target.value) },
                  }))
                }
                className="w-24 rounded-md border border-slate-300 px-2 py-1 text-sm"
              />
            </label>
            <label className="flex items-center gap-1.5">
              <span className="text-xs text-ink-500">qualidade (0,3–1,0)</span>
              <input
                type="number"
                step="0.05"
                min={0.3}
                max={1}
                value={estado[k].qualidade}
                onChange={(e) =>
                  setEstado((s) => ({
                    ...s,
                    [k]: { ...s[k], qualidade: Number(e.target.value) },
                  }))
                }
                className="w-24 rounded-md border border-slate-300 px-2 py-1 text-sm"
              />
            </label>
          </div>
        ))}
      </div>

      <div className="mt-4 flex items-center gap-2">
        <Botao
          variante="primaria"
          disabled={pendente}
          onClick={() => {
            setErro(null);
            setOk(false);
            iniciar(async () => {
              const r = await salvarImagemConfig(estado);
              if (!r.ok) setErro(r.erro);
              else setOk(true);
            });
          }}
        >
          {pendente ? "…" : "Salvar compressão"}
        </Botao>
        {erro && <span className="text-xs text-red-700">{erro}</span>}
        {ok && <span className="text-xs text-emerald-700">Salvo.</span>}
      </div>
    </div>
  );
}
