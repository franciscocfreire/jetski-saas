import { APIRequestContext } from '@playwright/test';

/**
 * Gera um identificador único para dados de teste
 */
export function uniqueId(prefix: string = 'TEST'): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`;
}

/**
 * Factory para criar um Modelo de teste
 */
export async function createTestModelo(
  apiClient: APIRequestContext,
  tenantId: string,
  overrides: Partial<ModeloData> = {}
): Promise<ModeloResponse> {
  const data: ModeloData = {
    nome: uniqueId('MODELO'),
    fabricante: 'Teste Fabricante',
    precoBaseHora: 150.00,
    toleranciaMin: 5,
    taxaHoraExtra: 50.00,
    incluiCombustivel: false,
    ativo: true,
    ...overrides,
  };

  const response = await apiClient.post(`/v1/tenants/${tenantId}/modelos`, {
    data,
  });

  if (!response.ok()) {
    throw new Error(`Falha ao criar modelo: ${response.status()}`);
  }

  return response.json();
}

/**
 * Factory para criar um Jetski de teste
 */
export async function createTestJetski(
  apiClient: APIRequestContext,
  tenantId: string,
  modeloId: string,
  overrides: Partial<JetskiData> = {}
): Promise<JetskiResponse> {
  const data: JetskiData = {
    modeloId,
    identificacao: uniqueId('JETSKI'),
    numeroSerie: uniqueId('SERIE'),
    anoFabricacao: 2024,
    horimetro: 0,
    status: 'DISPONIVEL',
    ...overrides,
  };

  const response = await apiClient.post(`/v1/tenants/${tenantId}/jetskis`, {
    data,
  });

  if (!response.ok()) {
    throw new Error(`Falha ao criar jetski: ${response.status()}`);
  }

  return response.json();
}

/**
 * Factory para criar um Cliente de teste
 */
export async function createTestCliente(
  apiClient: APIRequestContext,
  tenantId: string,
  overrides: Partial<ClienteData> = {}
): Promise<ClienteResponse> {
  const timestamp = Date.now();
  const data: ClienteData = {
    nome: `Cliente Teste ${timestamp}`,
    email: `cliente.teste.${timestamp}@example.com`,
    telefone: '11999999999',
    documento: `${Math.floor(Math.random() * 90000000000) + 10000000000}`,
    ...overrides,
  };

  const response = await apiClient.post(`/v1/tenants/${tenantId}/clientes`, {
    data,
  });

  if (!response.ok()) {
    throw new Error(`Falha ao criar cliente: ${response.status()}`);
  }

  return response.json();
}

/**
 * Factory para criar uma Reserva de teste
 */
export async function createTestReserva(
  apiClient: APIRequestContext,
  tenantId: string,
  modeloId: string,
  clienteId: string,
  overrides: Partial<ReservaData> = {}
): Promise<ReservaResponse> {
  const now = new Date();
  const data: ReservaData = {
    modeloId,
    clienteId,
    dataHoraInicio: new Date(now.getTime() + 60 * 60 * 1000).toISOString(), // +1h
    dataHoraFimPrevisto: new Date(now.getTime() + 2 * 60 * 60 * 1000).toISOString(), // +2h
    observacoes: 'Reserva de teste E2E',
    ...overrides,
  };

  const response = await apiClient.post(`/v1/tenants/${tenantId}/reservas`, {
    data,
  });

  if (!response.ok()) {
    throw new Error(`Falha ao criar reserva: ${response.status()}`);
  }

  return response.json();
}

/**
 * Helper para limpar dados de teste criados
 */
export async function cleanupTestData(
  apiClient: APIRequestContext,
  tenantId: string,
  resources: { type: string; id: string }[]
): Promise<void> {
  for (const resource of resources) {
    try {
      await apiClient.delete(`/v1/tenants/${tenantId}/${resource.type}/${resource.id}`);
    } catch (error) {
      console.warn(`Aviso: Não foi possível limpar ${resource.type}/${resource.id}`);
    }
  }
}

// Types
interface ModeloData {
  nome: string;
  fabricante?: string;
  precoBaseHora: number;
  toleranciaMin?: number;
  taxaHoraExtra?: number;
  incluiCombustivel?: boolean;
  ativo?: boolean;
}

interface ModeloResponse {
  id: string;
  nome: string;
  fabricante: string;
  precoBaseHora: number;
}

interface JetskiData {
  modeloId: string;
  identificacao: string;
  numeroSerie?: string;
  anoFabricacao?: number;
  horimetro?: number;
  status?: string;
}

interface JetskiResponse {
  id: string;
  identificacao: string;
  modeloId: string;
  status: string;
}

interface ClienteData {
  nome: string;
  email?: string;
  telefone?: string;
  documento?: string;
}

interface ClienteResponse {
  id: string;
  nome: string;
  email: string;
}

interface ReservaData {
  modeloId: string;
  clienteId: string;
  dataHoraInicio: string;
  dataHoraFimPrevisto: string;
  observacoes?: string;
}

interface ReservaResponse {
  id: string;
  status: string;
  modeloId: string;
  clienteId: string;
}
