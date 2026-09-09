// Rutas de la aplicacion. El login es la puerta de entrada (RF-008); las rutas de
// los modulos viven bajo AppLayout y exigen sesion (las protege RutaProtegida).
// "01" es el indicador visual de la posicion del modulo usada en la cabecera de
// cada pantalla (Materiales = 01).
import { BrowserRouter, Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { AppLayout } from '../layouts/AppLayout'
import { LoginPage } from '../pages/LoginPage'
import { HomePage } from '../pages/HomePage'
import { MaterialesPage } from '../pages/materiales/MaterialesPage'
import { ProveedoresPage } from '../pages/proveedores/ProveedoresPage'
import { UbicacionesPage } from '../pages/ubicaciones/UbicacionesPage'
import { MovimientosPage } from '../pages/movimientos/MovimientosPage'
import { AlertasPage } from '../pages/alertas/AlertasPage'
import { ReportesPage } from '../pages/reportes/ReportesPage'
import { PlaceholderPage } from '../pages/PlaceholderPage'

// Si no hay sesion, al login; si la hay, renderiza la ruta anidada
function RutaProtegida() {
  const { isAutenticado } = useAuth()
  if (!isAutenticado) {
    return <Navigate to="/login" replace />
  }
  return <Outlet />
}

export function AppRoutes() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<RutaProtegida />}>
          <Route element={<AppLayout />}>
            <Route index element={<HomePage />} />
            <Route path="materiales" element={<MaterialesPage />} />
            <Route path="proveedores" element={<ProveedoresPage />} />
            <Route path="ubicaciones" element={<UbicacionesPage />} />
            <Route path="movimientos" element={<MovimientosPage />} />
            <Route path="alertas" element={<AlertasPage />} />
            <Route path="reportes" element={<ReportesPage />} />
            <Route path="*" element={<PlaceholderPage titulo="No encontrada" />} />
          </Route>
        </Route>
      </Routes>
    </BrowserRouter>
  )
}