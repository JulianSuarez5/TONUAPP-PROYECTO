// Capa services (AGENTS-11): toda llamada de autenticacion viene de aca, nunca de una
// pantalla directamente
import type { AuthResponse } from '../types/auth'
import { http } from './http'

// Devuelve el mensaje generico del servidor (no revela si el correo existe, RF-008)
export async function solicitarCodigo(correo: string): Promise<string> {
  const { data } = await http.post<{ message: string }>('/api/auth/solicitar-codigo', { correo })
  return data.message
}

export async function verificar(correo: string, codigo: string): Promise<AuthResponse> {
  const { data } = await http.post<AuthResponse>('/api/auth/verificar', { correo, codigo })
  return data
}

// El backend revoca el refresh token; el access expira solo (D-11)
export async function cerrarSesion(refreshToken: string): Promise<void> {
  await http.post('/api/auth/cerrar-sesion', { refreshToken })
}