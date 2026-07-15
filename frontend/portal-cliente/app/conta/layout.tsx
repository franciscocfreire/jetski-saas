import { CpfGate } from "@/components/CpfGate";

/** Área da conta: exige CPF no perfil (ou declaração de estrangeiro). */
export default function ContaLayout({ children }: { children: React.ReactNode }) {
  return <CpfGate>{children}</CpfGate>;
}
