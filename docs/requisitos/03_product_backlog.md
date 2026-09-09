# TONUAPP — Product Backlog

## Épicas e historias de usuario

### EP-01 Gestión de usuarios
- US-01 (Alta) — Como administrador, quiero registrar usuarios para permitirles acceder al sistema.
- US-02 (Media) — Como administrador, quiero consultar los usuarios registrados.
- US-03 (Media) — Como administrador, quiero modificar la información de un usuario.
- US-04 (Media) — Como administrador, quiero eliminar usuarios cuando ya no deban tener acceso.
- US-05 (Alta) — Como usuario registrado, quiero iniciar sesión con correo + código de verificación
  temporal, según mi rol.
- US-06 (Media) — Como usuario, quiero cerrar sesión de forma segura.
- US-07 (Media) — Como usuario, quiero cambiar mi contraseña.
- US-08 (Alta) — Como administrador, quiero gestionar roles.
- US-09 (Alta) — Como administrador, quiero controlar permisos según el rol.

### EP-02 Gestión de materiales
- US-10 (Alta) — Registrar materiales al inventario.
- US-11 (Alta) — Consultar materiales registrados.
- US-12 (Alta) — Actualizar información de materiales.
- US-13 (Media) — Eliminar registros de materiales.
- US-14 (Alta) — Buscar materiales por nombre, tipo o cantidad.
- US-15 (Alta) — Consultar disponibilidad en tiempo real.
- US-16 (Media) — Registrar y modificar ubicaciones de materiales.
- US-17 (Media) — Controlar lotes de acopio asociados a materiales.

### EP-03 Movimientos de inventario
- US-18 (Alta) — Registrar entradas de materiales.
- US-19 (Alta) — Registrar salidas por ventas o despachos.
- US-20 (Alta) — Consultar movimientos de inventario.
- US-21 (Media) — Actualizar un movimiento con error de registro.
- US-22 (Media) — Anular o eliminar movimientos.
- US-23 (Alta) — Consultar historial de movimientos.

### EP-04 Control de inventario
- US-24 (Alta) — Verificar material recibido antes de ingresarlo oficialmente.
- US-25 (Alta) — Ajustar y conciliar inventario tras auditoría física.
- US-26 (Alta) — Recibir alertas de baja disponibilidad.
- US-27 (Media) — Registrar salidas por mermas y desperdicios.

### EP-05 Gestión de proveedores
- US-28 (Media) — Registrar proveedores.
- US-29 (Media) — Consultar proveedores.
- US-30 (Media) — Actualizar información de proveedor.
- US-31 (Media) — Eliminar proveedores sin afectar registros relacionados.

### EP-06 Reportes
- US-32 (Media) — Consultar reportes de inventario.
- US-33 (Baja) — Exportar reportes.

### EP-07 Interfaz y experiencia de usuario
- US-34 (Media) — Interfaz clara y adaptable a cualquier dispositivo.
- US-35 (Media) — Mensajes de confirmación y advertencia.
- US-36 (Media) — Configurar preferencias personales (panel de inicio, filtros favoritos, orden).

## Tareas técnicas base por historia
Frontend (UI: formularios, tablas, búsquedas, botones, mensajes) · Backend (lógica, validaciones, reglas
de negocio, API) · Base de datos (tablas, relaciones, consultas, persistencia) · Pruebas (casos de
prueba, validaciones, corrección de errores).

## Propuesta de sprints (referencia, no vinculante para el plan de fases del agente)
| Sprint | Enfoque | Historias | Resultado esperado |
|---|---|---|---|
| 1 | Base del sistema y usuarios | US-01 a US-09 | Autenticación, usuarios, roles, permisos, BD inicial |
| 2 | Gestión de materiales | US-10 a US-17 | CRUD materiales, búsqueda, disponibilidad, ubicaciones, lotes |
| 3 | Movimientos y control | US-18 a US-27 | Entradas, salidas, historial, verificación, ajustes, alertas, mermas |
| 4 | Proveedores, reportes y cierre | US-28 a US-36 | Proveedores, reportes, exportación, UI, pruebas finales |

## Criterios generales de "historia terminada"
- Interfaz implementada y funcional.
- Conectada correctamente con backend y base de datos.
- Validaciones y restricciones definidas aplicadas.
- Mensajes de confirmación/advertencia correctos.
- Pruebas realizadas y errores corregidos.
- Acceso respeta el rol y permisos del usuario.
