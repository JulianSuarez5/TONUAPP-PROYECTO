// Layout de las pantallas autenticadas: header con la firma de TONUAPP (emblem +
// wordmark + escuadra de ingeniero, D-28), navegacion (Inicio/Materiales, D-30),
// el correo de la sesion y el cierre de sesion (RF-008). MotionConfig con
// reducedMotion="user" hace que framer-motion respete prefers-reduced-motion
// globalmente (regla D-25).
import { NavLink, Outlet } from 'react-router-dom'
import { MotionConfig } from 'framer-motion'
import { Escuadra } from '../components/Escuadra'
import { useAuth } from '../hooks/useAuth'

export function AppLayout() {
  const { usuario, cerrarSesion } = useAuth()

  return (
    <MotionConfig reducedMotion="user">
      <div className="app">
        <header className="app__header">
          <div className="app__brand">
            <div className="app__emblem" aria-hidden="true" />
            <span className="app__wordmark">TONUAPP</span>
            <Escuadra />
          </div>
          <nav className="app__nav" aria-label="Principal">
            <NavLink className="app__nav-link" to="/">
              Inicio
            </NavLink>
            <NavLink className="app__nav-link" to="/materiales">
              Materiales
            </NavLink>
            <NavLink className="app__nav-link" to="/proveedores">
              Proveedores
            </NavLink>
            <NavLink className="app__nav-link" to="/ubicaciones">
              Ubicaciones
            </NavLink>
            {usuario?.rol === 'Administrador' && (
              <NavLink className="app__nav-link" to="/movimientos">
                Movimientos
              </NavLink>
            )}
            {usuario?.rol === 'Administrador' && (
              <NavLink className="app__nav-link" to="/alertas">
                Alertas
              </NavLink>
            )}
            {usuario?.rol === 'Administrador' && (
              <NavLink className="app__nav-link" to="/reportes">
                Reportes
              </NavLink>
            )}
          </nav>
          <div className="app__session">
            <span className="app__correo">{usuario?.correo}</span>
            <button type="button" className="app__salir" onClick={() => void cerrarSesion()}>
              Cerrar sesión
            </button>
          </div>
        </header>
        <main className="app__contenido">
          <Outlet />
        </main>
      </div>
    </MotionConfig>
  )
}