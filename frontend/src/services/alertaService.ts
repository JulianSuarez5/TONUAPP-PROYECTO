// Capa services (AGENTS-11): toda llamada del modulo Alertas viene de aca, nunca
// de una pantalla directamente. Usa el cliente http.ts (token, refresh y 401).
// El backend es de uso interno: @PreAuthorize hasRole('Administrador') a nivel de
// clase, por eso la UI solo ofrece la ruta a ese rol (RF-009).
import type { AlertaResponse, EstadoAlerta } from '../types/alertas'
import { http } from './http'

const BASE = '/api/alertas'

// Las alertas se GENERAN en el backend al caer bajo el minimo (D-20); aqui solo se
// listan (mas recientes primero) con filtro opcional por estado (RF-012).
export async function listar(estado?: EstadoAlerta): Promise<AlertaResponse[]> {
  const { data } = await http.get<AlertaResponse[]>(BASE, {
    params: { estado: estado || undefined },
  })
  return data
}

// Cierra manualmente una alerta activa (D-20): el Administrador confirma que actuo
export async function atender(id: number): Promise<AlertaResponse> {
  const { data } = await http.post<AlertaResponse>(`${BASE}/${id}/atender`)
  return data
}