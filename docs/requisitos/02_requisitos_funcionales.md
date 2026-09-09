# TONUAPP — Requisitos Funcionales (RF-001 a RF-015)

Fuente: PPI_mayo.docx, sección 5.1. Cada requisito indica prioridad, entradas, condiciones,
restricciones, criterios de aceptación y trazabilidad al caso de uso correspondiente.

## Necesidades de los stakeholders (resumen)

| Código | Necesidad | Stakeholder | Funcionalidad asociada |
|---|---|---|---|
| N001 | Consultar inventario en tiempo real | Administrador | RF-001 |
| N002 | Registrar entradas de materiales | Encargado de almacén | RF-002 |
| N003 | Registrar salidas por ventas/despachos | Vendedor | RF-004 |
| N004 | Alertas automáticas por bajo stock | Administrador (Gerencia) | RF-012 |
| N005 | Buscar materiales por nombre/tipo | Cliente / Vendedor | RF-003 |
| N006 | Generar reportes de inventario | Gerente | RF-014 |
| N007 | Control de acceso por usuarios y roles | Administrador | RF-006, RF-007, RF-009 |
| N008 | Historial de movimientos de inventario | Administrador | (implícito en movimientos) |

> **Nota de inconsistencia (déjala para que el agente la registre formalmente):** los stakeholders
> mencionan roles operativos como "Encargado de almacén", "Vendedor" y "Gerente", pero RF-006 dice
> explícitamente que **solo se pueden asignar los roles Administrador y Cliente**. Ver sección de
> inconsistencias en AGENTS.md.

## RF-001 — Consulta en tiempo real de disponibilidad de materiales
- Prioridad: Alta. Grupo: Gestión de inventarios.
- Entradas: nombre, tipo o categoría del material.
- Restricciones: el cliente solo consulta (no modifica); máximo dos filtros simultáneos.
- Salida: listado de materiales con nombre, tipo, cantidad disponible y estado.
- CU asociado: CU-001 Consultar inventario.

## RF-002 — Registro y actualización de inventario por el administrador
- Prioridad: Alta. Grupo: Gestión de inventario.
- Entradas: nombre, tipo, cantidad, proveedor, fecha de ingreso, datos actualizados.
- Restricciones: cantidad > 0; no se permiten materiales duplicados (mismo nombre + tipo); solo rol
  administrador puede registrar/actualizar.
- CU asociado: CU-002 Registrar/Actualizar inventario (flujo detallado abajo).

## RF-003 — Búsqueda de materiales por nombre, tipo o cantidad
- Prioridad: Media.
- Restricciones: máximo dos filtros simultáneos; no búsquedas vacías; solo materiales activos.
- CU asociado: CU-003 Buscar materiales.

## RF-004 — Edición y eliminación de registros de inventario
- Prioridad: Alta.
- Restricciones: no se pueden eliminar materiales con movimientos registrados; solo administrador edita
  o elimina; el sistema debe conservar trazabilidad de los cambios.
- CU asociado: CU-004 Editar/Eliminar inventario.

## RF-005 — Preferencias personales de visualización y acceso rápido
- Prioridad: Media.
- Restricciones: las preferencias no modifican inventario; solo se guardan asociadas al usuario
  autenticado.
- CU asociado: CU-005 Configurar preferencias.

## RF-006 — Gestión de usuarios con roles
- Prioridad: Alta. Grupo: Administración y seguridad.
- Restricciones: no se elimina la cuenta del administrador principal; correo único en el sistema; **solo
  se pueden asignar los roles Administrador y Cliente**; password con criterios mínimos de seguridad.
- CU asociado: CU-006 Gestionar usuarios y roles.

## RF-007 — Acceso multiplataforma y personalización de visualización
- Prioridad: Media.
- Interfaz adaptada a computador/tablet/móvil; accesos directos según rol.

## RF-008 — Autenticación segura de usuarios
- Prioridad: Alta.
- Mecanismo: login por correo + código de acceso temporal enviado al email.
- Restricciones: código válido máx. 10 minutos; un solo uso; bloqueo temporal tras varios intentos
  fallidos.
- CU asociado: CU-009 Autenticar usuario (flujo detallado abajo).

## RF-009 — Control de permisos según rol de usuario
- Prioridad: Alta.
- Administrador: acceso completo. Cliente: solo consulta y solicitudes, sin modificar inventario ni
  configuración. Un usuario no puede cambiar su propio rol.

## RF-010 — Registro y gestión de proveedores
- Prioridad: Media.
- Restricciones: no eliminar proveedores con materiales asociados; NIT único; solo se modifican datos,
  no el identificador único.
- CU asociado: CU-011 Gestionar proveedores.

## RF-011 — Ajuste y conciliación de inventario
- Prioridad: Alta. Grupo: Control Operativo de Inventarios.
- Entradas: ID del material, cantidad física real contada, motivo del ajuste.
- Restricciones: no se permiten ajustes que resulten en inventario negativo.
- Criterios de aceptación: el stock se actualiza inmediatamente y se genera un **log de auditoría con
  fecha y autor**.

## RF-012 — Generación de alertas por baja disponibilidad
- Prioridad: Alta.
- Se activa automáticamente tras un registro de salida; alerta visible en panel del administrador
  (ej. color rojo al llegar al límite).

## RF-013 — Gestión de salidas por mermas y desperdicios
- Prioridad: Media.
- Entradas: tipo de material, volumen perdido, causa (clima, derrame, transporte).
- Restricción: el volumen de merma no puede superar el stock actual disponible.
- Salida: registro de salida por merma, clasificado como tal en reportes de movimiento.

## RF-014 — Exportación de reportes
- Prioridad: Media.
- Entradas: rango de fechas, tipo de reporte, formato (.pdf / .xlsx).
- Restricciones: solo rol Administrador o Gerente; PDF de solo lectura.
- Salida: archivo descargable en el formato seleccionado, con logo de la empresa.

## RF-015 — Control de ubicaciones y lotes de acopio
- Prioridad: Baja.
- Entradas: ID de zona de acopio, capacidad máxima de la zona.
- Restricción: una zona no puede asignarse a materiales incompatibles simultáneamente.
- Salida: indicador de ubicación visible en pantalla de consulta.

---

## Casos de uso documentados con flujo completo

### CU-002 — Registrar y actualizar inventario (RF-002)
Iniciador: Administrador. Precondición: sesión iniciada, permisos de gestión de inventario.
1. Administrador selecciona "Registrar/Actualizar Inventario".
2. Sistema muestra formulario.
3. Administrador ingresa datos del material.
4. Sistema valida los datos.
5. Sistema verifica que el material no exista previamente.
6. Sistema guarda en base de datos.
7. Sistema actualiza el inventario en tiempo real.
8. Sistema muestra mensaje de confirmación.
- Alternativo 1 (paso 4): campos vacíos/incorrectos → mensaje de error, solicita corrección.
- Alternativo 2 (paso 5): material ya existe → informa que debe actualizarse el registro existente.
- Poscondición: material registrado o actualizado correctamente.

### CU-008/CU-009 — Autenticación segura (RF-008)
Iniciador: Usuario registrado. Otro actor: Sistema de correo.
1. Usuario ingresa usuario/correo.
2. Sistema verifica credenciales.
3. Sistema genera código temporal.
4. Sistema envía código al correo registrado.
5. Usuario ingresa el código recibido.
6. Sistema valida el código temporal.
7. Sistema autoriza el acceso.
8. Sistema muestra mensaje de autenticación exitosa.
- Alternativo 1 (paso 2): credenciales incorrectas → acceso denegado.
- Alternativo 2 (paso 6): código incorrecto o expirado → solicita generar uno nuevo.
- Poscondición: usuario accede de forma segura al sistema.

### CU-014 — Exportación de reportes (RF-014)
Iniciador: Administrador o Gerente.
1. Selecciona "Exportar Reportes".
2. Sistema muestra opciones de exportación.
3. Usuario selecciona rango de fechas y formato (PDF o Excel).
4. Sistema valida los parámetros.
5-7. Sistema genera el reporte, procesa la información, crea el archivo en el formato seleccionado.
8. Sistema habilita la descarga.
- Alternativo 1 (paso 4): fechas inválidas → mensaje de error.
- Alternativo 2 (paso 6): sin registros en el período → informa que no hay datos disponibles.
- Poscondición: usuario obtiene el archivo con la información solicitada.
