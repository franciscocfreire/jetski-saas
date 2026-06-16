"use client";

import { create } from "zustand";
import { persist } from "zustand/middleware";
import {
  RESERVAS_SEED,
  LOCACOES_SEED,
  type Reserva,
  type Locacao,
  type ChecklistEstado,
} from "./mock";

type Auth = { logged: boolean; nome?: string; emailVerified?: boolean };

type State = {
  auth: Auth;
  reservas: Reserva[];
  locacoes: Locacao[];
  login: (nome: string) => void;
  signup: (nome: string) => void;
  verifyEmail: () => void;
  logout: () => void;
  addReserva: (r: Reserva) => void;
  setEtapa: (
    id: string,
    etapa: "sinal" | "habilitacao" | "termos",
    estado: ChecklistEstado
  ) => void;
  setPagamentoTipo: (id: string, tipo: "sinal" | "total") => void;
  avaliar: (locacaoId: string) => void;
  reset: () => void;
};

function recomputeStatus(r: Reserva): Reserva {
  if (r.status === "CANCELADA" || r.status === "FINALIZADA") return r;
  const pronto =
    r.sinal === "ok" && r.habilitacao === "ok" && r.termos === "ok";
  return { ...r, status: pronto ? "PRONTA" : "PENDENTE" };
}

export const useStore = create<State>()(
  persist(
    (set) => ({
      auth: { logged: false },
      reservas: RESERVAS_SEED,
      locacoes: LOCACOES_SEED,
      // login = cliente recorrente (e-mail já verificado)
      login: (nome) => set({ auth: { logged: true, nome, emailVerified: true } }),
      // signup = cliente novo → conta RESTRITA até verificar o e-mail
      signup: (nome) => set({ auth: { logged: true, nome, emailVerified: false } }),
      verifyEmail: () =>
        set((s) => ({ auth: { ...s.auth, emailVerified: true } })),
      logout: () => set({ auth: { logged: false } }),
      addReserva: (r) =>
        set((s) => ({ reservas: [r, ...s.reservas] })),
      setEtapa: (id, etapa, estado) =>
        set((s) => ({
          reservas: s.reservas.map((r) =>
            r.id === id ? recomputeStatus({ ...r, [etapa]: estado }) : r
          ),
        })),
      setPagamentoTipo: (id, tipo) =>
        set((s) => ({
          reservas: s.reservas.map((r) =>
            r.id === id
              ? {
                  ...r,
                  pagamentoTipo: tipo,
                  valorSinal:
                    tipo === "total"
                      ? r.valorEstimado
                      : Math.round(r.valorEstimado * 0.3),
                }
              : r
          ),
        })),
      avaliar: (locacaoId) =>
        set((s) => ({
          locacoes: s.locacoes.map((l) =>
            l.id === locacaoId ? { ...l, avaliada: true } : l
          ),
        })),
      reset: () =>
        set({
          auth: { logged: false },
          reservas: RESERVAS_SEED,
          locacoes: LOCACOES_SEED,
        }),
    }),
    { name: "portal-cliente-proto" }
  )
);
