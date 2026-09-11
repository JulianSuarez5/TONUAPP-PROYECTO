// Materiales - lista paginada con busqueda por nombre y filtro por categoria
// (RF-001/RF-003). Mutaciones (crear/editar/desactivar) solo para Administrador
// (RF-002/RF-004); un Cliente ve la lista y el detalle en modo lectura.
// Motion (D-28): framer-motion para hover de fila y entrada/salida de modales;
// el costado naranja del hover es un <span> dentro del primer <td> (un ::before
// sobre <tr> genera una celda fantasma en Chromium - bug corregido).
import { useEffect, useState, type FormEvent } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { Paginacion } from '../../components/Paginacion'
import { paginar } from '../../utils/paginacion'
import { Modal } from '../../components/Modal'
import { usePaginacion } from '../../hooks/usePaginacion'
import { useAuth } from '../../hooks/useAuth'
import * as materialService from '../../services/materialService'
import type {
  MaterialProveedorResponse,
  MaterialResponse,
  PagedResponse,
  CategoriaResponse,
} from '../../types/materiales'
import {
  EASE,
  bloqueVariants,
  costadoVariants,
  filaVariants,
  paginaVariants,
} from '../../utils/animaciones'
import { extraerMensaje } from '../../utils/errores'
import { formatearCantidad } from '../../utils/formatos'
import './materiales.css'

type ModalActual =
  | { tipo: 'detalle'; material: MaterialResponse }
  | { tipo: 'formulario'; material: MaterialResponse | null }
  | { tipo: 'confirmar'; material: MaterialResponse }
  | null

export function EstadoStock({ material }: { material: MaterialResponse }) {
  const bajo = material.stock < material.stockMinimo
  return (
    <span className={`stock-badge${bajo ? ' stock-badge--bajo' : ''}`}>{bajo ? 'BAJO' : 'OK'}</span>
  )
}

export function MaterialesPage() {
  const { usuario } = useAuth()
  const esAdmin = usuario?.rol === 'Administrador'

  const [categorias, setCategorias] = useState<CategoriaResponse[]>([])
  const [datos, setDatos] = useState<PagedResponse<MaterialResponse> | null>(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Marca la lista en recarga y limpia el error previo. Se llama desde los
  // handlers que cambian filtros/pagina (no en el efecto, para evitar un
  // setState sincronico dentro del efecto de carga)
  const iniciarCarga = () => {
    setCargando(true)
    setError(null)
  }

  const { pagina, tamano, setPagina, cambiarPagina, cambiarTamano } = usePaginacion(() =>
    iniciarCarga(),
  )
  const [busqueda, setBusqueda] = useState('')
  const [categoria, setCategoria] = useState<number | ''>('')
  const [refrescar, setRefrescar] = useState(0)

  const [modal, setModal] = useState<ModalActual>(null)
  const [proveedores, setProveedores] = useState<MaterialProveedorResponse[]>([])
  const [cargandoProveedores, setCargandoProveedores] = useState(false)
  const [guardando, setGuardando] = useState(false)

  const aplicarParametros = (cambio: { page?: number; categoria?: number | '' }) => {
    const nuevaPagina = cambio.page ?? 0
    setPagina(nuevaPagina)
    if (cambio.categoria !== undefined) {
      setCategoria(cambio.categoria)
    }
  }

  // Categorias para el filtro (catálogo solo lectura)
  useEffect(() => {
    let vivo = true
    materialService
      .listarCategorias()
      .then((cats) => {
        if (vivo) setCategorias(cats)
      })
      .catch(() => {
        /* el filtro de categoria simplemente quedara vacio; la lista sigue visible */
      })
    return () => {
      vivo = false
    }
  }, [])

  // Carga la lista/Busqueda paginada. Reentra al cambiar pagina, tamano, busqueda
  // o categoria; la paginacion se reinicia en 0 al tocar filtros.
  useEffect(() => {
    let vivo = true

    const params = {
      page: pagina,
      size: tamano,
      nombre: busqueda.trim() || undefined,
      categoria: categoria === '' ? undefined : categoria,
    }
    const tieneFiltro = !!params.nombre || params.categoria !== undefined

    const peticion = tieneFiltro
      ? materialService.buscar(params)
      : materialService.listar(pagina, tamano)

    peticion
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
  }, [pagina, tamano, busqueda, categoria, refrescar])

  // Carga los proveedores (N:M) antes de abrir el detalle
  const abrirDetalle = (material: MaterialResponse) => {
    setModal({ tipo: 'detalle', material })
    setProveedores([])
    setCargandoProveedores(true)
    materialService
      .listarProveedores(material.idMaterial)
      .then(setProveedores)
      .catch(() => setProveedores([]))
      .finally(() => setCargandoProveedores(false))
  }

  const guardarMaterial = async (
    material: MaterialResponse | null,
    request: { nombre: string; idCategoria: number; idUnidad: number; stock: number; stockMinimo: number },
  ) => {
    setGuardando(true)
    try {
      if (material) {
        await materialService.actualizar(material.idMaterial, request)
      } else {
        await materialService.crear(request)
      }
      setModal(null)
      setBusqueda('')
      setCategoria('')
      setPagina(0)
      setRefrescar((r) => r + 1)
    } finally {
      setGuardando(false)
    }
  }

  const desactivarMaterial = async (material: MaterialResponse) => {
    await materialService.desactivar(material.idMaterial)
    setModal(null)
    // Quita el material de la pagina actual; si solo quedaba el actual, retrocede
    setPagina((p) => (datos && datos.content.length === 1 && p > 0 ? p - 1 : p))
    setRefrescar((r) => r + 1)
  }

  const porPagina = datos?.totalElements ?? 0
  const paginacion = paginar(pagina, tamano, porPagina, datos?.totalPages ?? 0)

  const modalMaterial = modal?.tipo === 'formulario' ? modal.material : null

  return (
    <motion.div
      className="materiales"
      variants={paginaVariants}
      initial="reposo"
      animate="visible"
      transition={{ duration: 0.28, ease: EASE }}
    >
      <motion.section variants={bloqueVariants} className="materiales__cabecera">
        <div>
          <h1 className="materiales__titulo">
            <span className="materiales__titulo-num" aria-hidden="true">
              01
            </span>
            Materiales
          </h1>
          <p className="materiales__subtitulo">
            Inventario de materiales de construcción · RF-001 a RF-004
          </p>
        </div>
        {esAdmin && (
          <button
            type="button"
            className="btn btn--cta"
            onClick={() => setModal({ tipo: 'formulario', material: null })}
          >
            ＋ Nuevo material
          </button>
        )}
      </motion.section>

      <motion.section variants={bloqueVariants} className="materiales__filtros" aria-label="Filtros">
        <label className="materiales__buscar">
          <input
            type="search"
            placeholder="Buscar por nombre…"
            value={busqueda}
            onChange={(e) => {
              iniciarCarga()
              setBusqueda(e.target.value)
              setPagina(0)
            }}
            aria-label="Buscar por nombre"
          />
        </label>
        <label className="materiales__categoria">
          <span className="materiales__label">Categoría</span>
          <select
            value={categoria}
            onChange={(e) => {
              iniciarCarga()
              aplicarParametros({ categoria: e.target.value === '' ? '' : Number(e.target.value) })
            }}
          >
            <option value="">Todas</option>
            {categorias.map((c) => (
              <option key={c.idCategoria} value={c.idCategoria}>
                {c.nombre}
              </option>
            ))}
          </select>
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
              <th scope="col">Material</th>
              <th scope="col">Categoría</th>
              <th scope="col">Unidad</th>
              <th scope="col" className="numero">
                Stock
              </th>
              <th scope="col" className="numero">
                Mín.
              </th>
              <th scope="col">Estado</th>
              {esAdmin && <th scope="col" className="acciones">Acciones</th>}
            </tr>
          </thead>
          <tbody>
            {cargando ? (
              <tr>
                <td colSpan={esAdmin ? 7 : 6} className="materiales__vacio">
                  Cargando materiales…
                </td>
              </tr>
            ) : datos && datos.content.length === 0 ? (
              <tr>
                <td colSpan={esAdmin ? 7 : 6} className="materiales__vacio">
                  {busqueda || categoria !== '' ? (
                    'Sin resultados para los filtros aplicados.'
                  ) : (
                    'Sin materiales registrados todavía.'
                  )}
                </td>
              </tr>
            ) : (
              datos?.content.map((m) => (
                <motion.tr
                  key={m.idMaterial}
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
                      onClick={() => abrirDetalle(m)}
                    >
                      {m.nombre}
                    </button>
                  </td>
                  <td>{m.categoriaNombre}</td>
                  <td className="mono">{m.unidadAbreviatura}</td>
                  <td className="numero mono">{formatearCantidad(m.stock)}</td>
                  <td className="numero mono">{formatearCantidad(m.stockMinimo)}</td>
                  <td>
                    <EstadoStock material={m} />
                  </td>
                  {esAdmin && (
                    <td className="acciones">
                      <button
                        type="button"
                        className="accion accion--editar"
                        title="Editar"
                        aria-label={`Editar ${m.nombre}`}
                        onClick={() => setModal({ tipo: 'formulario', material: m })}
                      >
                        ✎
                      </button>
                      <button
                        type="button"
                        className="accion accion--eliminar"
                        title="Desactivar"
                        aria-label={`Desactivar ${m.nombre}`}
                        onClick={() => setModal({ tipo: 'confirmar', material: m })}
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
          <Paginacion {...paginacion} total={porPagina} pagina={pagina} tamano={tamano}
            onCambiarPagina={cambiarPagina} onCambiarTamano={cambiarTamano}
            ariaLabel="Paginación de materiales" />
        )}
      </motion.section>

      <AnimatePresence>
        {modal?.tipo === 'detalle' && (
          <MaterialDetalleModal
            key="detalle"
            material={modal.material}
            proveedores={proveedores}
            cargandoProveedores={cargandoProveedores}
            esAdmin={esAdmin}
            onEditar={() => setModal({ tipo: 'formulario', material: modal.material })}
            onCerrar={() => setModal(null)}
          />
        )}
      </AnimatePresence>

      <AnimatePresence>
        {modal?.tipo === 'formulario' && (
          <MaterialFormModal
            key="formulario"
            material={modalMaterial}
            guardando={guardando}
            onGuardar={guardarMaterial}
            onCerrar={() => setModal(null)}
          />
        )}
      </AnimatePresence>

      <AnimatePresence>
        {modal?.tipo === 'confirmar' && (
          <ConfirmarEliminarModal
            key="confirmar"
            material={modal.material}
            onConfirmar={desactivarMaterial}
            onCerrar={() => setModal(null)}
          />
        )}
      </AnimatePresence>
    </motion.div>
  )
}

interface DetalleProps {
  material: MaterialResponse
  proveedores: MaterialProveedorResponse[]
  cargandoProveedores: boolean
  esAdmin: boolean
  onEditar: () => void
  onCerrar: () => void
}

function MaterialDetalleModal({
  material,
  proveedores,
  cargandoProveedores,
  esAdmin,
  onEditar,
  onCerrar,
}: DetalleProps) {
  const bajo = material.stock < material.stockMinimo

  return (
    <Modal titulo={material.nombre} onCerrar={onCerrar} ancho="lg">
      <p className="detalle__sub">{material.categoriaNombre} · {material.unidadNombre}</p>

      <div className="detalle__numeros">
        <div>
          <span className="detalle__label">Stock</span>
          <span className="detalle__valor mono">
            {formatearCantidad(material.stock)}{' '}
            <span className="detalle__unidad">{material.unidadAbreviatura}</span>
          </span>
        </div>
        <div>
          <span className="detalle__label">Stock mínimo</span>
          <span className="detalle__valor mono">
            {formatearCantidad(material.stockMinimo)} <span className="detalle__unidad">{material.unidadAbreviatura}</span>
          </span>
        </div>
        <div>
          <span className="detalle__label">Estado</span>
          <EstadoStock material={material} />
        </div>
      </div>

      {bajo && (
        <p className="detalle__alerta" role="alert">
          Stock por debajo del mínimo ({formatearCantidad(material.stockMinimo)} {material.unidadAbreviatura}). Recárgalo
          con una entrada de inventario.
        </p>
      )}

      <div className="detalle__meta mono">
        <span>Código {String(material.idMaterial).padStart(4, '0')}</span>
        <span>Registro: {material.fechaRegistro ? material.fechaRegistro.replace('T', ' ') : '—'}</span>
      </div>

      <h3 className="detalle__proveedores-titulo">Proveedores asociados</h3>
      {cargandoProveedores ? (
        <p className="detalle__texto">Cargando proveedores…</p>
      ) : proveedores.length === 0 ? (
        <p className="detalle__texto">No tiene proveedores asociados.</p>
      ) : (
        <table className="materiales__tabla detalle__proveedores">
          <thead>
            <tr>
              <th scope="col">Proveedor</th>
              <th scope="col">Principal</th>
              <th scope="col">Asociado desde</th>
            </tr>
          </thead>
          <tbody>
            {proveedores.map((p) => (
              <tr key={`${p.idProveedor}-${p.idMaterial}`}>
                <td>{p.nombreProveedor}</td>
                <td>{p.esPrincipal ? '●' : '○'}</td>
                <td className="mono">{p.fechaAsociacion ? p.fechaAsociacion.replace('T', ' ') : '—'}</td>
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
  material: MaterialResponse | null
  guardando: boolean
  onGuardar: (
    material: MaterialResponse | null,
    request: { nombre: string; idCategoria: number; idUnidad: number; stock: number; stockMinimo: number },
  ) => Promise<void>
  onCerrar: () => void
}

function MaterialFormModal({ material, guardando, onGuardar, onCerrar }: FormProps) {
  const [nombre, setNombre] = useState(material?.nombre ?? '')
  const [idCategoria, setIdCategoria] = useState<number | ''>(material?.idCategoria ?? '')
  const [idUnidad, setIdUnidad] = useState<number | ''>(material?.idUnidad ?? '')
  const [stock, setStock] = useState(material?.stock ?? '')
  const [stockMinimo, setStockMinimo] = useState(material?.stockMinimo ?? '')
  const [error, setError] = useState<string | null>(null)
  const [categorias, setCategorias] = useState<CategoriaResponse[]>([])
  const [unidades, setUnidades] = useState<{ idUnidad: number; nombre: string; abreviatura: string }[]>([])

  useEffect(() => {
    materialService
      .listarCategorias()
      .then(setCategorias)
      .catch(() => setError('No se pudieron cargar las categorías.'))
    materialService
      .listarUnidades()
      .then(setUnidades)
      .catch(() => setError('No se pudieron cargar las unidades de medida.'))
  }, [])

  // Los campos parten de los initializers (material) porque el modal se remonta
  // fresco en cada apertura (tipo de modal 'formulario' condiciona el render).

  const guardar = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!nombre.trim()) return setError('El nombre es obligatorio.')
    if (idCategoria === '') return setError('Selecciona la categoría.')
    if (idUnidad === '') return setError('Selecciona la unidad de medida.')
    const stockN = Number(stock)
    const minimoN = Number(stockMinimo)
    if (Number.isNaN(stockN) || stockN < 0) return setError('El stock no puede ser negativo.')
    if (Number.isNaN(minimoN) || minimoN < 0) return setError('El stock mínimo no puede ser negativo.')
    try {
      await onGuardar(material, {
        nombre: nombre.trim(),
        idCategoria: idCategoria as number,
        idUnidad: idUnidad as number,
        stock: stockN,
        stockMinimo: minimoN,
      })
    } catch (e) {
      setError(extraerMensaje(e))
    }
  }

  return (
    <Modal titulo={material ? `Editar · ${material.nombre}` : 'Nuevo material'} onCerrar={onCerrar}>
      <form className="formulario" onSubmit={guardar} noValidate>
        <label className="formulario__campo">
          <span className="formulario__label">Nombre</span>
          <input value={nombre} onChange={(e) => setNombre(e.target.value)} placeholder="Ej. Arena de río" />
        </label>

        <div className="formulario__fila">
          <label className="formulario__campo">
            <span className="formulario__label">Categoría</span>
            <select value={idCategoria} onChange={(e) => setIdCategoria(e.target.value === '' ? '' : Number(e.target.value))}>
              <option value="">Seleccionar…</option>
              {categorias.map((c) => (
                <option key={c.idCategoria} value={c.idCategoria}>
                  {c.nombre}
                </option>
              ))}
            </select>
          </label>
          <label className="formulario__campo">
            <span className="formulario__label">Unidad de medida</span>
            <select value={idUnidad} onChange={(e) => setIdUnidad(e.target.value === '' ? '' : Number(e.target.value))}>
              <option value="">Seleccionar…</option>
              {unidades.map((u) => (
                <option key={u.idUnidad} value={u.idUnidad}>
                  {u.nombre} ({u.abreviatura})
                </option>
              ))}
            </select>
          </label>
        </div>

        <div className="formulario__fila">
          <label className="formulario__campo">
            <span className="formulario__label">Stock</span>
            <input
              className="mono"
              type="number"
              min="0"
              step="any"
              value={stock}
              onChange={(e) => setStock(e.target.value)}
            />
          </label>
          <label className="formulario__campo">
            <span className="formulario__label">Stock mínimo</span>
            <input
              className="mono"
              type="number"
              min="0"
              step="any"
              value={stockMinimo}
              onChange={(e) => setStockMinimo(e.target.value)}
            />
          </label>
        </div>

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
  material: MaterialResponse
  onConfirmar: (material: MaterialResponse) => Promise<void>
  onCerrar: () => void
}

function ConfirmarEliminarModal({ material, onConfirmar, onCerrar }: ConfirmarProps) {
  const [eliminando, setEliminando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const confirmar = async () => {
    setEliminando(true)
    setError(null)
    try {
      await onConfirmar(material)
    } catch (e) {
      setError(extraerMensaje(e))
    } finally {
      setEliminando(false)
    }
  }

  return (
    <Modal titulo="Desactivar material" onCerrar={onCerrar}>
      <p className="modal__texto">
        ¿Desactivar <strong>{material.nombre}</strong>? Dejará de aparecer en el inventario.
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
        <button type="button" className="btn btn--danger" onClick={() => void confirmar()} disabled={eliminando}>
          {eliminando ? 'Desactivando…' : 'Desactivar'}
        </button>
      </div>
    </Modal>
  )
}