import { redirect } from 'next/navigation'

/** GRUs foi unificada com Documentos: o endereço antigo leva para a tela única. */
export default function GrusPage() {
  redirect('/dashboard/documentos')
}
