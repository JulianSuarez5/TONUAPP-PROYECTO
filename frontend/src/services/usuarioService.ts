// Capa services (AGENTS-11): toda llamada del modulo Usuarios viene de aca, nunca
// de una pantalla directamente. Usa el cliente http.ts (token, refresh y 401).
// El backend (RF-006/RF-009) exige rol Administrador: si un Cliente llama recibe
// 403, la UI simplemente no ofrece esta pantalla. Reglas que hace valer:
//  - correo unico (409 si ya existe);
//  - el admin principal no se puede desactivar (409);
//  - nadie puede cambiar su propio rol (403, RF-009).
import type { UsuarioRequest, UsuarioResponse } from '../types/usuarios'
import { http } from './http'

const BASE = '/api/usuarios'

// Directorio de usuarios (solo Admin). Sin paginar: GET devuelve la lista completa.
export async function listar(): Promise<UsuarioResponse[]> {
  const { data } = await http.get<UsuarioResponse[]>(BASE)
  return data
}

export async function obtener(id: number): Promise<UsuarioResponse> {
  const { data } = await http.get<UsuarioResponse>(`${BASE}/${id}`)
  return data
}

export async function crear(request: UsuarioRequest): Promise<UsuarioResponse> {
  const { data } = await http.post<UsuarioResponse>(BASE, request)
  return data
}

export async function actualizar(id: number, request: UsuarioRequest): Promise<UsuarioResponse> {
  const { data } = await http.put<UsuarioResponse>(`${BASE}/${id}`, request)
  return data
}

// Cambia solo el rol (endpoint propio del backend). 403 si se intenta sobre si mismo.
export async function cambiarRol(id: number, idRol: number): Promise<UsuarioResponse> {
  const { data } = await http.put<UsuarioResponse>(`${BASE}/${id}/rol`, { idRol })
  return data
}

// Soft delete (desactiva, D-04). 409 si es el admin principal.
export async function desactivar(id: number): Promise<void> {
  await http.delete(`${BASE}/${id}`)
}