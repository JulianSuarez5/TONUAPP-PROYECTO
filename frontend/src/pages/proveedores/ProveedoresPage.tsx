// Proveedores - lista paginada con busqueda por nombre/NIT y detalle con los
// materiales asociados (N:M, RF-010). Mutaciones (crear/editar/desactivar) solo
// para Administrador (RF-009); un Cliente ve la lista y el detalle en lectura.
// Eliminar un proveedor con materiales asociados devuelve 409 del backend
// (RF-010: no se pueden eliminar proveedores con materiales asociados).
// Reutiliza el patron de Materiales (D-28/D-30): framer-motion para hover de
// fila, entrada de pagina con stagger y modales con el estandar emilkowalski.
import { useEffect, useState, type FormEvent } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { Paginacion } from '../../components/Paginacion'
import { Modal } from '../../components/Modal'
import { useAuth } from '../../hooks/useAuth'
import * as materialService from '../../services/materialService'
import * as proveedorService from '../../services/proveedorService'
import type { MaterialResponse } from '../../types/materiales'
import type { MaterialProveedorResponse, ProveedorPaged, ProveedorResponse } from '../../types/proveedores'
import {
  EASE,
  bloqueVariants,
  costadoVariants,
  filaVariants,
  paginaVariants,
} from '../../utils/animaciones'
import { extraerMensaje } from '../../utils/errores'
import './proveedores.css'

const VENTANA_PAGINAS = 2

type ModalActual =
  | { tipo: 'detalle'; proveedor: ProveedorResponse }
  | { tipo: 'formulario'; proveedor: ProveedorResponse | null }
  | { tipo: 'confirmar'; proveedor: ProveedorResponse }
  | { tipo: 'materiales'; proveedor: ProveedorResponse }
  | null

export function ProveedoresPage() {
  const { usuario } = useAuth()
  const esAdmin = usuario?.rol === 'Administrador'

  const [datos, setDatos] = useState<ProveedorPaged | null>(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [pagina, setPagina] = useState(0)
  const [tamano, setTamano] = useState(20)
  const [busqueda, setBusqueda] = useState('')
  const [refrescar, setRefrescar] = useState(0)

  const [modal, setModal] = useState<ModalActual>(null)
  const [materiales, setMateriales] = useState<MaterialProveedorResponse[]>([])
  const [cargandoMateriales, setCargandoMateriales] = useState(false)
  const [guardando, setGuardando] = useState(false)

  const iniciarCarga = () => {
    setCargando(true)
    setError(null)
  }

  // Carga la lista paginada con busqueda. Reentra al cambiar pagina, tamano o
  // busqueda; la paginacion se reinicia en 0 al teclear.
  useEffect(() => {
    let vivo = true

    proveedorService
      .listar(busqueda.trim(), pagina, tamano)
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
  }, [pagina, tamano, busqueda, refrescar])

  // Carga los materiales asociados antes de abrir el detalle o el gestor
  const abrirConMateriales = (
    proveedor: ProveedorResponse,
    tipo: 'detalle' | 'materiales',
  ) => {
    setModal({ tipo, proveedor })
    setMateriales([])
    setCargandoMateriales(true)
    proveedorService
      .listarMateriales(proveedor.idProveedor)
      .then(setMateriales)
      .catch(() => setMateriales([]))
      .finally(() => setCargandoMateriales(false))
  }

  const guardarProveedor = async (
    proveedor: ProveedorResponse | null,
    request: { nombre: string; nit: string; contacto?: string; telefono?: string; ubicacion?: string },
  ) => {
    setGuardando(true)
    try {
      if (proveedor) {
        await proveedorService.actualizar(proveedor.idProveedor, request)
      } else {
        await proveedorService.crear(request)
      }
      setModal(null)
      setBusqueda('')
      setPagina(0)
      setRefrescar((r) => r + 1)
    } finally {
      setGuardando(false)
    }
  }

  const desactivarProveedor = async (proveedor: ProveedorResponse) => {
    await proveedorService.desactivar(proveedor.idProveedor)
    setModal(null)
    setPagina((p) => (datos && datos.content.length === 1 && p > 0 ? p - 1 : p))
    setRefrescar((r) => r + 1)
  }

  const admitirMaterial = async (idMaterial: number, esPrincipal: boolean) => {
    if (!modal || modal.tipo !== 'materiales') return
    await proveedorService.asociarMaterial(modal.proveedor.idProveedor, { idMaterial, esPrincipal })
    setMateriales(await proveedorService.listarMateriales(modal.proveedor.idProveedor))
  }

  const cambiarPrincipal = async (idMaterial: number, principal: boolean) => {
    if (!modal || (modal.tipo !== 'materiales' && modal.tipo !== 'detalle')) return
    await proveedorService.actualizarPrincipal(modal.proveedor.idProveedor, idMaterial, principal)
    setMateriales(await proveedorService.listarMateriales(modal.proveedor.idProveedor))
  }

  const sacarMaterial = async (idMaterial: number) => {
    if (!modal || (modal.tipo !== 'materiales' && modal.tipo !== 'detalle')) return
    await proveedorService.desasociarMaterial(modal.proveedor.idProveedor, idMaterial)
    setMateriales(await proveedorService.listarMateriales(modal.proveedor.idProveedor))
    setRefrescar((r) => r + 1)
  }

  const porPagina = datos?.totalElements ?? 0
  const totalPaginas = datos?.totalPages ?? 0
  const inicio = porPagina === 0 ? 0 : pagina * tamano + 1
  const fin = Math.min(pagina * tamano + tamano, porPagina)

  const paginasVisibles = Array.from(
    new Set([0, totalPaginas - 1, pagina, pagina - VENTANA_PAGINAS, pagina + VENTANA_PAGINAS]),
  )
    .filter((n) => n >= 0 && n < totalPaginas)
    .sort((a, b) => a - b)

  const modalProveedor = modal?.tipo === 'formulario' ? modal.proveedor : null

  return (
    <motion.div
      className="proveedores"
      variants={paginaVariants}
      initial="reposo"
      animate="visible"
      transition={{ duration: 0.28, ease: EASE }}
    >
      <motion.section variants={bloqueVariants} className="materiales__cabecera">
        <div>
          <h1 className="materiales__titulo">
            <span className="materiales__titulo-num" aria-hidden="true">
              02
            </span>
            Proveedores
          </h1>
          <p className="materiales__subtitulo">
            Registro y gestión de proveedores · RF-010
          </p>
        </div>
        {esAdmin && (
          <button
            type="button"
            className="btn btn--cta"
            onClick={() => setModal({ tipo: 'formulario', proveedor: null })}
          >
            ＋ Nuevo proveedor
          </button>
        )}
      </motion.section>

      <motion.section variants={bloqueVariants} className="materiales__filtros" aria-label="Filtros">
        <label className="materiales__buscar">
          <input
            type="search"
            placeholder="Buscar por nombre o NIT…"
            value={busqueda}
            onChange={(e) => {
              iniciarCarga()
              setBusqueda(e.target.value)
              setPagina(0)
            }}
            aria-label="Buscar por nombre o NIT"
          />
        </label>
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
              <th scope="col">Proveedor</th>
              <th scope="col">NIT</th>
              <th scope="col">Contacto</th>
              <th scope="col">Ubicación</th>
              <th scope="col" className="numero">Materiales</th>
              {esAdmin && <th scope="col" className="acciones">Acciones</th>}
            </tr>
          </thead>
          <tbody>
            {cargando ? (
              <tr>
                <td colSpan={esAdmin ? 6 : 5} className="materiales__vacio">
                  Cargando proveedores…
                </td>
              </tr>
            ) : datos && datos.content.length === 0 ? (
              <tr>
                <td colSpan={esAdmin ? 6 : 5} className="materiales__vacio">
                  {busqueda ? 'Sin resultados para la búsqueda.' : 'Sin proveedores registrados todavía.'}
                </td>
              </tr>
            ) : (
              datos?.content.map((p) => (
                <motion.tr
                  key={p.idProveedor}
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
                      onClick={() => abrirConMateriales(p, 'detalle')}
                    >
                      {p.nombre}
                    </button>
                  </td>
                  <td className="mono">{p.nit}</td>
                  <td>{p.contacto ?? '—'}</td>
                  <td>{p.ubicacion ?? '—'}</td>
                  <td className="numero mono">{p.cantidadMateriales}</td>
                  {esAdmin && (
                    <td className="acciones">
                      <button
                        type="button"
                        className="accion accion--editar"
                        title="Editar"
                        aria-label={`Editar ${p.nombre}`}
                        onClick={() => setModal({ tipo: 'formulario', proveedor: p })}
                      >
                        ✎
                      </button>
                      <button
                        type="button"
                        className="accion"
                        title="Materiales"
                        aria-label={`Gestionar materiales de ${p.nombre}`}
                        onClick={() => abrirConMateriales(p, 'materiales')}
                      >
                        ◈
                      </button>
                      <button
                        type="button"
                        className="accion accion--eliminar"
                        title="Desactivar"
                        aria-label={`Desactivar ${p.nombre}`}
                        onClick={() => setModal({ tipo: 'confirmar', proveedor: p })}
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

        {datos && porPagina > 0 && (
          <Paginacion
            inicio={inicio}
            fin={fin}
            total={porPagina}
            pagina={pagina}
            totalPaginas={totalPaginas}
            paginasVisibles={paginasVisibles}
            tamano={tamano}
            onCambiarPagina={(n) => {
              iniciarCarga()
              setPagina(n)
            }}
            onCambiarTamano={(s) => {
              iniciarCarga()
              setTamano(s)
              setPagina(0)
            }}
          />
        )}
      </motion.section>

      <AnimatePresence>
        {modal?.tipo === 'detalle' && (
          <ProveedorDetalleModal
            key="detalle"
            proveedor={modal.proveedor}
            materiales={materiales}
            cargandoMateriales={cargandoMateriales}
            esAdmin={esAdmin}
            onEditar={() => setModal({ tipo: 'formulario', proveedor: modal.proveedor })}
            onCerrar={() => setModal(null)}
          />
        )}
      </AnimatePresence>

      <AnimatePresence>
        {modal?.tipo === 'formulario' && (
          <ProveedorFormModal
            key="formulario"
            proveedor={modalProveedor}
            guardando={guardando}
            onGuardar={guardarProveedor}
            onCerrar={() => setModal(null)}
          />
        )}
      </AnimatePresence>

      <AnimatePresence>
        {modal?.tipo === 'confirmar' && (
          <ConfirmarDesactivarModal
            key="confirmar"
            proveedor={modal.proveedor}
            onConfirmar={desactivarProveedor}
            onCerrar={() => setModal(null)}
          />
        )}
      </AnimatePresence>

      <AnimatePresence>
        {modal?.tipo === 'materiales' && (
          <GestionarMaterialesModal
            key="materiales"
            proveedor={modal.proveedor}
            materiales={materiales}
            cargandoMateriales={cargandoMateriales}
            onAdmitir={admitirMaterial}
            onCambiarPrincipal={cambiarPrincipal}
            onSacar={sacarMaterial}
            onCerrar={() => setModal(null)}
          />
        )}
      </AnimatePresence>
    </motion.div>
  )
}

interface DetalleProps {
  proveedor: ProveedorResponse
  materiales: MaterialProveedorResponse[]
  cargandoMateriales: boolean
  esAdmin: boolean
  onEditar: () => void
  onCerrar: () => void
}

function ProveedorDetalleModal({
  proveedor,
  materiales,
  cargandoMateriales,
  esAdmin,
  onEditar,
  onCerrar,
}: DetalleProps) {
  return (
    <Modal titulo={proveedor.nombre} onCerrar={onCerrar} ancho="lg">
      <p className="detalle__sub">NIT {proveedor.nit}</p>

      <div className="detalle__numeros">
        <div>
          <span className="detalle__label">Contacto</span>
          <span className="detalle__valor">{proveedor.contacto ?? '—'}</span>
        </div>
        <div>
          <span className="detalle__label">Teléfono</span>
          <span className="detalle__valor mono">{proveedor.telefono ?? '—'}</span>
        </div>
        <div>
          <span className="detalle__label">Materiales</span>
          <span className="detalle__valor mono">{proveedor.cantidadMateriales}</span>
        </div>
      </div>

      <p className="detalle__meta mono">
        <span>Ubicación: {proveedor.ubicacion ?? '—'}</span>
        <span>Registro: {proveedor.fechaRegistro ? proveedor.fechaRegistro.replace('T', ' ') : '—'}</span>
      </p>

      <h3 className="detalle__proveedores-titulo">Materiales que abastece</h3>
      {cargandoMateriales ? (
        <p className="detalle__texto">Cargando materiales…</p>
      ) : materiales.length === 0 ? (
        <p className="detalle__texto">No tiene materiales asociados.</p>
      ) : (
        <table className="materiales__tabla detalle__proveedores">
          <thead>
            <tr>
              <th scope="col">Material</th>
              <th scope="col">Principal</th>
              <th scope="col">Asociado desde</th>
            </tr>
          </thead>
          <tbody>
            {materiales.map((m) => (
              <tr key={m.idMaterial}>
                <td>{m.nombreMaterial}</td>
                <td>{m.esPrincipal ? '●' : '○'}</td>
                <td className="mono">{m.fechaAsociacion ? m.fechaAsociacion.replace('T', ' ') : '—'}</td>
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

interface FormProps {
  proveedor: ProveedorResponse | null
  guardando: boolean
  onGuardar: (
    proveedor: ProveedorResponse | null,
    request: { nombre: string; nit: string; contacto?: string; telefono?: string; ubicacion?: string },
  ) => Promise<void>
  onCerrar: () => void
}

function ProveedorFormModal({ proveedor, guardando, onGuardar, onCerrar }: FormProps) {
  const [nombre, setNombre] = useState(proveedor?.nombre ?? '')
  const [nit, setNit] = useState(proveedor?.nit ?? '')
  const [contacto, setContacto] = useState(proveedor?.contacto ?? '')
  const [telefono, setTelefono] = useState(proveedor?.telefono ?? '')
  const [ubicacion, setUbicacion] = useState(proveedor?.ubicacion ?? '')
  const [error, setError] = useState<string | null>(null)

  const guardar = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!nombre.trim()) return setError('El nombre es obligatorio.')
    if (!nit.trim()) return setError('El NIT es obligatorio.')
    try {
      await onGuardar(
        proveedor,
        {
          nombre: nombre.trim(),
          nit: nit.trim(),
          contacto: contacto.trim() || undefined,
          telefono: telefono.trim() || undefined,
          ubicacion: ubicacion.trim() || undefined,
        },
      )
    } catch (e) {
      setError(extraerMensaje(e))
    }
  }

  return (
    <Modal titulo={proveedor ? `Editar · ${proveedor.nombre}` : 'Nuevo proveedor'} onCerrar={onCerrar}>
      <form className="formulario" onSubmit={guardar} noValidate>
        <label className="formulario__campo">
          <span className="formulario__label">Nombre</span>
          <input value={nombre} onChange={(e) => setNombre(e.target.value)} placeholder="Ej. Materiales El Tonusco" />
        </label>

        <div className="formulario__fila">
          <label className="formulario__campo">
            <span className="formulario__label">NIT</span>
            <input className="mono" value={nit} onChange={(e) => setNit(e.target.value)} placeholder="Ej. 900123456" />
          </label>
          <label className="formulario__campo">
            <span className="formulario__label">Teléfono</span>
            <input className="mono" value={telefono} onChange={(e) => setTelefono(e.target.value)} placeholder="Opcional" />
          </label>
        </div>

        <label className="formulario__campo">
          <span className="formulario__label">Contacto</span>
          <input value={contacto} onChange={(e) => setContacto(e.target.value)} placeholder="Opcional" />
        </label>

        <label className="formulario__campo">
          <span className="formulario__label">Ubicación</span>
          <input value={ubicacion} onChange={(e) => setUbicacion(e.target.value)} placeholder="Opcional" />
        </label>

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

interface ConfirmarProps {
  proveedor: ProveedorResponse
  onConfirmar: (proveedor: ProveedorResponse) => Promise<void>
  onCerrar: () => void
}

function ConfirmarDesactivarModal({ proveedor, onConfirmar, onCerrar }: ConfirmarProps) {
  const [eliminando, setEliminando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const confirmar = async () => {
    setEliminando(true)
    setError(null)
    try {
      await onConfirmar(proveedor)
    } catch (e) {
      setError(extraerMensaje(e))
    } finally {
      setEliminando(false)
    }
  }

  const conMateriales = proveedor.cantidadMateriales > 0

  return (
    <Modal titulo="Desactivar proveedor" onCerrar={onCerrar}>
      <p className="modal__texto">
        ¿Desactivar <strong>{proveedor.nombre}</strong>? Dejará de aparecer en la lista.
      </p>
      {conMateriales ? (
        <p className="detalle__alerta" role="alert">
          Tiene {proveedor.cantidadMateriales} material(es) asociado(s). Según RF-010 no se puede desactivar
          mientras tenga asociaciones; retíralas primero.
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

interface MaterialesProps {
  proveedor: ProveedorResponse
  materiales: MaterialProveedorResponse[]
  cargandoMateriales: boolean
  onAdmitir: (idMaterial: number, esPrincipal: boolean) => Promise<void>
  onCambiarPrincipal: (idMaterial: number, principal: boolean) => Promise<void>
  onSacar: (idMaterial: number) => Promise<void>
  onCerrar: () => void
}

function GestionarMaterialesModal({
  proveedor,
  materiales,
  cargandoMateriales,
  onAdmitir,
  onCambiarPrincipal,
  onSacar,
  onCerrar,
}: MaterialesProps) {
  // Materiales disponibles para asociar: solo los que aún no están asociados
  const [inventario, setInventario] = useState<MaterialResponse[] | null>(null)
  const [idSeleccionado, setIdSeleccionado] = useState<number | ''>('')
  const [principal, setPrincipal] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)

  // Catalogo completo de materiales activos: el backend limita el tamano de página
// a 100 (MaterialService.MAX_PAGE_SIZE), así que se pagina todo para que el select
// muestre todos los materiales disponibles.
  // pagina todo para que el select muestre todos los materiales disponibles.
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

  const asociados = new Set(materiales.map((m) => m.idMaterial))
  const disponibles = (inventario ?? []).filter((m) => !asociados.has(m.idMaterial))

  const admitir = async () => {
    if (idSeleccionado === '') return setError('Selecciona un material.')
    setOcupado(true)
    setError(null)
    try {
      await onAdmitir(idSeleccionado as number, principal)
      setIdSeleccionado('')
      setPrincipal(false)
    } catch (e) {
      setError(extraerMensaje(e))
    } finally {
      setOcupado(false)
    }
  }

  return (
    <Modal titulo={`Materiales · ${proveedor.nombre}`} onCerrar={onCerrar} ancho="lg">
      <p className="modal__texto">
        Asocia los materiales que abastece este proveedor. Uno de ellos puede marcarse como principal.
      </p>

      <div className="proveedores__admitir">
        <label className="formulario__campo proveedores__admitir-materiales">
          <span className="formulario__label">Material disponible</span>
          <select value={idSeleccionado} onChange={(e) => setIdSeleccionado(e.target.value === '' ? '' : Number(e.target.value))}>
            <option value="">Seleccionar…</option>
            {disponibles.map((m) => (
              <option key={m.idMaterial} value={m.idMaterial}>
                {m.nombre}
              </option>
            ))}
          </select>
        </label>
        <label className="formulario__campo proveedores__admitir-principal">
          <span className="formulario__label">Principal</span>
          <select value={principal ? '1' : '0'} onChange={(e) => setPrincipal(e.target.value === '1')}>
            <option value="0">No</option>
            <option value="1">Sí</option>
          </select>
        </label>
        <button type="button" className="btn" onClick={() => void admitir()} disabled={ocupado}>
          {ocupado ? 'Asociando…' : '＋ Asociar'}
        </button>
      </div>

      {error && (
        <p className="formulario__error" role="alert">
          {error}
        </p>
      )}

      <div className="proveedores__lista-titulo">
        <h3 className="detalle__proveedores-titulo">Materiales asociados</h3>
        <span className="proveedores__lista-num mono">{materiales.length}</span>
      </div>
      {cargandoMateriales ? (
        <p className="detalle__texto">Cargando materiales…</p>
      ) : materiales.length === 0 ? (
        <p className="detalle__texto">Todavía no tiene materiales asociados.</p>
      ) : (
        <table className="materiales__tabla detalle__proveedores">
          <thead>
            <tr>
              <th scope="col">Material</th>
              <th scope="col">Principal</th>
              <th scope="col">Asociado desde</th>
              <th scope="col" className="acciones">Quitar</th>
            </tr>
          </thead>
          <tbody>
            {materiales.map((m) => (
              <tr key={m.idMaterial}>
                <td>{m.nombreMaterial}</td>
                <td>
                  <button
                    type="button"
                    className={`proveedores__principal${m.esPrincipal ? ' proveedores__principal--si' : ''}`}
                    title={m.esPrincipal ? 'Principal (clic para quitar)' : 'Marcar como principal'}
                    onClick={() => void onCambiarPrincipal(m.idMaterial, !m.esPrincipal)}
                  >
                    {m.esPrincipal ? '● Principal' : '○'}
                  </button>
                </td>
                <td className="mono">{m.fechaAsociacion ? m.fechaAsociacion.replace('T', ' ') : '—'}</td>
                <td className="acciones">
                  <button
                    type="button"
                    className="accion accion--eliminar"
                    title="Quitar asociación"
                    aria-label={`Quitar ${m.nombreMaterial}`}
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