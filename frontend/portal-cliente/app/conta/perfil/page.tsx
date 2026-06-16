"use client";

import { useStore } from "@/lib/store";
import { CLIENTE } from "@/lib/mock";
import { Button, Card, Field, SectionTitle, inputCls, Badge } from "@/components/ui";
import { Mail, Bell, Trash2, RotateCcw } from "lucide-react";

export default function PerfilPage() {
  const reset = useStore((s) => s.reset);

  return (
    <div className="mx-auto max-w-2xl">
      <SectionTitle sub="Seus dados e preferências">Meu perfil</SectionTitle>

      <Card className="p-6">
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Nome completo">
            <input className={inputCls} defaultValue={CLIENTE.nome} />
          </Field>
          <Field label="CPF">
            <input className={inputCls} defaultValue={CLIENTE.cpf} />
          </Field>
          <Field label="E-mail">
            <input className={inputCls} defaultValue={CLIENTE.email} />
          </Field>
          <Field label="Telefone">
            <input className={inputCls} defaultValue={CLIENTE.telefone} />
          </Field>
        </div>
        <Button className="mt-4">Salvar alterações</Button>
      </Card>

      <Card className="mt-4 p-6">
        <h3 className="flex items-center gap-2 font-semibold text-ink-900">
          <Bell size={18} /> Notificações
        </h3>
        <p className="mt-1 text-sm text-slate-500">
          No v1 enviamos por e-mail. WhatsApp/push virão depois.
        </p>
        <div className="mt-3 space-y-2 text-sm">
          {[
            "Confirmação de sinal",
            "Habilitação aprovada / a expirar",
            "Lembrete da reserva",
            "Locação finalizada — avalie",
          ].map((t) => (
            <label
              key={t}
              className="flex items-center gap-2 rounded-lg bg-slate-50 px-3 py-2"
            >
              <Mail size={14} className="text-slate-400" />
              <span className="flex-1 text-slate-700">{t}</span>
              <Badge tone="green">E-mail</Badge>
            </label>
          ))}
        </div>
      </Card>

      <Card className="mt-4 p-6">
        <h3 className="font-semibold text-ink-900">Privacidade (LGPD)</h3>
        <div className="mt-3 flex flex-wrap gap-2">
          <Button variant="outline" size="sm">
            Exportar meus dados
          </Button>
          <Button variant="outline" size="sm">
            <Trash2 size={14} /> Excluir conta
          </Button>
        </div>
      </Card>

      <div className="mt-6 text-center">
        <button
          onClick={reset}
          className="inline-flex items-center gap-1 text-xs text-slate-400 hover:text-slate-600"
        >
          <RotateCcw size={12} /> Reiniciar protótipo (limpar estado mock)
        </button>
      </div>
    </div>
  );
}
