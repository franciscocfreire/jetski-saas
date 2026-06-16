"use client";

import Link from "next/link";
import { Inbox, UserPlus, ArrowRight, Clock, CheckCircle2, Anchor } from "lucide-react";
import { useStaff } from "@/lib/staff-store";
import { Card, Badge, SectionTitle } from "@/components/ui";

export default function StaffHome() {
  const sinais = useStaff((s) => s.sinais);
  const pendentes = sinais.filter((s) => s.status === "em_analise").length;
  const confirmados = sinais.filter((s) => s.status === "confirmado").length;

  return (
    <div>
      <SectionTitle sub="Operação de balcão — Jet Save Turismo Náutico">
        Painel do operador
      </SectionTitle>

      <div className="grid gap-4 sm:grid-cols-3">
        <Card className="p-5">
          <div className="flex items-center gap-2 text-sm text-slate-500">
            <Clock size={16} /> Sinais a validar
          </div>
          <div className="mt-1 text-3xl font-bold text-ink-900">{pendentes}</div>
        </Card>
        <Card className="p-5">
          <div className="flex items-center gap-2 text-sm text-slate-500">
            <CheckCircle2 size={16} /> Confirmados hoje
          </div>
          <div className="mt-1 text-3xl font-bold text-ink-900">{confirmados}</div>
        </Card>
        <Card className="p-5">
          <div className="flex items-center gap-2 text-sm text-slate-500">
            <Anchor size={16} /> Atendimentos hoje
          </div>
          <div className="mt-1 text-3xl font-bold text-ink-900">5</div>
        </Card>
      </div>

      <div className="mt-6 grid gap-4 sm:grid-cols-2">
        <Link href="/staff/sinais">
          <Card className="group flex h-full flex-col p-6 transition hover:shadow-md">
            <div className="flex items-center justify-between">
              <span className="grid h-12 w-12 place-items-center rounded-2xl bg-brand-50 text-brand-700">
                <Inbox size={24} />
              </span>
              {pendentes > 0 && <Badge tone="amber">{pendentes} na fila</Badge>}
            </div>
            <h3 className="mt-4 text-lg font-semibold text-ink-900">
              Validar sinais
            </h3>
            <p className="mt-1 flex-1 text-sm text-slate-500">
              Revise os comprovantes de PIX enviados pelos clientes e confirme ou
              recuse o sinal.
            </p>
            <span className="mt-4 inline-flex items-center gap-1 text-sm font-medium text-brand-600 group-hover:underline">
              Abrir fila <ArrowRight size={15} />
            </span>
          </Card>
        </Link>

        <Link href="/staff/embarque">
          <Card className="group flex h-full flex-col p-6 transition hover:shadow-md">
            <div className="flex items-center justify-between">
              <span className="grid h-12 w-12 place-items-center rounded-2xl bg-brand-50 text-brand-700">
                <UserPlus size={24} />
              </span>
              <Badge tone="brand">Novo</Badge>
            </div>
            <h3 className="mt-4 text-lg font-semibold text-ink-900">
              Atendimento de balcão
            </h3>
            <p className="mt-1 flex-1 text-sm text-slate-500">
              Registre o cliente sem celular: pré-conta, documentos, pagamento,
              GRU/CHA-MTA-E, termos e emissão dos documentos. (Check-in à parte.)
            </p>
            <span className="mt-4 inline-flex items-center gap-1 text-sm font-medium text-brand-600 group-hover:underline">
              Iniciar atendimento <ArrowRight size={15} />
            </span>
          </Card>
        </Link>
      </div>
    </div>
  );
}
