// Capa services (AGENTS-11): toda llamada del modulo Ubicaciones y lotes viene de
// aca, nunca de una pantalla directamente. Usa el cliente http.ts (token, refresh y
// 401). El backend exige q/page/size; eliminar una zona con materiales (RF-015) o un
// lote referenciado por movimientos da 409.
import type { MaterialResponse } from '../types/materiales'
import type {
  LoteRequest,
  LoteResponse,
  UbicacionPaged,
  ZonaAcopioRequest,
  ZonaAcopioResponse,
} from '../types/ubicaciones'
import { http } from './http'

const ZONAS_BASE = '/api/zonas-acopio'
const LOTES_BASE = '/api/lotes'

// ---------- Zonas de acopio (RF-015) ----------

// Lista paginada de zonas activas; q opcional filtra por nombre o tipo permitido
export async function listarZonas(q = '', page = 0, size = 20): Promise<UbicacionPaged<ZonaAcopioResponse>> {
  const { data } = await http.get<UbicacionPaged<ZonaAcopioResponse>>(ZONAS_BASE, {
    params: { q: q || undefined, page, size },
  })
  return data
}

export async function obtenerZona(id: number): Promise<ZonaAcopioResponse> {
  const { data } = await http.get<ZonaAcopioResponse>(`${ZONAS_BASE}/${id}`)
  return data
}

// Materiales activos asignados a la zona (indicador de ubicacion RF-015)
export async function listarMaterialesZona(id: number): Promise<MaterialResponse[]> {
  const { data } = await http.get<MaterialResponse[]>(`${ZONAS_BASE}/${id}/materiales`)
  return data
}

// Mutaciones: solo Administrador (RF-009). Un Cliente recibe 403 del backend,
// la UI simplemente no ofrece los controles.
export async function crearZona(request: ZonaAcopioRequest): Promise<ZonaAcopioResponse> {
  const { data } = await http.post<ZonaAcopioResponse>(ZONAS_BASE, request)
  return data
}

export async function actualizarZona(id: number, request: ZonaAcopioRequest): Promise<ZonaAcopioResponse> {
  const { data } = await http.put<ZonaAcopioResponse>(`${ZONAS_BASE}/${id}`, request)
  return data
}

// Soft delete (desactiva). 409 si la zona tiene materiales asignados o movimientos.
export async function desactivarZona(id: number): Promise<void> {
  await http.delete(`${ZONAS_BASE}/${id}`)
}

// Asigna un material a la zona (409 si es incompatible con el tipo o ya esta asignado)
export async function asignarMaterialZona(idZona: number, idMaterial: number): Promise<MaterialResponse> {
  const { data } = await http.post<MaterialResponse>(`${ZONAS_BASE}/${idZona}/materiales/${idMaterial}`)
  return data
}

// Desasigna un material de la zona
export async function desasignarMaterialZona(idZona: number, idMaterial: number): Promise<MaterialResponse> {
  const { data } = await http.delete<MaterialResponse>(`${ZONAS_BASE}/${idZona}/materiales/${idMaterial}`)
  return data
}

// ---------- Lotes (US-17, D-34) ----------

// Lista paginada de lotes activos; idMaterial restringe por material, q por codigo
export async function listarLotes(
  params: { idMaterial?: number; q?: string; page?: number; size?: number },
): Promise<UbicacionPaged<LoteResponse>> {
  const { data } = await http.get<UbicacionPaged<LoteResponse>>(LOTES_BASE, {
    params: { idMaterial: params.idMaterial || undefined, q: params.q || undefined, page: params.page ?? 0, size: params.size ?? 20 },
  })
  return data
}

export async function obtenerLote(id: number): Promise<LoteResponse> {
  const { data } = await http.get<LoteResponse>(`${LOTES_BASE}/${id}`)
  return data
}

// Lotes activos de un material, mas recientes primero
export async function listarLotesDeMaterial(idMaterial: number): Promise<LoteResponse[]> {
  const { data } = await http.get<LoteResponse[]>(`${LOTES_BASE}/por-material/${idMaterial}`)
  return data
}

export async function crearLote(request: LoteRequest): Promise<LoteResponse> {
  const { data } = await http.post<LoteResponse>(LOTES_BASE, request)
  return data
}

// El material del lote es inmutable tras la creacion (trazabilidad D-34)
export async function actualizarLote(id: number, request: LoteRequest): Promise<LoteResponse> {
  const { data } = await http.put<LoteResponse>(`${LOTES_BASE}/${id}`, request)
  return data
}

// Soft delete (desactiva). 409 si movimientos lo referencian.
export async function desactivarLote(id: number): Promise<void> {
  await http.delete(`${LOTES_BASE}/${id}`)
}