// Pagina principal de TONUAPP (D-30, "Premium utilitario"). Pantalla de inicio
// tras el login: hero con burbujas animadas (delight tier: primer uso), estadisticas
// REALES del backend (listar/categorias/unidades) y grid de modulos (Materiales
// activo; el resto marcado "Proximamente" hasta su fase). Sin inventar datos:
// contacto = nombre de la empresa documentado, sin direcciones/telefonos ficticios.
// Craft-floor impeccable: sin eyebrow sobre el H1, sin numeros de seccion, naranja
// SOLO en el CTA, teal en el branding/hero.
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { GruaConstruccion } from '../components/GruaConstruccion'
import * as materialService from '../services/materialService'
import './home.css'

// Curva del sistema (--ease-out en index.css equivale al estandar emilkowalski)
const EASE = [0.23, 1, 0.32, 1] as const

// Burbujas del hero: blobs de gradiente teal/naranja que derivan lento. Capa
// decorativa, pointer-events none, bajo el contenido. MotionConfig reducedMotion
// de AppLayout las suaviza en prefers-reduced-motion (transform relativo).
const burbujas = [
  { color: 'radial-gradient(circle at 30% 30%, rgba(15,118,110,0.16), transparent 70%)', tam: 340, x: 980, y: -60 },
  { color: 'radial-gradient(circle at 60% 40%, rgba(234,120,12,0.12), transparent 70%)', tam: 300, x: 40, y: 220 },
  { color: 'radial-gradient(circle at 40% 60%, rgba(15,118,110,0.10), transparent 70%)', tam: 260, x: 820, y: 330 },
]

interface Modulo {
  codigo: string
  nombre: string
  descripcion: string
  disponible: boolean
  rf: string
  ruta?: string
}

// Modulos del backlog (03_product_backlog). Con pantalla culminada se desbloquea
// su tarjeta (Materiales, Proveedores, Ubicaciones y lotes, Movimientos, Alertas,
// Reportes); el resto se marca "Proximamente" sin inventar su estado. RF segun el
// PPI (docs/requisitos): proveedores RF-010, movimientos RF-011/RF-013, alertas
// RF-012, reportes RF-014, ubicaciones RF-015.
const MODULOS: Modulo[] = [
  { codigo: '01', nombre: 'Materiales', descripcion: 'Inventario, búsqueda y control de stock.', disponible: true, rf: 'RF-001 a RF-004', ruta: '/materiales' },
  { codigo: '02', nombre: 'Proveedores', descripcion: 'Registro y asociación con materiales.', disponible: true, rf: 'RF-010', ruta: '/proveedores' },
  { codigo: '03', nombre: 'Ubicaciones y lotes', descripcion: 'Organización física del almacén.', disponible: true, rf: 'RF-015', ruta: '/ubicaciones' },
  { codigo: '04', nombre: 'Movimientos', descripcion: 'Entradas, salidas, mermas y ajustes.', disponible: true, rf: 'RF-011, RF-013', ruta: '/movimientos' },
  { codigo: '05', nombre: 'Alertas', descripcion: 'Avisos de stock mínimo y vencimientos.', disponible: true, rf: 'RF-012', ruta: '/alertas' },
  { codigo: '06', nombre: 'Reportes', descripcion: 'Consultas y exportación de inventario.', disponible: true, rf: 'RF-014', ruta: '/reportes' },
]

export function HomePage() {
  const [stats, setStats] = useState<{ materiales: number | null; categorias: number | null; unidades: number | null }>({
    materiales: null,
    categorias: null,
    unidades: null,
  })

  // Stats reales sin inventar: total de materiales (totalElements de una pagina
  // de 1), conteo de catalogos. Si una llamada falla, esa tarjeta queda en "—".
  useEffect(() => {
    let vivo = true
    void Promise.allSettled([
      materialService.listar(0, 1),
      materialService.listarCategorias(),
      materialService.listarUnidades(),
    ]).then(([mats, cats, unas]) => {
      if (!vivo) return
      setStats({
        materiales: mats.status === 'fulfilled' ? mats.value.totalElements : null,
        categorias: cats.status === 'fulfilled' ? cats.value.length : null,
        unidades: unas.status === 'fulfilled' ? unas.value.length : null,
      })
    })
    return () => {
      vivo = false
    }
  }, [])

  const tarjetasStats = useMemo(
    () => [
      { etiqueta: 'Materiales registrados', valor: stats.materiales, sufijo: '' },
      { etiqueta: 'Categorías', valor: stats.categorias, sufijo: '' },
      { etiqueta: 'Unidades de medida', valor: stats.unidades, sufijo: '' },
    ],
    [stats],
  )

  return (
    <div className="home">
      <div className="home__burbujas" aria-hidden="true">
        {burbujas.map((b, i) => (
          <motion.span
            key={i}
            className="home__burbuja"
            style={{ width: b.tam, height: b.tam, background: b.color, left: b.x, top: b.y }}
            animate={{ y: [0, -26, 0], x: [0, 18, 0], scale: [1, 1.06, 1] }}
            transition={{ duration: 22 + i * 7, repeat: Infinity, ease: 'easeInOut' }}
          />
        ))}
      </div>

      <section className="home__hero" aria-labelledby="home-titulo">
        <motion.div
          className="home__hero-texto"
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, ease: EASE }}
        >
          <h1 id="home-titulo" className="home__h1">
            Inventario de materiales
            <br />
            <span className="home__h1-bajo">en tiempo real</span>
          </h1>
          <p className="home__lede">
            Registro, actualización y consulta del inventario de{' '}
            <strong>Agregados el Tonusco S.A.S.</strong>
          </p>
          <Link className="btn btn--cta home__cta" to="/materiales">
            Ver inventario
          </Link>
        </motion.div>

        <motion.div
          className="home__hero-3d"
          initial={{ opacity: 0, scale: 0.92 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.6, ease: EASE, delay: 0.15 }}
        >
          <GruaConstruccion aria-label="Grúa de torre construyendo con bloques de material" />
        </motion.div>
      </section>

      <section className="home__stats" aria-label="Resumen del inventario">
        {tarjetasStats.map((s, i) => (
          <motion.div
            key={s.etiqueta}
            className="home__stat"
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, ease: EASE, delay: 0.15 + i * 0.06 }}
            whileHover={{ y: -3 }}
          >
            <span className="home__stat-valor mono">
              {s.valor === null ? '—' : s.valor.toLocaleString('es-CO')}
              {s.sufijo}
            </span>
            <span className="home__stat-etiqueta">{s.etiqueta}</span>
          </motion.div>
        ))}
      </section>

      <section className="home__modulos" aria-labelledby="home-modulos">
        <h2 id="home-modulos" className="home__h2">
          Módulos
        </h2>
        <div className="home__grid">
          {MODULOS.map((m, i) =>
            m.disponible ? (
              <motion.div
                key={m.codigo}
                className="home__card home__card--activo"
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.4, ease: EASE, delay: 0.2 + i * 0.04 }}
                whileHover={{ y: -4 }}
              >
                <Link className="home__card-link" to={m.ruta ?? '/materiales'}>
                  <span className="home__card-cab">
                    <span className="home__card-rf">{m.rf}</span>
                  </span>
                  <span className="home__card-nombre">{m.nombre}</span>
                  <span className="home__card-descripcion">{m.descripcion}</span>
                  <span className="home__card-ir">
                    <span className="home__card-disponible">Disponible</span> →
                  </span>
                </Link>
              </motion.div>
            ) : (
              <motion.div
                key={m.codigo}
                className="home__card home__card--prox"
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.4, ease: EASE, delay: 0.2 + i * 0.04 }}
                whileHover={{ y: -3 }}
              >
                <div className="home__card-cab">
                  <span className="home__card-rf">{m.rf}</span>
                  <span className="home__card-pill">Próximamente</span>
                </div>
                <span className="home__card-nombre">{m.nombre}</span>
                <span className="home__card-descripcion">{m.descripcion}</span>
              </motion.div>
            ),
          )}
        </div>
      </section>

      <footer className="home__pie">
        <span className="home__pie-marca">AGREGADOS EL TONUSCO S.A.S.</span>
        <span className="home__pie-note">Sistema de inventario de materiales de construcción.</span>
      </footer>
    </div>
  )
}