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
