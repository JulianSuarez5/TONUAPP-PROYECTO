// Capa services (AGENTS-11): toda llamada del modulo Movimientos viene de aca,
// nunca de una pantalla directamente. Usa el cliente http.ts (token, refresh y
// 401). El backend es de uso interno: @PreAuthorize hasRole('Administrador') a
// nivel de clase, por eso la UI solo ofrece la ruta a ese rol (RF-009).
import type {
  AjusteRequest,
  EstadoMovimiento,
  MovimientoRequest,
  MovimientoResponse,
  MovimientosPaged,
  TipoMovimiento,
} from '../types/movimientos'
import { http } from './http'

const BASE = '/api/movimientos'

export interface ListarMovimientosParams {
  idMaterial?: number
  tipo?: TipoMovimiento
  estado?: EstadoMovimiento
  desde?: string
  hasta?: string
  page?: number
  size?: number
}

// Historico paginado (mas reciente primero); filtros opcionales por material,
// tipo, estado y rango de fechas (RF-005 a RF-007, RF-004 historico)
export async function listar(params: ListarMovimientosParams = {}): Promise<MovimientosPaged<MovimientoResponse>> {
  const { data } = await http.get<MovimientosPaged<MovimientoResponse>>(BASE, {
    params: {
      idMaterial: params.idMaterial || undefined,
      tipo: params.tipo || undefined,
      estado: params.estado || undefined,
      desde: params.desde || undefined,
      hasta: params.hasta || undefined,
      page: params.page ?? 0,
      size: params.size ?? 20,
    },
  })
  return data
}

// Registra ENTRADA o SALIDA y actualiza el stock atomicamente (RF-005/RF-006).
// 409 si la salida supera el stock disponible o el lote/zona es incompatible.
export async function registrar(request: MovimientoRequest): Promise<MovimientoResponse> {
  const { data } = await http.post<MovimientoResponse>(BASE, request)
  return data
}

// Ajuste a stock objetivo (RF-011): cantidadNueva = inventario fisico contado
export async function ajustar(request: AjusteRequest): Promise<MovimientoResponse> {
  const { data } = await http.post<MovimientoResponse>(`${BASE}/ajuste`, request)
  return data
}

// Anula un movimiento y revierte su efecto sobre el stock
export async function anular(id: number): Promise<MovimientoResponse> {
  const { data } = await http.post<MovimientoResponse>(`${BASE}/${id}/anular`)
  return data
}