// Capa services (AGENTS-11): toda llamada del modulo Proveedores viene de aca,
// nunca de una pantalla directamente. Usa el cliente http.ts (token, refresh y
// 401). El backend exige q/page/size; eliminar un proveedor con materiales da 409.
import type {
  MaterialProveedorRequest,
  MaterialProveedorResponse,
  ProveedorPaged,
  ProveedorRequest,
  ProveedorResponse,
} from '../types/proveedores'
import { http } from './http'

const BASE = '/api/proveedores'

// Lista paginada de proveedores activos; q opcional filtra por nombre o NIT
export async function listar(q = '', page = 0, size = 20): Promise<ProveedorPaged> {
  const { data } = await http.get<ProveedorPaged>(BASE, { params: { q: q || undefined, page, size } })
  return data
}

export async function obtener(id: number): Promise<ProveedorResponse> {
  const { data } = await http.get<ProveedorResponse>(`${BASE}/${id}`)
  return data
}

// Materiales asociados a un proveedor (N:M, RF-010)
export async function listarMateriales(id: number): Promise<MaterialProveedorResponse[]> {
  const { data } = await http.get<MaterialProveedorResponse[]>(`${BASE}/${id}/materiales`)
  return data
}

// Mutaciones: solo Administrador (RF-009). Un Cliente recibe 403 del backend,
// la UI simplemente no ofrece los controles.
export async function crear(request: ProveedorRequest): Promise<ProveedorResponse> {
  const { data } = await http.post<ProveedorResponse>(BASE, request)
  return data
}

export async function actualizar(id: number, request: ProveedorRequest): Promise<ProveedorResponse> {
  const { data } = await http.put<ProveedorResponse>(`${BASE}/${id}`, request)
  return data
}

// Soft delete (desactiva, RF-010). 409 si el proveedor tiene materiales asociados.
export async function desactivar(id: number): Promise<void> {
  await http.delete(`${BASE}/${id}`)
}

// Asocia un material a un proveedor (409 si ya estaba asociado)
export async function asociarMaterial(id: number, request: MaterialProveedorRequest): Promise<MaterialProveedorResponse> {
  const { data } = await http.post<MaterialProveedorResponse>(`${BASE}/${id}/materiales`, request)
  return data
}

// Marca/desmarca un material como principal de un proveedor (PUT .../materiales/{idMaterial}?principal=)
export async function actualizarPrincipal(id: number, idMaterial: number, principal: boolean): Promise<MaterialProveedorResponse> {
  const { data } = await http.put<MaterialProveedorResponse>(
    `${BASE}/${id}/materiales/${idMaterial}`,
    undefined,
    { params: { principal } },
  )
  return data
}

// Quita la asociacion material-proveedor
export async function desasociarMaterial(id: number, idMaterial: number): Promise<void> {
  await http.delete(`${BASE}/${id}/materiales/${idMaterial}`)
}