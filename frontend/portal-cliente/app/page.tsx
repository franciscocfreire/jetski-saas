import { Search, MapPin, CalendarRange, ShieldCheck } from "lucide-react";
import { MODELOS } from "@/lib/mock";
import { ModelCard } from "@/components/ModelCard";
import { SectionTitle, inputCls } from "@/components/ui";

export default function HomePage() {
  return (
    <div>
      {/* Hero */}
      <section className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-brand-700 via-brand-600 to-brand-500 px-6 py-12 text-white sm:px-10 sm:py-16">
        <div className="relative z-10 max-w-xl">
          <h1 className="text-3xl font-extrabold leading-tight sm:text-4xl">
            Alugue um jet ski em minutos.
          </h1>
          <p className="mt-3 text-brand-50">
            Reserve, pague o sinal e resolva sua habilitação náutica — tudo
            online, direto do celular.
          </p>

          {/* Barra de busca (visual) */}
          <div className="mt-6 grid gap-2 rounded-2xl bg-white p-2 text-slate-700 shadow-lg sm:grid-cols-[1fr_1fr_auto]">
            <div className="flex items-center gap-2 rounded-xl px-3">
              <MapPin size={16} className="text-slate-400" />
              <input
                className="h-11 w-full bg-transparent text-sm outline-none"
                placeholder="Para onde? (cidade, praia)"
                defaultValue="Angra dos Reis"
              />
            </div>
            <div className="flex items-center gap-2 rounded-xl px-3 sm:border-l sm:border-slate-100">
              <CalendarRange size={16} className="text-slate-400" />
              <input
                className="h-11 w-full bg-transparent text-sm outline-none"
                placeholder="Data"
                defaultValue="22 jun, 10:00"
              />
            </div>
            <button className="flex h-11 items-center justify-center gap-2 rounded-xl bg-brand-600 px-5 text-sm font-semibold text-white hover:bg-brand-700">
              <Search size={16} /> Buscar
            </button>
          </div>

          <div className="mt-5 flex flex-wrap gap-4 text-xs text-brand-50">
            <span className="flex items-center gap-1">
              <ShieldCheck size={14} /> Habilitação CHA-MTA-E inclusa
            </span>
            <span className="flex items-center gap-1">
              <ShieldCheck size={14} /> Pagamento via PIX
            </span>
            <span className="flex items-center gap-1">
              <ShieldCheck size={14} /> Conforme NORMAM-212/Marinha
            </span>
          </div>
        </div>
        <div className="pointer-events-none absolute -right-10 -top-10 hidden h-72 w-72 rounded-full bg-white/10 blur-2xl md:block" />
      </section>

      {/* Catálogo */}
      <section className="mt-10">
        <SectionTitle sub="Modelos disponíveis em lojas parceiras">
          Escolha seu jet ski
        </SectionTitle>
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
          {MODELOS.map((m) => (
            <ModelCard key={m.id} m={m} />
          ))}
        </div>
      </section>

      {/* Como funciona */}
      <section className="mt-12 rounded-3xl border border-slate-200 bg-white p-6 sm:p-8">
        <SectionTitle sub="Da reserva ao passeio em 4 passos">
          Como funciona
        </SectionTitle>
        <div className="grid gap-5 sm:grid-cols-4">
          {[
            ["1", "Reserve", "Escolha o modelo, data e horário."],
            ["2", "Pague o sinal", "PIX da loja + envio do comprovante."],
            [
              "3",
              "Habilite-se",
              "Já tem CHA? Envie. Não tem? Emita a CHA-MTA-E.",
            ],
            ["4", "Assine e navegue", "Aceite o termo e faça o check-in."],
          ].map(([n, t, d]) => (
            <div key={n} className="rounded-2xl bg-slate-50 p-4">
              <span className="grid h-9 w-9 place-items-center rounded-full bg-brand-600 font-bold text-white">
                {n}
              </span>
              <h4 className="mt-3 font-semibold text-ink-900">{t}</h4>
              <p className="mt-1 text-sm text-slate-500">{d}</p>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
