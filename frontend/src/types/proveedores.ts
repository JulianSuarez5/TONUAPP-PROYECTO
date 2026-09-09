// Contrato del modulo Proveedores (backend Fase 4 / RF-010): misma forma que los
// DTO de Spring (ProveedorResponse/ProveedorRequest) y la asociacion N:M con
// materiales (D-01/D-14). El backend serializa LocalDateTime como ISO string.
export interface ProveedorResponse {
  idProveedor: number
  nombre: string
  nit: string
  contacto: string | null
  telefono: string | null
  ubicacion: string | null
  activo: boolean
  fechaRegistro: string
  cantidadMateriales: number
}

// Entrada del CRUD: solo nombre y nit son obligatorios (RF-010); el resto opcional
export interface ProveedorRequest {
  nombre: string
  nit: string
  contacto?: string
  telefono?: string
  ubicacion?: string
}

// Asociacion material-proveedor (N:M). Nombre del modelo de BD es material_proveedor
export interface MaterialProveedorResponse {
  idMaterial: number
  nombreMaterial: string
  idProveedor: number
  nombreProveedor: string
  esPrincipal: boolean
  fechaAsociacion: string
}

// Entrada para asociar un material a un proveedor (POST /{id}/materiales)
export interface MaterialProveedorRequest {
  idMaterial: number
  esPrincipal?: boolean
}

// Envoltorio de paginacion del backend (PagedResponse<T>, mismo shape que materiales)
export interface ProveedorPaged {
  content: ProveedorResponse[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}