// Pie de tabla con paginacion (primera/ultima, numeros con salto, tamanio de
// pagina). Antes cada pagina lo repetia integro (~67 lineas); aqui recibe los
// valores calculados por la pagina y los callbacks de navegacion.
interface PaginacionProps {
  inicio: number
  fin: number
  total: number
  pagina: number
  totalPaginas: number
  paginasVisibles: number[]
  tamano: number
  onCambiarPagina: (pagina: number) => void
  onCambiarTamano: (tamano: number) => void
  mostrarExtremos?: boolean
  ariaLabel?: string
}

export function Paginacion({
  inicio,
  fin,
  total,
  pagina,
  totalPaginas,
  paginasVisibles,
  tamano,
  onCambiarPagina,
  onCambiarTamano,
  mostrarExtremos = true,
  ariaLabel = 'Paginación',
}: PaginacionProps) {
  return (
    <div className="materiales__pie">
      <span className="materiales__total mono">
        {inicio}–{fin} de {total.toLocaleString('es-CO')}
      </span>
      <nav className="paginacion" aria-label={ariaLabel}>
        {mostrarExtremos && (
          <button
            type="button"
            className="paginacion__btn"
            disabled={pagina === 0}
            onClick={() => onCambiarPagina(0)}
            aria-label="Primera página"
          >
            ⟪
          </button>
        )}
        {paginasVisibles.map((n, i) => {
          const anterior = paginasVisibles[i - 1]
          const salto = anterior !== undefined && n - anterior > 1
          return (
            <span key={n} className="paginacion__grupo">
              {salto && <span className="paginacion__salto">…</span>}
              <button
                type="button"
                className={`paginacion__btn${n === pagina ? ' paginacion__btn--actual' : ''}`}
                aria-current={n === pagina ? 'page' : undefined}
                onClick={() => onCambiarPagina(n)}
              >
                {n + 1}
              </button>
            </span>
          )
        })}
        {mostrarExtremos && (
          <button
            type="button"
            className="paginacion__btn"
            disabled={pagina >= totalPaginas - 1}
            onClick={() => onCambiarPagina(totalPaginas - 1)}
            aria-label="Última página"
          >
            ⟫
          </button>
        )}
      </nav>
      <label className="materiales__tamano">
        Por página
        <select
          value={tamano}
          onChange={(e) => onCambiarTamano(Number(e.target.value))}
        >
          {[10, 20, 50, 100].map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </label>
    </div>
  )
}