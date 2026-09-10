import type { DocumentoTipo } from '@/lib/api/types'

/**
 * Formatação do documento de identificação para exibição.
 *
 * O backend guarda o valor canônico desde a V066 — só dígitos no CPF/CNPJ,
 * alfanumérico maiúsculo no passaporte — justamente para que "847.215.903-50" e
 * "84721590350" não sejam duas pessoas diferentes. A pontuação é assunto de
 * tela: aplique aqui, nunca no que vai para a API.
 */
export function formatarDocumento(
  documento?: string | null,
  tipo?: DocumentoTipo | null
): string {
  if (!documento) return ''

  const efetivo = tipo ?? inferirTipoDocumento(documento)

  if (efetivo === 'CPF') {
    const d = documento.replace(/\D/g, '')
    if (d.length === 11) return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6, 9)}-${d.slice(9)}`
  }
  if (efetivo === 'CNPJ') {
    const d = documento.replace(/\D/g, '')
    if (d.length === 14) {
      return `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8, 12)}-${d.slice(12)}`
    }
  }
  // Passaporte e documento malformado saem como estão.
  return documento
}

/**
 * Deduz o tipo pelo formato. Letra só pode ser passaporte; 11 e 14 dígitos são
 * CPF e CNPJ. Qualquer outra contagem é documento malformado — devolve
 * `undefined` em vez de chutar PASSAPORTE, que marcaria a pessoa como
 * estrangeira e mandaria os anexos 5-B em inglês.
 */
export function inferirTipoDocumento(documento?: string | null): DocumentoTipo | undefined {
  if (!documento) return undefined
  if (/[A-Za-z]/.test(documento)) return 'PASSAPORTE'
  const d = documento.replace(/\D/g, '')
  if (d.length === 11) return 'CPF'
  if (d.length === 14) return 'CNPJ'
  return undefined
}

/** Rótulo para humanos: "CPF", "CNPJ", "Passaporte". */
export function rotuloDocumento(tipo?: DocumentoTipo | null): string {
  return tipo === 'PASSAPORTE' ? 'Passaporte' : (tipo ?? 'CPF')
}
