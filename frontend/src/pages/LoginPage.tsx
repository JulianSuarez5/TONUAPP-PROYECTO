// Login de TONUAPP (RF-008): dos pasos intencionales.
// Paso 1: correo -> solicitar-codigo. Paso 2: codigo -> verificar. El campo de
// codigo NO se muestra hasta que se envia el codigo. Autenticacion sin contrasena
// (D-07): correo + codigo temporal de un solo uso.
import { useEffect, useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { extraerMensaje, extraerStatus } from '../utils/errores'

// El cooldown de reenvio del backend (D-12) son 60s; lo replicamos en la UI
// para deshabilitar el reenvio y no chocar con el 429
const COOLDOWN_SEGUNDOS = 60

type Paso = 'correo' | 'codigo'

const CORREO_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function LoginPage() {
  const { isAutenticado, solicitarCodigo, verificarCorreo } = useAuth()
  const navigate = useNavigate()

  const [paso, setPaso] = useState<Paso>('correo')
  const [correo, setCorreo] = useState('')
  const [codigo, setCodigo] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [entrando, setEntrando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [cooldown, setCooldown] = useState(0)
  const [nuevaSesion, setNuevaSesion] = useState(false)

  // Cuenta regresiva del cooldown de reenvio (D-12)
  useEffect(() => {
    if (cooldown <= 0) return
    const t = window.setInterval(() => {
      setCooldown((c) => Math.max(0, c - 1))
    }, 1000)
    return () => window.clearInterval(t)
  }, [cooldown])

  // Al autenticarse (verificarCorreo deja la sesion en el contexto) redirigimos al inicio
  useEffect(() => {
    if (nuevaSesion && isAutenticado) {
      navigate('/', { replace: true })
    }
  }, [nuevaSesion, isAutenticado, navigate])

  if (isAutenticado && !nuevaSesion) {
    return <Navigate to="/" replace />
  }

  const enviarCodigo = async (e?: FormEvent) => {
    e?.preventDefault()
    const correoTrim = correo.trim()
    if (!CORREO_REGEX.test(correoTrim)) {
      setError('Ingresa un correo valido para recibir el codigo.')
      return
    }
    setEnviando(true)
    setError(null)
    try {
      await solicitarCodigo(correoTrim)
      setCodigo('')
      setCooldown(COOLDOWN_SEGUNDOS)
      setPaso('codigo')
    } catch (err) {
      setError(extraerMensaje(err))
      // El cooldown de UI solo replica el del backend: se activa unicamente ante
      // un 429 (D-12). Un error de red/500 no debe bloquear el reenvio
      if (extraerStatus(err) === 429) {
        setCooldown(COOLDOWN_SEGUNDOS)
      }
    } finally {
      setEnviando(false)
    }
  }

  const entrar = async (e?: FormEvent) => {
    e?.preventDefault()
    const codigoTrim = codigo.trim()
    if (!codigoTrim) {
      setError('Ingresa el codigo que recibiste por correo.')
      return
    }
    setEntrando(true)
    setError(null)
    try {
      await verificarCorreo(correo.trim(), codigoTrim)
      setNuevaSesion(true)
    } catch (err) {
      setError(extraerMensaje(err))
    } finally {
      setEntrando(false)
    }
  }

  return (
    <main className="login">
      <section className="login__card" aria-labelledby="login-titulo">
        <div className="login__emblem" aria-hidden="true" />
        <h1 id="login-titulo" className="login__wordmark">
          TONUAPP
        </h1>
        <p className="login__subtitle">
          Agregados el Tonusco S.A.S. &middot; inventario en tiempo real
        </p>

        {paso === 'correo' ? (
          <>
            <h2 className="login__heading">Iniciar sesión</h2>
            <form className="login__form" onSubmit={enviarCodigo} noValidate>
              <div>
                <label className="login__label" htmlFor="login-correo">
                  Correo
                </label>
                <input
                  id="login-correo"
                  className="login__input"
                  type="email"
                  autoComplete="email"
                  inputMode="email"
                  placeholder="nombre@correo.com"
                  value={correo}
                  onChange={(e) => setCorreo(e.target.value)}
                  disabled={enviando}
                  autoFocus
                />
                <p className="login__hint">
                  Solo necesitas tu correo para solicitar el código de acceso.
                </p>
              </div>
              <p className="login__error" role="alert">
                {error}
              </p>
              <div className="login__actions">
                <button className="login__cta" type="submit" disabled={enviando}>
                  {enviando ? 'Enviando…' : 'Enviar código al correo'}
                </button>
              </div>
            </form>
          </>
        ) : (
          <>
            <h2 className="login__heading">Ingresa el código</h2>
            <form className="login__form" onSubmit={entrar} noValidate>
              <div>
                <label className="login__label" htmlFor="login-codigo">
                  Código enviado a
                </label>
                <p className="login__hint">{correo}</p>
                <input
                  id="login-codigo"
                  className="login__input login__input--code"
                  type="text"
                  autoComplete="one-time-code"
                  inputMode="numeric"
                  maxLength={8}
                  placeholder="000000"
                  value={codigo}
                  onChange={(e) => setCodigo(e.target.value)}
                  disabled={entrando}
                  autoFocus
                />
                <p className="login__hint">
                  Revisa tu bandeja de entrada. Si no llega, vuelve a pedirlo.
                </p>
              </div>
              <p className="login__error" role="alert">
                {error}
              </p>
              <div className="login__actions">
                <button className="login__cta" type="submit" disabled={entrando}>
                  {entrando ? 'Verificando…' : 'Entrar'}
                </button>
              </div>
              <div className="login__links">
                <button
                  className="login__link"
                  type="button"
                  onClick={() => {
                    setError(null)
                    setPaso('correo')
                  }}
                >
                  Editar correo
                </button>
                <button
                  className="login__link"
                  type="button"
                  onClick={() => void enviarCodigo()}
                  disabled={cooldown > 0}
                >
                  {cooldown > 0 ? (
                    <span className="login__cooldown">Reenviar en {cooldown}s</span>
                  ) : (
                    'Reenviar código'
                  )}
                </button>
              </div>
            </form>
          </>
        )}
      </section>
    </main>
  )
}