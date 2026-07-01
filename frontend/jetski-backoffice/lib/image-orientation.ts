/**
 * Corrige a orientação de fotos de celular (selfie/documento) que chegam "deitadas".
 *
 * Câmeras de celular gravam a imagem no sensor + um metadado EXIF "Orientation"
 * (1..8) indicando como rotacioná-la para exibição. Quem não aplica esse metadado
 * — como o OpenPDF no backend — mostra a foto girada. Aqui lemos a orientação EXIF
 * e reexportamos a imagem já rotacionada (JPEG), removendo a dependência do EXIF.
 */

/** Lê o tag EXIF Orientation (1..8) de um JPEG. 1 = sem rotação / desconhecido. */
async function lerOrientacaoExif(file: File): Promise<number> {
  // Só os primeiros KB carregam o EXIF; evita ler o arquivo inteiro.
  const buf = await file.slice(0, 256 * 1024).arrayBuffer()
  const view = new DataView(buf)
  if (view.byteLength < 2 || view.getUint16(0) !== 0xffd8) return 1 // não é JPEG (SOI)

  let offset = 2
  while (offset + 4 <= view.byteLength) {
    const marker = view.getUint16(offset)
    offset += 2
    if (marker === 0xffe1) {
      // APP1 (EXIF)
      if (view.getUint32(offset + 2) !== 0x45786966) return 1 // "Exif"
      const tiff = offset + 8
      const little = view.getUint16(tiff) === 0x4949 // "II" = little-endian
      const u16 = (o: number) => view.getUint16(o, little)
      const u32 = (o: number) => view.getUint32(o, little)
      const ifd = tiff + u32(tiff + 4)
      const entries = u16(ifd)
      for (let i = 0; i < entries; i++) {
        const entry = ifd + 2 + i * 12
        if (u16(entry) === 0x0112) return u16(entry + 8) || 1 // Orientation
      }
      return 1
    } else if ((marker & 0xff00) !== 0xff00) {
      break // fora dos marcadores JPEG
    } else {
      offset += view.getUint16(offset) // pula o segmento
    }
  }
  return 1
}

/** Aplica a transformação de canvas correspondente à orientação EXIF (2..8). */
function aplicarTransform(
  ctx: CanvasRenderingContext2D,
  orientation: number,
  w: number,
  h: number
) {
  switch (orientation) {
    case 2: ctx.transform(-1, 0, 0, 1, w, 0); break // espelho horizontal
    case 3: ctx.transform(-1, 0, 0, -1, w, h); break // 180°
    case 4: ctx.transform(1, 0, 0, -1, 0, h); break // espelho vertical
    case 5: ctx.transform(0, 1, 1, 0, 0, 0); break // transpõe
    case 6: ctx.transform(0, 1, -1, 0, h, 0); break // 90° horário
    case 7: ctx.transform(0, -1, -1, 0, h, w); break // transpõe + espelho
    case 8: ctx.transform(0, -1, 1, 0, 0, w); break // 90° anti-horário
    default: break
  }
}

/**
 * Devolve uma cópia do arquivo com a orientação já "assada" nos pixels. Imagens
 * já corretas (orientation 1) e não-imagens (PDF) passam inalteradas.
 */
export async function corrigirOrientacao(file: File): Promise<File> {
  if (!file.type.startsWith('image/')) return file

  let orientation = 1
  try {
    orientation = await lerOrientacaoExif(file)
  } catch {
    return file
  }
  if (orientation === 1) return file // nada a fazer

  try {
    // 'none' força pixels crus (sem o navegador já rotacionar) — nós controlamos.
    const bitmap = await createImageBitmap(file, { imageOrientation: 'none' })
    const canvas = document.createElement('canvas')
    const { width: w, height: h } = bitmap
    // Orientações 5..8 giram 90° → dimensões trocam.
    canvas.width = orientation >= 5 ? h : w
    canvas.height = orientation >= 5 ? w : h
    const ctx = canvas.getContext('2d')
    if (!ctx) {
      bitmap.close?.()
      return file
    }
    aplicarTransform(ctx, orientation, w, h)
    ctx.drawImage(bitmap, 0, 0)
    bitmap.close?.()

    const blob = await new Promise<Blob | null>((res) =>
      canvas.toBlob(res, 'image/jpeg', 0.92)
    )
    if (!blob) return file
    const nome = file.name.replace(/\.[^.]+$/, '') + '.jpg'
    return new File([blob], nome, { type: 'image/jpeg' })
  } catch {
    return file // qualquer falha → devolve o original (não bloqueia o upload)
  }
}
