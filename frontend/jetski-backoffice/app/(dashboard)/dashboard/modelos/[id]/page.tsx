'use client'

import { useState, useEffect } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  ArrowLeft,
  Edit,
  Trash2,
  Plus,
  Image as ImageIcon,
  Video,
  Star,
  GripVertical,
  Eye,
  EyeOff,
  Globe,
  DollarSign,
  Users,
  Gauge,
  Fuel,
  Shield,
  Clock,
  X,
  Check,
  Store,
} from 'lucide-react'
import { useTenantStore } from '@/lib/store/tenant-store'
import { modelosService, type ModeloCreateRequest } from '@/lib/api/services/modelos'
import type { Modelo, ModeloMidia, TipoMidia, ModeloMidiaCreateRequest } from '@/lib/api/types'
import { formatCurrency } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Switch } from '@/components/ui/switch'
import { Skeleton } from '@/components/ui/skeleton'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'

// Stats Card Component
function StatCard({
  icon: Icon,
  label,
  value,
  description,
}: {
  icon: React.ElementType
  label: string
  value: string | number
  description?: string
}) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-sm font-medium">{label}</CardTitle>
        <Icon className="h-4 w-4 text-muted-foreground" />
      </CardHeader>
      <CardContent>
        <div className="text-2xl font-bold">{value}</div>
        {description && <p className="text-xs text-muted-foreground">{description}</p>}
      </CardContent>
    </Card>
  )
}

// Helper to detect and parse video URLs
function parseVideoUrl(url: string): { type: 'youtube' | 'vimeo' | 'direct'; embedUrl?: string; videoId?: string } {
  // YouTube
  const youtubeMatch = url.match(/(?:youtube\.com\/(?:watch\?v=|embed\/)|youtu\.be\/)([a-zA-Z0-9_-]{11})/)
  if (youtubeMatch) {
    return { type: 'youtube', videoId: youtubeMatch[1], embedUrl: `https://www.youtube.com/embed/${youtubeMatch[1]}` }
  }

  // Vimeo
  const vimeoMatch = url.match(/(?:vimeo\.com\/)(\d+)/)
  if (vimeoMatch) {
    return { type: 'vimeo', videoId: vimeoMatch[1], embedUrl: `https://player.vimeo.com/video/${vimeoMatch[1]}` }
  }

  return { type: 'direct' }
}

// Video Player Component with YouTube/Vimeo support
function VideoPlayer({ url, thumbnailUrl }: { url: string; thumbnailUrl?: string }) {
  const [isPlaying, setIsPlaying] = useState(false)
  const parsed = parseVideoUrl(url)

  if (parsed.type === 'youtube' || parsed.type === 'vimeo') {
    return (
      <div className="relative h-full w-full">
        {!isPlaying ? (
          <>
            {/* Thumbnail */}
            {thumbnailUrl ? (
              <img
                src={thumbnailUrl}
                alt="Video thumbnail"
                className="h-full w-full object-cover"
              />
            ) : parsed.type === 'youtube' ? (
              <img
                src={`https://img.youtube.com/vi/${parsed.videoId}/maxresdefault.jpg`}
                alt="Video thumbnail"
                className="h-full w-full object-cover"
                onError={(e) => {
                  // Fallback to hqdefault if maxresdefault not available
                  e.currentTarget.src = `https://img.youtube.com/vi/${parsed.videoId}/hqdefault.jpg`
                }}
              />
            ) : (
              <div className="h-full w-full bg-muted flex items-center justify-center">
                <Video className="h-12 w-12 text-muted-foreground" />
              </div>
            )}
            {/* Play button overlay */}
            <button
              onClick={() => setIsPlaying(true)}
              className="absolute inset-0 flex items-center justify-center bg-black/30 hover:bg-black/40 transition-colors"
            >
              <div className="w-16 h-16 rounded-full bg-red-600 flex items-center justify-center shadow-lg hover:scale-110 transition-transform">
                <svg className="h-8 w-8 text-white ml-1" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M8 5v14l11-7z" />
                </svg>
              </div>
            </button>
          </>
        ) : (
          <iframe
            src={`${parsed.embedUrl}?autoplay=1`}
            className="h-full w-full"
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
            allowFullScreen
          />
        )}
      </div>
    )
  }

  // Direct video URL
  return (
    <video
      src={url}
      poster={thumbnailUrl}
      controls
      preload="metadata"
      playsInline
      className="h-full w-full object-cover"
    >
      <source src={url} type="video/mp4" />
      <source src={url} type="video/webm" />
      Seu navegador não suporta vídeo.
    </video>
  )
}

// Edit Media Dialog Component
function EditMediaDialog({
  midia,
  modeloId,
  open,
  onOpenChange,
}: {
  midia: ModeloMidia
  modeloId: string
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const [formData, setFormData] = useState({
    tipo: midia.tipo,
    url: midia.url,
    thumbnailUrl: midia.thumbnailUrl || '',
    titulo: midia.titulo || '',
  })

  useEffect(() => {
    setFormData({
      tipo: midia.tipo,
      url: midia.url,
      thumbnailUrl: midia.thumbnailUrl || '',
      titulo: midia.titulo || '',
    })
  }, [midia])

  const updateMutation = useMutation({
    mutationFn: (data: Partial<ModeloMidiaCreateRequest>) =>
      modelosService.midia.update(modeloId, midia.id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['modelo-midias', modeloId] })
      queryClient.invalidateQueries({ queryKey: ['modelo', modeloId] })
      onOpenChange(false)
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    updateMutation.mutate(formData)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[500px]">
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Editar Mídia</DialogTitle>
            <DialogDescription>
              Atualize as informações da {midia.tipo === 'IMAGEM' ? 'imagem' : 'vídeo'}
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label>Tipo</Label>
              <Select
                value={formData.tipo}
                onValueChange={(value: TipoMidia) => setFormData({ ...formData, tipo: value })}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="IMAGEM">
                    <div className="flex items-center gap-2">
                      <ImageIcon className="h-4 w-4" />
                      Imagem
                    </div>
                  </SelectItem>
                  <SelectItem value="VIDEO">
                    <div className="flex items-center gap-2">
                      <Video className="h-4 w-4" />
                      Vídeo
                    </div>
                  </SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="edit-url">URL *</Label>
              <Input
                id="edit-url"
                value={formData.url}
                onChange={(e) => setFormData({ ...formData, url: e.target.value })}
                placeholder="https://..."
                required
              />
            </div>

            {formData.tipo === 'VIDEO' && (
              <div className="grid gap-2">
                <Label htmlFor="edit-thumbnailUrl">URL da Thumbnail</Label>
                <Input
                  id="edit-thumbnailUrl"
                  value={formData.thumbnailUrl}
                  onChange={(e) => setFormData({ ...formData, thumbnailUrl: e.target.value })}
                  placeholder="https://..."
                />
                <p className="text-xs text-muted-foreground">
                  Imagem exibida antes do vídeo ser reproduzido
                </p>
              </div>
            )}

            <div className="grid gap-2">
              <Label htmlFor="edit-titulo">Título</Label>
              <Input
                id="edit-titulo"
                value={formData.titulo}
                onChange={(e) => setFormData({ ...formData, titulo: e.target.value })}
                placeholder="Ex: Vista lateral"
              />
            </div>

            {/* Preview */}
            {formData.url && formData.tipo === 'IMAGEM' && (
              <div className="grid gap-2">
                <Label>Preview</Label>
                <div className="aspect-video rounded-lg border bg-muted overflow-hidden">
                  <img
                    src={formData.url}
                    alt="Preview"
                    className="h-full w-full object-cover"
                    onError={(e) => {
                      e.currentTarget.style.display = 'none'
                    }}
                  />
                </div>
              </div>
            )}
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={updateMutation.isPending}>
              {updateMutation.isPending ? 'Salvando...' : 'Salvar'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

// Media Item Component
function MediaItem({
  midia,
  modeloId,
  onSetPrincipal,
  onDelete,
  isPrincipal,
}: {
  midia: ModeloMidia
  modeloId: string
  onSetPrincipal: () => void
  onDelete: () => void
  isPrincipal: boolean
}) {
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [showEditDialog, setShowEditDialog] = useState(false)

  return (
    <>
      <div className="group relative aspect-video rounded-lg border bg-muted overflow-hidden">
        {midia.tipo === 'IMAGEM' ? (
          <img
            src={midia.url}
            alt={midia.titulo || 'Imagem do modelo'}
            className="h-full w-full object-cover"
          />
        ) : (
          <VideoPlayer url={midia.url} thumbnailUrl={midia.thumbnailUrl} />
        )}

        {/* Principal Badge */}
        {isPrincipal && (
          <div className="absolute top-2 left-2">
            <Badge variant="default" className="bg-yellow-500">
              <Star className="mr-1 h-3 w-3" />
              Principal
            </Badge>
          </div>
        )}

        {/* Title Badge */}
        {midia.titulo && (
          <div className="absolute bottom-2 left-2 right-2">
            <span className="text-xs text-white bg-black/50 px-2 py-1 rounded">
              {midia.titulo}
            </span>
          </div>
        )}

        {/* Hover Actions */}
        <div className="absolute inset-0 bg-black/60 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center gap-2">
          {midia.tipo === 'IMAGEM' && !isPrincipal && (
            <Button size="sm" variant="secondary" onClick={onSetPrincipal}>
              <Star className="mr-1 h-3 w-3" />
              Principal
            </Button>
          )}
          <Button size="sm" variant="secondary" onClick={() => setShowEditDialog(true)}>
            <Edit className="h-4 w-4" />
          </Button>
          <Button size="sm" variant="destructive" onClick={() => setShowDeleteConfirm(true)}>
            <Trash2 className="h-4 w-4" />
          </Button>
        </div>

        {/* Drag Handle */}
        <div className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 cursor-grab">
          <GripVertical className="h-5 w-5 text-white" />
        </div>
      </div>

      {/* Edit Dialog */}
      <EditMediaDialog
        midia={midia}
        modeloId={modeloId}
        open={showEditDialog}
        onOpenChange={setShowEditDialog}
      />

      {/* Delete Confirmation */}
      <AlertDialog open={showDeleteConfirm} onOpenChange={setShowDeleteConfirm}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Excluir mídia?</AlertDialogTitle>
            <AlertDialogDescription>
              Esta ação não pode ser desfeita. A {midia.tipo === 'IMAGEM' ? 'imagem' : 'vídeo'} será
              removida permanentemente.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancelar</AlertDialogCancel>
            <AlertDialogAction onClick={onDelete} className="bg-destructive text-destructive-foreground">
              Excluir
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}

// Add Media Dialog
function AddMediaDialog({
  modeloId,
  open,
  onOpenChange,
}: {
  modeloId: string
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const [formData, setFormData] = useState<ModeloMidiaCreateRequest>({
    tipo: 'IMAGEM',
    url: '',
    thumbnailUrl: '',
    titulo: '',
    principal: false,
  })

  const addMutation = useMutation({
    mutationFn: (data: ModeloMidiaCreateRequest) => modelosService.midia.add(modeloId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['modelo-midias', modeloId] })
      queryClient.invalidateQueries({ queryKey: ['modelo', modeloId] })
      onOpenChange(false)
      setFormData({ tipo: 'IMAGEM', url: '', thumbnailUrl: '', titulo: '', principal: false })
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    addMutation.mutate(formData)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[500px]">
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Adicionar Mídia</DialogTitle>
            <DialogDescription>
              Adicione uma imagem ou vídeo para o modelo
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label>Tipo</Label>
              <Select
                value={formData.tipo}
                onValueChange={(value: TipoMidia) => setFormData({ ...formData, tipo: value })}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="IMAGEM">
                    <div className="flex items-center gap-2">
                      <ImageIcon className="h-4 w-4" />
                      Imagem
                    </div>
                  </SelectItem>
                  <SelectItem value="VIDEO">
                    <div className="flex items-center gap-2">
                      <Video className="h-4 w-4" />
                      Vídeo
                    </div>
                  </SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="url">URL *</Label>
              <Input
                id="url"
                value={formData.url}
                onChange={(e) => setFormData({ ...formData, url: e.target.value })}
                placeholder="https://..."
                required
              />
              <p className="text-xs text-muted-foreground">
                Cole a URL da imagem ou vídeo
              </p>
            </div>

            {formData.tipo === 'VIDEO' && (
              <div className="grid gap-2">
                <Label htmlFor="thumbnailUrl">URL da Thumbnail</Label>
                <Input
                  id="thumbnailUrl"
                  value={formData.thumbnailUrl || ''}
                  onChange={(e) => setFormData({ ...formData, thumbnailUrl: e.target.value })}
                  placeholder="https://..."
                />
              </div>
            )}

            <div className="grid gap-2">
              <Label htmlFor="titulo">Título (opcional)</Label>
              <Input
                id="titulo"
                value={formData.titulo || ''}
                onChange={(e) => setFormData({ ...formData, titulo: e.target.value })}
                placeholder="Ex: Vista lateral"
              />
            </div>

            {formData.tipo === 'IMAGEM' && (
              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label>Definir como principal</Label>
                  <p className="text-xs text-muted-foreground">
                    Esta será a imagem exibida no marketplace
                  </p>
                </div>
                <Switch
                  checked={formData.principal || false}
                  onCheckedChange={(checked) => setFormData({ ...formData, principal: checked })}
                />
              </div>
            )}

            {/* Preview */}
            {formData.url && formData.tipo === 'IMAGEM' && (
              <div className="grid gap-2">
                <Label>Preview</Label>
                <div className="aspect-video rounded-lg border bg-muted overflow-hidden">
                  <img
                    src={formData.url}
                    alt="Preview"
                    className="h-full w-full object-cover"
                    onError={(e) => {
                      e.currentTarget.style.display = 'none'
                    }}
                  />
                </div>
              </div>
            )}
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={addMutation.isPending}>
              {addMutation.isPending ? 'Adicionando...' : 'Adicionar'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

// Edit Modelo Dialog
function EditModeloDialog({
  modelo,
  open,
  onOpenChange,
}: {
  modelo: Modelo
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const queryClient = useQueryClient()
  const [formData, setFormData] = useState<ModeloCreateRequest>({
    nome: modelo.nome || '',
    fabricante: modelo.fabricante || '',
    potenciaHp: modelo.potenciaHp || 90,
    capacidadePessoas: modelo.capacidadePessoas || 2,
    precoBaseHora: modelo.precoBaseHora || 150,
    toleranciaMin: modelo.toleranciaMin || 5,
    taxaHoraExtra: modelo.taxaHoraExtra || 50,
    incluiCombustivel: modelo.incluiCombustivel || false,
    caucao: modelo.caucao || 300,
    exibirNoMarketplace: modelo.exibirNoMarketplace ?? true,
  })

  useEffect(() => {
    setFormData({
      nome: modelo.nome || '',
      fabricante: modelo.fabricante || '',
      potenciaHp: modelo.potenciaHp || 90,
      capacidadePessoas: modelo.capacidadePessoas || 2,
      precoBaseHora: modelo.precoBaseHora || 150,
      toleranciaMin: modelo.toleranciaMin || 5,
      taxaHoraExtra: modelo.taxaHoraExtra || 50,
      incluiCombustivel: modelo.incluiCombustivel || false,
      caucao: modelo.caucao || 300,
      exibirNoMarketplace: modelo.exibirNoMarketplace ?? true,
    })
  }, [modelo])

  const updateMutation = useMutation({
    mutationFn: (data: Partial<ModeloCreateRequest>) => modelosService.update(modelo.id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['modelo', modelo.id] })
      queryClient.invalidateQueries({ queryKey: ['modelos'] })
      onOpenChange(false)
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    updateMutation.mutate(formData)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[600px]">
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Editar Modelo</DialogTitle>
            <DialogDescription>
              Atualize os dados do modelo
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-4 max-h-[60vh] overflow-y-auto">
            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="nome">Nome *</Label>
                <Input
                  id="nome"
                  value={formData.nome}
                  onChange={(e) => setFormData({ ...formData, nome: e.target.value })}
                  placeholder="Ex: Sea-Doo GTI 130"
                  required
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="fabricante">Fabricante</Label>
                <Input
                  id="fabricante"
                  value={formData.fabricante || ''}
                  onChange={(e) => setFormData({ ...formData, fabricante: e.target.value })}
                  placeholder="Ex: Sea-Doo"
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="potenciaHp">Potência (HP)</Label>
                <Input
                  id="potenciaHp"
                  type="number"
                  value={formData.potenciaHp || 90}
                  onChange={(e) => setFormData({ ...formData, potenciaHp: Number(e.target.value) })}
                  min={0}
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="capacidadePessoas">Capacidade (pessoas) *</Label>
                <Input
                  id="capacidadePessoas"
                  type="number"
                  value={formData.capacidadePessoas}
                  onChange={(e) => setFormData({ ...formData, capacidadePessoas: Number(e.target.value) })}
                  min={1}
                  max={4}
                  required
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="precoBase">Preço/Hora (R$) *</Label>
                <Input
                  id="precoBase"
                  type="number"
                  value={formData.precoBaseHora}
                  onChange={(e) => setFormData({ ...formData, precoBaseHora: Number(e.target.value) })}
                  min={0}
                  step={10}
                  required
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="taxaHoraExtra">Taxa Hora Extra (R$)</Label>
                <Input
                  id="taxaHoraExtra"
                  type="number"
                  value={formData.taxaHoraExtra || 0}
                  onChange={(e) => setFormData({ ...formData, taxaHoraExtra: Number(e.target.value) })}
                  min={0}
                  step={10}
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="toleranciaMin">Tolerância (min)</Label>
                <Input
                  id="toleranciaMin"
                  type="number"
                  value={formData.toleranciaMin || 5}
                  onChange={(e) => setFormData({ ...formData, toleranciaMin: Number(e.target.value) })}
                  min={0}
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="caucao">Caução (R$)</Label>
                <Input
                  id="caucao"
                  type="number"
                  value={formData.caucao || 0}
                  onChange={(e) => setFormData({ ...formData, caucao: Number(e.target.value) })}
                  min={0}
                  step={50}
                />
              </div>
            </div>

            <div className="flex items-center justify-between rounded-lg border p-4">
              <div className="space-y-0.5">
                <Label>Inclui Combustível</Label>
                <p className="text-xs text-muted-foreground">
                  O combustível está incluso no preço da locação
                </p>
              </div>
              <Switch
                checked={formData.incluiCombustivel || false}
                onCheckedChange={(checked) => setFormData({ ...formData, incluiCombustivel: checked })}
              />
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={updateMutation.isPending}>
              {updateMutation.isPending ? 'Salvando...' : 'Salvar'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

export default function ModeloDetailsPage() {
  const params = useParams()
  const router = useRouter()
  const queryClient = useQueryClient()
  const { currentTenant } = useTenantStore()
  const modeloId = params.id as string

  const [editDialogOpen, setEditDialogOpen] = useState(false)
  const [addMediaDialogOpen, setAddMediaDialogOpen] = useState(false)

  // Fetch modelo
  const { data: modelo, isLoading } = useQuery({
    queryKey: ['modelo', modeloId],
    queryFn: () => modelosService.getById(modeloId),
    enabled: !!currentTenant && !!modeloId,
  })

  // Fetch midias
  const { data: midias, isLoading: midiasLoading } = useQuery({
    queryKey: ['modelo-midias', modeloId],
    queryFn: () => modelosService.midia.list(modeloId),
    enabled: !!currentTenant && !!modeloId,
  })

  // Toggle marketplace mutation
  const toggleMarketplaceMutation = useMutation({
    mutationFn: (exibir: boolean) => modelosService.toggleMarketplace(modeloId, exibir),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['modelo', modeloId] })
      queryClient.invalidateQueries({ queryKey: ['modelos'] })
    },
  })

  // Set principal mutation
  const setPrincipalMutation = useMutation({
    mutationFn: (midiaId: string) => modelosService.midia.setPrincipal(modeloId, midiaId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['modelo-midias', modeloId] })
      queryClient.invalidateQueries({ queryKey: ['modelo', modeloId] })
    },
  })

  // Delete midia mutation
  const deleteMidiaMutation = useMutation({
    mutationFn: (midiaId: string) => modelosService.midia.delete(modeloId, midiaId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['modelo-midias', modeloId] })
      queryClient.invalidateQueries({ queryKey: ['modelo', modeloId] })
    },
  })

  if (!currentTenant) {
    return (
      <div className="flex h-[50vh] items-center justify-center">
        <p className="text-muted-foreground">Selecione um tenant para continuar</p>
      </div>
    )
  }

  if (isLoading) {
    return (
      <div className="space-y-6">
        <div className="flex items-center gap-4">
          <Skeleton className="h-10 w-10" />
          <div className="space-y-2">
            <Skeleton className="h-8 w-48" />
            <Skeleton className="h-4 w-32" />
          </div>
        </div>
        <div className="grid gap-4 md:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-32" />
          ))}
        </div>
      </div>
    )
  }

  if (!modelo) {
    return (
      <div className="flex h-[50vh] flex-col items-center justify-center gap-4">
        <p className="text-muted-foreground">Modelo não encontrado</p>
        <Button variant="outline" onClick={() => router.push('/dashboard/modelos')}>
          <ArrowLeft className="mr-2 h-4 w-4" />
          Voltar
        </Button>
      </div>
    )
  }

  const principalMidia = midias?.find((m) => m.principal)
  const imagemPrincipalUrl = principalMidia?.url || modelo.fotoReferenciaUrl

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div className="flex items-start gap-4">
          <Button variant="outline" size="icon" onClick={() => router.push('/dashboard/modelos')}>
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <div className="flex items-start gap-4">
            {/* Model Image */}
            <div className="h-20 w-20 rounded-lg border bg-muted overflow-hidden">
              {imagemPrincipalUrl ? (
                <img
                  src={imagemPrincipalUrl}
                  alt={modelo.nome}
                  className="h-full w-full object-cover"
                />
              ) : (
                <div className="flex h-full items-center justify-center">
                  <ImageIcon className="h-8 w-8 text-muted-foreground" />
                </div>
              )}
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-3xl font-bold">{modelo.nome}</h1>
                <Badge variant={modelo.ativo ? 'success' : 'destructive'}>
                  {modelo.ativo ? 'Ativo' : 'Inativo'}
                </Badge>
                {modelo.exibirNoMarketplace && (
                  <Badge variant="outline" className="gap-1">
                    <Globe className="h-3 w-3" />
                    Marketplace
                  </Badge>
                )}
              </div>
              <p className="text-muted-foreground">
                {modelo.fabricante || 'Fabricante não informado'}
              </p>
            </div>
          </div>
        </div>
        <Button onClick={() => setEditDialogOpen(true)}>
          <Edit className="mr-2 h-4 w-4" />
          Editar
        </Button>
      </div>

      {/* Stats */}
      <div className="grid gap-4 md:grid-cols-4">
        <StatCard
          icon={DollarSign}
          label="Preço/Hora"
          value={formatCurrency(modelo.precoBaseHora)}
          description={modelo.taxaHoraExtra ? `+${formatCurrency(modelo.taxaHoraExtra)} hora extra` : undefined}
        />
        <StatCard
          icon={Users}
          label="Capacidade"
          value={`${modelo.capacidadePessoas} pessoas`}
        />
        <StatCard
          icon={Gauge}
          label="Potência"
          value={modelo.potenciaHp ? `${modelo.potenciaHp} HP` : '-'}
        />
        <StatCard
          icon={Shield}
          label="Caução"
          value={modelo.caucao ? formatCurrency(modelo.caucao) : '-'}
        />
      </div>

      {/* Tabs */}
      <Tabs defaultValue="overview" className="space-y-4">
        <TabsList>
          <TabsTrigger value="overview">Visão Geral</TabsTrigger>
          <TabsTrigger value="midias">
            Fotos & Vídeos
            {midias && midias.length > 0 && (
              <Badge variant="secondary" className="ml-2">
                {midias.length}
              </Badge>
            )}
          </TabsTrigger>
          <TabsTrigger value="marketplace">Marketplace</TabsTrigger>
        </TabsList>

        {/* Overview Tab */}
        <TabsContent value="overview" className="space-y-4">
          <div className="grid gap-4 md:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle>Informações</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Nome</span>
                  <span className="font-medium">{modelo.nome}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Fabricante</span>
                  <span className="font-medium">{modelo.fabricante || '-'}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Potência</span>
                  <span className="font-medium">{modelo.potenciaHp ? `${modelo.potenciaHp} HP` : '-'}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Capacidade</span>
                  <span className="font-medium">{modelo.capacidadePessoas} pessoas</span>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Preços</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Preço/Hora</span>
                  <span className="font-medium">{formatCurrency(modelo.precoBaseHora)}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Taxa Hora Extra</span>
                  <span className="font-medium">{modelo.taxaHoraExtra ? formatCurrency(modelo.taxaHoraExtra) : '-'}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Tolerância</span>
                  <span className="font-medium">{modelo.toleranciaMin || 0} min</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Caução</span>
                  <span className="font-medium">{modelo.caucao ? formatCurrency(modelo.caucao) : '-'}</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-muted-foreground">Combustível Incluso</span>
                  {modelo.incluiCombustivel ? (
                    <Badge variant="success" className="gap-1">
                      <Check className="h-3 w-3" />
                      Sim
                    </Badge>
                  ) : (
                    <Badge variant="secondary" className="gap-1">
                      <X className="h-3 w-3" />
                      Não
                    </Badge>
                  )}
                </div>
              </CardContent>
            </Card>
          </div>
        </TabsContent>

        {/* Midias Tab */}
        <TabsContent value="midias" className="space-y-4">
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle>Fotos e Vídeos</CardTitle>
                  <CardDescription>
                    Gerencie as imagens e vídeos que aparecem no marketplace
                  </CardDescription>
                </div>
                <Button onClick={() => setAddMediaDialogOpen(true)}>
                  <Plus className="mr-2 h-4 w-4" />
                  Adicionar
                </Button>
              </div>
            </CardHeader>
            <CardContent>
              {midiasLoading ? (
                <div className="grid gap-4 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
                  {Array.from({ length: 4 }).map((_, i) => (
                    <Skeleton key={i} className="aspect-video rounded-lg" />
                  ))}
                </div>
              ) : midias && midias.length > 0 ? (
                <div className="grid gap-4 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
                  {midias.map((midia) => (
                    <MediaItem
                      key={midia.id}
                      midia={midia}
                      modeloId={modeloId}
                      isPrincipal={midia.principal}
                      onSetPrincipal={() => setPrincipalMutation.mutate(midia.id)}
                      onDelete={() => deleteMidiaMutation.mutate(midia.id)}
                    />
                  ))}
                </div>
              ) : (
                <div className="flex flex-col items-center justify-center py-12 text-center">
                  <ImageIcon className="h-12 w-12 text-muted-foreground" />
                  <p className="mt-4 text-lg font-medium">Nenhuma mídia cadastrada</p>
                  <p className="text-muted-foreground">
                    Adicione fotos e vídeos para exibir no marketplace
                  </p>
                  <Button className="mt-4" onClick={() => setAddMediaDialogOpen(true)}>
                    <Plus className="mr-2 h-4 w-4" />
                    Adicionar Mídia
                  </Button>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* Marketplace Tab */}
        <TabsContent value="marketplace" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Configurações do Marketplace</CardTitle>
              <CardDescription>
                Controle como este modelo aparece no marketplace público
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              {/* Visibility Toggle */}
              <div className="flex items-center justify-between rounded-lg border p-4">
                <div className="flex items-center gap-4">
                  <div className={`rounded-full p-2 ${modelo.exibirNoMarketplace ? 'bg-green-100' : 'bg-gray-100'}`}>
                    {modelo.exibirNoMarketplace ? (
                      <Eye className="h-5 w-5 text-green-600" />
                    ) : (
                      <EyeOff className="h-5 w-5 text-gray-500" />
                    )}
                  </div>
                  <div>
                    <p className="font-medium">Exibir no Marketplace</p>
                    <p className="text-sm text-muted-foreground">
                      {modelo.exibirNoMarketplace
                        ? 'Este modelo está visível para clientes no marketplace'
                        : 'Este modelo está oculto do marketplace'}
                    </p>
                  </div>
                </div>
                <Switch
                  checked={modelo.exibirNoMarketplace ?? true}
                  onCheckedChange={(checked) => toggleMarketplaceMutation.mutate(checked)}
                  disabled={toggleMarketplaceMutation.isPending}
                />
              </div>

              {/* Marketplace Preview */}
              {modelo.exibirNoMarketplace && (
                <div className="rounded-lg border p-4">
                  <p className="mb-4 font-medium">Preview do Card</p>
                  <div className="mx-auto max-w-sm rounded-lg border bg-card overflow-hidden">
                    <div className="aspect-[4/3] bg-muted">
                      {imagemPrincipalUrl ? (
                        <img
                          src={imagemPrincipalUrl}
                          alt={modelo.nome}
                          className="h-full w-full object-cover"
                        />
                      ) : (
                        <div className="flex h-full items-center justify-center">
                          <ImageIcon className="h-12 w-12 text-muted-foreground" />
                        </div>
                      )}
                    </div>
                    <div className="p-4">
                      <h3 className="font-semibold">{modelo.nome}</h3>
                      <p className="text-sm text-muted-foreground">{modelo.fabricante}</p>
                      <div className="mt-2 flex items-center justify-between">
                        <div>
                          <span className="text-lg font-bold">{formatCurrency(modelo.precoBaseHora)}</span>
                          <span className="text-sm text-muted-foreground">/hora</span>
                        </div>
                        <Badge variant="outline">{modelo.capacidadePessoas} pessoas</Badge>
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {/* Info */}
              <div className="rounded-lg bg-muted/50 p-4">
                <div className="flex gap-3">
                  <Store className="h-5 w-5 text-muted-foreground shrink-0" />
                  <div className="space-y-1">
                    <p className="text-sm font-medium">Sobre o Marketplace</p>
                    <p className="text-sm text-muted-foreground">
                      O marketplace é onde clientes podem descobrir e reservar jetskis.
                      Certifique-se de adicionar boas fotos e manter os preços atualizados
                      para atrair mais clientes.
                    </p>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      {/* Dialogs */}
      <EditModeloDialog modelo={modelo} open={editDialogOpen} onOpenChange={setEditDialogOpen} />
      <AddMediaDialog modeloId={modeloId} open={addMediaDialogOpen} onOpenChange={setAddMediaDialogOpen} />
    </div>
  )
}
