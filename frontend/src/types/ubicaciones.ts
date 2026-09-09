// Contrato del modulo Ubicaciones y lotes (backend Fase / RF-015, US-16/US-17, D-34):
// misma forma que los DTO de Spring. La cantidad del lote es SOLO informativa (D-02):
// el stock real se calcula desde movimientos. La zona valida el tipo de material
// permitido contra la categoria del material (RF-015).
export interface ZonaAcopioResponse {
  idZona: number
  nombreZona: string
  capacidadMaxima: number
  tipoMaterialPermitido: string | null
  activo: boolean
  cantidadMateriales: number
}

// Entrada del CRUD de zonas: nombre y capacidad maxima obligatorios; el tipo de
// material permitido es opcional ("" en el form se envia como undefined)
export interface ZonaAcopioRequest {
  nombreZona: string
  capacidadMaxima: number
  tipoMaterialPermitido?: string
}

export interface LoteResponse {
  idLote: number
  idMaterial: number
  materialNombre: string
  codigoLote: string
  cantidad: number
  fechaIngreso: string
  activo: boolean
}

// Entrada del CRUD de lotes: material y codigo obligatorios; cantidad informativa
export interface LoteRequest {
  idMaterial: number
  codigoLote: string
  cantidad: number
}

// Envoltorio de paginacion del backend (PagedResponse<T>, mismo shape que materiales)
export interface UbicacionPaged<T> {
  content: T[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}