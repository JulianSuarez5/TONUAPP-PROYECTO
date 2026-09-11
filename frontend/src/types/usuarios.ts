// Contrato del modulo Usuarios (backend Fase 3 / RF-006, RF-009): misma forma que
// los DTO de Spring (UsuarioResponse/UsuarioRequest). Toda la gestion es exclusiva
// del rol Administrador; un Cliente no debe acceder a esta pantalla (RF-009).
// El backend serializa LocalDateTime como ISO string (fechaCreacion).
export interface UsuarioResponse {
  idUsuario: number
  nombre: string
  correo: string
  idRol: number
  nombreRol: string
  activo: boolean
  esAdminPrincipal: boolean
  fechaCreacion: string
}

// Entrada del CRUD: nombre, correo y rol son obligatorios (RF-006). Un admin
// principal marcado como esAdminPrincipal=true no puede desactivarse (409).
export interface UsuarioRequest {
  nombre: string
  correo: string
  idRol: number
}

// Catalogo de roles (tabla flexible RF-006: agregar roles no rehace el sistema);
// se carga de GET /api/roles para llenar el select del formulario.
export interface RolResponse {
  idRol: number
  nombreRol: string
  descripcion: string
}