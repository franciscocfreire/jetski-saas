/**
 * Compressão de imagem no navegador antes do upload — reduz fotos de celular
 * (3–8 MB) para centenas de KB mantendo legibilidade de documento. Essencial na
 * operação de praia (sinal fraco) e para não estourar limites de upload.
 *
 * A qualidade/resolução é parametrizada pela plataforma (super admin) por tipo de
 * documento. Assinatura (PNG line-art) NÃO passa por aqui — não deve virar JPEG.
 */
import { corrigirOrientacao } from '@/lib/image-orientation'

export type PresetCompressao = {
  /** Lado maior (px) para o qual a imagem é reduzida. */
  maxDimensao: number
  /** Qualidade JPEG (0.3–1.0). */
  qualidade: number
}

/**
 * Devolve o arquivo comprimido (JPEG) redimensionado ao preset. Não-imagem (PDF)
 * e qualquer falha → devolve o original (a compressão nunca bloqueia o upload).
 */
export async function comprimirImagem(file: File, preset: PresetCompressao): Promise<File> {
  if (!file.type.startsWith('image/')) return file

  try {
    // 1) Orientação EXIF já "assada" nos pixels (foto de celular vem deitada).
    const base = await corrigirOrientacao(file).catch(() => file)

    // 2) Redimensiona (lado maior ≤ maxDimensao) + reexporta JPEG na qualidade.
    const bitmap = await createImageBitmap(base)
    const { width: w, height: h } = bitmap
    const maior = Math.max(w, h)
    const escala = maior > preset.maxDimensao ? preset.maxDimensao / maior : 1

    const canvas = document.createElement('canvas')
    canvas.width = Math.round(w * escala)
    canvas.height = Math.round(h * escala)
    const ctx = canvas.getContext('2d')
    if (!ctx) {
      bitmap.close?.()
      return base
    }
    ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
    bitmap.close?.()

    const blob = await new Promise<Blob | null>((res) =>
      canvas.toBlob(res, 'image/jpeg', preset.qualidade)
    )
    if (!blob) return base
    // Se, por acaso, o "comprimido" ficou maior (imagem já pequena), fica o menor.
    if (blob.size >= base.size && escala === 1) return base

    const nome = file.name.replace(/\.[^.]+$/, '') + '.jpg'
    return new File([blob], nome, { type: 'image/jpeg' })
  } catch {
    return file
  }
}
