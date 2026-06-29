/**
 * Abre um PDF por uma URL pública temporária (uso único) — a forma mais confiável
 * no iOS Safari, que renderiza PDFs de URLs https nativamente (mas não blob: em aba
 * nova). `mintUrl` faz a chamada autenticada que gera o PDF no servidor e devolve
 * a URL ({url}); a aba é aberta sincronicamente (preserva o gesto do clique).
 */
export async function abrirPdfPorLink(mintUrl: () => Promise<{ url: string }>): Promise<void> {
  const win = window.open('', '_blank')
  try {
    const { url } = await mintUrl()
    if (win && !win.closed) {
      win.location.href = url
    } else {
      window.location.href = url // popup bloqueado → abre na mesma aba
    }
  } catch (e) {
    win?.close()
    throw e
  }
}

/**
 * Abre um PDF (obtido via fetch autenticado → Blob) de forma compatível com iOS.
 *
 * iOS Safari só permite `window.open` dentro do gesto do clique; depois de um
 * `await` o gesto se perde e a aba é bloqueada (sintoma: "não abre"/aba em branco).
 * Por isso abrimos a aba SINCRONAMENTE e só então apontamos para o object URL.
 * Se o popup for bloqueado, caímos para um download via âncora.
 */
export async function abrirPdfBlob(
  fetcher: () => Promise<Blob>,
  filename = 'documento.pdf'
): Promise<void> {
  // Aba aberta ainda dentro do gesto do usuário (crucial no iOS).
  const win = window.open('', '_blank')

  let blob: Blob
  try {
    blob = await fetcher()
  } catch (e) {
    win?.close()
    throw e
  }

  // Garante o tipo correto (iOS é exigente com application/pdf).
  const pdf = blob.type === 'application/pdf' ? blob : new Blob([blob], { type: 'application/pdf' })
  const url = URL.createObjectURL(pdf)

  if (win && !win.closed) {
    win.location.href = url
  } else {
    // Popup bloqueado → baixa/abre via âncora (também funciona no iOS).
    const a = document.createElement('a')
    a.href = url
    a.target = '_blank'
    a.rel = 'noopener'
    a.download = filename
    document.body.appendChild(a)
    a.click()
    a.remove()
  }

  // Revoga depois de um tempo (o visualizador já carregou o conteúdo).
  setTimeout(() => URL.revokeObjectURL(url), 60_000)
}
