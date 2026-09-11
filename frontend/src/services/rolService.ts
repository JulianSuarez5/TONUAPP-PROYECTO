// Catalogo de roles (GET /api/roles): tabla flexible (RF-006). Lo consumen las
// pantallas que necesitan llenar un select de roles sin hardcodear ids.
import type { RolResponse } from '../types/usuarios'
import { http } from './http'

export async function listarRoles(): Promise<RolResponse[]> {
  const { data } = await http.get<RolResponse[]>('/api/roles')
  return data
}