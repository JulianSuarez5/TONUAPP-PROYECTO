// Cliente HTTP unico: base URL configurable (VITE_API_BASE_URL), adjunta el
// accessToken y, ante un 401, rota el refresh token una sola vez y reintenta
// (single-flight: peticiones concurrentes esperan la misma renovacion, D-11)
import axios, { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import type { AuthResponse } from '../types/auth'
import { clearSession, loadSession, saveSession } from '../utils/session'

// En dev apunta al backend local; se sobreescribe con VITE_API_BASE_URL
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

// Endpoints publicos: nunca llevan token ni disparan el refresh (RF-008)
const AUTH_PATHS = [
  '/api/auth/solicitar-codigo',
  '/api/auth/verificar',
  '/api/auth/renovar-token',
  '/api/auth/cerrar-sesion',
]

// Se dispara cuando el refresh falla (token revocado/expirado): el AuthContext
// escucha para cerrar sesion y redirigir al login
export const UNAUTHORIZED_EVENT = 'tonuapp:unauthorized'

interface RetriableConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

export const http = axios.create({ baseURL: API_BASE_URL })

let refreshPromise: Promise<boolean> | null = null

function isAuthPath(url?: string): boolean {
  return !!url && AUTH_PATHS.some((p) => url.startsWith(p))
}

http.interceptors.request.use((config) => {
  const session = loadSession()
  if (session && !isAuthPath(config.url)) {
    config.headers.set('Authorization', `Bearer ${session.accessToken}`)
  }
  return config
})

http.interceptors.response.use(
  (response: AxiosResponse) => response,
  async (error: AxiosError) => {
    const config = error.config as RetriableConfig | undefined
    if (error.response?.status !== 401 || !config || config._retry || isAuthPath(config.url)) {
      throw error
    }
    config._retry = true
    if (!(await refreshAccessToken())) {
      window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
      throw error
    }
    const session = loadSession()
    if (session) {
      config.headers.set('Authorization', `Bearer ${session.accessToken}`)
    }
    return http(config)
  },
)

function refreshAccessToken(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = doRefresh().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

// Renueva usando axios pelado (evita recursividad del interceptor de 401)
async function doRefresh(): Promise<boolean> {
  const session = loadSession()
  if (!session?.refreshToken) return false
  try {
    const { data } = await axios.post<AuthResponse>(`${API_BASE_URL}/api/auth/renovar-token`, {
      refreshToken: session.refreshToken,
    })
    saveSession(data)
    return true
  } catch {
    clearSession()
    return false
  }
}