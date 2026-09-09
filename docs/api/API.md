# TONUAPP — API REST

Documentación de los endpoints expuestos por el backend Spring Boot. Formato de error
consistente en toda la API:

```json
{ "timestamp": "...", "status": 400, "error": "Bad Request", "message": "...", "path": "/..." }
```

Autenticación mediante `Authorization: Bearer <accessToken>`. Solo los accesos de lectura
de materiales y catálogos están disponibles para cualquier usuario autenticado; las
mutaciones sobre materiales requieren el rol `Administrador`.

El filtro JWT es stateless: NO se valida contra la BD el estado de la cuenta en cada
petición. Si una cuenta se bloquea o desactiva, un access token ya emitido sigue
aceptándose hasta su expiración (15 min, ventana residual aceptada y documentada en
D-23); el bloqueo/desactivación surte efecto a más tardar en el refresco siguiente.

## Auth — `POST /api/auth/*`

| Endpoint | Descripción | Resultado |
|---|---|---|
| `POST /api/auth/solicitar-codigo` | Envía un código temporal de acceso al correo (10 min, 1 uso). | 200 |
| `POST /api/auth/verificar` | Valida correo + código y emite token. | 200 tokens (access 15 min, refresh 7 días) |
| `POST /api/auth/renovar-token` | Renueva el access token con el refresh token. | 200 |
| `POST /api/auth/cerrar-sesion` | Revoca el refresh token (validez de 1 intento). | 204 |

Los códigos se guardan con hash PBKDF2; no hay contraseñas en el sistema (decisión D-07 /
inconsistencia #2 resuelta en `docs/decisions/DECISIONES.md`).

## Catálogos — solo lectura (any authenticated)

| Endpoint | Descripción | Resultado |
|---|---|---|
| `GET /api/categorias` | Lista categorías activas. | 200 `[CategoriaResponse]` |
| `GET /api/unidades-medida` | Lista unidades de medida. | 200 `[UnidadMedidaResponse]` |

`CategoriaResponse`: `{ idCategoria, nombre, activo }` ·
`UnidadMedidaResponse`: `{ idUnidad, nombre, abreviatura }`

## Materiales — RF-001 a RF-004 (mutaciones solo Administrador)

| Endpoint | Descripción | Resultado |
|---|---|---|
| `GET /api/materiales?page=&size=` | Lista **paginada** de materiales activos ordenados por nombre. `page` (default 0) y `size` (default 20, rango **1..100**, fuera de rango → 400, `page<0` → 400). | 200 `PagedResponse<MaterialResponse>` |

`PagedResponse<T>`: `{ content: T[], totalElements, totalPages, page, size }` — envoltorio
genérico de paginación para los listados (materiales y movimientos).
| `GET /api/materiales/buscar?nombre=&categoria=&page=&size=` | Búsqueda por nombre (contiene) y/o categoría; exige al menos un filtro. Paginada igual que el listado (`page`/`size`, `size` 1..100). | 200 `PagedResponse<MaterialResponse>` / **400** sin filtros o `size`/`page` inválidos |
| `GET /api/materiales/{id}` | Detalle de un material activo. | 200 / **404** |
| `POST /api/materiales` | Crea material (nombre + categoría + unidad + stock inicial). | **201** / 400 validación / **409** duplicado (nombre+categoría) / **404** categoría o unidad inexistente |
| `PUT /api/materiales/{id}` | Actualiza material. | 200 / **404** / **409** duplicado |
| `DELETE /api/materiales/{id}` | Soft delete (desactiva). | **204** / 404 |

`MaterialRequest`:
```json
{ "nombre": "Arena de rio", "idCategoria": 1, "idUnidad": 3, "stock": 10.00, "stockMinimo": 5.00 }
```

`MaterialResponse`: `{ idMaterial, nombre, idCategoria, categoriaNombre, idUnidad,
unidadNombre, unidadAbreviatura, stock, stockMinimo, activo, fechaRegistro,
fechaActualizacion }`

Errores adicionales: **403** si un `Cliente` intenta mutar; **415** content type no
soportado; **401** token ausente/inválido/revocado.

## Proveedores — RF-010 (mutaciones solo Administrador)

| Endpoint | Descripción | Resultado |
|---|---|---|
| `GET /api/proveedores?q=&page=&size=` | Lista **paginada** de proveedores activos con `cantidadMateriales`. `q` opcional filtra por **nombre o NIT** (contiene, insensible a mayúsculas) — decisión de UX por consistencia con la búsqueda de materiales (no exigida por RF-010/US-29). `page` default 0, `size` default 20 (rango **1..100**). | 200 `PagedResponse<ProveedorResponse>` |
| `GET /api/proveedores/{id}` | Detalle de un proveedor activo. | 200 / **404** |
| `GET /api/proveedores/{id}/materiales` | Materiales asociados al proveedor (N:M). | 200 `[MaterialProveedorResponse]` |
| `GET /api/materiales/{id}/proveedores` | Proveedores asociados a un material (N:M). | 200 / **404** (material inactivo) |
| `POST /api/proveedores` | Crea proveedor. | **201** / 400 validación / **409** NIT duplicado |
| `PUT /api/proveedores/{id}` | Actualiza proveedor. | 200 / 404 / 409 |
| `DELETE /api/proveedores/{id}` | Soft delete (desactiva). | **204** / **409** si tiene materiales (RF-010) |
| `POST /api/proveedores/{id}/materiales` | Asocia un material al proveedor; con `esPrincipal:true` desmarca el principal anterior de ese material. | **201** / 404 / **409** ya asociado |
| `PUT /api/proveedores/{id}/materiales/{idMaterial}?principal=true` | Marca/desmarca el material como principal del proveedor. | 200 / 404 |
| `DELETE /api/proveedores/{id}/materiales/{idMaterial}` | Quita la asociación N:M. | **204** / 404 |

`ProveedorRequest`: `{ nombre, nit, contacto?, telefono?, ubicacion? }`
`ProveedorResponse`: `{ idProveedor, nombre, nit, contacto, telefono, ubicacion, activo,
fechaRegistro, cantidadMateriales }`
`MaterialProveedorResponse`: `{ idMaterial, nombreMaterial, idProveedor, nombreProveedor,
esPrincipal, fechaAsociacion }`

## Ubicaciones y lotes — RF-015 / US-16 / US-17 (mutaciones solo Administrador, D-34)

| Endpoint | Descripción | Resultado |
|---|---|---|
| `GET /api/zonas-acopio?q=&page=&size=` | Lista **paginada** de zonas activas con `cantidadMateriales`. `q` opcional filtra por **nombre o tipo permitido**. `page` default 0, `size` default 20 (rango **1..100**). | 200 `PagedResponse<ZonaAcopioResponse>` |
| `GET /api/zonas-acopio/{id}` | Detalle de una zona activa. | 200 / **404** |
| `GET /api/zonas-acopio/{id}/materiales` | Materiales activos asignados a la zona (indicador de ubicación RF-015). | 200 `[MaterialResponse]` |
| `POST /api/zonas-acopio` | Crea zona (nombre + capacidad + tipo permitido opcional). | **201** / 400 validación / **409** nombre duplicado |
| `PUT /api/zonas-acopio/{id}` | Actualiza zona; si cambia `tipoMaterialPermitido`, los materiales ya asignados deben seguir siendo compatibles. | 200 / 404 / **409** nombre duplicado o incompatibles |
| `DELETE /api/zonas-acopio/{id}` | Soft delete (desactiva). | **204** / **409** si tiene materiales asignados o movimientos |
| `POST /api/zonas-acopio/{id}/materiales/{idMaterial}` | Asigna un material a la zona (escribe `materiales.id_zona`). | 200 `MaterialResponse` / 404 / **409** incompatible con el tipo o ya asignado |
| `DELETE /api/zonas-acopio/{id}/materiales/{idMaterial}` | Desasigna el material de la zona. | 200 `MaterialResponse` / 404 / **409** no estaba asignado |
| `GET /api/lotes?idMaterial=&q=&page=&size=` | Lista **paginada** de lotes activos; `idMaterial` restringe por material, `q` por **código**. | 200 `PagedResponse<LoteResponse>` |
| `GET /api/lotes/{id}` | Detalle de un lote activo. | 200 / **404** |
| `GET /api/lotes/por-material/{idMaterial}` | Lotes activos de un material, más recientes primero. | 200 `[LoteResponse]` / 404 |
| `POST /api/lotes` | Crea lote (material + código + cantidad informativa). | **201** / 400 / **409** código duplicado / 404 material |
| `PUT /api/lotes/{id}` | Actualiza código/cantidad; **el material es inmutable** (trazabilidad). | 200 / 404 / **409** |
| `DELETE /api/lotes/{id}` | Soft delete (desactiva). | **204** / **409** si está referenciado por movimientos |

`ZonaAcopioRequest`: `{ nombreZona, capacidadMaxima, tipoMaterialPermitido? }`
`ZonaAcopioResponse`: `{ idZona, nombreZona, capacidadMaxima, tipoMaterialPermitido, activo,
cantidadMateriales }`
`LoteRequest`: `{ idMaterial, codigoLote, cantidad }`
`LoteResponse`: `{ idLote, idMaterial, materialNombre, codigoLote, cantidad, fechaIngreso, activo }`
`MaterialResponse` ahora incluye `idZona`/`zonaNombre` (nulos si el material no tiene zona).

## Movimientos de inventario — RF-004/RF-011/RF-013 (solo Administrador, D-18)

| Endpoint | Descripción | Resultado |
|---|---|---|
| `GET /api/movimientos?page=&size=&idMaterial=&tipo=&estado=&desde=&hasta=` | Historial **paginado** (más reciente primero). Filtros opcionales: `idMaterial`, `tipo` (`entrada`/`salida_venta`/`salida_merma`/`ajuste`), `estado` (`activo`/`anulado`), `desde`/`hasta` (`YYYY-MM-DD`). `page` default 0, `size` default 20 (rango **1..100**). `desde`>`hasta`, `page`<0 o `size` fuera de rango → 400. | 200 `PagedResponse<MovimientoResponse>` |
| `POST /api/movimientos` | Entrada o salida (venta/merma); actualiza `materiales.stock` en la misma transacción. Cantidad > 0; signo según tipo. | **201** / 400 / 404 / **409** stock insuficiente |
| `POST /api/movimientos/ajuste` | Ajuste a stock objetivo (RF-011): body `{ idMaterial, cantidadNueva, motivo }`; guarda `\|diferencia\|` y fija el stock. | **201** / 400 (sin diferencia o negativa) / 404 |
| `POST /api/movimientos/{id}/anular` | Revierte el efecto de la entrada/salida y marca `estado=anulado`. Un **ajuste** se revierte a su `cantidad_anterior` desde `ajustes_inventario` (D-19); si falta el detalle → 409. | 200 / 404 / 409 |
| `GET /api/movimientos/ajustes` | Historial de conciliaciones (RF-011): anterior/nueva por ajuste, más reciente primero. | 200 `[AjusteResponse]` |

Reglas (D-02/D-03/D-18/D-19): `cantidad > 0` siempre (el efecto lo da el tipo); ningún
movimiento deja el stock en negativo; `entrada` suma y `salida_venta`/`salida_merma`
restan; `@Version` sobre `materiales` evita condiciones de carrera (el perdedor de dos
operaciones concurrentes recibe **409 "Conflicto de concurrencia"**); cada movimiento se
audita con `MOVEMENT`/`ADJUST`/`UPDATE`.

`MovimientoRequest`: `{ idMaterial, tipo, cantidad, motivo?, observaciones?, idLote?, idZona? }`
(`tipo` ∈ `entrada | salida_venta | salida_merma | ajuste`, pero `ajuste` va por `/ajuste`).
`MovimientoResponse`: `{ idMovimiento, idMaterial, nombreMaterial, idUsuario,
nombreUsuario, tipo, cantidad, estado, motivo, observaciones, idLote, idZona,
fechaMovimiento }`
`AjusteRequest`: `{ idMaterial, cantidadNueva, motivo }`
`AjusteResponse`: `{ idAjuste, idMaterial, nombreMaterial, idUsuario, nombreUsuario,
idMovimiento, cantidadAnterior, cantidadNueva, motivo, fechaAjuste }`

## Alertas de bajo stock — RF-012 (solo Administrador, D-20)

| Endpoint | Descripción | Resultado |
|---|---|---|
| `GET /api/alertas` | Historial (más reciente primero). Filtros: `?estado=activa\|atendida`, `?idMaterial=`. | 200 `[AlertaResponse]` |
| `POST /api/alertas/{id}/atender` | Cierra manualmente una alerta activa (D-20: sin auto-resolución). Quién/cuándo queda en `audit_log`. | 200 / 404 / 409 |

Generación (RF-012): tras una entrada/salida/merma, ajuste o anulación que deje el stock
bajo `stock_minimo`, en la misma transacción; **una solo alerta activa por material**
(no se duplica si sigue bajo). Se registra `CREATE` en `audit_log` solo cuando se crea.

`AlertaResponse`: `{ idAlerta, idMaterial, nombreMaterial, estado, mensaje, fechaGenerada }`

## Reportes — RF-014 (solo Administrador, D-21)

Consultas JSON (base de las tablas del frontend):

| Endpoint | Descripción |
|---|---|
| `GET /api/reportes/inventario` | Stock actual por material activo (con flag de alerta activa). |
| `GET /api/reportes/bajo-stock` | Solo activos bajo el mínimo, del más crítico al menos. |
| `GET /api/reportes/movimientos?desde=&hasta=` | Historial de movimientos del rango (incluye anulados). Rango opcional: ambas o ninguna; `desde>hasta` o una sola fecha → 400. |
| `GET /api/reportes/resumen?desde=&hasta=` | Totales por material en el rango (entradas, salidas venta/merma, ajuste neto, efecto neto, stock actual). Solo movimientos activos; el delta del ajuste sale de `ajustes_inventario` (D-19). |
| `GET /api/reportes/bitacora` | Historial de exportaciones (quién, cuándo, tipo, formato, rango, archivo). |
| `GET /api/reportes/{id}/descargar` | Re-descarga el archivo generado (ruta validada anti path traversal). |

Exportación:

| Endpoint | Descripción | Resultado |
|---|---|---|
| `POST /api/reportes/exportar` | Body `{ tipo: inventario\|movimientos\|resumen\|bajo_stock, formato: pdf\|xlsx, desde?, hasta? }`. Genera el archivo, lo guarda en disco, registra la bitácora y lo devuelve (Content-Disposition attachment + header `X-Reporte-Id`). | 200 binario / 400 (formato o rango inválidos) / 404 (sin datos en el período) |

Fuente de datos: tablas de negocio, nunca `audit_log` (D-06). `ReporteRequest`:
`{ tipo, formato, desde?, hasta? }`. Filas: `ReporteInventarioRow`, `ReporteResumenRow`
(ver DTO en `docs/api`).

## Auditoría — RF-011/N007 (solo Administrador, D-22)

| Endpoint | Descripción |
|---|---|
| `GET /api/auditoria` | Log de auditoría ordenado desc por fecha: quién (nombre/correo), qué acción, sobre qué registro, cuándo, valores antes/después. Filtros opcionales: `entidad`, `operacion`, `idUsuario`, `desde`, `hasta` (formato `YYYY-MM-DD`). `size` opcional (default **100**, rango **1..500**, fuera de rango → 400): la lista llega siempre acotada (D-23). `desde > hasta` → 400. |

Ejemplo: `GET /api/auditoria?entidad=materiales&operacion=CREATE&desde=2026-09-01`

## Trazabilidad

| Requisito | Endpoint | Servicio |
|---|---|---|
| RF-001 (consulta en tiempo real) | `GET /api/materiales`, `/buscar` | `MaterialService.listarActivos` / `buscar` |
| RF-002 (registro sin duplicados) | `POST /api/materiales` | `MaterialService.crear` (validación + `uq_materiales_nombre_categoria`) |
| RF-003 (búsqueda/edición) | `GET/PUT /api/materiales` | `MaterialService.obtener/actualizar` |
| RF-004 (no eliminar con movimientos) | `DELETE /api/materiales/{id}` | `MaterialService.desactivar` (soft delete; 409 si tiene movimientos) |
| RF-010 (proveedores con NIT único) | `/api/proveedores*`, `/api/materiales/{id}/proveedores` | `ProveedorService` (CRUD + N:M + `es_principal`) |
| RF-008 (autenticación segura) | `POST /api/auth/solicitar-codigo`, `/verificar`, `/renovar-token`, `/cerrar-sesion` | `AuthService` (código hasheado 10 min + 1 uso; bloqueo por código y por cuenta en `FallosLoginService` REQUIRES_NEW, D-22) |
| RF-011 (ajuste/conciliación) | `POST /api/movimientos/ajuste`, `GET /api/movimientos/ajustes`, `POST /api/movimientos/{id}/anular` (ajuste) | `MovimientoService.registrarAjuste`/`listarAjustes`/`anular` (stock objetivo + `ajustes_inventario`, D-18/D-19) |
| RF-013 (mermas ≤ stock) | `POST /api/movimientos` | `MovimientoService.registrarMovimiento` (409 si supera el stock) |
| RF-012 (alertas de baja disponibilidad) | `GET /api/alertas`, `POST /api/alertas/{id}/atender` | `AlertaService` (generación en misma tx + cierre manual, D-20) |
| RF-014 (exportación de reportes) | `GET /api/reportes/*`, `POST /api/reportes/exportar`, `GET /api/reportes/bitacora`, `GET /api/reportes/{id}/descargar` | `ReporteService` + `ReporteExporter` (POI/OpenPDF, D-21) |
| N007 (auditoría: quién/hace/qué/cuándo) | `GET /api/auditoria` | `AuditoriaService` + `AuditAspect`/`AuditService` (D-22) |
| RF-004 (no eliminar con movimientos) | `DELETE /api/materiales/{id}` | `MovimientoInventarioRepository.countByMaterial_IdMaterial` |
| RF-015 (ubicaciones y lotes) | `/api/zonas-acopio*`, `/api/lotes*` (D-34) | `ZonaAcopioService` (CRUD + asignación/`tipo_material_permitido`) y `LoteService` (CRUD, cantidad informativa D-02) |

## Trazabilidad frontend (Fase 11, pantalla Materiales)

| Requisito | Pantalla / componente | Consume |
|---|---|---|
| RF-001 (consulta en tiempo real) | `MaterialesPage` (lista paginada + detalle) | `materialService.listar/buscar/obtener` |
| RF-002 (registro sin duplicados) | `MaterialFormModal` (crear, solo Admin) | `materialService.crear` |
| RF-003 (búsqueda/edición) | `MaterialesPage` (búsqueda por nombre/categoría) + `MaterialFormModal` (editar) | `materialService.buscar/actualizar` |
| RF-004 (no eliminar con movimientos) | `ConfirmarEliminarModal` (desactivar, solo Admin; 409 del backend) | `materialService.desactivar` |
| RF-009 (permisos por rol) | `AppLayout` + `useAuth` (`rol`), botones de mutación solo `Administrador` | sesión JWT (http.ts) |
| RF-010 (proveedores N:M) | `MaterialDetalleModal` (lista `esPrincipal`) | `materialService.listarProveedores` |
| D-25 (modal como patrón) | `Modal` (detalle/form/confirmar superpuestos) | — |

## Trazabilidad frontend (Fase 11, pantalla Ubicaciones y lotes)

| Requisito | Pantalla / componente | Consume |
|---|---|---|
| RF-015 (zonas de acopio + indicador) | `UbicacionesPage` (pestaña zonas, lista paginada) + `ZonaFormModal` (crear/editar con tipo permitido) + `ConfirmarDesactivarZonaModal` (bloqueado si `cantidadMateriales>0`) | `ubicacionService.listarZonas/crearZona/actualizarZona/desactivarZona` |
| RF-015 (asignación/compatibilidad) | `GestionarMaterialesZonaModal` (select con incompatibles deshabilitados + aviso "Solo admite la categoría…") | `ubicacionService.listarMaterialesZona/asignarMaterialZona/desasignarMaterialZona` |
| US-17 (lotes) | `UbicacionesPage` (pestaña lotes) + `LoteFormModal` (cantidad informativa D-02, material inmutable) | `ubicacionService.listarLotes/crearLote/actualizarLote/desactivarLote` |
| RF-001 (consulta en tiempo real) | `ZonaDetalleModal` (materiales de la zona con stock) + indicador `idZona/zonaNombre` en `MaterialResponse` | `ubicacionService.listarMaterialesZona` |
| RF-009 (permisos por rol) | `AppLayout` + `useAuth` (`rol`); botones/`＋` de mutación solo `Administrador` | sesión JWT (http.ts) |
| D-32 (patrón de pantalla) | pestanas Zonas/Lotes, servicios centralizados, reutiliza `materiales.css` | — |

## Trazabilidad frontend (Fase 12, pantalla Movimientos)

| Requisito | Pantalla / componente | Consume |
|---|---|---|
| RF-011 (ajustes a stock objetivo) | `MovimientosPage` (modal Ajuste: cantidad contada = stock objetivo + motivo) | `movimientoService.ajustar` |
| RF-013 (mermas ≤ stock; clasificación) | `MovimientosPage` (form registrar con tipos entrada/salida venta/merma; 409 del backend) | `movimientoService.registrar` |
| RF-011 (histórico/auditoría) | `MovimientosPage` (histórico paginado con filtros material/tipo/estado/fecha + modal detalle) | `movimientoService.listar` |
| RF-004/RF-011 (anulación reversible) | `MovimientosPage` (modal Anular con confirmación; revierte el stock) | `movimientoService.anular` |
| RF-015/RF-011 (ubicación opcional) | `MovimientosPage` (plegable Ubicación: lote + zona, opcionales) | `ubicacionService.listarLotesDeMaterial/listarZonas` |
| RF-009 (permisos por rol) | Ruta `/movimientos` solo `Administrador` en `AppLayout` + aviso en la pantalla | sesión JWT (http.ts) |

## Trazabilidad frontend (Fase 12, pantalla Alertas — RF-012 / D-20)

| Requisito | Pantalla / componente | Consume |
|---|---|---|
| RF-012 (aviso de baja disponibilidad) | `AlertasPage` (lista de avisos con material, estado y fecha, filtro por estado) | `alertaService.listar` |
| RF-012 (cierre manual documentado D-20) | `AlertasPage` (botón "Marcar atendida" solo en activas) | `alertaService.atender` |
| RF-009 (permisos por rol) | Ruta `/alertas` solo `Administrador` en `AppLayout` + aviso en la pantalla | sesión JWT (http.ts) |

## Trazabilidad frontend (Fase 12, pantalla Reportes — RF-014 / D-21)

| Requisito | Pantalla / componente | Consume |
|---|---|---|
| RF-001 (stock en tiempo real) | `ReportesPage` (pestaña Inventario: todos los materiales activos) | `reporteService.inventario` |
| RF-012 (bajo stock) | `ReportesPage` (pestaña Bajo stock: crítico primero) | `reporteService.bajoStock` |
| RF-014 (historial por rango) | `ReportesPage` (pestaña Movimientos: rango inicio/fin o ninguno, validación calco del service) | `reporteService.movimientos` |
| RF-013/RF-004 (totales por material) | `ReportesPage` (pestaña Resumen: entradas/ventas/mermas/ajuste neto/efecto neto/stock) | `reporteService.resumen` |
| RF-014 (exportación pdf/xlsx + bitácora) | `ReportesPage` (exportar descarga binario vía Content-Disposition; pestaña Bitácora re-descarga) | `reporteService.exportar/bitacora/descargar` |
| RF-009 (permisos por rol) | Ruta `/reportes` solo `Administrador` en `AppLayout` + aviso en la pantalla | sesión JWT (http.ts) |