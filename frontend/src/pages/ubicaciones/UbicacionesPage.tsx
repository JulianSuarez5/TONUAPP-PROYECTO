// Ubicaciones y lotes (RF-015, US-16/US-17, D-34): doble tabla con pestanas
// "Zonas de acopio" y "Lotes". La zona valida el tipo de material permitido contra
// la categoria del material (409 del backend si es incompatible); la cantidad del
// lote es solo informativa (D-02), el stock deriva de movimientos. Mutaciones
// (crear/editar/desactivar/asignar) solo para Administrador (RF-009); un Cliente
// ve las listas en lectura. Reutiliza el patron de Materiales/Proveedores
// (D-28/D-30/D-32): framer-motion, modales con el estandar emilkowalski.
import { useEffect, useState, type FormEvent } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { Modal } from '../../components/Modal'
import { useAuth } from '../../hooks/useAuth'
import * as materialService from '../../services/materialService'
import * as ubicacionService from '../../services/ubicacionService'
import type { CategoriaResponse, MaterialResponse } from '../../types/materiales'
import type {
  LoteRequest,
  LoteResponse,
  UbicacionPaged,
  ZonaAcopioRequest,
  ZonaAcopioResponse,
} from '../../types/ubicaciones'
import { extraerMensaje } from '../../utils/errores'
import './ubicaciones.css'

const VENTANA_PAGINAS = 2

const EASE = [0.2, 0, 0, 1] as const

type Vista = 'zonas' | 'lotes'

type ZonaModalActual =
  | { tipo: 'detalle'; zona: ZonaAcopioResponse }
  | { tipo: 'formulario'; zona: ZonaAcopioResponse | null }
  | { tipo: 'confirmar'; zona: ZonaAcopioResponse }
  | { tipo: 'materiales'; zona: ZonaAcopioResponse }
  | null

type LoteModalActual =
  | { tipo: 'formulario'; lote: LoteResponse | null }
  | { tipo: 'confirmar'; lote: LoteResponse }
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

export function UbicacionesPage() {
  const { usuario } = useAuth()
  const esAdmin = usuario?.rol === 'Administrador'

  const [vista, setVista] = useState<Vista>('zonas')

  // Zonas
  const [zonas, setZonas] = useState<UbicacionPaged<ZonaAcopioResponse> | null>(null)
  const [cargandoZonas, setCargandoZonas] = useState(true)
  const [errorZonas, setErrorZonas] = useState<string | null>(null)
  const [paginaZonas, setPaginaZonas] = useState(0)
  const [tamanoZonas, setTamanoZonas] = useState(20)
  const [busquedaZonas, setBusquedaZonas] = useState('')
  const [refrescarZonas, setRefrescarZonas] = useState(0)

  // Lotes
  const [lotes, setLotes] = useState<UbicacionPaged<LoteResponse> | null>(null)
  const [cargandoLotes, setCargandoLotes] = useState(true)
  const [errorLotes, setErrorLotes] = useState<string | null>(null)
  const [paginaLotes, setPaginaLotes] = useState(0)
  const [tamanoLotes, setTamanoLotes] = useState(20)
  const [busquedaLotes, setBusquedaLotes] = useState('')
  const [refrescarLotes, setRefrescarLotes] = useState(0)

  const [zonaModal, setZonaModal] = useState<ZonaModalActual>(null)
  const [loteModal, setLoteModal] = useState<LoteModalActual>(null)

  const [materialesZona, setMaterialesZona] = useState<MaterialResponse[]>([])
  const [cargandoMateriales, setCargandoMateriales] = useState(false)
  const [guardando, setGuardando] = useState(false)

  // Carga la lista de zonas. Reentra al cambiar pagina, tamano, busqueda o refresco.
  useEffect(() => {
    let vivo = true

    ubicacionService
      .listarZonas(busquedaZonas.trim(), paginaZonas, tamanoZonas)
      .then((res) => {
        if (vivo) setZonas(res)
      })
      .catch((e) => {
        if (vivo) setErrorZonas(extraerMensaje(e))
      })
      .finally(() => {
        if (vivo) setCargandoZonas(false)
      })

    return () => {
      vivo = false
    }
  }, [paginaZonas, tamanoZonas, busquedaZonas, refrescarZonas])

  // Carga la lista de lotes (al cambiar de pestana no se pierde)
  useEffect(() => {
    let vivo = true

    ubicacionService
      .listarLotes({ q: busquedaLotes.trim(), page: paginaLotes, size: tamanoLotes })
      .then((res) => {
        if (vivo) setLotes(res)
      })
      .catch((e) => {
        if (vivo) setErrorLotes(extraerMensaje(e))
      })
      .finally(() => {
        if (vivo) setCargandoLotes(false)
      })

    return () => {
      vivo = false
    }
  }, [paginaLotes, tamanoLotes, busquedaLotes, refrescarLotes])

  // Carga los materiales asignados antes de abrir el detalle o el gestor de la zona
  const abrirZonaConMateriales = (zona: ZonaAcopioResponse, tipo: 'detalle' | 'materiales') => {
    setZonaModal({ tipo, zona })
    setMaterialesZona([])
    setCargandoMateriales(true)
    ubicacionService
      .listarMaterialesZona(zona.idZona)
      .then(setMaterialesZona)
      .catch(() => setMaterialesZona([]))
      .finally(() => setCargandoMateriales(false))
  }

  const guardarZona = async (zona: ZonaAcopioResponse | null, request: ZonaAcopioRequest) => {
    setGuardando(true)
    try {
      if (zona) {
        await ubicacionService.actualizarZona(zona.idZona, request)
      } else {
        await ubicacionService.crearZona(request)
      }
      setZonaModal(null)
      setBusquedaZonas('')
      setPaginaZonas(0)
      setRefrescarZonas((r) => r + 1)
    } finally {
      setGuardando(false)
    }
  }

  const desactivarZona = async (zona: ZonaAcopioResponse) => {
    await ubicacionService.desactivarZona(zona.idZona)
    setZonaModal(null)
    setPaginaZonas((p) => (zonas && zonas.content.length === 1 && p > 0 ? p - 1 : p))
    setRefrescarZonas((r) => r + 1)
  }

  const admitirMaterial = async (idMaterial: number) => {
    if (!zonaModal || zonaModal.tipo !== 'materiales') return
    await ubicacionService.asignarMaterialZona(zonaModal.zona.idZona, idMaterial)
    setMaterialesZona(await ubicacionService.listarMaterialesZona(zonaModal.zona.idZona))
    setRefrescarZonas((r) => r + 1)
  }

  const sacarMaterial = async (idMaterial: number) => {
    if (!zonaModal || zonaModal.tipo !== 'materiales') return
    await ubicacionService.desasignarMaterialZona(zonaModal.zona.idZona, idMaterial)
    setMaterialesZona(await ubicacionService.listarMaterialesZona(zonaModal.zona.idZona))
    setRefrescarZonas((r) => r + 1)
  }

  const guardarLote = async (lote: LoteResponse | null, request: LoteRequest) => {
    setGuardando(true)
    try {
      if (lote) {
        await ubicacionService.actualizarLote(lote.idLote, request)
      } else {
        await ubicacionService.crearLote(request)
      }
      setLoteModal(null)
      setBusquedaLotes('')
      setPaginaLotes(0)
      setRefrescarLotes((r) => r + 1)
    } finally {
      setGuardando(false)
    }
  }

  const desactivarLote = async (lote: LoteResponse) => {
    await ubicacionService.desactivarLote(lote.idLote)
    setLoteModal(null)
    setPaginaLotes((p) => (lotes && lotes.content.length === 1 && p > 0 ? p - 1 : p))
    setRefrescarLotes((r) => r + 1)
  }

  const resumenZonas = (p: UbicacionPaged<ZonaAcopioResponse>) => {
    const inicio = p.totalElements === 0 ? 0 : paginaZonas * tamanoZonas + 1
    const fin = Math.min(paginaZonas * tamanoZonas + tamanoZonas, p.totalElements)
    return { inicio, fin }
  }
  const resumenLotes = (p: UbicacionPaged<LoteResponse>) => {
    const inicio = p.totalElements === 0 ? 0 : paginaLotes * tamanoLotes + 1
    const fin = Math.min(paginaLotes * tamanoLotes + tamanoLotes, p.totalElements)
    return { inicio, fin }
  }

  const paginasVisibles = (pagina: number, totalPaginas: number) =>
    Array.from(new Set([0, totalPaginas - 1, pagina, pagina - VENTANA_PAGINAS, pagina + VENTANA_PAGINAS]))
      .filter((n) => n >= 0 && n < totalPaginas)
      .sort((a, b) => a - b)

  return (
    <motion.div
      className="ubicaciones"
      variants={paginaVariants}
      initial="reposo"
      animate="visible"
      transition={{ duration: 0.28, ease: EASE }}
    >
      <motion.section variants={bloqueVariants} className="materiales__cabecera">
        <div>
          <h1 className="materiales__titulo">
            <span className="materiales__titulo-num" aria-hidden="true">
              03
            </span>
            Ubicaciones y lotes
          </h1>
          <p className="materiales__subtitulo">Control de zonas de acopio y lotes · RF-015</p>
        </div>
        {esAdmin && (
          <button
            type="button"
            className="btn btn--cta"
            onClick={() => (vista === 'zonas' ? setZonaModal({ tipo: 'formulario', zona: null }) : setLoteModal({ tipo: 'formulario', lote: null }))}
          >
            ＋ {vista === 'zonas' ? 'Nueva zona' : 'Nuevo lote'}
          </button>
        )}
      </motion.section>

      <motion.section variants={bloqueVariants} className="ubicaciones__pestanas" role="tablist" aria-label="Secciones">
        <button
          type="button"
          role="tab"
          aria-selected={vista === 'zonas'}
          className={`ubicaciones__pestana${vista === 'zonas' ? ' ubicaciones__pestana--activa' : ''}`}
          onClick={() => setVista('zonas')}
        >
          Zonas de acopio
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={vista === 'lotes'}
          className={`ubicaciones__pestana${vista === 'lotes' ? ' ubicaciones__pestana--activa' : ''}`}
          onClick={() => setVista('lotes')}
        >
          Lotes
        </button>
      </motion.section>

      <AnimatePresence mode="wait">
        {vista === 'zonas' ? (
          <motion.div key="zonas" variants={bloqueVariants} initial="reposo" animate="visible" exit={{ opacity: 0 }}>
            <motion.section className="materiales__filtros" aria-label="Filtros">
              <label className="materiales__buscar">
                <input
                  type="search"
                  placeholder="Buscar por nombre o tipo…"
                  value={busquedaZonas}
                  onChange={(e) => {
                    setCargandoZonas(true)
                    setBusquedaZonas(e.target.value)
                    setPaginaZonas(0)
                  }}
                  aria-label="Buscar zona por nombre o tipo"
                />
              </label>
            </motion.section>

            {errorZonas && (
              <p className="materiales__error" role="alert">
                {errorZonas}
              </p>
            )}

            <motion.section className="materiales__tabla-wrap" aria-live="polite">
              <table className="materiales__tabla">
                <thead>
                  <tr>
                    <th scope="col">Zona</th>
                    <th scope="col">Tipo permitido</th>
                    <th scope="col" className="numero">Capacidad</th>
                    <th scope="col" className="numero">Materiales</th>
                    {esAdmin && <th scope="col" className="acciones">Acciones</th>}
                  </tr>
                </thead>
                <tbody>
                  {cargandoZonas ? (
                    <tr>
                      <td colSpan={esAdmin ? 5 : 4} className="materiales__vacio">
                        Cargando zonas…
                      </td>
                    </tr>
                  ) : zonas && zonas.content.length === 0 ? (
                    <tr>
                      <td colSpan={esAdmin ? 5 : 4} className="materiales__vacio">
                        {busquedaZonas ? 'Sin resultados para la búsqueda.' : 'Sin zonas de acopio registradas todavía.'}
                      </td>
                    </tr>
                  ) : (
                    zonas?.content.map((z) => (
                      <motion.tr
                        key={z.idZona}
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
                            onClick={() => abrirZonaConMateriales(z, 'detalle')}
                          >
                            {z.nombreZona}
                          </button>
                        </td>
                        <td>{z.tipoMaterialPermitido ?? '—'}</td>
                        <td className="numero mono">{z.capacidadMaxima.toLocaleString('es-CO')}</td>
                        <td className="numero mono">{z.cantidadMateriales}</td>
                        {esAdmin && (
                          <td className="acciones">
                            <button
                              type="button"
                              className="accion accion--editar"
                              title="Editar"
                              aria-label={`Editar ${z.nombreZona}`}
                              onClick={() => setZonaModal({ tipo: 'formulario', zona: z })}
                            >
                              ✎
                            </button>
                            <button
                              type="button"
                              className="accion"
                              title="Materiales"
                              aria-label={`Gestionar materiales de ${z.nombreZona}`}
                              onClick={() => abrirZonaConMateriales(z, 'materiales')}
                            >
                              ◈
                            </button>
                            <button
                              type="button"
                              className="accion accion--eliminar"
                              title="Desactivar"
                              aria-label={`Desactivar ${z.nombreZona}`}
                              onClick={() => setZonaModal({ tipo: 'confirmar', zona: z })}
                            >
                              🗑
                            </button>
                          </td>
                        )}
                      </motion.tr>
                    ))
                  )}
                </tbody>
              </table>

              {zonas && zonas.totalElements > 0 && (
                <div className="materiales__pie">
                  <span className="materiales__total mono">
                    {resumenZonas(zonas).inicio}–{resumenZonas(zonas).fin} de {zonas.totalElements.toLocaleString('es-CO')}
                  </span>
                  <nav className="paginacion" aria-label="Paginación de zonas">
                    {paginasVisibles(paginaZonas, zonas.totalPages).map((n, i, arr) => {
                      const anterior = arr[i - 1]
                      const salto = anterior !== undefined && n - anterior > 1
                      return (
                        <span key={n} className="paginacion__grupo">
                          {salto && <span className="paginacion__salto">…</span>}
                          <button
                            type="button"
                            className={`paginacion__btn${n === paginaZonas ? ' paginacion__btn--actual' : ''}`}
                            aria-current={n === paginaZonas ? 'page' : undefined}
                            onClick={() => {
                              setCargandoZonas(true)
                              setPaginaZonas(n)
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
                      value={tamanoZonas}
                      onChange={(e) => {
                        setCargandoZonas(true)
                        setTamanoZonas(Number(e.target.value))
                        setPaginaZonas(0)
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
          </motion.div>
        ) : (
          <motion.div key="lotes" variants={bloqueVariants} initial="reposo" animate="visible" exit={{ opacity: 0 }}>
            <motion.section className="materiales__filtros" aria-label="Filtros">
              <label className="materiales__buscar">
                <input
                  type="search"
                  placeholder="Buscar por código de lote…"
                  value={busquedaLotes}
                  onChange={(e) => {
                    setCargandoLotes(true)
                    setBusquedaLotes(e.target.value)
                    setPaginaLotes(0)
                  }}
                  aria-label="Buscar lote por código"
                />
              </label>
            </motion.section>

            {errorLotes && (
              <p className="materiales__error" role="alert">
                {errorLotes}
              </p>
            )}

            <motion.section className="materiales__tabla-wrap" aria-live="polite">
              <table className="materiales__tabla">
                <thead>
                  <tr>
                    <th scope="col">Código</th>
                    <th scope="col">Material</th>
                    <th scope="col" className="numero">Cantidad</th>
                    <th scope="col">Ingreso</th>
                    {esAdmin && <th scope="col" className="acciones">Acciones</th>}
                  </tr>
                </thead>
                <tbody>
                  {cargandoLotes ? (
                    <tr>
                      <td colSpan={esAdmin ? 5 : 4} className="materiales__vacio">
                        Cargando lotes…
                      </td>
                    </tr>
                  ) : lotes && lotes.content.length === 0 ? (
                    <tr>
                      <td colSpan={esAdmin ? 5 : 4} className="materiales__vacio">
                        {busquedaLotes ? 'Sin resultados para la búsqueda.' : 'Sin lotes registrados todavía.'}
                      </td>
                    </tr>
                  ) : (
                    lotes?.content.map((l) => (
                      <motion.tr
                        key={l.idLote}
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
                          <span className="mono">{l.codigoLote}</span>
                        </td>
                        <td>{l.materialNombre}</td>
                        <td className="numero mono">{l.cantidad.toLocaleString('es-CO')}</td>
                        <td className="mono">{l.fechaIngreso ? l.fechaIngreso.replace('T', ' ') : '—'}</td>
                        {esAdmin && (
                          <td className="acciones">
                            <button
                              type="button"
                              className="accion accion--editar"
                              title="Editar"
                              aria-label={`Editar ${l.codigoLote}`}
                              onClick={() => setLoteModal({ tipo: 'formulario', lote: l })}
                            >
                              ✎
                            </button>
                            <button
                              type="button"
                              className="accion accion--eliminar"
                              title="Desactivar"
                              aria-label={`Desactivar ${l.codigoLote}`}
                              onClick={() => setLoteModal({ tipo: 'confirmar', lote: l })}
                            >
                              🗑
                            </button>
                          </td>
                        )}
                      </motion.tr>
                    ))
                  )}
                </tbody>
              </table>

              {lotes && lotes.totalElements > 0 && (
                <div className="materiales__pie">
                  <span className="materiales__total mono">
                    {resumenLotes(lotes).inicio}–{resumenLotes(lotes).fin} de {lotes.totalElements.toLocaleString('es-CO')}
                  </span>
                  <nav className="paginacion" aria-label="Paginación de lotes">
                    {paginasVisibles(paginaLotes, lotes.totalPages).map((n, i, arr) => {
                      const anterior = arr[i - 1]
                      const salto = anterior !== undefined && n - anterior > 1
                      return (
                        <span key={n} className="paginacion__grupo">
                          {salto && <span className="paginacion__salto">…</span>}
                          <button
                            type="button"
                            className={`paginacion__btn${n === paginaLotes ? ' paginacion__btn--actual' : ''}`}
                            aria-current={n === paginaLotes ? 'page' : undefined}
                            onClick={() => {
                              setCargandoLotes(true)
                              setPaginaLotes(n)
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
                      value={tamanoLotes}
                      onChange={(e) => {
                        setCargandoLotes(true)
                        setTamanoLotes(Number(e.target.value))
                        setPaginaLotes(0)
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
          </motion.div>
        )}
      </AnimatePresence>

      {/* ---- Modales de zonas ---- */}
      <AnimatePresence>
        {zonaModal?.tipo === 'detalle' && (
          <ZonaDetalleModal
            key="zona-detalle"
            zona={zonaModal.zona}
            materiales={materialesZona}
            cargandoMateriales={cargandoMateriales}
            esAdmin={esAdmin}
            onEditar={() => setZonaModal({ tipo: 'formulario', zona: zonaModal.zona })}
            onCerrar={() => setZonaModal(null)}
          />
        )}
      </AnimatePresence>

      <AnimatePresence>
        {zonaModal?.tipo === 'formulario' && (
          <ZonaFormModal
            key="zona-formulario"
            zona={zonaModal.zona}
            guardando={guardando}
            onGuardar={guardarZona}
            onCerrar={() => setZonaModal(null)}
          />
        )}
      </AnimatePresence>

      <AnimatePresence>
        {zonaModal?.tipo === 'confirmar' && (
          <ConfirmarDesactivarZonaModal
            key="zona-confirmar"
            zona={zonaModal.zona}
            onConfirmar={desactivarZona}
            onCerrar={() => setZonaModal(null)}
          />
        )}
      </AnimatePresence>

      <AnimatePresence>
        {zonaModal?.tipo === 'materiales' && (
          <GestionarMaterialesZonaModal
            key="zona-materiales"
            zona={zonaModal.zona}
            materiales={materialesZona}
            cargandoMateriales={cargandoMateriales}
            onAdmitir={admitirMaterial}
            onSacar={sacarMaterial}
            onCerrar={() => setZonaModal(null)}
          />
        )}
      </AnimatePresence>

      {/* ---- Modales de lotes ---- */}
      <AnimatePresence>
        {loteModal?.tipo === 'formulario' && (
          <LoteFormModal
            key="lote-formulario"
            lote={loteModal.lote}
            guardando={guardando}
            onGuardar={guardarLote}
            onCerrar={() => setLoteModal(null)}
          />
        )}
      </AnimatePresence>

      <AnimatePresence>
        {loteModal?.tipo === 'confirmar' && (
          <ConfirmarDesactivarLoteModal
            key="lote-confirmar"
            lote={loteModal.lote}
            onConfirmar={desactivarLote}
            onCerrar={() => setLoteModal(null)}
          />
        )}
      </AnimatePresence>
    </motion.div>
  )
}

interface ZonaDetalleProps {
  zona: ZonaAcopioResponse
  materiales: MaterialResponse[]
  cargandoMateriales: boolean
  esAdmin: boolean
  onEditar: () => void
  onCerrar: () => void
}

function ZonaDetalleModal({
  zona,
  materiales,
  cargandoMateriales,
  esAdmin,
  onEditar,
  onCerrar,
}: ZonaDetalleProps) {
  return (
    <Modal titulo={zona.nombreZona} onCerrar={onCerrar} ancho="lg">
      <p className="detalle__sub">
        {zona.tipoMaterialPermitido ? `Admite materiales de: ${zona.tipoMaterialPermitido}` : 'Sin restricción de tipo (RF-015)'}
      </p>

      <div className="detalle__numeros">
        <div>
          <span className="detalle__label">Capacidad máx.</span>
          <span className="detalle__valor mono">{zona.capacidadMaxima.toLocaleString('es-CO')}</span>
        </div>
        <div>
          <span className="detalle__label">Materiales</span>
          <span className="detalle__valor mono">{zona.cantidadMateriales}</span>
        </div>
      </div>

      <h3 className="detalle__proveedores-titulo">Materiales en esta zona</h3>
      {cargandoMateriales ? (
        <p className="detalle__texto">Cargando materiales…</p>
      ) : materiales.length === 0 ? (
        <p className="detalle__texto">No hay materiales asignados a esta zona.</p>
      ) : (
        <table className="materiales__tabla detalle__proveedores">
          <thead>
            <tr>
              <th scope="col">Material</th>
              <th scope="col">Categoría</th>
              <th scope="col" className="numero">Stock</th>
            </tr>
          </thead>
          <tbody>
            {materiales.map((m) => (
              <tr key={m.idMaterial}>
                <td>{m.nombre}</td>
                <td>{m.categoriaNombre}</td>
                <td className="numero mono">{m.stock.toLocaleString('es-CO')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <div className="modal__actions">
        {esAdmin && (
          <button type="button" className="btn" onClick={onEditar}>
            ✎ Editar
          </button>
        )}
        <button type="button" className="btn btn--ghost" onClick={onCerrar}>
          Cerrar
        </button>
      </div>
    </Modal>
  )
}

interface ZonaFormProps {
  zona: ZonaAcopioResponse | null
  guardando: boolean
  onGuardar: (zona: ZonaAcopioResponse | null, request: ZonaAcopioRequest) => Promise<void>
  onCerrar: () => void
}

function ZonaFormModal({ zona, guardando, onGuardar, onCerrar }: ZonaFormProps) {
  const [nombreZona, setNombreZona] = useState(zona?.nombreZona ?? '')
  const [capacidad, setCapacidad] = useState(zona ? String(zona.capacidadMaxima) : '')
  const [tipo, setTipo] = useState(zona?.tipoMaterialPermitido ?? '')
  const [error, setError] = useState<string | null>(null)
  const [categorias, setCategorias] = useState<CategoriaResponse[]>([])

  useEffect(() => {
    let vivo = true
    materialService
      .listarCategorias()
      .then((res) => {
        if (vivo) setCategorias(res)
      })
      .catch(() => {
        if (vivo) setCategorias([])
      })
    return () => {
      vivo = false
    }
  }, [])

  const guardar = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!nombreZona.trim()) return setError('El nombre de la zona es obligatorio.')
    if (capacidad === '') return setError('La capacidad máxima es obligatoria.')
    const capacidadNum = Number(capacidad)
    if (!Number.isFinite(capacidadNum) || capacidadNum < 0) return setError('Ingresa una capacidad válida (≥ 0).')
    try {
      await onGuardar(
        zona,
        {
          nombreZona: nombreZona.trim(),
          capacidadMaxima: capacidadNum,
          tipoMaterialPermitido: tipo || undefined,
        },
      )
    } catch (e) {
      setError(extraerMensaje(e))
    }
  }

  return (
    <Modal titulo={zona ? `Editar · ${zona.nombreZona}` : 'Nueva zona de acopio'} onCerrar={onCerrar}>
      <form className="formulario" onSubmit={guardar} noValidate>
        <label className="formulario__campo">
          <span className="formulario__label">Nombre</span>
          <input value={nombreZona} onChange={(e) => setNombreZona(e.target.value)} placeholder="Ej. Patio Este" />
        </label>

        <div className="formulario__fila">
          <label className="formulario__campo">
            <span className="formulario__label">Capacidad máxima</span>
            <input
              type="number"
              step="0.01"
              min="0"
              className="mono"
              value={capacidad}
              onChange={(e) => setCapacidad(e.target.value)}
              placeholder="0.00"
            />
          </label>
          <label className="formulario__campo">
            <span className="formulario__label">Tipo de material permitido</span>
            <select value={tipo} onChange={(e) => setTipo(e.target.value)}>
              <option value="">Sin restricción</option>
              {categorias.map((c) => (
                <option key={c.idCategoria} value={c.nombre}>
                  {c.nombre}
                </option>
              ))}
            </select>
          </label>
        </div>

        <p className="formulario__nota">
          Si defines un tipo, la zona solo admite materiales de esa categoría (RF-015): al asignar un material
          incompatible el backend responde 409.
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
            {guardando ? 'Guardando…' : 'Guardar'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

interface ZonaConfirmarProps {
  zona: ZonaAcopioResponse
  onConfirmar: (zona: ZonaAcopioResponse) => Promise<void>
  onCerrar: () => void
}

function ConfirmarDesactivarZonaModal({ zona, onConfirmar, onCerrar }: ZonaConfirmarProps) {
  const [eliminando, setEliminando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const confirmar = async () => {
    setEliminando(true)
    setError(null)
    try {
      await onConfirmar(zona)
    } catch (e) {
      setError(extraerMensaje(e))
    } finally {
      setEliminando(false)
    }
  }

  const conMateriales = zona.cantidadMateriales > 0

  return (
    <Modal titulo="Desactivar zona" onCerrar={onCerrar}>
      <p className="modal__texto">
        ¿Desactivar <strong>{zona.nombreZona}</strong>? Dejará de aparecer en la lista.
      </p>
      {conMateriales ? (
        <p className="detalle__alerta" role="alert">
          Tiene {zona.cantidadMateriales} material(es) asignado(s). Según RF-015 no se puede desactivar una
          zona con materiales asignados; retíralos primero.
        </p>
      ) : (
        error && (
          <p className="formulario__error" role="alert">
            {error}
          </p>
        )
      )}
      <div className="modal__actions">
        <button type="button" className="btn btn--ghost" onClick={onCerrar}>
          Cancelar
        </button>
        <button
          type="button"
          className="btn btn--danger"
          onClick={() => void confirmar()}
          disabled={eliminando || conMateriales}
        >
          {eliminando ? 'Desactivando…' : 'Desactivar'}
        </button>
      </div>
    </Modal>
  )
}

interface ZonaMaterialesProps {
  zona: ZonaAcopioResponse
  materiales: MaterialResponse[]
  cargandoMateriales: boolean
  onAdmitir: (idMaterial: number) => Promise<void>
  onSacar: (idMaterial: number) => Promise<void>
  onCerrar: () => void
}

function GestionarMaterialesZonaModal({
  zona,
  materiales,
  cargandoMateriales,
  onAdmitir,
  onSacar,
  onCerrar,
}: ZonaMaterialesProps) {
  // Todos los materiales activos (el backend limita size a 100, se pagina todo)
  const [inventario, setInventario] = useState<MaterialResponse[] | null>(null)
  const [idSeleccionado, setIdSeleccionado] = useState<number | ''>('')
  const [error, setError] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)

  useEffect(() => {
    let vivo = true
    materialService
      .listar(0, 100)
      .then(async (primera) => {
        const todos = [...primera.content]
        for (let p = 1; p < primera.totalPages && vivo; p++) {
          const res = await materialService.listar(p, 100)
          todos.push(...res.content)
        }
        if (vivo) setInventario(todos)
      })
      .catch(() => {
        if (vivo) setInventario([])
      })
    return () => {
      vivo = false
    }
  }, [])

  const asignados = new Set(materiales.map((m) => m.idMaterial))
  const disponibles = (inventario ?? []).filter((m) => !asignados.has(m.idMaterial))
  // Con tipo permitido, solo los de esa categoria son compatibles (RF-015)
  const tipo = zona.tipoMaterialPermitido
  const esCompatible = (m: MaterialResponse) => !tipo || m.categoriaNombre === tipo

  const admitir = async () => {
    if (idSeleccionado === '') return setError('Selecciona un material.')
    if (!esCompatible(disponibles.find((m) => m.idMaterial === idSeleccionado)!)) {
      return setError(`La zona solo admite materiales de tipo "${tipo}".`)
    }
    setOcupado(true)
    setError(null)
    try {
      await onAdmitir(idSeleccionado as number)
      setIdSeleccionado('')
    } catch (e) {
      setError(extraerMensaje(e))
    } finally {
      setOcupado(false)
    }
  }

  return (
    <Modal titulo={`Materiales · ${zona.nombreZona}`} onCerrar={onCerrar} ancho="lg">
      <p className="modal__texto">
        Asigna materiales a esta zona. {tipo ? `Solo admite la categoría "${tipo}" (RF-015).` : 'Sin restricción de tipo.'}{' '}
        La cantidad del material no se altera: la zona es un indicador de ubicación.
      </p>

      <div className="proveedores__admitir">
        <label className="formulario__campo proveedores__admitir-materiales">
          <span className="formulario__label">Material disponible</span>
          <select
            value={idSeleccionado}
            onChange={(e) => setIdSeleccionado(e.target.value === '' ? '' : Number(e.target.value))}
          >
            <option value="">Seleccionar…</option>
            {disponibles.map((m) => {
              const compatible = esCompatible(m)
              return (
                <option key={m.idMaterial} value={m.idMaterial} disabled={!compatible}>
                  {m.nombre}
                  {!compatible ? ' (incompatible)' : ''}
                </option>
              )
            })}
          </select>
        </label>
        <button type="button" className="btn" onClick={() => void admitir()} disabled={ocupado}>
          {ocupado ? 'Asignando…' : '＋ Asignar'}
        </button>
      </div>

      {error && (
        <p className="formulario__error" role="alert">
          {error}
        </p>
      )}

      <div className="proveedores__lista-titulo">
        <h3 className="detalle__proveedores-titulo">Materiales asignados</h3>
        <span className="proveedores__lista-num mono">{materiales.length}</span>
      </div>
      {cargandoMateriales ? (
        <p className="detalle__texto">Cargando materiales…</p>
      ) : materiales.length === 0 ? (
        <p className="detalle__texto">Todavía no tiene materiales asignados.</p>
      ) : (
        <table className="materiales__tabla detalle__proveedores">
          <thead>
            <tr>
              <th scope="col">Material</th>
              <th scope="col">Categoría</th>
              <th scope="col" className="numero">Stock</th>
              <th scope="col" className="acciones">Quitar</th>
            </tr>
          </thead>
          <tbody>
            {materiales.map((m) => (
              <tr key={m.idMaterial}>
                <td>{m.nombre}</td>
                <td>{m.categoriaNombre}</td>
                <td className="numero mono">{m.stock.toLocaleString('es-CO')}</td>
                <td className="acciones">
                  <button
                    type="button"
                    className="accion accion--eliminar"
                    title="Quitar de la zona"
                    aria-label={`Quitar ${m.nombre} de la zona`}
                    onClick={() => void onSacar(m.idMaterial)}
                  >
                    ✕
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <div className="modal__actions">
        <button type="button" className="btn btn--ghost" onClick={onCerrar}>
          Cerrar
        </button>
      </div>
    </Modal>
  )
}

interface LoteFormProps {
  lote: LoteResponse | null
  guardando: boolean
  onGuardar: (lote: LoteResponse | null, request: LoteRequest) => Promise<void>
  onCerrar: () => void
}

function LoteFormModal({ lote, guardando, onGuardar, onCerrar }: LoteFormProps) {
  const [idMaterial, setIdMaterial] = useState<number | ''>(lote?.idMaterial ?? '')
  const [codigoLote, setCodigoLote] = useState(lote?.codigoLote ?? '')
  const [cantidad, setCantidad] = useState(lote ? String(lote.cantidad) : '')
  const [error, setError] = useState<string | null>(null)
  const [inventario, setInventario] = useState<MaterialResponse[] | null>(null)

  useEffect(() => {
    let vivo = true
    materialService
      .listar(0, 100)
      .then(async (primera) => {
        const todos = [...primera.content]
        for (let p = 1; p < primera.totalPages && vivo; p++) {
          const res = await materialService.listar(p, 100)
          todos.push(...res.content)
        }
        if (vivo) setInventario(todos)
      })
      .catch(() => {
        if (vivo) setInventario([])
      })
    return () => {
      vivo = false
    }
  }, [])

  const guardar = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (idMaterial === '') return setError('Selecciona el material del lote.')
    if (!codigoLote.trim()) return setError('El código del lote es obligatorio.')
    if (cantidad === '') return setError('La cantidad del lote es obligatoria.')
    const cantidadNum = Number(cantidad)
    if (!Number.isFinite(cantidadNum) || cantidadNum < 0) return setError('Ingresa una cantidad válida (≥ 0).')
    try {
      await onGuardar(
        lote,
        {
          idMaterial: idMaterial as number,
          codigoLote: codigoLote.trim(),
          cantidad: cantidadNum,
        },
      )
    } catch (e) {
      setError(extraerMensaje(e))
    }
  }

  return (
    <Modal titulo={lote ? `Editar · ${lote.codigoLote}` : 'Nuevo lote'} onCerrar={onCerrar}>
      <form className="formulario" onSubmit={guardar} noValidate>
        <label className="formulario__campo">
          <span className="formulario__label">Material</span>
          <select value={idMaterial} onChange={(e) => setIdMaterial(e.target.value === '' ? '' : Number(e.target.value))} disabled={Boolean(lote)}>
            <option value="">Seleccionar…</option>
            {(inventario ?? []).map((m) => (
              <option key={m.idMaterial} value={m.idMaterial}>
                {m.nombre}
              </option>
            ))}
          </select>
          {lote && <span className="formulario__nota">El material es inmutable tras la creación (D-34).</span>}
        </label>

        <div className="formulario__fila">
          <label className="formulario__campo">
            <span className="formulario__label">Código del lote</span>
            <input className="mono" value={codigoLote} onChange={(e) => setCodigoLote(e.target.value)} placeholder="Ej. L-2026-101" />
          </label>
          <label className="formulario__campo">
            <span className="formulario__label">Cantidad</span>
            <input
              type="number"
              step="0.01"
              min="0"
              className="mono"
              value={cantidad}
              onChange={(e) => setCantidad(e.target.value)}
              placeholder="0.00"
            />
          </label>
        </div>

        <p className="formulario__nota">
          La cantidad es solo informativa: el stock real se calcula desde los movimientos de inventario (D-02).
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
          <button type="submit" className="btn btn--cta" disabled={guardando || !inventario}>
            {guardando ? 'Guardando…' : 'Guardar'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

interface LoteConfirmarProps {
  lote: LoteResponse
  onConfirmar: (lote: LoteResponse) => Promise<void>
  onCerrar: () => void
}

function ConfirmarDesactivarLoteModal({ lote, onConfirmar, onCerrar }: LoteConfirmarProps) {
  const [eliminando, setEliminando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const confirmar = async () => {
    setEliminando(true)
    setError(null)
    try {
      await onConfirmar(lote)
    } catch (e) {
      setError(extraerMensaje(e))
    } finally {
      setEliminando(false)
    }
  }

  return (
    <Modal titulo="Desactivar lote" onCerrar={onCerrar}>
      <p className="modal__texto">
        ¿Desactivar el lote <strong>{lote.codigoLote}</strong>? Dejará de aparecer en la lista.
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
        <button
          type="button"
          className="btn btn--danger"
          onClick={() => void confirmar()}
          disabled={eliminando}
        >
          {eliminando ? 'Desactivando…' : 'Desactivar'}
        </button>
      </div>
    </Modal>
  )
}