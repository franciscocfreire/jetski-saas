'use client'

import { useEffect, useRef, useState } from 'react'
import { Upload, X, FileCheck, Camera, SwitchCamera, ZoomIn, ExternalLink } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { cn } from '@/lib/utils'
import { comprimirImagem } from '@/lib/image-compress'
import { useImagemConfig } from '@/lib/hooks/use-imagem-config'
import type { TipoImagemDoc } from '@/lib/api/types'

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
  tipoDocumento,
  cameraPadrao,
  testId,
}: {
  label: string
  accept?: string
  onChange?: (f: UploadedFile | null) => void
  /** URL de uma imagem já enviada (carrega automaticamente; pode trocar). */
  initialUrl?: string
  /** Tipo do documento — define o preset de compressão da imagem (plataforma). */
  tipoDocumento?: TipoImagemDoc
  /**
   * Câmera que abre por padrão: "user" (frontal, selfie) ou "environment" (traseira).
   * Sem valor, selfie abre na frontal e o resto na traseira.
   */
  cameraPadrao?: 'user' | 'environment'
  /**
   * Identificador estável para automação (harness de capturas, Playwright).
   * Vai no <input type=file> — que é oculto, mas aceita setInputFiles() sem
   * abrir diálogo nenhum. Sem isto a automação teria que caçar o botão pelo
   * texto do rótulo, que muda.
   */
  testId?: string
}) {
  const { presetPara } = useImagemConfig()
  const inputRef = useRef<HTMLInputElement>(null)
  const videoRef = useRef<HTMLVideoElement>(null)
  const [picked, setPicked] = useState<UploadedFile | null>(null)
  const [loading, setLoading] = useState(false)
  const [cameraAberta, setCameraAberta] = useState(false)
  const [stream, setStream] = useState<MediaStream | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [trocar, setTrocar] = useState(false)
  // Visualização ampliada p/ conferir o documento (legibilidade, dados, rosto).
  const [ampliada, setAmpliada] = useState<string | null>(null)
  const [ampliadaNaoImagem, setAmpliadaNaoImagem] = useState(false)
  function abrirAmpliada(url: string) {
    setAmpliadaNaoImagem(false)
    setAmpliada(url)
  }
  const [facing, setFacing] = useState<'user' | 'environment'>(
    cameraPadrao ?? (tipoDocumento === 'SELFIE' ? 'user' : 'environment')
  )
  const [variasCameras, setVariasCameras] = useState(false)

  // Liga o stream ao <video> quando a câmera abre.
  useEffect(() => {
    if (cameraAberta && stream && videoRef.current) {
      videoRef.current.srcObject = stream
      videoRef.current.play().catch(() => {})
    }
  }, [cameraAberta, stream])

  // Libera a câmera ao desmontar / trocar de stream.
  useEffect(() => () => stream?.getTracks().forEach((t) => t.stop()), [stream])

  async function pick(fileOriginal: File) {
    setLoading(true)
    // Comprime a imagem (orientação EXIF + resize + qualidade do preset) antes de
    // ler. Foto de celular de vários MB vira centenas de KB — leve na rede da praia
    // e sem estourar limite de upload. PDF/assinatura passam intactos.
    const file = await comprimirImagem(fileOriginal, presetPara(tipoDocumento)).catch(() => fileOriginal)
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

  async function abrirCamera(modo: 'user' | 'environment' = facing) {
    setErro(null)
    try {
      const s = await navigator.mediaDevices
        .getUserMedia({ video: { facingMode: { ideal: modo } } })
        .catch(() => navigator.mediaDevices.getUserMedia({ video: true }))
      setStream(s)
      setFacing(modo)
      setCameraAberta(true)
      // Só faz sentido oferecer a troca quando existe mais de uma câmera. A
      // contagem só é confiável depois da permissão concedida (acima).
      navigator.mediaDevices
        .enumerateDevices()
        .then((ds) => setVariasCameras(ds.filter((d) => d.kind === 'videoinput').length > 1))
        .catch(() => {})
    } catch {
      setErro('Não foi possível acessar a câmera. Verifique as permissões.')
    }
  }

  function trocarCamera() {
    // Libera a câmera atual antes de pedir a outra: em vários celulares as duas
    // não abrem ao mesmo tempo e o getUserMedia falharia.
    stream?.getTracks().forEach((t) => t.stop())
    setStream(null)
    abrirCamera(facing === 'environment' ? 'user' : 'environment')
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
        data-testid={testId}
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
          <div className="relative">
            {/* eslint-disable-next-line jsx-a11y/media-has-caption */}
            <video
              ref={videoRef}
              playsInline
              muted
              className={cn(
                'w-full rounded-md bg-black',
                // Selfie fica espelhada na prévia (como o usuário se vê no espelho);
                // o arquivo capturado sai sem espelho.
                facing === 'user' && '-scale-x-100'
              )}
            />
            {variasCameras && (
              <Button
                type="button"
                variant="secondary"
                size="icon"
                title="Trocar câmera (frontal/traseira)"
                aria-label="Trocar câmera"
                className="absolute right-2 top-2 h-8 w-8 rounded-full bg-black/50 text-white hover:bg-black/70"
                onClick={trocarCamera}
              >
                <SwitchCamera className="h-4 w-4" />
              </Button>
            )}
          </div>
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
          <Miniatura src={initialUrl} alt="anexo enviado" onClick={() => abrirAmpliada(initialUrl)} />
          <div className="min-w-0 flex-1">
            <p className="text-sm font-medium text-emerald-600">✓ Já enviado</p>
            <p className="text-xs text-muted-foreground">Carregado do cadastro do cliente</p>
          </div>
          <Button type="button" variant="ghost" size="sm" onClick={() => abrirAmpliada(initialUrl)}>
            <ZoomIn className="mr-1 h-4 w-4" /> Conferir
          </Button>
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
              onClick={() => abrirCamera()}
            >
              <Camera className="mr-2 h-4 w-4" /> Tirar foto (câmera/webcam)
            </Button>
          )}
          {erro && <p className="text-xs text-destructive">{erro}</p>}
        </div>
      ) : (
        <div className="flex items-center gap-3 rounded-xl border bg-card p-3">
          {isImage ? (
            <Miniatura src={picked.dataUrl} alt={picked.file.name} onClick={() => abrirAmpliada(picked.dataUrl)} />
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

      <Dialog open={!!ampliada} onOpenChange={(aberto) => !aberto && setAmpliada(null)}>
        <DialogContent className="max-w-4xl">
          <DialogHeader>
            <DialogTitle>Conferir documento</DialogTitle>
          </DialogHeader>
          {ampliada &&
            (ampliadaNaoImagem ? (
              // Anexo em PDF (ex.: comprovante de residência): o navegador renderiza.
              <iframe src={ampliada} title="Documento" className="h-[75vh] w-full rounded-md border" />
            ) : (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={ampliada}
                alt="Documento ampliado"
                className="max-h-[75vh] w-full rounded-md bg-muted object-contain"
                onError={() => setAmpliadaNaoImagem(true)}
              />
            ))}
          {ampliada && (
            <a
              href={ampliada}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1 text-sm text-primary underline-offset-2 hover:underline"
            >
              <ExternalLink className="h-4 w-4" /> Abrir em nova aba
            </a>
          )}
        </DialogContent>
      </Dialog>
    </div>
  )
}

/** Miniatura clicável: abre a visualização ampliada. */
function Miniatura({ src, alt, onClick }: { src: string; alt: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      title="Ampliar para conferir"
      className="group relative h-12 w-12 shrink-0 overflow-hidden rounded-md"
    >
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src={src} alt={alt} className="h-12 w-12 object-cover" />
      <span className="absolute inset-0 hidden items-center justify-center bg-black/40 text-white group-hover:flex">
        <ZoomIn size={16} />
      </span>
    </button>
  )
}
