// Util unica para traducir errores HTTP del backend (formato ApiError:
// { timestamp, status, error, message, path }). La usan login y modulos.
export interface ErrorConRespuesta {
  response?: { data?: { message?: string }; status?: number }
}

export function esErrorConRespuesta(e: unknown): e is ErrorConRespuesta {
  return typeof e === 'object' && e !== null && 'response' in e
}

// Mensaje del backend (ApiError.message) o generico
export function extraerMensaje(e: unknown): string {
  if (esErrorConRespuesta(e)) {
    const msg = e.response?.data?.message
    if (msg) return msg
  }
  return 'No se pudo completar la operacion. Intentelo nuevamente.'
}

export function extraerStatus(e: unknown): number | null {
  if (esErrorConRespuesta(e)) {
    const status = e.response?.status
    return typeof status === 'number' ? status : null
  }
  return null
}

// Mensaje de error de validacion de formulario (campo) del backend cuando aplica
export function extraerCampoError(e: unknown): string | null {
  if (esErrorConRespuesta(e)) {
    const data = e.response?.data as
      | { campo?: string; message?: string }
      | undefined
    if (data?.campo) return data.message ?? null
  }
  return null
}