'use client'

import { useEffect, useState } from 'react'

/**
 * Object URL (`blob:`) de um Blob, criado e liberado pelo PRÓPRIO componente.
 *
 * Nunca crie object URLs dentro de uma `queryFn`: o cache do React Query sobrevive
 * ao componente (staleTime global de 60 s). Quem revoga a URL ao trocar de item
 * deixa no cache uma URL morta, que volta na próxima abertura — imagem quebrada e
 * `blob:` com ERR_FILE_NOT_FOUND. O Blob, sim, pode ficar no cache.
 */
export function useObjectUrl(blob?: Blob | null): string | undefined {
  const [url, setUrl] = useState<string>()

  useEffect(() => {
    if (!blob) {
      setUrl(undefined)
      return
    }
    const u = URL.createObjectURL(blob)
    setUrl(u)
    return () => URL.revokeObjectURL(u)
  }, [blob])

  return url
}

/** Idem, para um mapa de Blobs (ex.: anexos por tipo). */
export function useObjectUrls<K extends string>(
  blobs?: Partial<Record<K, Blob>> | null
): Partial<Record<K, string>> {
  const [urls, setUrls] = useState<Partial<Record<K, string>>>({})

  useEffect(() => {
    if (!blobs) {
      setUrls({})
      return
    }
    const criadas: Partial<Record<K, string>> = {}
    for (const [chave, blob] of Object.entries(blobs) as [K, Blob | undefined][]) {
      if (blob) criadas[chave] = URL.createObjectURL(blob)
    }
    setUrls(criadas)
    return () => {
      Object.values(criadas).forEach((u) => URL.revokeObjectURL(u as string))
    }
  }, [blobs])

  return urls
}
