// Dados mock do protótipo — nenhum backend é chamado.

import { jetImage } from "./img";

export type Modelo = {
  id: string;
  lojaId: string;
  lojaNome: string;
  cidade: string;
  nome: string;
  fabricante: string;
  potenciaHp: number;
  capacidadePessoas: number;
  precoBaseHora: number;
  caucao: number;
  combustivel: "Incluso" | "Medido" | "Taxa fixa";
  fotoUrl: string;
  rating: number;
  avaliacoes: number;
  destaque?: boolean;
};

export type Loja = {
  id: string;
  slug: string;
  nome: string;
  cidade: string;
  cnpj: string;
  pixChave: string;
  rating: number;
};

export const LOJAS: Loja[] = [
  {
    id: "loja-jetsave",
    slug: "jet-save",
    nome: "Jet Save Turismo Náutico",
    cidade: "Angra dos Reis · RJ",
    cnpj: "65.455.888/0001-00",
    pixChave: "65455888000100",
    rating: 4.8,
  },
  {
    id: "loja-maresia",
    slug: "maresia",
    nome: "Maresia Jet Locações",
    cidade: "Florianópolis · SC",
    cnpj: "12.345.678/0001-99",
    pixChave: "contato@maresiajet.com.br",
    rating: 4.6,
  },
];

export const MODELOS: Modelo[] = [
  {
    id: "mod-gtx-300",
    lojaId: "loja-jetsave",
    lojaNome: "Jet Save Turismo Náutico",
    cidade: "Angra dos Reis · RJ",
    nome: "Sea-Doo GTX 300",
    fabricante: "Sea-Doo",
    potenciaHp: 300,
    capacidadePessoas: 3,
    precoBaseHora: 450,
    caucao: 1500,
    combustivel: "Medido",
    fotoUrl: jetImage("#33689a", "#1e4266", 0),
    rating: 4.9,
    avaliacoes: 132,
    destaque: true,
  },
  {
    id: "mod-fx-cruiser",
    lojaId: "loja-jetsave",
    lojaNome: "Jet Save Turismo Náutico",
    cidade: "Angra dos Reis · RJ",
    nome: "Yamaha FX Cruiser HO",
    fabricante: "Yamaha",
    potenciaHp: 180,
    capacidadePessoas: 3,
    precoBaseHora: 380,
    caucao: 1200,
    combustivel: "Incluso",
    fotoUrl: jetImage("#5e95c3", "#33689a", 1),
    rating: 4.7,
    avaliacoes: 88,
  },
  {
    id: "mod-spark",
    lojaId: "loja-maresia",
    lojaNome: "Maresia Jet Locações",
    cidade: "Florianópolis · SC",
    nome: "Sea-Doo Spark Trixx",
    fabricante: "Sea-Doo",
    potenciaHp: 90,
    capacidadePessoas: 2,
    precoBaseHora: 240,
    caucao: 700,
    combustivel: "Taxa fixa",
    fotoUrl: jetImage("#c9a24b", "#b78934", 2),
    rating: 4.5,
    avaliacoes: 54,
  },
  {
    id: "mod-vx",
    lojaId: "loja-maresia",
    lojaNome: "Maresia Jet Locações",
    cidade: "Florianópolis · SC",
    nome: "Yamaha VX Deluxe",
    fabricante: "Yamaha",
    potenciaHp: 125,
    capacidadePessoas: 3,
    precoBaseHora: 300,
    caucao: 900,
    combustivel: "Medido",
    fotoUrl: jetImage("#12263f", "#0a1628", 3),
    rating: 4.6,
    avaliacoes: 41,
  },
];

export function getModelo(id: string) {
  return MODELOS.find((m) => m.id === id);
}
export function getLoja(id: string) {
  return LOJAS.find((l) => l.id === id);
}
export function getLojaBySlug(slug: string) {
  return LOJAS.find((l) => l.slug === slug);
}

// ---- Reservas / Locações (estado inicial; o store evolui a partir daqui) ----

export type ChecklistEstado = "pendente" | "em_validacao" | "ok" | "expirada";

export type Reserva = {
  id: string;
  modeloId: string;
  data: string; // ISO do início
  duracaoHoras: number;
  pessoas: number;
  valorEstimado: number;
  valorSinal: number;
  pagamentoTipo?: "sinal" | "total";
  status: "PENDENTE" | "PRONTA" | "EM_CURSO" | "FINALIZADA" | "CANCELADA";
  sinal: ChecklistEstado;
  habilitacao: ChecklistEstado;
  termos: ChecklistEstado;
};

export type Locacao = {
  id: string;
  modeloId: string;
  data: string;
  duracaoMin: number;
  valorTotal: number;
  avaliada: boolean;
};

export const RESERVAS_SEED: Reserva[] = [
  {
    id: "RSV-2041",
    modeloId: "mod-gtx-300",
    data: "2026-06-22T10:00:00",
    duracaoHoras: 2,
    pessoas: 2,
    valorEstimado: 900,
    valorSinal: 270,
    status: "PENDENTE",
    sinal: "pendente",
    habilitacao: "pendente",
    termos: "pendente",
  },
  {
    id: "RSV-2038",
    modeloId: "mod-spark",
    data: "2026-06-28T14:00:00",
    duracaoHoras: 1,
    pessoas: 1,
    valorEstimado: 240,
    valorSinal: 72,
    status: "PENDENTE",
    sinal: "em_validacao",
    habilitacao: "ok",
    termos: "pendente",
  },
];

export const LOCACOES_SEED: Locacao[] = [
  {
    id: "LOC-1990",
    modeloId: "mod-fx-cruiser",
    data: "2026-05-12T09:00:00",
    duracaoMin: 118,
    valorTotal: 760,
    avaliada: false,
  },
  {
    id: "LOC-1974",
    modeloId: "mod-vx",
    data: "2026-04-03T15:30:00",
    duracaoMin: 95,
    valorTotal: 480,
    avaliada: true,
  },
];

export const CLIENTE = {
  nome: "Marina Albuquerque",
  email: "marina.alb@example.com",
  cpf: "123.456.789-00",
  telefone: "(21) 99876-5432",
};
