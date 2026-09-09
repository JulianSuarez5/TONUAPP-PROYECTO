// Capa services (AGENTS-11): toda llamada del modulo Reportes viene de aca, nunca
// de una pantalla directamente. Usa el cliente http.ts (token, refresh y 401).
// El backend es de uso interno: @PreAuthorize hasRole('Administrador') a nivel de
// clase (RF-009). Las consultas JSON alimentan las pestanas y la exportacion
// descarga el binario (pdf/xlsx) que genera el service (RF-014/D-21).
import type { MovimientoResponse } from '../types/movimientos'
import type {
  FormatoReporte,
  ReporteGeneradoRow,
  ReporteInventarioRow,
  ReporteRequest,
  ReporteResumenRow,
} from '../types/reportes'
import { http } from './http'

const BASE = '/api/reportes'

// Stock actual de todos los materiales activos (RF-001)
export async function inventario(): Promise<ReporteInventarioRow[]> {
  const { data } = await http.get<ReporteInventarioRow[]>(`${BASE}/inventario`)
  return data
}

// Materiales bajo el stock minimo (RF-012), del mas critico al menos critico
export async function bajoStock(): Promise<ReporteInventarioRow[]> {
  const { data } = await http.get<ReporteInventarioRow[]>(`${BASE}/bajo-stock`)
  return data
}

// Historial de movimientos en un rango (RF-014); sin rango devuelve todo
export async function movimientos(desde?: string, hasta?: string): Promise<MovimientoResponse[]> {
  const { data } = await http.get<MovimientoResponse[]>(`${BASE}/movimientos`, {
    params: { desde: desde || undefined, hasta: hasta || undefined },
  })
  return data
}

// Totales por material en el rango (RF-013/RF-004)
export async function resumen(desde?: string, hasta?: string): Promise<ReporteResumenRow[]> {
  const { data } = await http.get<ReporteResumenRow[]>(`${BASE}/resumen`, {
    params: { desde: desde || undefined, hasta: hasta || undefined },
  })
  return data
}

// Historial de exportaciones (RF-014): quien, cuando, tipo, formato y rango
export async function bitacora(): Promise<ReporteGeneradoRow[]> {
  const { data } = await http.get<ReporteGeneradoRow[]>(`${BASE}/bitacora`)
  return data
}

// Extrae el nombre de archivo del Content-Disposition que arma el backend
function nombreDescarga(headers: Record<string, string>): string {
  const cd = headers['content-disposition'] ?? headers['Content-Disposition'] ?? ''
  const match = /filename="?([^";]+)"?/.exec(cd)
  return match ? match[1] : 'reporte'
}

export interface DescargaReporte {
  blob: Blob
  nombre: string
  contentType: string
  headers: Record<string, string>
}

// Genera y descarga el reporte en el formato pedido; el service registra la bitacora
// y responde el Content-Disposition con el nombre del archivo (RF-014/D-21)
export async function exportar(request: ReporteRequest): Promise<DescargaReporte> {
  const res = await http.post(`${BASE}/exportar`, request, { responseType: 'blob' })
  return {
    blob: res.data,
    nombre: nombreDescarga(res.headers as Record<string, string>),
    contentType: String(res.headers['content-type'] ?? ''),
    headers: res.headers as Record<string, string>,
  }
}

// Re-descarga el archivo generado previamente, desde la bitacora
export async function descargar(id: number): Promise<DescargaReporte> {
  const res = await http.get(`${BASE}/${id}/descargar`, { responseType: 'blob' })
  return {
    blob: res.data,
    nombre: nombreDescarga(res.headers as Record<string, string>),
    contentType: String(res.headers['content-type'] ?? ''),
    headers: res.headers as Record<string, string>,
  }
}

// Etiqueta legible de un tipo de reporte (para selects y bitacora)
export const ETIQUETAS_TIPO: Record<FormatoReporte | 'inventario' | 'movimientos' | 'resumen' | 'bajo_stock', string> = {
  inventario: 'Inventario',
  bajo_stock: 'Bajo stock',
  movimientos: 'Movimientos',
  resumen: 'Resumen',
  pdf: 'PDF',
  xlsx: 'Excel',
}

export const TIPOS_REPORTE = ['inventario', 'bajo_stock', 'movimientos', 'resumen'] as const

export const FORMATOS = ['pdf', 'xlsx'] as const