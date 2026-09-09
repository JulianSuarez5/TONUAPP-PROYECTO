// Provider de sesion: restaura/guarda la sesion y expone las acciones de login
// (RF-008). El refresh automatico vive en el interceptor de http.ts
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { UNAUTHORIZED_EVENT } from '../services/http'
import * as authService from '../services/authService'
import type { AuthResponse } from '../types/auth'
import { clearSession, loadSession, saveSession } from '../utils/session'
import { AuthContext, type AuthContextValue } from './sessionContext'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [auth, setAuth] = useState<AuthResponse | null>(() => loadSession())

  // El interceptor avisa con UNAUTHORIZED_EVENT cuando el refresh fallo: cerramos
  // la sesion local y las rutas protegidas devolveran al login
  useEffect(() => {
    const onUnauthorized = () => {
      clearSession()
      setAuth(null)
    }
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      usuario: auth
        ? { idUsuario: auth.idUsuario, correo: auth.correo, rol: auth.rol }
        : null,
      accessToken: auth?.accessToken ?? null,
      isAutenticado: auth !== null,
      solicitarCodigo: (correo) => authService.solicitarCodigo(correo),
      verificarCorreo: async (correo, codigo) => {
        const sesion = await authService.verificar(correo, codigo)
        saveSession(sesion)
        setAuth(sesion)
      },
      cerrarSesion: async () => {
        const refreshToken = auth?.refreshToken
        clearSession()
        setAuth(null)
        // Revoca el refresh en el backend; si falla, no bloqueamos el cierre local
        if (refreshToken) {
          try {
            await authService.cerrarSesion(refreshToken)
          } catch {
            // ignorado a proposito: la sesion local ya se cerro
          }
        }
      },
    }),
    [auth],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}