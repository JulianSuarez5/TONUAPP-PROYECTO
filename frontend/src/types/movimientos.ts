// Contrato del modulo Movimientos (backend Fase 6-7 / RF-005 a RF-007, RF-011):
// misma forma que los DTO de Spring. Los enums coinciden con los CHECK de la BD
// (D-02): entrada, salida_venta, salida_merma, ajuste; estado activo/anulado.
// La cantidad del movimiento SIEMPRE es positiva (D-02/D-18): el signo del efecto
// sobre el stock lo decide el backend — en la UI entrada se muestra "+", salidas
// "−" y el ajuste (|diferencia|) queda sin signo porque su efecto depende del
// stock previo. Todo el modulo es solo Administrador (backend @PreAuthorize).
export type TipoMovimiento = 'entrada' | 'salida_venta' | 'salida_merma' | 'ajuste'

export type EstadoMovimiento = 'activo' | 'anulado'

export interface MovimientoResponse {
  idMovimiento: number
  idMaterial: number
  nombreMaterial: string
  idUsuario: number
  nombreUsuario: string
  tipo: TipoMovimiento
  cantidad: number
  estado: EstadoMovimiento
  motivo: string | null
  observaciones: string | null
  idLote: number | null
  idZona: number | null
  fechaMovimiento: string
}

// Alta de ENTRADA o SALIDA (RF-005/RF-006): la cantidad siempre > 0. El ajuste va
// por AjusteRequest (stock objetivo, RF-011). idLote/idZona opcionales (RF-015/D-34).
export interface MovimientoRequest {
  idMaterial: number
  tipo: 'entrada' | 'salida_venta' | 'salida_merma'
  cantidad: number
  motivo?: string
  observaciones?: string
  idLote?: number | null
  idZona?: number | null
}

export interface AjusteRequest {
  idMaterial: number
  cantidadNueva: number
  motivo: string
}

export interface MovimientosPaged<T> {
  content: T[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}