# TONUAPP — Base de Datos

## 1. Introducción

Este documento describe el diseño de la base de datos de **TONUAPP**, la aplicación web de
gestión de inventario de **Agregados el Tonusco S.A.S.**. Cubre el motor seleccionado,
las entidades, el diccionario de datos, las relaciones y cardinalidades, las reglas de
integridad, las decisiones de diseño y las inconsistencias pendientes.

## 2. Motor seleccionado y por qué

**Microsoft SQL Server** (probado en SQL Server Express 2019).

SQL Server es coherente con Spring Data JPA por varias razones:
- El driver oficial `mssql-jdbc` es de primera clase en el ecosistema Java/Spring.
- Los tipos de datos mapean de forma natural: `NVARCHAR` para cadenas Unicode,
  `IDENTITY(1,1)` como clave autoincremental, `DATETIME2` para fechas con alta precisión,
  `DECIMAL` para cantidades monetarias/de inventario.
- Spring Boot 3.x soporta SQL Server de forma nativa a través de Hibernate.
- La empresa ya corre SQL Server (instancia SQLEXPRESS), lo que elimina costos y reduce
  fricción de despliegue.

Las migraciones se gestionan con **Flyway** (decisión documentada en
`docs/decisions/DECISIONES.md`). Este script (`tonuapp_database.sql`) es la versión
completa y definitiva a partir de la cual Flyway aplicará las migraciones incrementales
(`V1__init.sql`, etc.) y sirve además como script de creación desde cero.

## 3. Entidades

| Tabla | Propósito |
|---|---|
| `roles` | Catálogo de roles del sistema. Flexible: se pueden agregar roles sin rehacer nada. |
| `usuarios` | Usuarios del sistema. Autenticación por correo + código temporal. |
| `codigos_acceso` | Histórico de códigos temporales para autenticación (vigencia 10 min, 1 uso). |
| `preferencias_usuario` | Preferencias de visualización por usuario (panel, filtros, orden, tema). |
| `proveedores` | Proveedores. NIT único. Soft delete. |
| `zonas_acopio` | Ubicaciones/lotes de acopio. Capacidad máxima y tipo de material permitido. |
| `categorias` | Catálogo de categorías de materiales (D-15). |
| `unidades_medida` | Catálogo de unidades de medida (D-16). |
| `materiales` | El núcleo del inventario. Existencia materializada + stock mínimo. |
| `material_proveedor` | Relación N:M entre materiales y proveedores. |
| `lotes` | Lotes de acopio asociados a un material. |
| `movimientos_inventario` | Fuente de verdad del inventario: entradas, salidas, mermas, ajustes. |
| `ajustes_inventario` | Detalle de ajustes y conciliaciones (antes/después, motivo, autor). |
| `alertas_inventario` | Alertas por baja disponibilidad, almacenadas permanentemente. |
| `reportes_generados` | Bitácora de exportaciones de reportes. |
| `audit_log` | Log de auditoría append-only (quién, qué, cuándo, sobre qué registro). |

## 4. Diccionario de datos

### 4.1 `roles`
| Campo | Tipo | PK | FK | NULL | Descripción |
|---|---|---|---|---|---|
| id_rol | INT IDENTITY | Sí | — | No | Identificador del rol. |
| nombre_rol | NVARCHAR(30) | | | No | Nombre del rol (único). |
| descripcion | NVARCHAR(150) | | | Sí | Descripción del rol. |

### 4.2 `usuarios`
| Campo | Tipo | PK | FK | NULL | Descripción |
|---|---|---|---|---|---|
| id_usuario | INT IDENTITY | Sí | — | No | Identificador del usuario. |
| nombre | NVARCHAR(100) | | | No | Nombre completo del usuario. |
| correo | NVARCHAR(150) | | | No | Correo electrónico (único, usado para autenticación). |
| id_rol | INT | | roles | No | Rol asignado. |
| activo | BIT | | | No | Soft delete (1 = activo, 0 = inactivo). Default 1. |
| es_admin_principal | BIT | | | No | Marca al admin principal (no se puede eliminar). Default 0. |
| fecha_creacion | DATETIME2(0) | | | No | Fecha de creación. Default SYSDATETIME(). |
| fecha_actualizacion | DATETIME2(0) | | | Sí | Fecha de última actualización. |
| intentos_login_fallidos | INT | | | No | Verificaciones fallidas consecutivas (bloqueo por cuenta, RF-008/D-22). Default 0; CHECK ≥ 0. |
| bloqueo_hasta | DATETIME2 | | | Sí | Fin del bloqueo temporal por cuenta; NULL = sin bloqueo (V6). |

### 4.3 `codigos_acceso`
| Campo | Tipo | PK | FK | NULL | Descripción |
|---|---|---|---|---|---|
| id_codigo | INT IDENTITY | Sí | — | No | Identificador del código. |
| id_usuario | INT | | usuarios | No | Usuario al que pertenece el código. CASCADE. |
| codigo | NVARCHAR(10) | | | No | Código temporal. |
| fecha_generacion | DATETIME2(0) | | | No | Momento de generación. |
| fecha_expiracion | DATETIME2(0) | | | No | Vigencia (máx. 10 min tras generación). |
| usado | BIT | | | No | Un solo uso (1 = ya usado). Default 0. |
| intentos_fallidos | INT | | | No | Contador para bloqueo temporal. Default 0. |

### 4.4 `preferencias_usuario`
| Campo | Tipo | PK | FK | NULL | Descripción |
|---|---|---|---|---|---|
| id_usuario | INT | Sí | usuarios | No | Usuario (relación 1:1). |
| panel_inicio | NVARCHAR(50) | | | No | Panel de inicio. Default 'dashboard'. |
| filtros_favoritos | NVARCHAR(MAX) | | | Sí | Filtros favoritos en JSON. |
| orden_default | NVARCHAR(30) | | | No | Orden por defecto. Default 'nombre_asc'. |
| tema_visual | NVARCHAR(20) | | | No | Tema visual. Default 'claro'. |

### 4.5 `proveedores`
| Campo | Tipo | PK | FK | NULL | Descripción |
|---|---|---|---|---|---|
| id_proveedor | INT IDENTITY | Sí | — | No | Identificador del proveedor. |
| nombre | NVARCHAR(150) | | | No | Razón social / nombre. |
| nit | NVARCHAR(20) | | | No | NIT (único). |
| contacto | NVARCHAR(100) | | | Sí | Persona de contacto. |
| telefono | NVARCHAR(20) | | | Sí | Teléfono. |
| ubicacion | NVARCHAR(200) | | | Sí | Ubicación. |
| activo | BIT | | | No | Soft delete. Default 1. |
| fecha_registro | DATETIME2(0) | | | No | Fecha de registro. |

### 4.6 `zonas_acopio`
| Campo | Tipo | PK | FK | NULL | Descripción |
|---|---|---|---|---|---|
| id_zona | INT IDENTITY | Sí | — | No | Identificador de la zona. |
| nombre_zona | NVARCHAR(100) | | | No | Nombre de la zona/lote. |
| capacidad_maxima | DECIMAL(12,2) | | | No | Capacidad máxima. CHECK >= 0. |
| tipo_material_permitido | NVARCHAR(100) | | | Sí | Incompatibilidad de materiales en la zona. |
| activo | BIT | | | No | Soft delete. Default 1. |

### 4.7 `categorias`
| Campo | Tipo | PK | FK | NULL | Descripción |
|---|---|---|---|---|---|
| id_categoria | INT IDENTITY | Sí | — | No | Identificador de la categoría. |
| nombre | NVARCHAR(100) | | | No | Nombre de la categoría (único). |
| activo | BIT | | | No | Soft delete. Default 1. |

Tabla catálogo (D-15): editable desde la aplicación sin migrar el esquema.

### 4.8 `unidades_medida`
| Campo | Tipo | PK | FK | NULL | Descripción |
|---|---|---|---|---|---|
| id_unidad | INT IDENTITY | Sí | — | No | Identificador de la unidad. |
| nombre | NVARCHAR(50) | | | No | Nombre de la unidad (único, ej. Metro cúbico). |
| abreviatura | NVARCHAR(10) | | | No | Abreviatura (ej. m3). |

Tabla catálogo (D-16): puebla dropdowns del frontend sin hardcodear valores.

### 4.9 `materiales`
| Campo | Tipo | PK | FK | NULL | Descripción |
|---|---|---|---|---|---|
| id_material | INT IDENTITY | Sí | — | No | Identificador del material. |
| nombre | NVARCHAR(100) | | | No | Nombre del material. |
| id_categoria | INT | | categorias | No | Categoría (D-15). |
| id_unidad | INT | | unidades_medida | No | Unidad de medida (D-16). |
| stock | DECIMAL(12,2) | | | No | Existencia materializada (D-17). CHECK >= 0. Default 0. |
| stock_minimo | DECIMAL(12,2) | | | No | Umbral de alerta. CHECK >= 0. Default 0. |
| id_zona | INT | | zonas_acopio | Sí | Zona asignada (opcional). |
| activo | BIT | | | No | Soft delete. Default 1. |
| fecha_registro | DATETIME2(0) | | | No | Fecha de registro. |
| fecha_actualizacion | DATETIME2(0) | | | Sí | Fecha de actualización. |
| version | INT | | | No | Optimistic locking `@Version` (D-03, migración V4). Default 0. |

Índices: `ix_materiales_busqueda (nombre)`, `ix_materiales_categoria (id_categoria)`,
`ix_materiales_unidad (id_unidad)`, `ix_materiales_zona (id_zona)`.
Restricción única: `(nombre, id_categoria)` — RF-002 no permite duplicados.
La columna `stock` materializada (D-17) se inicializa al crear el material y la Fase 6
(movimientos) la mantiene actualizada de forma atómica.

### 4.10 `material_proveedor`
| Campo | Tipo | PK | FK | NULL | Descripción |
|---|---|---|---|---|---|
| id_material | INT | Sí | materiales | No | Material. CASCADE. |
| id_proveedor | INT | Sí | proveedores | No | Proveedor. CASCADE. |
| es_principal | BIT | | | No | Proveedor principal (1/0). Default 0. |
| fecha_asociacion | DATETIME2(0) | | | No | Fecha de asociación. |

### 4.11 `lotes`
| Campo | Tipo | PK | FK | NULL | Descripción |
|---|---|---|---|---|---|
| id_lote | INT IDENTITY | Sí | — | No | Identificador del lote. |
| id_material | INT | | materiales | No | Material asociado. |
| codigo_lote | NVARCHAR(50) | | | No | Código del lote (único). |
| cantidad | DECIMAL(12,2) | | | No | Cantidad del lote. CHECK >= 0. |
| fecha_ingreso | DATETIME2(0) | | | No | Fecha de ingreso. |
| activo | BIT | | | No | Soft delete. Default 1. |

### 4.12 `movimientos_inventario`
| Campo | Tipo | PK | FK | NULL | Descripción |
|---|---|---|---|---|---|
| id_movimiento | INT IDENTITY | Sí | — | No | Identificador del movimiento. |
| id_material | INT | | materiales | No | Material afectado. |
| id_usuario | INT | | usuarios | No | Usuario que ejecuta el movimiento. |
| tipo_movimiento | NVARCHAR(20) | | | No | entrada/salida_venta/salida_merma/ajuste (CHECK). |
| cantidad | DECIMAL(12,2) | | | No | Cantidad (siempre positiva; CHECK > 0). |
| id_lote | INT | | lotes | Sí | Lote (opcional). |
| id_zona | INT | | zonas_acopio | Sí | Zona (opcional). |
| motivo | NVARCHAR(200) | | | Sí | Obligatorio para mermas/ajustes (causa). |
| observaciones | NVARCHAR(300) | | | Sí | Observaciones. |
| estado | NVARCHAR(10) | | | No | activo/anulado (CHECK). Anula sin borrar (US-22). Default 'activo'. |
| fecha_movimiento | DATETIME2(0) | | | No | Momento del movimiento. |

Índices: `ix_movimientos_material_fecha (id_material, fecha_movimiento)`,
`ix_movimientos_usuario (id_usuario)`, `ix_movimientos_tipo (tipo_movimiento)`.

### 4.13 `ajustes_inventario`
| Campo | Tipo | PK | FK | NULL | Descripción |
|---|---|---|---|---|---|
| id_ajuste | INT IDENTITY | Sí | — | No | Identificador del ajuste. |
| id_material | INT | | materiales | No | Material ajustado. |
| id_usuario | INT | | usuarios | No | Autor del ajuste. |
| id_movimiento | INT | | movimientos_inventario | Sí | Movimiento 'ajuste' que lo origina. 1:1 en la práctica (Fase 7, D-19): una fila por ajuste, usada para anularlo revirtiendo a `cantidad_anterior`. |
| cantidad_anterior | DECIMAL(12,2) | | | No | Cantidad antes. |
| cantidad_nueva | DECIMAL(12,2) | | | No | Cantidad después. CHECK >= 0 (sin stock negativo). |
| motivo | NVARCHAR(200) | | | No | Motivo del ajuste. |
| fecha_ajuste | DATETIME2(0) | | | No | Fecha del ajuste. |

### 4.14 `alertas_inventario`
| Campo | Tipo | PK | FK | NULL | Descripción |
|---|---|---|---|---|---|
| id_alerta | INT IDENTITY | Sí | — | No | Identificador de la alerta. |
| id_material | INT | | materiales | No | Material con baja disponibilidad. |
| fecha_generada | DATETIME2(0) | | | No | Fecha de generación. |
| estado | NVARCHAR(20) | | | No | activa/atendida (CHECK). Default 'activa'. |
| mensaje | NVARCHAR(200) | | | No | Mensaje de la alerta. |

Índice: `ix_alertas_material (id_material, estado)`.

Notas (Fase 8, D-20): las alertas se guardan permanentemente (D-05) y se cierran de forma
MANUAL (`estado='atendida'`, sin auto-resolución). No hay columnas de atención: quién y
cuándo la atendió queda en `audit_log` (D-06). La generación ocurre en la misma transacción
de la operación de stock que deja el material bajo el mínimo (una sola alerta activa por
material y episodio).

### 4.15 `reportes_generados`
| Campo | Tipo | PK | FK | NULL | Descripción |
|---|---|---|---|---|---|
| id_reporte | INT IDENTITY | Sí | — | No | Identificador del reporte. |
| id_usuario | INT | | usuarios | No | Usuario que generó el reporte. |
| tipo_reporte | NVARCHAR(50) | | | No | Tipo de reporte. |
| formato | NVARCHAR(10) | | | No | pdf/xlsx (CHECK). |
| fecha_generacion | DATETIME2(0) | | | No | Fecha de generación. |
| rango_fecha_inicio | DATE | | | Sí | Inicio del rango. |
| rango_fecha_fin | DATE | | | Sí | Fin del rango. |
| ruta_archivo | NVARCHAR(300) | | | Sí | Ruta del archivo generado. |

Notas (Fase 9, D-21): la tabla es bitácora de exportaciones (RF-014) alimentada por
`ReporteService` al exportar; el binario NO vive en la BD (se guarda en disco,
`tonuapp.reportes.directorio`, default `./reportes`) y la ruta se guarda solo con el
nombre del archivo. Quién/cuándo se audita explicitamente en `audit_log` (CREATE,
id_registro = id_reporte). `nombreArchivo` en la respuesta = nombre del archivo guardado.
El re-descargo valida que `ruta_archivo` quede dentro del directorio (anti path
traversal).

### 4.16 `audit_log`
| Campo | Tipo | PK | FK | NULL | Descripción |
|---|---|---|---|---|---|
| id | BIGINT IDENTITY | Sí | — | No | Identificador del registro. |
| entidad | NVARCHAR(100) | | | No | Entidad afectada. |
| id_registro | NVARCHAR(50) | | | No | Identificador del registro en la entidad. |
| operacion | NVARCHAR(20) | | | No | CREATE/UPDATE/DELETE/MOVEMENT/ADJUST (CHECK). |
| id_usuario | INT | | usuarios* | Sí | Usuario que ejecutó la acción. |
| fecha | DATETIME2(0) | | | No | Momento de la acción. |
| valores_antes | NVARCHAR(MAX) | | | Sí | Estado antes (JSON). |
| valores_despues | NVARCHAR(MAX) | | | Sí | Estado después (JSON). |

\* FK opcional (nullable) declarada hacia `usuarios`; sin `ON DELETE CASCADE` (default
  `NO ACTION`). Como el diseño usa soft delete (D-04), el usuario referenciado nunca se
  borra físicamente, así que la FK no produce efectos secundarios y permite conservar el
  log aunque el usuario se desactive. Append-only:
los registros no se modifican ni eliminan.

## 5. Relaciones y cardinalidades

| Origen | Cardinalidad | Destino | Regla |
|---|---|---|---|
| roles | 1 - N | usuarios | Un rol tiene muchos usuarios; un usuario tiene un rol. |
| usuarios | 1 - N | codigos_acceso | Un usuario genera muchos códigos. CASCADE. |
| usuarios | 1 - 1 | preferencias_usuario | Cada usuario tiene una fila de preferencias. CASCADE. |
| proveedores | N - M | materiales | Vía `material_proveedor`. Un proveedor abastece muchos materiales y un material puede tener varios proveedores. |
| categorias | 1 - N | materiales | Una categoría agrupa muchos materiales; un material pertenece a una categoría (D-15). |
| unidades_medida | 1 - N | materiales | Una unidad de medida puede aplicarse a muchos materiales (D-16). |
| zonas_acopio | 1 - N | materiales | Una zona puede alojar varios materiales; un material está en una zona (opcional). |
| materiales | 1 - N | lotes | Un material puede tener varios lotes. |
| usuarios | 1 - N | movimientos_inventario | Un usuario puede registrar muchos movimientos. |
| materiales | 1 - N | movimientos_inventario | Un material puede tener muchos movimientos. |
| lotes | 1 - N | movimientos_inventario | Opcional; un lote puede tener movimientos. |
| zonas_acopio | 1 - N | movimientos_inventario | Opcional. |
| materiales | 1 - N | ajustes_inventario | Un material puede tener muchos ajustes. |
| usuarios | 1 - N | ajustes_inventario | Un usuario puede hacer muchos ajustes. |
| movimientos_inventario | 1 - N | ajustes_inventario | Un movimiento 'ajuste' puede estar en un ajuste (0..1 en este lado). |
| materiales | 1 - N | alertas_inventario | Un material puede tener muchas alertas. |
| usuarios | 1 - N | reportes_generados | Un usuario puede generar muchos reportes. |
| usuarios | 1 - N | audit_log | Un usuario puede generar muchos registros de auditoría. |

## 6. Reglas de integridad

- **Claves primarias** en todas las tablas (`IDENTITY(1,1)` excepto tablas compuestas/1:1).
- **Claves foráneas** entre tablas relacionadas, con reglas de borrado definidas.
  `ON DELETE CASCADE` solo en `codigos_acceso` y `preferencias_usuario` (datos
  estrictamente subordinados a su usuario, sin riesgo de perder información crítica).
  La relación `material_proveedor` y el resto de FKs usan **NO ACTION** para proteger
  la trazabilidad: RF-010 prohíbe eliminar proveedores con materiales asociados y
  RF-004 prohíbe eliminar materiales con movimientos.
- **Unicidad:**
  - `roles.nombre_rol` único.
  - `usuarios.correo` único (RF-006: correo único en el sistema).
  - `proveedores.nit` único (RF-010: NIT único).
  - `materiales (nombre, id_categoria)` único (RF-002: sin duplicados dentro de una categoría).
  - `lotes.codigo_lote` único.
  - `material_proveedor (id_material, id_proveedor)` PK compuesta.
  - `ajustes_inventario.id_movimiento` único filtrado donde no es NULL (V5,
    `uq_ajustes_movimiento`): garantiza en BD el 1:1 detalle-ajuste → movimiento (D-19).
- **CHECK constraints:**
  - `zonas_acopio.capacidad_maxima >= 0`.
  - `materiales.stock >= 0` (nunca stock negativo).
  - `materiales.stock_minimo >= 0`.
  - `lotes.cantidad >= 0`.
  - `movimientos_inventario.cantidad > 0`.
  - `movimientos_inventario.tipo_movimiento` en (entrada, salida_venta, salida_merma, ajuste).
  - `movimientos_inventario.estado` en (activo, anulado).
  - `ajustes_inventario.cantidad_nueva >= 0` (RF-011: sin inventario negativo).
  - `alertas_inventario.estado` en (activa, atendida).
  - `reportes_generados.formato` en (pdf, xlsx).
  - `audit_log.operacion` en (CREATE, UPDATE, DELETE, MOVEMENT, ADJUST).
- **Soft delete** (columna `activo`/`estado`) en entidades críticas: `usuarios`,
  `proveedores`, `zonas_acopio`, `materiales`, `lotes`. Los **movimientos** nunca se
  borran: se anulan (`estado = 'anulado'`).
- **Regla de negocio (RF-004/AGENTS):** no se puede eliminar/desactivar un material que
  tiene movimientos registrados (validación en backend).
- **Regla de negocio (RF-010):** no se puede eliminar/desactivar un proveedor con
  materiales asociados en `material_proveedor` (validación en backend).

## 7. Decisiones de diseño

| Código | Decisión | Justificación |
|---|---|---|
| D-01 | Proveedor↔Material **N:M** (tabla intermedia) | Un material (ej. Arena) puede ser suministrado por varios proveedores; preserva trazabilidad de procedencia sin reescribir el registro del material al cambiar de proveedor. |
| D-02 | **Movimientos como fuente de verdad** | Permite historial, auditoría, reportes y trazabilidad (RF-011, RF-013, RF-014). |
| D-03 | Stock = **movimientos + existencia materializada** en `materiales.stock` | Consultas en tiempo real rápidas (RF-001) + historial íntegro. Sincronización atómica en el backend (`@Transactional`), **sin trigger** para control explícito y testeable. Optimistic locking (`@Version`) para evitar carreras. |
| D-04 | **Soft delete** en entidades críticas | Preserva integridad referencial y trazabilidad. H3 = Sí. |
| D-05 | **Alertas almacenadas permanentemente** | Permite historial y análisis de desabastecimiento. H4 = Sí. |
| D-06 | **Auditoría** vía AOP + tabla append-only | Registra QUIÉN/QUÉ/CUÁNDO/SOBRE QUÉ. H6 = Sí, inicialmente movimientos y ajustes, ampliable. |
| D-07 | **Sin password** — autenticación por correo + código temporal | H2 = Sí (se elimina US-07 y todo flujo de contraseña). El código se guarda con hash y expira a los 10 min, 1 uso, bloqueo por intentos. |

## 8. Inconsistencias pendientes

- **Inconsistencia #1 (roles):** los stakeholders mencionan roles operativos
  (Encargado de almacén, Vendedor, Gerente) pero RF-006 solo permite Administrador y
  Cliente. Se implementa tabla de roles flexible sembrando solo Admin y Cliente.
- **Inconsistencia #2 (autenticación vs. contraseña):** RF-008 usa código temporal;
  US-07 pedía cambiar contraseña. Se resolvió: sin password (solo código temporal).
- **Inconsistencia #3 (Gerente en RF-014):** sin rol definido; por ahora los reportes son
  solo para Administrador.
- **Inconsistencia #7 (RF-024 inventado → FUN-04):** la verificación de material recibido
  corresponde a FUN-04 (en el PPI, vinculada a RF-004 aunque el texto de RF-004 describe
  "edición y eliminación"). No existe RF nuevo; se registra como inconsistencia.

El detalle completo de inconsistencias vive en la sección correspondiente del análisis de
la Fase 0 y en `docs/decisions/DECISIONES.md`.

## 9. Script SQL

El script definitivo para SQL Server está en `database/tonuapp_database.sql`. Para crearlo
desde cero, se ejecuta el script completo (crea la BD `tonuapp` si no existe, tablas,
constraints, índices y datos semilla justificados; los datos de prueba están marcados con
el sufijo `(PRUEBA)`).
