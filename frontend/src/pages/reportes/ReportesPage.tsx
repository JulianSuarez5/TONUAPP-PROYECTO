// Reportes de inventario (RF-014, D-21): consultas JSON del backend (stock actual
// RF-001, bajo stock RF-012, historial de movimientos y resumen por material en un
// rango RF-013/RF-004) y exportacion pdf/xlsx con bitacora persistiendo quien,
// cuando, tipo, formato y rango. Solo Administrador (backend @PreAuthorize, RF-009;
// el rol "Gerente" de RF-014 sigue pendiente de la decision de roles RF-006). El
// binario no se guarda en el frontend: se re-descarga desde la bitacora.
import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { useAuth } from '../../hooks/useAuth'
import type { MovimientoResponse, TipoMovimiento } from '../../types/movimientos'
import {
  ETIQUETAS_TIPO,
  FORMATOS,
  bitacora,
  bajoStock,
  descargar,
  exportar as exportarReporte,
  inventario,
  movimientos,
  resumen,
  type DescargaReporte,
} from '../../services/reporteService'
import type {
  FormatoReporte,
  ReporteGeneradoRow,
  ReporteInventarioRow,
  ReporteResumenRow,
  TipoReporte,
  Vista,
} from '../../types/reportes'
import { extraerMensaje } from '../../utils/errores'
import { EASE, bloqueVariants, paginaVariants } from '../../utils/animaciones'
import './reportes.css'

const PESTANAS: { valor: Vista; etiqueta: string }[] = [
  { valor: 'inventario', etiqueta: 'Inventario' },
  { valor: 'bajo_stock', etiqueta: 'Bajo stock' },
  { valor: 'movimientos', etiqueta: 'Movimientos' },
  { valor: 'resumen', etiqueta: 'Resumen' },
  { valor: 'bitacora', etiqueta: 'Bitacora' },
]

const ETIQUETA_TIPO_MOV: Record<TipoMovimiento, string> = {
  entrada: 'Entrada',
  salida_venta: 'Salida (venta)',
  salida_merma: 'Merma',
  ajuste: 'Ajuste',
}

// Valida el rango de fechas del reporte: ambas o ninguna, y desde <= hasta
function validarRango(desde: string, hasta: string): string | null {
  if ((desde === '') !== (hasta === '')) {
    return 'El rango de fechas debe indicar inicio y fin, o ninguno.'
  }
  if (desde && hasta && desde > hasta) {
    return 'La fecha de inicio no puede ser posterior a la de fin.'
  }
  return null
}

function numero(n: number): string {
  return n.toLocaleString('es-CO', { maximumFractionDigits: 2 })
}

function guardarArchivo(desc: DescargaReporte): void {
  const url = URL.createObjectURL(desc.blob)
  const a = document.createElement('a')
  a.href = url
  a.download = desc.nombre
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

type ErrorDescarga = { response?: { data?: unknown; status?: number } }

// Un fallo 4xx/5xx de exportacion llega como Blob con JSON de ApiError
async function mensajeDescarga(e: unknown): Promise<string> {
  const err = e as ErrorDescarga | null
  if (err?.response?.data instanceof Blob) {
    const texto = await err.response.data.text()
    try {
      const json = JSON.parse(texto) as { message?: string }
      if (json.message) return json.message
    } catch {
      // la descarga fallo sin cuerpo JSON util; se cae al generico
    }
  }
  return extraerMensaje(e)
}

export function ReportesPage() {
  const { usuario } = useAuth()
  const esAdmin = usuario?.rol === 'Administrador'

  const [vista, setVista] = useState<Vista>('inventario')
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')

  const [invData, setInvData] = useState<ReporteInventarioRow[]>([])
  const [bajoData, setBajoData] = useState<ReporteInventarioRow[]>([])
  const [movsData, setMovsData] = useState<MovimientoResponse[]>([])
  const [resData, setResData] = useState<ReporteResumenRow[]>([])
  const [bitData, setBitData] = useState<ReporteGeneradoRow[]>([])

  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [exportando, setExportando] = useState<string | null>(null)
  const [descargando, setDescargando] = useState<number | null>(null)
  const [refrescar, setRefrescar] = useState(0)

  const requiereRango = vista === 'movimientos' || vista === 'resumen'

  // Carga la pestana actual; en las de rango se re-consulta al cambiar las fechas o
  // al refrescar tras una exportacion (para verla en la bitacora).
  useEffect(() => {
    let vivo = true
    if (!esAdmin) return
    setCargando(true)
    const err = requiereRango ? validarRango(desde, hasta) : null
    if (err) {
      setError(err)
      setCargando(false)
      return
    }
    const desdeF = desde || undefined
    const hastaF = hasta || undefined
    const promesa =
      vista === 'inventario'
        ? inventario().then((d) => setInvData(d))
        : vista === 'bajo_stock'
          ? bajoStock().then((d) => setBajoData(d))
          : vista === 'movimientos'
            ? movimientos(desdeF, hastaF).then((d) => setMovsData(d))
            : vista === 'resumen'
              ? resumen(desdeF, hastaF).then((d) => setResData(d))
              : bitacora().then((d) => setBitData(d))
    promesa
      .then(() => {
        if (vivo) setError(null)
      })
      .catch((e: unknown) => {
        if (vivo) setError(extraerMensaje(e))
      })
      .finally(() => {
        if (vivo) setCargando(false)
      })
    return () => {
      vivo = false
    }
  }, [esAdmin, vista, desde, hasta, refrescar])

  const exportarActual = async (formato: FormatoReporte) => {
    if (requiereRango) {
      const err = validarRango(desde, hasta)
      if (err) {
        setError(err)
        return
      }
    }
    setExportando(`${vista}:${formato}`)
    setError(null)
    try {
      const desc = await exportarReporte({
        tipo: vista as TipoReporte,
        formato,
        desde: requiereRango ? desde || undefined : undefined,
        hasta: requiereRango ? hasta || undefined : undefined,
      })
      guardarArchivo(desc)
      setRefrescar((n) => n + 1)
    } catch (e) {
      setError(await mensajeDescarga(e))
    } finally {
      setExportando(null)
    }
  }

  const descargarBitacora = async (id: number) => {
    setDescargando(id)
    setError(null)
    try {
      guardarArchivo(await descargar(id))
    } catch (e) {
      setError(await mensajeDescarga(e))
    } finally {
      setDescargando(null)
    }
  }

  // ---- Render ----

  if (!esAdmin) {
    return (
      <motion.div variants={bloqueVariants} initial="reposo" animate="visible">
        <h1 className="materiales__titulo">Reportes</h1>
        <p className="materiales__subtitulo">
          Los reportes son de uso interno: solo un Administrador puede consultarlos (RF-009).
        </p>
      </motion.div>
    )
  }

  return (
    <motion.div
      className="reportes"
      variants={paginaVariants}
      initial="reposo"
      animate="visible"
      transition={{ duration: 0.28, ease: EASE }}
    >
      <motion.section variants={bloqueVariants} className="materiales__cabecera">
        <div>
          <h1 className="materiales__titulo">
            <span className="materiales__titulo-num" aria-hidden="true">
              06
            </span>
            Reportes
          </h1>
          <p className="materiales__subtitulo">Consulta y exportación del inventario · RF-014</p>
        </div>
      </motion.section>

      <motion.section variants={bloqueVariants} className="reportes__pestanas" role="tablist" aria-label="Reportes">
        {PESTANAS.map((p) => (
          <button
            key={p.valor}
            type="button"
            role="tab"
            aria-selected={vista === p.valor}
            className={`reportes__pestana${vista === p.valor ? ' reportes__pestana--activa' : ''}`}
            onClick={() => setVista(p.valor)}
          >
            {p.etiqueta}
          </button>
        ))}
      </motion.section>

      <motion.section variants={bloqueVariants} className="reportes__barra">
        {requiereRango && (
          <div className="reportes__rango">
            <label className="materiales__categoria">
              <span className="materiales__label">Desde</span>
              <input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} aria-label="Desde" />
            </label>
            <label className="materiales__categoria">
              <span className="materiales__label">Hasta</span>
              <input type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} aria-label="Hasta" />
            </label>
          </div>
        )}
        <div className="reportes__exportar">
          {vista !== 'bitacora' &&
            FORMATOS.map((f) => (
              <button
                key={f}
                type="button"
                className="btn btn--cta"
                disabled={exportando !== null}
                onClick={() => void exportarActual(f)}
              >
                Exportar {f === 'pdf' ? 'PDF' : 'Excel'}
              </button>
            ))}
        </div>
      </motion.section>

      {error && (
        <p className="materiales__error" role="alert">
          {error}
        </p>
      )}

      <motion.section variants={bloqueVariants} className="materiales__tabla-wrap" aria-live="polite">
        {cargando ? (
          <p className="materiales__vacio">Cargando reporte…</p>
        ) : vista === 'inventario' || vista === 'bajo_stock' ? (
          <FilasInventario
            filas={vista === 'inventario' ? invData : bajoData}
            bajo={vista === 'bajo_stock'}
          />
        ) : vista === 'movimientos' ? (
          <FilasMovimientos filas={movsData} />
        ) : vista === 'resumen' ? (
          <FilasResumen filas={resData} />
        ) : (
          <FilasBitacora
            filas={bitData}
            descargando={descargando}
            onDescargar={(id) => void descargarBitacora(id)}
          />
        )}
      </motion.section>
    </motion.div>
  )
}

// Tabla compartida por Inventario (RF-001) y Bajo stock (RF-012)
function CeldaMaterial({ nombre }: { nombre: string }) {
  return (
    <td className="materiales__primera">
      <span className="materiales__fila-acento" aria-hidden="true" />
      <span className="reportes__material">{nombre}</span>
    </td>
  )
}

function FilasInventario({ filas, bajo }: { filas: ReporteInventarioRow[]; bajo: boolean }) {
  if (filas.length === 0) {
    return (
      <p className="materiales__vacio">
        {bajo ? 'No hay materiales bajo el stock mínimo (RF-012).' : 'Sin datos de inventario.'}
      </p>
    )
  }
  return (
    <table className="materiales__tabla">
      <thead>
        <tr>
          <th scope="col">Material</th>
          <th scope="col">Categoría</th>
          <th scope="col">Unidad</th>
          <th scope="col" className="numero">Stock</th>
          <th scope="col" className="numero">Mínimo</th>
          <th scope="col">Alerta</th>
        </tr>
      </thead>
      <tbody>
        {filas.map((f) => (
          <motion.tr key={f.idMaterial} variants={bloqueVariants} initial="reposo" whileInView="visible" viewport={{ once: true }}>
            <CeldaMaterial nombre={f.nombreMaterial} />
            <td>{f.categoria}</td>
            <td>{f.unidad}</td>
            <td className={`numero mono${bajo ? ' reportes__critico' : ''}`}>{numero(f.stock)}</td>
            <td className="numero mono">{numero(f.stockMinimo)}</td>
            <td>
              {f.conAlertaActiva ? (
                <span className="reportes__alerta-activa">Alerta activa</span>
              ) : (
                <span className="materiales__muted">—</span>
              )}
            </td>
          </motion.tr>
        ))}
      </tbody>
    </table>
  )
}

// Historial de movimientos en el rango (reporte de movimientos, RF-014)
function FilasMovimientos({ filas }: { filas: MovimientoResponse[] }) {
  if (filas.length === 0) {
    return <p className="materiales__vacio">Sin movimientos para el rango seleccionado.</p>
  }
  return (
    <table className="materiales__tabla">
      <thead>
        <tr>
          <th scope="col">Fecha</th>
          <th scope="col">Material</th>
          <th scope="col">Tipo</th>
          <th scope="col" className="numero">Cantidad</th>
          <th scope="col">Usuario</th>
          <th scope="col">Estado</th>
        </tr>
      </thead>
      <tbody>
        {filas.map((m) => (
          <motion.tr key={m.idMovimiento} variants={bloqueVariants} initial="reposo" whileInView="visible" viewport={{ once: true }}>
            <td className="mono">{m.fechaMovimiento.replace('T', ' ').slice(0, 16)}</td>
            <CeldaMaterial nombre={m.nombreMaterial} />
            <td>
              <span className="reportes__tipo">{ETIQUETA_TIPO_MOV[m.tipo]}</span>
            </td>
            <td className="numero mono">{numero(m.cantidad)}</td>
            <td>{m.nombreUsuario}</td>
            <td>
              <span className={`movimientos__estado${m.estado === 'anulado' ? ' movimientos__estado--anulado' : ''}`}>
                {m.estado === 'activo' ? 'Activo' : 'Anulado'}
              </span>
            </td>
          </motion.tr>
        ))}
      </tbody>
    </table>
  )
}

// Resumen por material en el rango (RF-013/RF-004)
function FilasResumen({ filas }: { filas: ReporteResumenRow[] }) {
  if (filas.length === 0) {
    return <p className="materiales__vacio">Sin movimientos para el rango seleccionado.</p>
  }
  return (
    <table className="materiales__tabla">
      <thead>
        <tr>
          <th scope="col">Material</th>
          <th scope="col" className="numero">Entradas</th>
          <th scope="col" className="numero">Ventas</th>
          <th scope="col" className="numero">Mermas</th>
          <th scope="col" className="numero">Ajuste neto</th>
          <th scope="col" className="numero">Efecto neto</th>
          <th scope="col" className="numero">Stock actual</th>
        </tr>
      </thead>
      <tbody>
        {filas.map((f) => (
          <motion.tr key={f.idMaterial} variants={bloqueVariants} initial="reposo" whileInView="visible" viewport={{ once: true }}>
            <CeldaMaterial nombre={f.nombreMaterial} />
            <td className="numero mono">{numero(f.entradas)}</td>
            <td className="numero mono">{numero(f.salidasVenta)}</td>
            <td className="numero mono">{numero(f.salidasMerma)}</td>
            <td className="numero mono">{numero(f.ajusteNeto)}</td>
            <td className="numero mono">{numero(f.efectoNeto)}</td>
            <td className="numero mono">{numero(f.stockActual)}</td>
          </motion.tr>
        ))}
      </tbody>
    </table>
  )
}

// Bitacora de exportaciones (RF-014): quien, cuando, tipo, formato y re-descarga
function FilasBitacora({
  filas,
  descargando,
  onDescargar,
}: {
  filas: ReporteGeneradoRow[]
  descargando: number | null
  onDescargar: (id: number) => void
}) {
  if (filas.length === 0) {
    return <p className="materiales__vacio">Aún no se han exportado reportes.</p>
  }
  return (
    <table className="materiales__tabla">
      <thead>
        <tr>
          <th scope="col">Generado</th>
          <th scope="col">Reporte</th>
          <th scope="col">Formato</th>
          <th scope="col">Rango</th>
          <th scope="col">Archivo</th>
          <th scope="col" className="acciones">Acciones</th>
        </tr>
      </thead>
      <tbody>
        {filas.map((b) => (
          <motion.tr key={b.idReporte} variants={bloqueVariants} initial="reposo" whileInView="visible" viewport={{ once: true }}>
            <td className="mono">{b.fechaGeneracion.replace('T', ' ').slice(0, 16)}</td>
            <CeldaMaterial nombre={ETIQUETAS_TIPO[b.tipoReporte as TipoReporte] ?? b.tipoReporte} />
            <td>{b.formato.toUpperCase()}</td>
            <td className="mono">
              {b.rangoFechaInicio ? `${b.rangoFechaInicio.slice(0, 10)} → ${b.rangoFechaFin?.slice(0, 10)}` : 'Sin rango'}
            </td>
            <td>{b.nombreArchivo}</td>
            <td className="acciones">
              <button
                type="button"
                className="accion accion--editar"
                disabled={descargando !== null}
                onClick={() => onDescargar(b.idReporte)}
              >
                {descargando === b.idReporte ? 'Descargando…' : 'Descargar'}
              </button>
            </td>
          </motion.tr>
        ))}
      </tbody>
    </table>
  )
}