"use client";

import { useState, useTransition } from "react";
import { AcaoComTexto, Botao } from "@/components/Acao";
import { Badge } from "@/components/ui";
import { concederCondicao, encerrarCondicao } from "@/lib/actions";
import {
  ROTULO_FORMA,
  ROTULO_TIPO,
  descreverForma,
  hojeIso,
  valorEfetivo,
} from "@/lib/condicao";
import { BRL, dataCurta } from "@/lib/platform";
import type { CondicaoComercial, FormaCondicao, TipoCondicao } from "@/lib/types";

/**
 * Condição comercial da mensalidade (V076): quanto a empresa paga de fato, por quê e
 * até quando. Só a mensalidade — créditos de emissão seguem à parte.
 */
export function CondicaoComercialDaEmpresa({
  tenantId,
  condicoes,
  precoPlano,
  podeEditar,
}: {
  tenantId: string;
  condicoes: CondicaoComercial[];
  /** Preço de tabela do plano atual; null = sem assinatura. */
  precoPlano: number | null;
  podeEditar: boolean;
}) {
  const vigente = condicoes.find((c) => c.situacao === "VIGENTE") ?? null;
  const agendadas = condicoes.filter((c) => c.situacao === "AGENDADA");
  const passadas = condicoes.filter(
    (c) => c.situacao === "ENCERRADA" || c.situacao === "EXPIRADA",
  );

  return (
    <div className="space-y-4" data-testid="console-condicao-comercial">
      {vigente ? (
        <Condicao c={vigente} precoPlano={precoPlano} tenantId={tenantId} podeEditar={podeEditar} />
      ) : (
        <p className="text-sm text-ink-500">
          Sem condição especial: paga o plano cheio
          {precoPlano !== null ? ` (${BRL.format(precoPlano)}/mês)` : ""}.
        </p>
      )}

      {agendadas.map((c) => (
        <Condicao key={c.id} c={c} precoPlano={precoPlano} tenantId={tenantId} podeEditar={podeEditar} />
      ))}

      {podeEditar && !vigente && agendadas.length === 0 && (
        <div className="border-t border-slate-100 pt-4">
          <NovaCondicaoForm tenantId={tenantId} precoPlano={precoPlano} />
        </div>
      )}

      {passadas.length > 0 && (
        <details className="border-t border-slate-100 pt-3 text-sm">
          <summary className="cursor-pointer text-xs uppercase tracking-wide text-ink-300">
            Histórico ({passadas.length})
          </summary>
          <ul className="mt-2 space-y-2">
            {passadas.map((c) => (
              <li key={c.id} className="text-ink-700">
                <span className="font-medium">{ROTULO_TIPO[c.tipo]}</span> {descreverForma(c)} ·{" "}
                {dataCurta(c.inicio)} a{" "}
                {c.encerradaEm ? `${dataCurta(c.encerradaEm)} (encerrada)` : dataCurta(c.fim)}
                <div className="text-xs text-ink-300">
                  {c.motivo}
                  {c.motivoEncerramento ? ` · encerramento: ${c.motivoEncerramento}` : ""}
                </div>
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}

function Condicao({
  c,
  precoPlano,
  tenantId,
  podeEditar,
}: {
  c: CondicaoComercial;
  precoPlano: number | null;
  tenantId: string;
  podeEditar: boolean;
}) {
  const agendada = c.situacao === "AGENDADA";
  return (
    <div>
      <div className="flex flex-wrap items-center gap-2">
        <Badge tom={agendada ? "atencao" : "marca"}>{ROTULO_TIPO[c.tipo]}</Badge>
        <span className="font-display text-xl text-brand-800">{descreverForma(c)}</span>
        {agendada && <span className="text-xs text-ink-500">começa em {dataCurta(c.inicio)}</span>}
      </div>
      {precoPlano !== null && (
        <p className="mt-1 text-sm text-ink-700">
          Paga {BRL.format(valorEfetivo(precoPlano, c))} de {BRL.format(precoPlano)}/mês
        </p>
      )}
      <p className="mt-0.5 text-xs text-ink-500">
        {c.fim ? `Até ${dataCurta(c.fim)}` : "Sem prazo"} · desde {dataCurta(c.inicio)} · {c.motivo}
      </p>
      {podeEditar && (
        <div className="mt-2">
          <AcaoComTexto
            variante="perigo"
            rotulo={agendada ? "Cancelar" : "Encerrar hoje"}
            placeholder="motivo (obrigatório, auditado)"
            acao={(motivo) => encerrarCondicao(tenantId, c.id, motivo)}
          />
        </div>
      )}
    </div>
  );
}

function NovaCondicaoForm({
  tenantId,
  precoPlano,
}: {
  tenantId: string;
  precoPlano: number | null;
}) {
  const hoje = hojeIso();
  const [tipo, setTipo] = useState<TipoCondicao>("PILOTO");
  const [forma, setForma] = useState<FormaCondicao>("ISENCAO");
  const [valor, setValor] = useState("");
  const [inicio, setInicio] = useState(hoje);
  const [fim, setFim] = useState("");
  const [motivo, setMotivo] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [pendente, iniciar] = useTransition();

  const numero = forma === "ISENCAO" ? null : Number(valor);
  const valorOk =
    forma === "ISENCAO" ||
    (valor !== "" &&
      numero !== null &&
      Number.isFinite(numero) &&
      (forma === "PERCENTUAL" ? numero > 0 && numero <= 100 : numero >= 0));
  const fimOk = fim === "" ? tipo !== "PILOTO" : fim >= inicio;
  const valido = valorOk && fimOk && inicio >= hoje && motivo.trim().length > 0;
  const previa =
    precoPlano !== null && valorOk
      ? valorEfetivo(precoPlano, { forma, valor: numero })
      : null;

  const campo = "rounded-md border border-slate-300 px-2 py-1.5 text-sm";

  return (
    <div className="space-y-2">
      <div className="text-xs uppercase tracking-wide text-ink-300">Conceder condição</div>
      <div className="flex flex-wrap items-center gap-2">
        <select value={tipo} onChange={(e) => setTipo(e.target.value as TipoCondicao)} className={campo}>
          {Object.entries(ROTULO_TIPO).map(([k, v]) => (
            <option key={k} value={k}>{v}</option>
          ))}
        </select>
        <select value={forma} onChange={(e) => setForma(e.target.value as FormaCondicao)} className={campo}>
          {Object.entries(ROTULO_FORMA).map(([k, v]) => (
            <option key={k} value={k}>{v}</option>
          ))}
        </select>
        {forma !== "ISENCAO" && (
          <input
            type="number"
            min={0}
            max={forma === "PERCENTUAL" ? 100 : undefined}
            step="0.01"
            value={valor}
            onChange={(e) => setValor(e.target.value)}
            placeholder={forma === "PERCENTUAL" ? "% de desconto" : "R$ por mês"}
            className={`w-32 ${campo}`}
          />
        )}
      </div>
      <div className="flex flex-wrap items-center gap-2 text-sm text-ink-500">
        <label className="flex items-center gap-1">
          de
          <input type="date" min={hoje} value={inicio} onChange={(e) => setInicio(e.target.value)} className={campo} />
        </label>
        <label className="flex items-center gap-1">
          até
          <input type="date" min={inicio} value={fim} onChange={(e) => setFim(e.target.value)} className={campo} />
        </label>
        {tipo === "PILOTO" && fim === "" && (
          <span className="text-xs text-amber-700">piloto precisa de término</span>
        )}
      </div>
      <input
        value={motivo}
        onChange={(e) => setMotivo(e.target.value)}
        maxLength={300}
        placeholder="motivo (obrigatório, auditado)"
        className={`w-full max-w-md ${campo}`}
      />
      <div className="flex flex-wrap items-center gap-3">
        <Botao
          variante="primaria"
          disabled={!valido || pendente}
          onClick={() => {
            setErro(null);
            iniciar(async () => {
              const r = await concederCondicao(tenantId, {
                tipo,
                forma,
                valor: numero,
                inicio,
                fim: fim || null,
                motivo: motivo.trim(),
              });
              if (!r.ok) setErro(r.erro);
              else {
                setValor("");
                setFim("");
                setMotivo("");
              }
            });
          }}
        >
          {pendente ? "…" : "Conceder"}
        </Botao>
        {previa !== null && precoPlano !== null && (
          <span className="text-xs text-ink-500">
            Mensalidade: {BRL.format(previa)} (tabela {BRL.format(precoPlano)})
          </span>
        )}
      </div>
      {erro && <p className="text-xs text-red-700">{erro}</p>}
    </div>
  );
}
