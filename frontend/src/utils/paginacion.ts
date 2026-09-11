// Calculo comun de paginacion: compartido por las paginas para no repetir la
// ventana de numeros y los indices visibles en cada una.

export const VENTANA_PAGINAS = 2

export interface ResumenPagina {
  inicio: number
  fin: number
  totalPaginas: number
  paginasVisibles: number[]
}

export function paginar(
  pagina: number,
  tamano: number,
  total: number,
  totalPaginas: number,
): ResumenPagina {
  const inicio = total === 0 ? 0 : pagina * tamano + 1
  const fin = Math.min(pagina * tamano + tamano, total)
  const paginasVisibles = Array.from(
    new Set([0, totalPaginas - 1, pagina, pagina - VENTANA_PAGINAS, pagina + VENTANA_PAGINAS]),
  )
    .filter((n) => n >= 0 && n < totalPaginas)
    .sort((a, b) => a - b)
  return { inicio, fin, totalPaginas, paginasVisibles }
}