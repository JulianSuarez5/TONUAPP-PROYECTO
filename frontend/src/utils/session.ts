// Persistencia de la sesion JWT en localStorage. El access expira en 15 min (D-11);
// la sesion se refresca automaticamente por el interceptor de http.ts
import type { Session } from '../types/auth'

const SESSION_KEY = 'tonuapp.session'

export function loadSession(): Session | null {
  const raw = localStorage.getItem(SESSION_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as Session
  } catch {
    localStorage.removeItem(SESSION_KEY)
    return null
  }
}

export function saveSession(session: Session): void {
  localStorage.setItem(SESSION_KEY, JSON.stringify(session))
}

export function clearSession(): void {
  localStorage.removeItem(SESSION_KEY)
}