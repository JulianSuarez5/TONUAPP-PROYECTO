// Contrato del modulo Materiales (backend Fase 3 / RF-001 a RF-004): misma forma que
// los DTO de Spring (MaterialResponse/MaterialRequest) y los catalogos (Catálogos,
// solo lectura). El backend serializa BigDecimal como numero JSON (0.00).
export interface MaterialResponse {
  idMaterial: number
  nombre: string
  idCategoria: number
  categoriaNombre: string
  idUnidad: number
  unidadNombre: string
  unidadAbreviatura: string
  stock: number
  stockMinimo: number
  activo: boolean
  fechaRegistro: string
  fechaActualizacion: string
  idZona: number | null
  zonaNombre: string | null
}

// Entrada del CRUD: nombre + categoria + unidad + stock(es inicial/editable, RF-002)
export interface MaterialRequest {
  nombre: string
  idCategoria: number
  idUnidad: number
  stock: number
  stockMinimo: number
}

// Envoltorio de paginacion del backend (PagedResponse<T>)
export interface PagedResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

export interface CategoriaResponse {
  idCategoria: number
  nombre: string
  activo: boolean
}

export interface UnidadMedidaResponse {
  idUnidad: number
  nombre: string
  abreviatura: string
}

// Proveedores asociados a un material (N:M, RF-010). Nombre del modelo de BD es
// material_proveedor y el endpoint GET /api/materiales/{id}/proveedores
export interface MaterialProveedorResponse {
  idMaterial: number
  nombreMaterial: string
  idProveedor: number
  nombreProveedor: string
  esPrincipal: boolean
  fechaAsociacion: string
}