// Alertas de bajo stock (RF-012, D-20): las alertas se GENERAN en el backend al
// actualizar el stock (materiales bajo el minimo), aqui solo se listan (las mas
// recientes primero) con filtro por estado y se CIERRAN manualmente cuando el
// Administrador confirma que actuo. No se almacenan respuestas en la UI; la
// confirmacion la persiste el backend. Modulo de uso interno: solo Administrador
// (backend @PreAuthorize, RF-009) — la UI no ofrece la ruta a un Cliente.
import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { useAuth } from '../../hooks/useAuth'
import * as alertaService from '../../services/alertaService'
import type { AlertaResponse, EstadoAlerta } from '../../types/alertas'
import { extraerMensaje } from '../../utils/errores'
import './alertas.css'

const EASE = [0.2, 0, 0, 1] as const

const paginaVariants = {
  reposo: { opacity: 0 },
  visible: { opacity: 1, transition: { staggerChildren: 0.03 } },
}

const bloqueVariants = {
  reposo: { opacity: 0, y: 10 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.28, ease: EASE } },
}

export function AlertasPage() {
  const { usuario } = useAuth()
  const esAdmin = usuario?.rol === 'Administrador'

  const [alertas, setAlertas] = useState<AlertaResponse[]>([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [filtro, setFiltro] = useState<EstadoAlerta | ''>('activa')
  const [refrescar, setRefrescar] = useState(0)
  const [atendiendo, setAtendiendo] = useState<number | null>(null)

  useEffect(() => {
    let vivo = true
    if (!esAdmin) return
    setCargando(true)
    alertaService
      .listar(filtro || undefined)
      .then((data) => {
        if (!vivo) return
        setAlertas(data)
        setError(null)
      })
      .catch((e) => {
        if (vivo) setError(extraerMensaje(e))
      })
      .finally(() => {
        if (vivo) setCargando(false)
      })
    return () => {
      vivo = false
    }
  }, [esAdmin, filtro, refrescar])

  const atender = async (alerta: AlertaResponse) => {
    setAtendiendo(alerta.idAlerta)
    setError(null)
    try {
      await alertaService.atender(alerta.idAlerta)
      setRefrescar((n) => n + 1)
    } catch (e) {
      setError(extraerMensaje(e))
    } finally {
      setAtendiendo(null)
    }
  }

  if (!esAdmin) {
    return (
      <motion.div variants={bloqueVariants} initial="reposo" animate="visible">
        <h1 className="materiales__titulo">Alertas</h1>
        <p className="materiales__subtitulo">
          El panel de alertas es de uso interno: solo un Administrador puede gestionarlo (RF-009).
        </p>
      </motion.div>
    )
  }

  return (
    <motion.div
      className="alertas"
      variants={paginaVariants}
      initial="reposo"
      animate="visible"
      transition={{ duration: 0.28, ease: EASE }}
    >
      <motion.section variants={bloqueVariants} className="materiales__cabecera">
        <div>
          <h1 className="materiales__titulo">
            <span className="materiales__titulo-num" aria-hidden="true">
              05
            </span>
            Alertas
          </h1>
          <p className="materiales__subtitulo">Avisos de stock mínimo · RF-012</p>
        </div>
      </motion.section>

      <motion.section variants={bloqueVariants} className="materiales__filtros alertas__filtros" aria-label="Filtros">
        <label className="materiales__categoria">
          <span className="materiales__label">Estado</span>
          <select
            value={filtro}
            onChange={(e) => {
              setCargando(true)
              setFiltro(e.target.value as EstadoAlerta | '')
            }}
          >
            <option value="">Todas</option>
            <option value="activa">Activas</option>
            <option value="atendida">Atendidas</option>
          </select>
        </label>
        {filtro !== '' && (
          <button type="button" className="btn btn--ghost" onClick={() => setFiltro('')}>
            Quitar filtro
          </button>
        )}
      </motion.section>

      {error && (
        <p className="materiales__error" role="alert">
          {error}
        </p>
      )}

      <motion.section variants={bloqueVariants} className="materiales__tabla-wrap" aria-live="polite">
        <table className="materiales__tabla">
          <thead>
            <tr>
              <th scope="col">Generada</th>
              <th scope="col">Material</th>
              <th scope="col">Aviso</th>
              <th scope="col">Estado</th>
              <th scope="col" className="acciones">Acciones</th>
            </tr>
          </thead>
          <tbody>
            {cargando ? (
              <tr>
                <td colSpan={5} className="materiales__vacio">
                  Cargando alertas…
                </td>
              </tr>
            ) : alertas.length === 0 ? (
              <tr>
                <td colSpan={5} className="materiales__vacio">
                  {filtro === ''
                    ? 'No hay alertas registradas.'
                    : 'Sin alertas para el filtro seleccionado.'}
                </td>
              </tr>
            ) : (
              alertas.map((a) => (
                <motion.tr
                  key={a.idAlerta}
                  variants={bloqueVariants}
                  initial="reposo"
                  whileInView="visible"
                  viewport={{ once: true }}
                >
                  <td className="mono">
                    {a.fechaGenerada.replace('T', ' ').slice(0, 16)}
                  </td>
                  <td className="materiales__primera">
                    <span className="materiales__fila-acento" aria-hidden="true" />
                    <span className="alertas__material">{a.nombreMaterial}</span>
                  </td>
                  <td>{a.mensaje}</td>
                  <td>
                    <span className={`alertas__estado${a.estado === 'atendida' ? ' alertas__estado--atendida' : ''}`}>
                      {a.estado === 'activa' ? 'Activa' : 'Atendida'}
                    </span>
                  </td>
                  <td className="acciones">
                    {a.estado === 'activa' ? (
                      <button
                        type="button"
                        className="accion accion--editar"
                        disabled={atendiendo === a.idAlerta}
                        onClick={() => void atender(a)}
                      >
                        {atendiendo === a.idAlerta ? 'Confirmando…' : 'Marcar atendida'}
                      </button>
                    ) : (
                      <span className="materiales__muted">—</span>
                    )}
                  </td>
                </motion.tr>
              ))
            )}
          </tbody>
        </table>
      </motion.section>
    </motion.div>
  )
}