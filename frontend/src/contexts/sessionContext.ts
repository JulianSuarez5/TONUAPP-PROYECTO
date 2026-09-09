// Definiciones del contexto de sesion, separadas del provider para evitar
// mezclar exports (fast-refresh de oxlint no aplica a modulos sin componentes)
import { createContext } from 'react'
import type { Rol } from '../types/auth'

export interface UsuarioLogueado {
  idUsuario: number
  correo: string
  rol: Rol
}

export interface AuthContextValue {
  // Usuario de la sesion restaurada (persistida); null si no hay sesion
  usuario: UsuarioLogueado | null
  accessToken: string | null
  isAutenticado: boolean
  solicitarCodigo: (correo: string) => Promise<string>
  verificarCorreo: (correo: string, codigo: string) => Promise<void>
  cerrarSesion: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined)