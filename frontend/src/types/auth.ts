// Contrato de autenticacion (backend Fase 3 / D-10 / D-11): misma forma que AuthResponse
export type Rol = 'Administrador' | 'Cliente'

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  accessTokenExpira: string
  refreshTokenExpira: string
  idUsuario: number
  correo: string
  rol: Rol
}

// La sesion persistida es el mismo objeto que devuelve auth (verificar/renovar)
export type Session = AuthResponse