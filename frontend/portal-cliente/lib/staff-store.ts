"use client";

import { create } from "zustand";
import { persist } from "zustand/middleware";

export type SinalStatus = "em_analise" | "confirmado" | "recusado";

export type Sinal = {
  id: string; // id da reserva
  clienteNome: string;
  emailVerificado: boolean;
  modeloId: string;
  dataISO: string;
  tipo: "sinal" | "total"; // remoto: cliente escolhe (sinal parcial ou total)
  valorEsperado: number;
  valorInformado: number;
  comprovante: "img" | "pdf";
  enviadoMin: number; // minutos atrás
  status: SinalStatus;
  motivo?: string;
  valorConfirmado?: number;
};

const SEED: Sinal[] = [
  {
    id: "RSV-2038",
    clienteNome: "Marina Albuquerque",
    emailVerificado: false,
    modeloId: "mod-spark",
    dataISO: "2026-06-28T14:00:00",
    tipo: "sinal",
    valorEsperado: 72,
    valorInformado: 72,
    comprovante: "img",
    enviadoMin: 8,
    status: "em_analise",
  },
  {
    id: "RSV-2041",
    clienteNome: "João Pereira",
    emailVerificado: false,
    modeloId: "mod-gtx-300",
    dataISO: "2026-06-22T10:00:00",
    tipo: "sinal",
    valorEsperado: 270,
    valorInformado: 200,
    comprovante: "pdf",
    enviadoMin: 64,
    status: "em_analise",
  },
  {
    id: "RSV-3001",
    clienteNome: "Ana Martins",
    emailVerificado: true,
    modeloId: "mod-fx-cruiser",
    dataISO: "2026-06-25T09:00:00",
    tipo: "total",
    valorEsperado: 760,
    valorInformado: 760,
    comprovante: "img",
    enviadoMin: 132,
    status: "em_analise",
  },
  {
    id: "RSV-3002",
    clienteNome: "Carlos Souza",
    emailVerificado: true,
    modeloId: "mod-vx",
    dataISO: "2026-06-30T16:00:00",
    tipo: "sinal",
    valorEsperado: 180,
    valorInformado: 180,
    comprovante: "img",
    enviadoMin: 240,
    status: "em_analise",
  },
];

type State = {
  sinais: Sinal[];
  confirmar: (id: string, valorConfirmado: number) => void;
  recusar: (id: string, motivo: string) => void;
  reset: () => void;
};

export const useStaff = create<State>()(
  persist(
    (set) => ({
      sinais: SEED,
      confirmar: (id, valorConfirmado) =>
        set((s) => ({
          sinais: s.sinais.map((x) =>
            x.id === id ? { ...x, status: "confirmado", valorConfirmado } : x
          ),
        })),
      recusar: (id, motivo) =>
        set((s) => ({
          sinais: s.sinais.map((x) =>
            x.id === id ? { ...x, status: "recusado", motivo } : x
          ),
        })),
      reset: () => set({ sinais: SEED }),
    }),
    { name: "portal-staff-proto" }
  )
);
