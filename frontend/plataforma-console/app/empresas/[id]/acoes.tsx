"use client";

import { useState, useTransition } from "react";
import { Acao, AcaoComTexto, Botao } from "@/components/Acao";
import {
  aprovarEmpresa,
  definirLimiteUsuarios,
  desabilitarEmissora,
  habilitarEmissora,
  lancarCreditos,
  mudarPlano,
  reativarEmpresa,
  suspenderEmpresa,
  testarSmtp,
} from "@/lib/actions";
import type { LimiteUsuarios, PlanoInfo, ResultadoTesteSmtp } from "@/lib/types";
import { BRL } from "@/lib/platform";

/** Ações de status: só as que fazem sentido para o status atual aparecem. */
export function AcoesStatus({ tenantId, status }: { tenantId: string; status: string }) {
  return (
    <div className="flex flex-wrap items-start gap-2">
      {status === "PENDENTE_APROVACAO" && (
        <Acao
          variante="primaria"
          rotulo="Aprovar empresa"
          confirmar="Aprovar e iniciar o trial?"
          acao={() => aprovarEmpresa(tenantId)}
        />
      )}
      {(status === "ATIVO" || status === "TRIAL") && (
        <AcaoComTexto
          variante="perigo"
          rotulo="Suspender"
          placeholder="motivo da suspensão"
          obrigatorio={false}
          acao={(motivo) => suspenderEmpresa(tenantId, motivo)}
        />
      )}
      {(status === "SUSPENSO" || status === "INATIVO") && (
        <Acao
          variante="primaria"
          rotulo="Reativar"
          confirmar="Reativar a empresa?"
          acao={() => reativarEmpresa(tenantId)}
        />
      )}
      {status === "CANCELADO" && (
        <span className="text-sm text-ink-300">Empresa cancelada — sem ações de status.</span>
      )}
    </div>
  );
}

export function AcoesEmissora({
  tenantId,
  habilitada,
}: {
  tenantId: string;
  habilitada: boolean;
}) {
  return habilitada ? (
    <Acao
      variante="perigo"
      rotulo="Desabilitar emissora"
      confirmar="Remover a habilitação de EAMA?"
      acao={() => desabilitarEmissora(tenantId)}
    />
  ) : (
    <Acao
      variante="primaria"
      rotulo="Habilitar emissora"
      confirmar="Habilitar como EAMA emissora?"
      acao={() => habilitarEmissora(tenantId)}
    />
  );
}

/**
 * Dispara um e-mail de teste pelo SMTP da empresa para o e-mail da plataforma. Falha de
 * SMTP volta como resultado (não como erro HTTP) para mostrar a causa ao operador.
 */
export function TesteSmtp({ tenantId }: { tenantId: string }) {
  const [resultado, setResultado] = useState<ResultadoTesteSmtp | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [pendente, iniciar] = useTransition();

  return (
    <div>
      <Botao
        data-testid="console-smtp-teste"
        disabled={pendente}
        onClick={() => {
          setErro(null);
          setResultado(null);
          iniciar(async () => {
            const r = await testarSmtp(tenantId);
            if (!r.ok) setErro(r.erro);
            else setResultado(r.dados);
          });
        }}
      >
        {pendente ? "Enviando teste…" : "Enviar e-mail de teste"}
      </Botao>
      {erro && <p className="mt-1 text-xs text-red-700">{erro}</p>}
      {resultado?.enviado && (
        <p className="mt-1 text-xs text-emerald-700" data-testid="console-smtp-teste-ok">
          Enviado pela conta {resultado.usuario ?? resultado.de}
          {resultado.usuario && resultado.usuario.toLowerCase() !== resultado.de.toLowerCase()
            ? ` (remetente configurado: ${resultado.de})`
            : ""}{" "}
          via {resultado.servidor}. Confira a caixa de {resultado.para}.
        </p>
      )}
      {resultado && !resultado.enviado && (
        <p className="mt-1 text-xs text-red-700" data-testid="console-smtp-teste-falha">
          Falhou ({resultado.servidor}): {resultado.erro}
        </p>
      )}
    </div>
  );
}

export function TrocarPlano({
  tenantId,
  planoAtual,
  planos,
}: {
  tenantId: string;
  planoAtual: string | null;
  planos: PlanoInfo[];
}) {
  const [escolhido, setEscolhido] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [pendente, iniciar] = useTransition();

  return (
    <div>
      <div className="flex flex-wrap items-center gap-2">
        <select
          value={escolhido}
          onChange={(e) => setEscolhido(e.target.value)}
          className="rounded-md border border-slate-300 px-2 py-1.5 text-sm"
        >
          <option value="">
            {planoAtual ? `Atual: ${planoAtual}` : "Sem assinatura — escolher plano"}
          </option>
          {planos.map((p) => (
            <option key={p.id} value={p.id}>
              {p.nome} — {BRL.format(p.precoMensal)}/mês
            </option>
          ))}
        </select>
        <Botao
          variante="primaria"
          disabled={!escolhido || pendente}
          onClick={() => {
            setErro(null);
            iniciar(async () => {
              const r = await mudarPlano(tenantId, escolhido);
              if (!r.ok) setErro(r.erro);
              else setEscolhido("");
            });
          }}
        >
          {pendente ? "…" : "Trocar plano"}
        </Botao>
      </div>
      {erro && <p className="mt-1 text-xs text-red-700">{erro}</p>}
    </div>
  );
}

/**
 * Lançamento manual de créditos. Ajuste = correção (±, negativo vira estorno); cortesia =
 * crédito dado de graça (só positiva), que pode ficar ligada à condição comercial vigente.
 */
export function LancarCreditos({
  tenantId,
  condicao,
}: {
  tenantId: string;
  /** Condição comercial vigente, para vincular a cortesia (ex.: "Piloto isenta"). */
  condicao?: { id: string; rotulo: string } | null;
}) {
  const [tipo, setTipo] = useState<"AJUSTE" | "CORTESIA">("AJUSTE");
  const [vincular, setVincular] = useState(true);
  const [quantidade, setQuantidade] = useState("");
  const [motivo, setMotivo] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [ok, setOk] = useState(false);
  const [pendente, iniciar] = useTransition();

  const qtd = Number(quantidade);
  const cortesia = tipo === "CORTESIA";
  const valido =
    Number.isInteger(qtd) && (cortesia ? qtd > 0 : qtd !== 0) && motivo.trim().length > 0;

  return (
    <div>
      <div className="flex flex-wrap items-center gap-2">
        <select
          value={tipo}
          onChange={(e) => setTipo(e.target.value as "AJUSTE" | "CORTESIA")}
          className="rounded-md border border-slate-300 px-2 py-1.5 text-sm"
        >
          <option value="AJUSTE">Ajuste (±)</option>
          <option value="CORTESIA">Cortesia</option>
        </select>
        <input
          type="number"
          value={quantidade}
          onChange={(e) => setQuantidade(e.target.value)}
          min={cortesia ? 1 : undefined}
          placeholder={cortesia ? "qtd" : "±qtd"}
          className="w-24 rounded-md border border-slate-300 px-2 py-1.5 text-sm"
        />
        <input
          value={motivo}
          onChange={(e) => setMotivo(e.target.value)}
          placeholder="motivo (obrigatório, auditado)"
          className="w-64 rounded-md border border-slate-300 px-2 py-1.5 text-sm"
        />
        <Botao
          variante="primaria"
          disabled={!valido || pendente}
          onClick={() => {
            setErro(null);
            setOk(false);
            iniciar(async () => {
              const r = await lancarCreditos(
                tenantId,
                qtd,
                motivo,
                tipo,
                cortesia && vincular && condicao ? condicao.id : null,
              );
              if (!r.ok) setErro(r.erro);
              else {
                setQuantidade("");
                setMotivo("");
                setOk(true);
              }
            });
          }}
        >
          {pendente ? "…" : "Lançar"}
        </Botao>
      </div>
      {cortesia && condicao && (
        <label className="mt-2 flex items-center gap-2 text-xs text-ink-500">
          <input type="checkbox" checked={vincular} onChange={(e) => setVincular(e.target.checked)} />
          vincular à condição comercial ({condicao.rotulo})
        </label>
      )}
      {erro && <p className="mt-1 text-xs text-red-700">{erro}</p>}
      {ok && <p className="mt-1 text-xs text-emerald-700">Lançamento registrado.</p>}
    </div>
  );
}

/**
 * Teto de usuários ATIVOS da empresa. Sem personalização, vale o do plano. Baixar
 * abaixo do uso não desativa ninguém: só bloqueia convites e reativações até caber.
 */
export function LimiteDeUsuarios({
  tenantId,
  limite,
}: {
  tenantId: string;
  limite: LimiteUsuarios;
}) {
  const [maximo, setMaximo] = useState("");
  const [motivo, setMotivo] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [ok, setOk] = useState<string | null>(null);
  const [pendente, iniciar] = useTransition();

  const qtd = Number(maximo);
  const temMotivo = motivo.trim().length > 0;
  const valido = maximo !== "" && Number.isInteger(qtd) && qtd >= 1 && temMotivo;
  const personalizado = limite.personalizado !== null;
  const acimaDoLimite = limite.efetivo !== null && limite.ativos > limite.efetivo;

  function salvar(valor: number | null) {
    setErro(null);
    setOk(null);
    iniciar(async () => {
      const r = await definirLimiteUsuarios(tenantId, valor, motivo.trim());
      if (!r.ok) setErro(r.erro);
      else {
        setMaximo("");
        setMotivo("");
        setOk(valor === null ? "Voltou a seguir o plano." : "Limite atualizado.");
      }
    });
  }

  return (
    <div>
      <div className="flex flex-wrap items-baseline gap-x-2">
        <span className="font-display text-2xl text-brand-800">
          {limite.efetivo ?? "ilimitado"}
        </span>
        <span className="text-sm text-ink-500">
          {personalizado
            ? "personalizado para esta empresa"
            : limite.plano
              ? `do plano ${limite.plano}`
              : "sem plano contratado"}
        </span>
      </div>
      <p className="mt-0.5 text-xs text-ink-500">
        {limite.ativos} ativo{limite.ativos === 1 ? "" : "s"} hoje
        {personalizado && ` · o plano prevê ${limite.doPlano ?? "ilimitado"}`}
      </p>
      {acimaDoLimite && (
        <p className="mt-2 text-xs text-amber-800">
          Acima do limite: ninguém é desativado, mas novos convites e reativações ficam
          bloqueados.
        </p>
      )}
      <div className="mt-3 flex flex-wrap items-center gap-2">
        <input
          type="number"
          min={1}
          step={1}
          value={maximo}
          onChange={(e) => setMaximo(e.target.value)}
          placeholder="novo limite"
          className="w-28 rounded-md border border-slate-300 px-2 py-1.5 text-sm"
        />
        <input
          value={motivo}
          onChange={(e) => setMotivo(e.target.value)}
          placeholder="motivo (obrigatório, auditado)"
          className="w-64 max-w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm"
        />
        <Botao variante="primaria" disabled={!valido || pendente} onClick={() => salvar(qtd)}>
          {pendente ? "…" : "Definir"}
        </Botao>
        {personalizado && (
          <Botao disabled={!temMotivo || pendente} onClick={() => salvar(null)}>
            Voltar ao plano
          </Botao>
        )}
      </div>
      {erro && <p className="mt-1 text-xs text-red-700">{erro}</p>}
      {ok && <p className="mt-1 text-xs text-emerald-700">{ok}</p>}
    </div>
  );
}
