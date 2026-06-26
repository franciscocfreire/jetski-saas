'use client'

import { useEffect, useRef, useState } from 'react'
import { Upload, X, FileCheck, Camera } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

export type UploadedFile = {
  file: File
  /** dataURL (base64) — pronto p/ enviar no corpo ou converter p/ presigned. */
  dataUrl: string
}

/**
 * Seletor de arquivo reutilizável com preview (imagem) e estado.
 * Permite escolher um arquivo OU tirar foto com a câmera/webcam (getUserMedia).
 * Retorna o File + dataURL (base64) via `onChange`.
 */
export function FileUpload({
  label,
  accept = 'image/*,application/pdf',
  onChange,
  initialUrl,
}: {
  label: string
  accept?: string
  onChange?: (f: UploadedFile | null) => void
  /** URL de uma imagem já enviada (carrega automaticamente; pode trocar). */
  initialUrl?: string
}) {
  const inputRef = useRef<HTMLInputElement>(null)
  const videoRef = useRef<HTMLVideoElement>(null)
  const [picked, setPicked] = useState<UploadedFile | null>(null)
  const [loading, setLoading] = useState(false)
  const [cameraAberta, setCameraAberta] = useState(false)
  const [stream, setStream] = useState<MediaStream | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [trocar, setTrocar] = useState(false)

  // Liga o stream ao <video> quando a câmera abre.
  useEffect(() => {
    if (cameraAberta && stream && videoRef.current) {
      videoRef.current.srcObject = stream
      videoRef.current.play().catch(() => {})
    }
  }, [cameraAberta, stream])

  // Libera a câmera ao desmontar / trocar de stream.
  useEffect(() => () => stream?.getTracks().forEach((t) => t.stop()), [stream])

  function pick(file: File) {
    setLoading(true)
    const reader = new FileReader()
    reader.onload = () => {
      const uf = { file, dataUrl: String(reader.result) }
      setPicked(uf)
      setLoading(false)
      onChange?.(uf)
    }
    reader.onerror = () => setLoading(false)
    reader.readAsDataURL(file)
  }

  function clear() {
    setPicked(null)
    if (inputRef.current) inputRef.current.value = ''
    onChange?.(null)
  }

  async function abrirCamera() {
    setErro(null)
    try {
      const s = await navigator.mediaDevices
        .getUserMedia({ video: { facingMode: 'environment' } })
        .catch(() => navigator.mediaDevices.getUserMedia({ video: true }))
      setStream(s)
      setCameraAberta(true)
    } catch {
      setErro('Não foi possível acessar a câmera. Verifique as permissões.')
    }
  }

  function fecharCamera() {
    stream?.getTracks().forEach((t) => t.stop())
    setStream(null)
    setCameraAberta(false)
  }

  function capturar() {
    const v = videoRef.current
    if (!v || !v.videoWidth) return
    const canvas = document.createElement('canvas')
    canvas.width = v.videoWidth
    canvas.height = v.videoHeight
    canvas.getContext('2d')?.drawImage(v, 0, 0)
    canvas.toBlob(
      (blob) => {
        if (!blob) return
        const file = new File([blob], `foto-${Date.now()}.jpg`, { type: 'image/jpeg' })
        fecharCamera()
        pick(file)
      },
      'image/jpeg',
      0.9
    )
  }

  const temCamera =
    typeof navigator !== 'undefined' && !!navigator.mediaDevices?.getUserMedia

  const isImage = picked?.file.type.startsWith('image/')

  return (
    <div>
      <input
        ref={inputRef}
        type="file"
        accept={accept}
        className="hidden"
        onChange={(e) => {
          const f = e.target.files?.[0]
          if (f) pick(f)
        }}
      />

      {cameraAberta ? (
        <div className="space-y-2 rounded-xl border bg-card p-3">
          {/* eslint-disable-next-line jsx-a11y/media-has-caption */}
          <video ref={videoRef} playsInline muted className="w-full rounded-md bg-black" />
          <div className="flex gap-2">
            <Button type="button" className="flex-1" onClick={capturar}>
              <Camera className="mr-2 h-4 w-4" /> Capturar
            </Button>
            <Button type="button" variant="outline" onClick={fecharCamera}>
              Cancelar
            </Button>
          </div>
        </div>
      ) : initialUrl && !picked && !trocar ? (
        <div className="flex items-center gap-3 rounded-xl border bg-card p-3">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={initialUrl} alt="anexo enviado" className="h-12 w-12 rounded-md object-cover" />
          <div className="min-w-0 flex-1">
            <p className="text-sm font-medium text-emerald-600">✓ Já enviado</p>
            <p className="text-xs text-muted-foreground">Carregado do cadastro do cliente</p>
          </div>
          <Button type="button" variant="ghost" size="sm" onClick={() => setTrocar(true)}>
            Trocar foto
          </Button>
        </div>
      ) : !picked ? (
        <div className="space-y-2">
          <button
            type="button"
            onClick={() => inputRef.current?.click()}
            className={cn(
              'flex w-full flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed',
              'border-muted-foreground/30 bg-muted/30 px-4 py-6 text-sm text-muted-foreground',
              'transition-colors hover:border-primary/50 hover:text-foreground'
            )}
          >
            <Upload size={20} />
            <span>{loading ? 'Carregando…' : label}</span>
          </button>
          {temCamera && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="w-full"
              onClick={abrirCamera}
            >
              <Camera className="mr-2 h-4 w-4" /> Tirar foto (câmera/webcam)
            </Button>
          )}
          {erro && <p className="text-xs text-destructive">{erro}</p>}
        </div>
      ) : (
        <div className="flex items-center gap-3 rounded-xl border bg-card p-3">
          {isImage ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={picked.dataUrl}
              alt={picked.file.name}
              className="h-12 w-12 rounded-md object-cover"
            />
          ) : (
            <FileCheck className="h-10 w-10 text-emerald-500" />
          )}
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium">{picked.file.name}</p>
            <p className="text-xs text-muted-foreground">
              {(picked.file.size / 1024).toFixed(0)} KB
            </p>
          </div>
          <Button type="button" variant="ghost" size="icon" onClick={clear}>
            <X size={16} />
          </Button>
        </div>
      )}
    </div>
  )
}
