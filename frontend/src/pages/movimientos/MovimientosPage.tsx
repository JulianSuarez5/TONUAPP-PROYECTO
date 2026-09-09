// Movimientos de inventario (RF-005 a RF-007, RF-011, patrón D-32/D-34): historico
// paginado con filtros por material/tipo/estado/fecha y tres acciones de registro:
// ENTRADA/SALIDA (modal Movimiento), AJUSTE a stock objetivo (modal Ajuste, RF-011)
// y ANULAR (revierte el efecto sobre el stock). La cantidad del movimiento siempre
// es positiva (D-02/D-18); la UI muestra "+"/"−" según el tipo y el ajuste sin signo
// porque su efecto depende del stock previo. idLote/idZona opcionales en una sección
// plegable (RF-015/D-34). Modulo de uso interno: SOLO Administrador (RF-009), el
// backend lo exige (@PreAuthorize) y la UI no ofrece la ruta a un Cliente.
import { useEffect, useState, type FormEvent } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { Modal } from '../../components/Modal'
import { useAuth } from '../../hooks/useAuth'
import * as materialService from '../../services/materialService'
import * as movimientoService from '../../services/movimientoService'
import { listarLotesDeMaterial, listarZonas } from '../../services/ubicacionService'
import type { MaterialResponse } from '../../types/materiales'
import type {
  AjusteRequest,
  EstadoMovimiento,
  MovimientoRequest,
  MovimientoResponse,
  MovimientosPaged,
  TipoMovimiento,
} from '../../types/movimientos'
import type { LoteResponse, ZonaAcopioResponse } from '../../types/ubicaciones'
import { extraerMensaje } from '../../utils/errores'
import './movimientos.css'

const VENTANA_PAGINAS = 2

const EASE = [0.2, 0, 0, 1] as const

const TIPOS_MOVIMIENTO: { valor: TipoMovimiento; etiqueta: string }[] = [
  { valor: 'entrada', etiqueta: 'Entrada' },
  { valor: 'salida_venta', etiqueta: 'Salida (venta)' },
  { valor: 'salida_merma', etiqueta: 'Merma' },
]

const ETIQUETA_TIPO: Record<TipoMovimiento, string> = {
  entrada: 'Entrada',
  salida_venta: 'Salida (venta)',
  salida_merma: 'Merma',
  ajuste: 'Ajuste',
}

type ModalActual =
  | { tipo: 'registrar' }
  | { tipo: 'ajuste' }
  | { tipo: 'detalle'; mov: MovimientoResponse }
  | { tipo: 'anular'; mov: MovimientoResponse }
  | null

const filaVariants = {
  reposo: { backgroundColor: 'rgb(255 255 255 / 0)' },
  hover: { backgroundColor: 'var(--color-row-hover)' },
}

const costadoVariants = {
  reposo: { opacity: 0, x: -8, scaleY: 0 },
  hover: { opacity: 1, x: 0, scaleY: 1 },
  transition: { duration: 0.16, ease: EASE },
}

const paginaVariants = {
  reposo: { opacity: 0 },
  visible: { opacity: 1, transition: { staggerChildren: 0.03 } },
}

const bloqueVariants = {
  reposo: { opacity: 0, y: 10 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.28, ease: EASE } },
}

function cargarTodosMateriales(): Promise<MaterialResponse[]> {
  return materialService.listar(0, 100).then(async (primera) => {
    const todos = [...primera.content]
    for (let p = 1; p < primera.totalPages; p++) {
      const res = await materialService.listar(p, 100)
      todos.push(...res.content)
    }
    return todos
  })
}

// Cantidad con signo según el tipo (D-02): entrada suma, salidas restan, el ajuste
// guarda |diferencia| y su efecto depende del stock previo (no se firma).
function formatearCantidad(mov: MovimientoResponse): string {
  const n = mov.cantidad.toLocaleString('es-CO', { maximumFractionDigits: 2 })
  if (mov.tipo === 'entrada') return `+${n}`
  if (mov.tipo === 'salida_venta' || mov.tipo === 'salida_merma') return `−${n}`
  return n
}

export function MovimientosPage() {
  const { usuario } = useAuth()
  const esAdmin = usuario?.rol === 'Administrador'

  const [datos, setDatos] = useState<MovimientosPaged<MovimientoResponse> | null>(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [pagina, setPagina] = useState(0)
  const [tamano, setTamano] = useState(20)
  const [refrescar, setRefrescar] = useState(0)

  const [filtroMaterial, setFiltroMaterial] = useState<number | ''>('')
  const [filtroTipo, setFiltroTipo] = useState<TipoMovimiento | ''>('')
  const [filtroEstado, setFiltroEstado] = useState<EstadoMovimiento | ''>('')
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')

  const [modal, setModal] = useState<ModalActual>(null)
  const [guardando, setGuardando] = useState(false)

  // Inventario para selects de filtro y formularios; se recarga tras cada operación
  const [inventario, setInventario] = useState<MaterialResponse[]>([])
  const [refrescarInventario, setRefrescarInventario] = useState(0)

  useEffect(() => {
    let vivo = true
    if (!esAdmin) return
    cargarTodosMateriales()
      .then((res) => {
        if (vivo) setInventario(res)
      })
      .catch(() => {
        if (vivo) setInventario([])
      })
    return () => {
      vivo = false
    }
  }, [esAdmin, refrescarInventario])

  useEffect(() => {
    let vivo = true
    if (!esAdmin) {
      setCargando(false)
      return undefined
    }
    setCargando(true)
    movimientoService
      .listar({
        idMaterial: filtroMaterial === '' ? undefined : filtroMaterial,
        tipo: filtroTipo || undefined,
        estado: filtroEstado || undefined,
        desde: desde || undefined,
        hasta: hasta || undefined,
        page: pagina,
        size: tamano,
      })
      .then((res) => {
        if (vivo) setDatos(res)
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
  }, [esAdmin, filtroMaterial, filtroTipo, filtroEstado, desde, hasta, pagina, tamano, refrescar])

  const recargar = () => {
    setPagina(0)
    setRefrescar((r) => r + 1)
    setRefrescarInventario((r) => r + 1)
  }

  const guardarMovimiento = async (request: MovimientoRequest) => {
    setGuardando(true)
    try {
      await movimientoService.registrar(request)
      setModal(null)
      recargar()
    } finally {
      setGuardando(false)
    }
  }

  const guardarAjuste = async (request: AjusteRequest) => {
    setGuardando(true)
    try {
      await movimientoService.ajustar(request)
      setModal(null)
      recargar()
    } finally {
      setGuardando(false)
    }
  }

  const anularMovimiento = async (mov: MovimientoResponse) => {
    setGuardando(true)
    try {
      await movimientoService.anular(mov.idMovimiento)
      setModal(null)
      setRefrescar((r) => r + 1)
      setRefrescarInventario((r) => r + 1)
    } finally {
      setGuardando(false)
    }
  }

  const limpiarFiltros = () => {
    setFiltroMaterial('')
    setFiltroTipo('')
    setFiltroEstado('')
    setDesde('')
    setHasta('')
    setPagina(0)
  }

  const resumen = (p: MovimientosPaged<MovimientoResponse>) => {
    const inicio = p.totalElements === 0 ? 0 : pagina * tamano + 1
    const fin = Math.min(pagina * tamano + tamano, p.totalElements)
    return { inicio, fin }
  }

  const paginasVisibles = (totalPaginas: number) =>
    Array.from(new Set([0, totalPaginas - 1, pagina, pagina - VENTANA_PAGINAS, pagina + VENTANA_PAGINAS]))
      .filter((n) => n >= 0 && n < totalPaginas)
      .sort((a, b) => a - b)

  if (!esAdmin) {
    return (
      <motion.div className="ubigueros-inicio" variants={bloqueVariants} initial="reposo" animate="visible">
        <h1 className="materiales__titulo">Movimientos</h1>
        <p className="materiales__subtitulo">
          El historial de movimientos es de uso interno: solo un Administrador puede consultarlo (RF-009).
        </p>
      </motion.div>
    )
  }

  return (
    <motion.div
      className="movimientos"
      variants={paginaVariants}
      initial="reposo"
      animate="visible"
      transition={{ duration: 0.28, ease: EASE }}
    >
      <motion.section variants={bloqueVariants} className="materiales__cabecera">
        <div>
          <h1 className="materiales__titulo">
            <span className="materiales__titulo-num" aria-hidden="true">
              04
            </span>
            Movimientos
          </h1>
          <p className="materiales__subtitulo">Entradas, salidas, mermas y ajustes · RF-011, RF-013</p>
        </div>
        <div className="movimientos__ctas">
          <button type="button" className="btn" onClick={() => setModal({ tipo: 'ajuste' })}>
            Ajustar stock
          </button>
          <button type="button" className="btn btn--cta" onClick={() => setModal({ tipo: 'registrar' })}>
            ＋ Registrar movimiento
          </button>
        </div>
      </motion.section>

      <motion.section variants={bloqueVariants} className="materiales__filtros movimientos__filtros" aria-label="Filtros">
        <label className="materiales__categoria">
          <span className="materiales__label">Material</span>
          <select
            value={filtroMaterial}
            onChange={(e) => {
              setCargando(true)
              setFiltroMaterial(e.target.value === '' ? '' : Number(e.target.value))
              setPagina(0)
            }}
          >
            <option value="">Todos</option>
            {inventario.map((m) => (
              <option key={m.idMaterial} value={m.idMaterial}>
                {m.nombre}
              </option>
            ))}
          </select>
        </label>

        <label className="materiales__categoria">
          <span className="materiales__label">Tipo</span>
          <select
            value={filtroTipo}
            onChange={(e) => {
              setCargando(true)
              setFiltroTipo(e.target.value as TipoMovimiento | '')
              setPagina(0)
            }}
          >
            <option value="">Todos</option>
            {[...TIPOS_MOVIMIENTO, { valor: 'ajuste', etiqueta: 'Ajuste' }].map((t) => (
              <option key={t.valor} value={t.valor}>
                {t.etiqueta}
              </option>
            ))}
          </select>
        </label>

        <label className="materiales__categoria">
          <span className="materiales__label">Estado</span>
          <select
            value={filtroEstado}
            onChange={(e) => {
              setCargando(true)
              setFiltroEstado(e.target.value as EstadoMovimiento | '')
              setPagina(0)
            }}
          >
            <option value="">Todos</option>
            <option value="activo">Activo</option>
            <option value="anulado">Anulado</option>
          </select>
        </label>

        <label className="materiales__categoria movimientos__fecha">
          <span className="materiales__label">Desde</span>
          <input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} aria-label="Desde" />
        </label>

        <label className="materiales__categoria movimientos__fecha">
          <span className="materiales__label">Hasta</span>
          <input type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} aria-label="Hasta" />
        </label>

        {(filtroMaterial !== '' || filtroTipo !== '' || filtroEstado !== '' || desde !== '' || hasta !== '') && (
          <button type="button" className="btn btn--ghost" onClick={limpiarFiltros}>
            Quitar filtros
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
              <th scope="col">Fecha</th>
              <th scope="col">Material</th>
              <th scope="col">Tipo</th>
              <th scope="col" className="numero">Cantidad</th>
              <th scope="col">Usuario</th>
              <th scope="col">Estado</th>
              <th scope="col" className="acciones">Acciones</th>
            </tr>
          </thead>
          <tbody>
            {cargando ? (
              <tr>
                <td colSpan={7} className="materiales__vacio">
                  Cargando movimientos…
                </td>
              </tr>
            ) : datos && datos.content.length === 0 ? (
              <tr>
                <td colSpan={7} className="materiales__vacio">
                  Sin movimientos para los filtros seleccionados.
                </td>
              </tr>
            ) : (
              datos?.content.map((mov) => (
                <motion.tr
                  key={mov.idMovimiento}
                  variants={filaVariants}
                  initial="reposo"
                  whileHover="hover"
                  transition={{ duration: 0.16, ease: EASE }}
                >
                  <td className="materiales__primera">
                    <motion.span
                      className="materiales__fila-acento"
                      variants={costadoVariants}
                      aria-hidden="true"
                    />
                    <button
                      type="button"
                      className="materiales__link"
                      onClick={() => setModal({ tipo: 'detalle', mov })}
                    >
                      <span className="mono">{mov.fechaMovimiento.replace('T', ' ')}</span>
                    </button>
                  </td>
                  <td>{mov.nombreMaterial}</td>
                  <td>
                    <span className={mov.tipo === 'ajuste' ? 'movimientos__tipo movimientos__tipo--ajuste' : 'movimientos__tipo'}>
                      {ETIQUETA_TIPO[mov.tipo]}
                    </span>
                  </td>
                  <td className="numero mono movimientos__cantidad">{formatearCantidad(mov)}</td>
                  <td>{mov.nombreUsuario}</td>
                  <td>
                    <span
                      className={`movimientos__estado${mov.estado === 'anulado' ? ' movimientos__estado--anulado' : ''}`}
                    >
                      {mov.estado === 'activo' ? 'Activo' : 'Anulado'}
                    </span>
                  </td>
                  <td className="acciones">
                    <button
                      type="button"
                      className="accion"
                      title="Ver detalle"
                      aria-label={`Ver detalle del movimiento ${mov.idMovimiento}`}
                      onClick={() => setModal({ tipo: 'detalle', mov })}
                    >
                      ◉
                    </button>
                    {mov.estado === 'activo' && (
                      <button
                        type="button"
                        className="accion accion--eliminar"
                        title="Anular"
                        aria-label={`Anular movimiento ${mov.idMovimiento}`}
                        onClick={() => setModal({ tipo: 'anular', mov })}
                      >
                        ✕
                      </button>
                    )}
                  </td>
                </motion.tr>
              ))
            )}
          </tbody>
        </table>

        {datos && datos.totalElements > 0 && (
          <div className="materiales__pie">
            <span className="materiales__total mono">
              {resumen(datos).inicio}–{resumen(datos).fin} de {datos.totalElements.toLocaleString('es-CO')}
            </span>
            <nav className="paginacion" aria-label="Paginación de movimientos">
              {paginasVisibles(datos.totalPages).map((n, i, arr) => {
                const anterior = arr[i - 1]
                const salto = anterior !== undefined && n - anterior > 1
                return (
                  <span key={n} className="paginacion__grupo">
                    {salto && <span className="paginacion__salto">…</span>}
                    <button
                      type="button"
                      className={`paginacion__btn${n === pagina ? ' paginacion__btn--actual' : ''}`}
                      aria-current={n === pagina ? 'page' : undefined}
                      onClick={() => {
                        setCargando(true)
                        setPagina(n)
                      }}
                    >
                      {n + 1}
                    </button>
                  </span>
                )
              })}
            </nav>
            <label className="materiales__tamano">
              Por página
              <select
                value={tamano}
                onChange={(e) => {
                  setCargando(true)
                  setTamano(Number(e.target.value))
                  setPagina(0)
                }}
              >
                {[10, 20, 50, 100].map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </label>
          </div>
        )}
      </motion.section>

      <AnimatePresence>
        {modal?.tipo === 'registrar' && (
          <MovimientoFormModal
            key="movimiento-form"
            materiales={inventario}
            guardando={guardando}
            onGuardar={guardarMovimiento}
            onCerrar={() => setModal(null)}
          />
        )}
      </AnimatePresence>

      <AnimatePresence>
        {modal?.tipo === 'ajuste' && (
          <AjusteFormModal
            key="ajuste-form"
            materiales={inventario}
            guardando={guardando}
            onGuardar={guardarAjuste}
            onCerrar={() => setModal(null)}
          />
        )}
      </AnimatePresence>

      <AnimatePresence>
        {modal?.tipo === 'detalle' && (
          <DetalleMovimientoModal key={modal.mov.idMovimiento} mov={modal.mov} onCerrar={() => setModal(null)} />
        )}
      </AnimatePresence>

      <AnimatePresence>
        {modal?.tipo === 'anular' && (
          <ConfirmarAnularModal
            key={`anular-${modal.mov.idMovimiento}`}
            mov={modal.mov}
            guardando={guardando}
            onConfirmar={anularMovimiento}
            onCerrar={() => setModal(null)}
          />
        )}
      </AnimatePresence>
    </motion.div>
  )
}

interface MovimientoFormProps {
  materiales: MaterialResponse[]
  guardando: boolean
  onGuardar: (request: MovimientoRequest) => Promise<void>
  onCerrar: () => void
}

function MovimientoFormModal({ materiales, guardando, onGuardar, onCerrar }: MovimientoFormProps) {
  const [tipo, setTipo] = useState<TipoMovimiento>('entrada')
  const [idMaterial, setIdMaterial] = useState<number | ''>('')
  const [cantidad, setCantidad] = useState('')
  const [motivo, setMotivo] = useState('')
  const [observaciones, setObservaciones] = useState('')
  const [idLote, setIdLote] = useState<number | ''>('')
  const [idZona, setIdZona] = useState<number | ''>('')
  const [lotes, setLotes] = useState<LoteResponse[]>([])
  const [zonas, setZonas] = useState<ZonaAcopioResponse[]>([])
  const [error, setError] = useState<string | null>(null)
  const [verUbicacion, setVerUbicacion] = useState(false)

  // Lotes del material elegido (solo informativos si no hay material seleccionado)
  useEffect(() => {
    let vivo = true
    if (idMaterial === '') {
      setLotes([])
      return undefined
    }
    listarLotesDeMaterial(idMaterial)
      .then((res) => {
        if (vivo) setLotes(res)
      })
      .catch(() => {
        if (vivo) setLotes([])
      })
    return () => {
      vivo = false
    }
  }, [idMaterial])

  useEffect(() => {
    let vivo = true
    listarZonas()
      .then((res) => {
        if (vivo) setZonas(res.content)
      })
      .catch(() => {
        if (vivo) setZonas([])
      })
    return () => {
      vivo = false
    }
  }, [])

  const esSalida = tipo === 'salida_venta' || tipo === 'salida_merma'

  const guardar = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (idMaterial === '') return setError('Selecciona el material.')
    const cantidadNum = Number(cantidad)
    if (cantidad === '' || !Number.isFinite(cantidadNum) || cantidadNum <= 0) {
      return setError('Ingresa una cantidad mayor que 0.')
    }
    const material = materiales.find((m) => m.idMaterial === idMaterial)
    if (esSalida && material && material.stock <= 0) {
      return setError('El material no tiene stock disponible para esta salida.')
    }
    try {
      await onGuardar({
        idMaterial: idMaterial as number,
        tipo: tipo as 'entrada' | 'salida_venta' | 'salida_merma',
        cantidad: cantidadNum,
        motivo: motivo.trim() || undefined,
        observaciones: observaciones.trim() || undefined,
        idLote: idLote === '' ? null : (idLote as number),
        idZona: idZona === '' ? null : (idZona as number),
      })
    } catch (e) {
      setError(extraerMensaje(e))
    }
  }

  return (
    <Modal titulo="Registrar movimiento" onCerrar={onCerrar}>
      <form className="formulario" onSubmit={guardar} noValidate>
        <div className="formulario__fila">
          <label className="formulario__campo">
            <span className="formulario__label">Tipo</span>
            <select value={tipo} onChange={(e) => setTipo(e.target.value as TipoMovimiento)}>
              {TIPOS_MOVIMIENTO.map((t) => (
                <option key={t.valor} value={t.valor}>
                  {t.etiqueta}
                </option>
              ))}
            </select>
          </label>
          <label className="formulario__campo">
            <span className="formulario__label">Material</span>
            <select value={idMaterial} onChange={(e) => setIdMaterial(e.target.value === '' ? '' : Number(e.target.value))}>
              <option value="">Seleccionar…</option>
              {materiales.map((m) => (
                <option key={m.idMaterial} value={m.idMaterial} disabled={esSalida && m.stock <= 0}>
                  {m.nombre} · stock {m.stock.toLocaleString('es-CO')}
                  {esSalida && m.stock <= 0 ? ' (sin stock)' : ''}
                </option>
              ))}
            </select>
          </label>
        </div>

        <div className="formulario__fila">
          <label className="formulario__campo">
            <span className="formulario__label">Cantidad</span>
            <input
              type="number"
              step="0.01"
              min="0.01"
              className="mono"
              value={cantidad}
              onChange={(e) => setCantidad(e.target.value)}
              placeholder="0.00"
            />
          </label>
          <label className="formulario__campo">
            <span className="formulario__label">Motivo</span>
            <input value={motivo} onChange={(e) => setMotivo(e.target.value)} placeholder="Opcional" />
          </label>
        </div>

        <label className="formulario__campo">
          <span className="formulario__label">Observaciones</span>
          <textarea value={observaciones} onChange={(e) => setObservaciones(e.target.value)} rows={2} placeholder="Opcional" />
        </label>

        <button
          type="button"
          className="movimientos__plegable-cab"
          onClick={() => setVerUbicacion((v) => !v)}
          aria-expanded={verUbicacion}
        >
          {verUbicacion ? '△' : '▽'} Ubicación (opcional)
        </button>
        {verUbicacion && (
          <div className="formulario__fila">
            <label className="formulario__campo">
              <span className="formulario__label">Lote</span>
              <select value={idLote} onChange={(e) => setIdLote(e.target.value === '' ? '' : Number(e.target.value))}>
                <option value="">Sin lote</option>
                {lotes.map((l) => (
                  <option key={l.idLote} value={l.idLote}>
                    {l.codigoLote}
                  </option>
                ))}
              </select>
              {idMaterial === '' && <span className="formulario__nota">Elige un material para ver sus lotes.</span>}
            </label>
            <label className="formulario__campo">
              <span className="formulario__label">Zona de acopio</span>
              <select value={idZona} onChange={(e) => setIdZona(e.target.value === '' ? '' : Number(e.target.value))}>
                <option value="">Sin zona</option>
                {zonas.map((z) => (
                  <option key={z.idZona} value={z.idZona}>
                    {z.nombreZona}
                  </option>
                ))}
              </select>
            </label>
          </div>
        )}

        <p className="formulario__nota">
          La cantidad se suma o resta al stock en el mismo instante (una transacción, RF-005/RF-006). Una salida
          superior al stock disponible será rechazada.
        </p>

        {error && (
          <p className="formulario__error" role="alert">
            {error}
          </p>
        )}

        <div className="modal__actions">
          <button type="button" className="btn btn--ghost" onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className="btn btn--cta" disabled={guardando}>
            {guardando ? 'Registrando…' : 'Registrar'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

interface AjusteFormProps {
  materiales: MaterialResponse[]
  guardando: boolean
  onGuardar: (request: AjusteRequest) => Promise<void>
  onCerrar: () => void
}

function AjusteFormModal({ materiales, guardando, onGuardar, onCerrar }: AjusteFormProps) {
  const [idMaterial, setIdMaterial] = useState<number | ''>('')
  const [cantidadNueva, setCantidadNueva] = useState('')
  const [motivo, setMotivo] = useState('')
  const [error, setError] = useState<string | null>(null)

  const materialElegido = materiales.find((m) => m.idMaterial === idMaterial)

  const guardar = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (idMaterial === '') return setError('Selecciona el material.')
    const cifra = Number(cantidadNueva)
    if (cantidadNueva === '' || !Number.isFinite(cifra) || cifra < 0) {
      return setError('Ingresa la cantidad contada (≥ 0).')
    }
    if (!motivo.trim()) return setError('El motivo del ajuste es obligatorio.')
    try {
      await onGuardar({ idMaterial: idMaterial as number, cantidadNueva: cifra, motivo: motivo.trim() })
    } catch (e) {
      setError(extraerMensaje(e))
    }
  }

  return (
    <Modal titulo="Ajustar stock (conteo físico)" onCerrar={onCerrar}>
      <form className="formulario" onSubmit={guardar} noValidate>
        <label className="formulario__campo">
          <span className="formulario__label">Material</span>
          <select value={idMaterial} onChange={(e) => setIdMaterial(e.target.value === '' ? '' : Number(e.target.value))}>
            <option value="">Seleccionar…</option>
            {materiales.map((m) => (
              <option key={m.idMaterial} value={m.idMaterial}>
                {m.nombre} · stock {m.stock.toLocaleString('es-CO')}
              </option>
            ))}
          </select>
        </label>

        <div className="formulario__fila">
          <label className="formulario__campo">
            <span className="formulario__label">Cantidad contada</span>
            <input
              type="number"
              step="0.01"
              min="0"
              className="mono"
              value={cantidadNueva}
              onChange={(e) => setCantidadNueva(e.target.value)}
              placeholder="0.00"
            />
          </label>
          <label className="formulario__campo">
            <span className="formulario__label">Motivo</span>
            <input value={motivo} onChange={(e) => setMotivo(e.target.value)} placeholder="Ej. conteo físico mensual" />
          </label>
        </div>

        <p className="formulario__nota">
          El stock quedará exactamente en la cifra contada (RF-011).{' '}
          {materialElegido
            ? `El material está en ${materialElegido.stock.toLocaleString('es-CO')}.`
            : 'Se registra la diferencia como un movimiento tipo ajuste.'}
        </p>

        {error && (
          <p className="formulario__error" role="alert">
            {error}
          </p>
        )}

        <div className="modal__actions">
          <button type="button" className="btn btn--ghost" onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className="btn btn--cta" disabled={guardando}>
            {guardando ? 'Ajustando…' : 'Ajustar'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

interface DetalleProps {
  mov: MovimientoResponse
  onCerrar: () => void
}

function DetalleMovimientoModal({ mov, onCerrar }: DetalleProps) {
  return (
    <Modal titulo={`Movimiento #${mov.idMovimiento}`} onCerrar={onCerrar}>
      <p className="detalle__sub">
        {ETIQUETA_TIPO[mov.tipo]} de <strong>{mov.nombreMaterial}</strong>
      </p>

      <div className="detalle__numeros">
        <div>
          <span className="detalle__label">Cantidad</span>
          <span className="detalle__valor mono">{formatearCantidad(mov)}</span>
        </div>
        <div>
          <span className="detalle__label">Estado</span>
          <span className="detalle__valor">{mov.estado === 'activo' ? 'Activo' : 'Anulado'}</span>
        </div>
      </div>

      <div className="detalle__meta">
        <span>
          <span className="detalle__label">Fecha</span>
          <span className="mono">{mov.fechaMovimiento.replace('T', ' ')}</span>
        </span>
        <span>
          <span className="detalle__label">Usuario</span>
          <span>{mov.nombreUsuario}</span>
        </span>
        <span>
          <span className="detalle__label">Lote</span>
          <span className="mono">{mov.idLote ?? '—'}</span>
        </span>
        <span>
          <span className="detalle__label">Zona</span>
          <span>{mov.idZona ?? '—'}</span>
        </span>
        <span>
          <span className="detalle__label">Motivo</span>
          <span>{mov.motivo ?? '—'}</span>
        </span>
        <span>
          <span className="detalle__label">Observaciones</span>
          <span>{mov.observaciones ?? '—'}</span>
        </span>
      </div>

      <div className="modal__actions">
        <button type="button" className="btn btn--ghost" onClick={onCerrar}>
          Cerrar
        </button>
      </div>
    </Modal>
  )
}

interface AnularProps {
  mov: MovimientoResponse
  guardando: boolean
  onConfirmar: (mov: MovimientoResponse) => Promise<void>
  onCerrar: () => void
}

function ConfirmarAnularModal({ mov, guardando, onConfirmar, onCerrar }: AnularProps) {
  const [error, setError] = useState<string | null>(null)

  const confirmar = async () => {
    setError(null)
    try {
      await onConfirmar(mov)
    } catch (e) {
      setError(extraerMensaje(e))
    }
  }

  return (
    <Modal titulo="Anular movimiento" onCerrar={onCerrar}>
      <p className="modal__texto">
        ¿Anular el movimiento <strong>{ETIQUETA_TIPO[mov.tipo].toLowerCase()} #{mov.idMovimiento}</strong> de{' '}
        <strong>{mov.nombreMaterial}</strong>? Se revierte su efecto sobre el stock y queda marcado como{' '}
        <em>anulado</em> en el historial.
      </p>
      {mov.tipo === 'ajuste' && (
        <p className="formulario__nota">Es un ajuste: la reversión devuelve el stock a su valor anterior (D-19).</p>
      )}
      {error && (
        <p className="formulario__error" role="alert">
          {error}
        </p>
      )}
      <div className="modal__actions">
        <button type="button" className="btn btn--ghost" onClick={onCerrar}>
          Cancelar
        </button>
        <button type="button" className="btn btn--danger" onClick={() => void confirmar()} disabled={guardando}>
          {guardando ? 'Anulando…' : 'Anular movimiento'}
        </button>
      </div>
    </Modal>
  )
}