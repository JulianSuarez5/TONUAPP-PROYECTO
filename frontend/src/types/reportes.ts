// Contrato del modulo Reportes (backend Fase 9 / RF-014, D-21): misma forma que los
// DTO de Spring. Tipos en minuscula como los enums del dominio (a juego con los
// CHECK). El binario de una exportacion no vive en la BD; se recupera por
// /api/reportes/{id}/descargar desde la bitacora (quien/cuando/tipo/formato/rango).
// Modulo de uso interno: solo Administrador (RF-009; el rol "Gerente" de RF-014
// sigue pendiente de la decision de roles RF-006). El cliente consulta el stock en
// tiempo real por /api/materiales (RF-001).
export type TipoReporte = 'inventario' | 'movimientos' | 'resumen' | 'bajo_stock'

export type Vista = 'inventario' | 'bajo_stock' | 'movimientos' | 'resumen' | 'bitacora'

export type FormatoReporte = 'pdf' | 'xlsx'

export interface ReporteRequest {
  tipo: TipoReporte
  formato: FormatoReporte
  desde?: string
  hasta?: string
}

// Fila de inventario y bajo stock (RF-001/RF-012): stock actual, minimo y si el
// material tiene una alerta activa. Bajo stock devuelve solo los que estan por
// debajo del minimo, ordenados por stock ascendente.
export interface ReporteInventarioRow {
  idMaterial: number
  nombreMaterial: string
  categoria: string
  unidad: string
  stock: number
  stockMinimo: number
  conAlertaActiva: boolean
}

// Fila del reporte resumen por material en un rango (RF-013/RF-004): el ajusteNeto
// suma el delta de cada ajuste (cantidadNueva - cantidadAnterior, D-19) y
// efectoNeto = entradas - salidasVenta - salidasMerma + ajusteNeto.
export interface ReporteResumenRow {
  idMaterial: number
  nombreMaterial: string
  entradas: number
  salidasVenta: number
  salidasMerma: number
  ajusteNeto: number
  efectoNeto: number
  stockActual: number
}

// Entrada de la bitacora de exportaciones (RF-014)
export interface ReporteGeneradoRow {
  idReporte: number
  idUsuario: number
  tipoReporte: string
  formato: string
  fechaGeneracion: string
  rangoFechaInicio: string | null
  rangoFechaFin: string | null
  nombreArchivo: string
}