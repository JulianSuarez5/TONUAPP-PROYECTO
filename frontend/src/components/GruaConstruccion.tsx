// GruaConstruccion — elemento animado del hero (D-33). Sustituye a la Pila3D.
// Escena SVG cinemática de una grúa de torre (side view) que levanta un bloque,
// lo traslada y lo deposita encima de una estructura de obra, en bucle infinito.
// Cinemática inspirada en el recurso facilitado por el usuario (estados
// moving_to_block > lifting > dropping > returning > idle), corregida: el bucle
// usa setTimeout recursivo (nunca setInterval, que dispararía ciclos solapados);
// la pluma, el carro (trolley), el cable y el bloque viven en un mismo grupo
// articulado cuyo pivote es la punta del mástil (126,46) — el cable siempre
// cuelga vertical bajo el carro y el bloque aterriza sobre la estructura.
// Bucle: PLUMA rota ±7°, CARRO recorre el aguilón, CABLE sube/baja, BLOQUE viaja
// ground > air > structure. La estructura crece 1 piso por ciclo (máx 6) y se
// reinicia a 3. Colores D-25: carbono/hueso/teal dominan; naranja SOLO como acento
// decorativo (casco del operador); hay un Truck quieto como atrezo de obra. Solo
// transform/opacity/attr, GPU-safe. prefers-reduced-motion => escena estática.
import { useReducedMotion, useSpring, motion, useMotionValue } from 'framer-motion'
import { useEffect, useRef, useState, type ReactElement } from 'react'
import { HardHat, Truck } from 'lucide-react'

// Posiciones world (viewBox 0 0 480 300). Suelo y=290; mástil en x=126, y=46.
const PIVOTE = { x: 126, y: 46 } // punta del mástil (origen de la rotación de la pluma)
const CARRIL = 48 // cota Y de la cabina del operador (el carro viaja a esta altura)
const ALTURA_BLOQUE = 24
// El bloque transportado cuelga del gancho: vástago local CARRIL→18 + bloque
// local 18..44. En coords world el fondo del bloque = colgar + 44; aterriza al
// posar colgar sobre SUELO - piso*24, luego fondobloque = colgar_real + 44.
const COLGAR_ATERRIZA = 44
const PISO_INICIAL = 3
const PISO_MAXIMO = 6
const SUELO = 290
const ESTRUCTURA = { x: 330, w: 120 } // torre a la derecha donde se construye

interface Etapa {
  pluma: number // giro de la pluma (grados)
  carro: number // posición X world del carro/trolley
  colgar: number // longitud del cable (distancia bajo el carro)
  dur: number // duración de la etapa en ms
}

type EtapaConstruccion = 'moving_to_block' | 'lifting' | 'dropping' | 'returning' | 'idle'
type EtapasRecord = Record<EtapaConstruccion, Etapa>

// Duración total del ciclo: 1500+1500+1500+1500+700 ≈ 6700ms.
// colgar del pickup (246): fondo del bloque world = colgar + 44 = 290 = SUELO
// (el bloque queda apoyado en el suelo al recogerlo).
const CICLO: EtapasRecord = {
  moving_to_block: { pluma: -6, carro: 210, colgar: 246, dur: 1500 }, // baja a recoger del suelo
  lifting: { pluma: -3, carro: 210, colgar: 96, dur: 1500 }, // lo eleva al aire
  dropping: { pluma: 7, carro: 390, colgar: 0, dur: 1500 }, // lo lleva a la estructura (colgar real según piso)
  returning: { pluma: 0, carro: 74, colgar: 44, dur: 1500 }, // vuelve vacío junto al mástil
  idle: { pluma: -6, carro: 210, colgar: 246, dur: 700 }, // reposo pre-pickup
}

const ORDEN: EtapaConstruccion[] = ['moving_to_block', 'lifting', 'dropping', 'returning', 'idle']

const TRANS_OSCILACION = { type: 'spring', stiffness: 130, damping: 19, mass: 0.8 } as const
const TRANS_CAIDA = { type: 'spring', stiffness: 90, damping: 21, mass: 0.9 } as const
const TRANS_PLUMA = { type: 'spring', stiffness: 60, damping: 15 } as const

interface GruaConstruccionProps {
  className?: string
  'aria-label'?: string
}

/** Un bloque de obra: rectángulo con hairline y cara superior más clara. */
function Bloque({ color, w = ALTURA_BLOQUE, h = ALTURA_BLOQUE + 2, x, y }: { color: string; w?: number; h?: number; x: number; y: number }): ReactElement {
  return (
    <rect x={x} y={y} width={w} height={h - 3} rx={2} fill={color} stroke="rgba(41,37,36,0.12)" strokeWidth={1} />
  )
}

export function GruaConstruccion({ className = '', 'aria-label': ariaLabel = 'Grúa de torre levantando bloques en una obra de construcción' }: GruaConstruccionProps) {
  const reducir = useReducedMotion()
  // piso impulsa el RENDER de la torre; pisoRef lo lee el ciclo sin re-armarlo
  // (si el efecto dependiera de piso, al crecer la torre se reiniciaría el bucle
  // y se saltaría la vuelta del carro — bug evitado con el ref).
  const [piso, setPiso] = useState(PISO_INICIAL)
  const pisoRef = useRef(PISO_INICIAL)
  const carro = useMotionValue(CICLO.idle.carro)
  const colgar = useMotionValue(CICLO.idle.colgar)
  const pluma = useMotionValue(CICLO.idle.pluma)
  const carroSpring = useSpring(carro, TRANS_OSCILACION)
  const colgarSpring = useSpring(colgar, TRANS_CAIDA)
  const plumaSpring = useSpring(pluma, TRANS_PLUMA)

  const timers = useRef<number[]>([])

  // Ciclo de construcción. setTimeout recursivo (nunca setInterval, que
  // solaparía ciclos). La torre crece un piso al depositar y se regenera al
  // llegar al máximo. El ciclo arranca una sola vez (deps estables): la cota de
  // caída del bloque se lee de pisoRef, por lo que sube con cada piso sin que
  // el efecto se reinicie.
  useEffect(() => {
    if (reducir) return
    let cancelado = false
    timers.current.forEach(clearTimeout)
    timers.current = []

    const encolar = (fn: () => void, ms: number) => {
      if (cancelado) return
      timers.current.push(window.setTimeout(fn, ms))
    }

    const subirPiso = () =>
      setPiso((p) => {
        const siguiente = p >= PISO_MAXIMO ? PISO_INICIAL : p + 1
        pisoRef.current = siguiente
        return siguiente
      })

    const ciclo = () => {
      let t = 0
      for (const etapa of ORDEN) {
        const e = CICLO[etapa]
        encolar(() => {
          carro.set(e.carro)
          pluma.set(e.pluma)
          colgar.set(
            etapa === 'dropping'
              ? SUELO - pisoRef.current * ALTURA_BLOQUE - COLGAR_ATERRIZA
              : e.colgar,
          )
        }, t)
        t += e.dur
        if (etapa === 'dropping') {
          encolar(subirPiso, t + 260)
        }
      }
      encolar(ciclo, t + 400)
    }

    ciclo()
    return () => {
      cancelado = true
      timers.current.forEach(clearTimeout)
      timers.current = []
    }
  }, [reducir, carro, colgar, pluma])

  return (
    <div className={`grua${className ? ` ${className}` : ''}`} role="img" aria-label={ariaLabel}>
      <svg viewBox="0 0 480 300" role="presentation" aria-hidden="true">
        <defs>
          <linearGradient id="grua-mastil" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#3a362f" />
            <stop offset="1" stopColor="#292524" />
          </linearGradient>
          <linearGradient id="grua-pluma" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0" stopColor="#e7e2d9" />
            <stop offset="1" stopColor="#d6d0c4" />
          </linearGradient>
        </defs>

        {/* ---- Suelo ---- */}
        <rect x={0} y={SUELO} width={480} height={10} fill="#ece8e0" />

        {/* ---- Torre de la estructura (derecha): piso de bloques ---- */}
        {Array.from({ length: piso }).map((_, i) => {
          const y = SUELO - (i + 1) * ALTURA_BLOQUE
          return (
            <Bloque
              key={i}
              x={ESTRUCTURA.x}
              y={y}
              w={ESTRUCTURA.w}
              color={i % 3 === 1 ? '#e7e2d9' : i % 3 === 2 ? '#d3ccc0' : '#cbbfb0'}
            />
          )
        })}

        {/* ---- Camión decorativo (atrezo, izquierda) ---- */}
        <g transform="translate(28, 234)">
          <Truck size={46} color="#938b7c" strokeWidth={1.6} />
          <rect x={0} y={44} width={56} height={1} fill="rgba(41,37,36,0.14)" />
        </g>

        {/* ---- Grupo articulado (pivote en la punta del mástil) ---- */}
        <motion.g style={{ transformBox: 'view-box', transformOrigin: `${PIVOTE.x}px ${PIVOTE.y}px`, rotate: plumaSpring }}>
          {/* Aguilón (pluma) + contrapluma */}
          <rect x={PIVOTE.x} y={PIVOTE.y - 6} width={206} height={9} rx={4} fill="url(#grua-pluma)" stroke="rgba(41,37,36,0.2)" strokeWidth={1} />
          <rect x={PIVOTE.x - 98} y={PIVOTE.y - 4} width={98} height={6} rx={3} fill="#332f29" />
          {/* tensor diagonal */}
          <line x1={PIVOTE.x} y1={PIVOTE.y} x2={PIVOTE.x + 206} y2={PIVOTE.y - 6} stroke="rgba(41,37,36,0.18)" strokeWidth={1} />

          {/* Carro + cable + bloque */}
          <motion.g style={{ x: carroSpring }}>
            {/* carro */}
            <rect x={-9} y={CARRIL - 9} width={18} height={12} rx={2} fill="#4a443c" />
            {/* cable: rect de altura animada (SVG2 permite height CSS en rect) */}
            <motion.rect x={-0.7} y={CARRIL} width={1.4} rx={0.7} fill="#6b6459" style={{ height: colgarSpring }} />
            {/* gancho + bloque colgado: grupo trasladado por colgar; local 0 = fondo del cable */}
            <motion.g style={{ y: colgarSpring }}>
              {/* barra superior (fondo del cable en local CARRIL) + vástago hasta el bloque */}
              <path d={`M-5 ${CARRIL} L5 ${CARRIL} M0 ${CARRIL} L0 18`} stroke="#6b6459" strokeWidth={2} fill="none" />
              {/* el bloque transportado: teal (acento del hero, D-25); fondo = 18+26 = local 44 -> world colgar+44 < SUELO */}
              <Bloque x={-12} y={18} w={24} h={26} color="#0f766e" />
            </motion.g>
          </motion.g>
        </motion.g>

        {/* ---- Mástil fijo (bajo el pivote) ---- */}
        <rect x={PIVOTE.x - 7} y={PIVOTE.y - 6} width={14} height={SUELO - PIVOTE.y + 6} fill="url(#grua-mastil)" />
        {/* cabina del operador + ventanal teal */}
        <rect x={PIVOTE.x} y={PIVOTE.y + 4} width={34} height={34} rx={3} fill="#33302a" />
        <rect x={PIVOTE.x + 6} y={PIVOTE.y + 12} width={26} height={16} rx={2} fill="#0f766e" opacity={0.85} />
        {/* contrapeso */}
        <rect x={PIVOTE.x - 32} y={PIVOTE.y + 12} width={24} height={14} rx={2} fill="#524b42" />
        {/* casco del operador (acento naranja decorativo, no CTA — regla D-25) */}
        <g transform={`translate(${PIVOTE.x + 12}, ${PIVOTE.y - 26})`}>
          <HardHat size={22} color="#ea580c" strokeWidth={1.7} />
        </g>
      </svg>

      {/* Leyenda sutil bajo la escena (describe el elemento, sin inventar datos) */}
      <span className="grua__leyenda" aria-hidden="true">
        Obra simulada
      </span>
    </div>
  )
}