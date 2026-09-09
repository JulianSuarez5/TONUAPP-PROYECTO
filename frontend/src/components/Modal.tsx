// Modal basico de TONUAPP: overlays simples (Flat Design, sin sombras). Lo usan
// detalle, formulario y confirmaciones. Cierra con Escape o clic en el fondo.
// La entrada/salida la organiza framer-motion (D-28 revisado): entrada con
// scale(0.97)->1 + fade 240ms ease-out (presencia) y salida mas rapida (160ms).
// El AnimatePresence lo pone el padre condicionando el render con <Modal>.
import { useEffect, useRef, type ReactNode } from 'react'
import { motion } from 'framer-motion'

interface ModalProps {
  titulo: string
  onCerrar: () => void
  children: ReactNode
  ancho?: 'md' | 'lg'
}

const EASE = [0.2, 0, 0, 1] as const

export function Modal({ titulo, onCerrar, children, ancho = 'md' }: ModalProps) {
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCerrar()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onCerrar])

  return (
    <motion.div
      className="modal__overlay"
      onClick={onCerrar}
      role="presentation"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0, transition: { duration: 0.16, ease: EASE } }}
      transition={{ duration: 0.24, ease: EASE }}
    >
      <motion.div
        ref={ref}
        className={`modal${ancho === 'lg' ? ' modal--lg' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-titulo"
        onClick={(e) => e.stopPropagation()}
        initial={{ opacity: 0, scale: 0.97, y: 8 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.98, y: 4, transition: { duration: 0.16, ease: EASE } }}
        transition={{ duration: 0.24, ease: EASE }}
      >
        <header className="modal__header">
          <h2 id="modal-titulo" className="modal__titulo">
            {titulo}
          </h2>
          <button
            type="button"
            className="modal__cerrar"
            aria-label="Cerrar"
            onClick={onCerrar}
          >
            ✕
          </button>
        </header>
        <div className="modal__body">{children}</div>
      </motion.div>
    </motion.div>
  )
}