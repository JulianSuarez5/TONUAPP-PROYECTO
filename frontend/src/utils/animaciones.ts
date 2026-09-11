// Animaciones compartidas entre paginas (framer-motion). Cada pagina redefinia
// estos mismos variants y la curva EASE (duplicacion que marcaba SonarQube Cloud).
// EASE es la curva del design system (--ease-flat en index.css, estandar ease-out).
import type { Variants } from 'framer-motion'

export const EASE: [number, number, number, number] = [0.2, 0, 0, 1]

export const filaVariants: Variants = {
  reposo: { backgroundColor: 'rgb(255 255 255 / 0)' },
  hover: { backgroundColor: 'var(--color-row-hover)' },
}

// transition a nivel de variant no lo admite la interfaz Variants de framer-motion 13;
// se deja sin anotar (mismo codigo que usaban las paginas, validado por el propio
// componente <motion> en el consumo).
export const costadoVariants = {
  reposo: { opacity: 0, x: -8, scaleY: 0 },
  hover: { opacity: 1, x: 0, scaleY: 1 },
  transition: { duration: 0.16, ease: EASE },
}

export const paginaVariants: Variants = {
  reposo: { opacity: 0 },
  visible: { opacity: 1, transition: { staggerChildren: 0.03 } },
}

export const bloqueVariants: Variants = {
  reposo: { opacity: 0, y: 10 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.28, ease: EASE } },
}