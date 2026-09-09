// Escuadra de ingeniero (D-28): firma tecnica del header. Sustituye al compas
// (D-26) que no se leia como tal. SVG estatico, decorativo (aria-hidden), sin
// will-change ni rAF: evita los artefactos de pintado del intento anterior.
// La marca (reglas del interior) usa el segundo acento teal SOLO aqui.
import { useId } from 'react'

export function Escuadra() {
  const id = useId()
  return (
    <span className="escuadra" aria-hidden="true">
      <svg viewBox="0 0 26 34" width="26" height="34" role="presentation">
        <g fill="none" strokeLinecap="round" strokeLinejoin="round">
          <path
            d="M6 30 L2 6 L24 30 Z"
            stroke="currentColor"
            strokeWidth="1.6"
          />
          <path
            d="M6 30 L10 8 L20 28"
            stroke="currentColor"
            strokeWidth="0.8"
          />
          <path
            id={`${id}-regla`}
            d="M7 24 L8.2 8.9"
            className="escuadra--marca"
            strokeWidth="1"
          />
          <path
            d="M12 28 L13.2 13"
            className="escuadra--marca"
            strokeWidth="1"
          />
          <path
            d="M16.6 29.4 L17.4 17"
            className="escuadra--marca"
            strokeWidth="0.8"
          />
        </g>
      </svg>
    </span>
  )
}