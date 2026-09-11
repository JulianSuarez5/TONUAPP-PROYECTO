import { useState } from 'react'

// Estado y callbacks de paginacion compartidos entre paginas (Materiales,
// Proveedores, etc.): al cambiar pagina/tamano ejecuta 'onCambiar' (mostrar el
// skeleton) y reajusta el estado. Las paginas conservan setPagina/setTamano
// para reiniciar la pagina al tocar filtros o al borrar la ultima fila.
export function usePaginacion(onCambiar: () => void) {
  const [pagina, setPagina] = useState(0)
  const [tamano, setTamano] = useState(20)

  const cambiarPagina = (n: number) => {
    onCambiar()
    setPagina(n)
  }

  const cambiarTamano = (s: number) => {
    onCambiar()
    setTamano(s)
    setPagina(0)
  }

  return { pagina, tamano, setPagina, setTamano, cambiarPagina, cambiarTamano }
}