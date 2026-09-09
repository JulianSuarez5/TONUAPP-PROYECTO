// Capa services (AGENTS-11): toda llamada del modulo Materiales viene de aca, nunca
// de una pantalla directamente. Usa el cliente http.ts (token, refresh y 401).
import type {
  CategoriaResponse,
  MaterialProveedorResponse,
  MaterialRequest,
  MaterialResponse,
  PagedResponse,
  UnidadMedidaResponse,
} from '../types/materiales'
import { http } from './http'

const BASE = '/api/materiales'

export async function listar(page = 0, size = 20): Promise<PagedResponse<MaterialResponse>> {
  const { data } = await http.get<PagedResponse<MaterialResponse>>(BASE, { params: { page, size } })
  return data
}

// Búsqueda RF-003: exige al menos un filtro (nombre y/o categoria); el backend
// rechaza llamada vacia con 400
export async function buscar(
  params: { nombre?: string; categoria?: number; page?: number; size?: number },
): Promise<PagedResponse<MaterialResponse>> {
  const { data } = await http.get<PagedResponse<MaterialResponse>>(`${BASE}/buscar`, { params })
  return data
}

export async function obtener(id: number): Promise<MaterialResponse> {
  const { data } = await http.get<MaterialResponse>(`${BASE}/${id}`)
  return data
}

// Proveedores asociados a un material (N:M, RF-010)
export async function listarProveedores(id: number): Promise<MaterialProveedorResponse[]> {
  const { data } = await http.get<MaterialProveedorResponse[]>(`${BASE}/${id}/proveedores`)
  return data
}

// Mutaciones: solo Administrador (RF-002/RF-004). Un Cliente recibe 403 del backend,
// la UI simplemente no ofrece los controles.
export async function crear(request: MaterialRequest): Promise<MaterialResponse> {
  const { data } = await http.post<MaterialResponse>(BASE, request)
  return data
}

export async function actualizar(id: number, request: MaterialRequest): Promise<MaterialResponse> {
  const { data } = await http.put<MaterialResponse>(`${BASE}/${id}`, request)
  return data
}

// Soft delete (desactiva, RF-004). 409 si el material tiene movimientos.
export async function desactivar(id: number): Promise<void> {
  await http.delete(`${BASE}/${id}`)
}

// Catálogos de la pantalla (GET /api/categorias y /api/unidades-medida, solo lectura)
export async function listarCategorias(): Promise<CategoriaResponse[]> {
  const { data } = await http.get<CategoriaResponse[]>('/api/categorias')
  return data
}

export async function listarUnidades(): Promise<UnidadMedidaResponse[]> {
  const { data } = await http.get<UnidadMedidaResponse[]>('/api/unidades-medida')
  return data
}