# Decisiones — Fase 4: Materiales

## D-14: Cardinalidad proveedor–material
Se adopta **N:M** mediante tabla puente `proveedor_material`
(proveedor_id, material_id, y campos adicionales si aplican como
precio_referencia o tiempo_entrega). Justificación: un mismo material
puede ser surtido por más de un proveedor, y un proveedor puede surtir
más de un material — coherente con la recomendación de Fase 1.

## D-15: Categoría de material
Se adopta **tabla propia** `categorias` (id, nombre, activo) en vez de
un CHECK constraint. Justificación: permite administrar categorías
desde la aplicación (crear/renombrar/desactivar) sin requerir una
migración de esquema cada vez.

## D-16: Unidad de medida
Se adopta **tabla catálogo** `unidades_medida` (id, nombre, abreviatura)
en vez de un CHECK. Misma justificación que D-15, además de facilitar
poblar un dropdown en el frontend sin hardcodear valores.

## D-17: Stock materializado en Fase 4
Se expone `materiales.stock` como **columna materializada**, inicializada
en la creación del material (RF-001). Los movimientos de inventario
(Fase 6) serán responsables de mantener esta columna actualizada
(vía trigger o lógica de servicio), en vez de calcular el stock
sumando movimientos en cada consulta. Justificación: listar/buscar
materiales es una operación de lectura frecuente (RF-003/RF-004),
y recalcular en cada request sería costoso.

# TONUAPP — Registro de Decisiones Técnicas (ADR)

Cada decisión técnica importante se registra aquí con fecha, problema, opciones
consideradas, decisión, motivo e impacto.

---

## D-01 — Cardinalidad Proveedor ↔ Material: N:M
- **Fecha:** 2026-08-31
- **Problema:** ¿Un material puede tener más de un proveedor?
- **Opciones consideradas:**
  - 1:N (columna `id_proveedor` en `materiales`, como el borrador MySQL).
  - N:M con tabla intermedia `material_proveedor`.
- **Decisión:** N:M con tabla intermedia.
- **Motivo:** Un material de construcción (ej. arena) puede ser abastecido por múltiples
  proveedores; con 1:N cambiar de proveedor obligaría a reescribir el material sin
  conservar la trazabilidad de la procedencia previa. La tabla intermedia además permite
  marcar un proveedor principal (`es_principal`).
- **Impacto:** Se agrega una tabla `material_proveedor`. Las validaciones de "no eliminar
  proveedor con materiales asociados" (RF-010) se hacen contra esta tabla.

## D-02 — Modelado de movimientos de inventario
- **Fecha:** 2026-08-31
- **Problema:** ¿Cómo modelar entradas, salidas, mermas, ajustes y su historial sin usar
  una sola columna `cantidad` en `materiales`?
- **Decisión:** Tabla `movimientos_inventario` como **fuente de verdad** de todo
  movimiento (entrada, salida_venta, salida_merma, ajuste), con `id_usuario` (responsable),
  `fecha_movimiento`, `cantidad` (siempre positiva), `tipo_movimiento`, `motivo`,
  `estado` (activo/anulado) y FKs opcionales a `lote`/`zona`.
- **Motivo:** Permite historial, auditoría (RF-011), reportes (RF-014), clasificación de
  mermas (RF-013) y anulación sin borrado (US-22).
- **Impacto:** La columna `cantidad` de `materiales` pasa a ser una existencia
  materializada (D-03), no la única fuente.

## D-03 — Cálculo de stock (existencia materializada + movimientos)
- **Fecha:** 2026-08-31
- **Problema:** ¿Stock almacenado, calculado desde movimientos, o combinación?
- **Opciones consideradas:**
  - Solo calculado desde movimientos (SUM): siempre consistente pero lento al escalar y
    en consultas en tiempo real.
  - Solo almacenado: rápido pero sin historial.
  - Combinación (movimientos + existencia materializada).
- **Decisión:** **Combinación.** `materiales.cantidad_disponible` es un caché
  materializado, sincronizado en el **backend** dentro de la misma transacción
  (`@Transactional`) al registrar cada movimiento. **No se usa trigger** del borrador.
- **Motivo:** Consultas de disponibilidad en tiempo real (RF-001) y alertas (RF-012)
  instantáneas + historial íntegro. Control explícito y testeable en el service.
- **Concurrencia:** Se usará optimistic locking (`@Version`) en `materiales` y verificación
  de stock antes de salidas para evitar condiciones de carrera; la actualización de stock es
  atómica con el registro del movimiento (nunca quedan desincronizados).
- **Impacto:** La consistencia stock/movimiento depende de la transacción del backend; por
  eso es crítica y se cubrirá con pruebas de concurrencia obligatorias (AGENTS §13).

## D-04 — Soft delete en entidades críticas
- **Fecha:** 2026-08-31
- **Problema:** Los RF/US mencionan "eliminar" usuarios, materiales, proveedores, etc.
- **Decisión:** Soft delete (columna `activo`/`estado`) en `usuarios`, `proveedores`,
  `zonas_acopio`, `materiales`, `lotes`. Los movimientos se anulan (`estado = 'anulado'`),
  nunca se borran.
- **Motivo:** Preserva integridad referencial, auditoría y trazabilidad (prioridad #2 del
  proyecto). "Eliminar" en los requisitos se traduce como "desactivar".
- **Impacto:** Las consultas del backend deben filtrar por activo (se usará Specification /
  filtro global de Hibernate).

## D-05 — Alertas almacenadas permanentemente
- **Fecha:** 2026-08-31
- **Problema:** ¿Alertas calculadas dinámicamente o almacenadas?
- **Decisión:** Almacenadas en `alertas_inventario` (activa/atendida).
- **Motivo:** Permite historial y análisis de patrones de desabastecimiento. Costo de
  almacenamiento bajo para este volumen.
- **Impacto:** Se necesita limpieza/archivado periódico de alertas viejas (a largo plazo).

## D-06 — Auditoría
- **Fecha:** 2026-08-31
- **Problema:** ¿Cómo saber QUIÉN hizo QUÉ, CUÁNDO y SOBRE QUÉ registro?
- **Decisión:** Interceptor AOP + tabla `audit_log` append-only con
  `entidad`, `id_registro`, `operacion`, `id_usuario`, `fecha`, `valores_antes/despues`.
- **Motivo:** Respuesta consistente y centralizada, sin convertirla en una tabla de texto
  libre.
- **Impacto:** Alcance inicial (H6): movimientos y ajustes; se amplía en fases posteriores.

### Aclaración (Fase 8) — Trade-off de `AuditService` con `REQUIRES_NEW`
`AuditService.registrar()` usa `propagation = REQUIRES_NEW`: el registro de auditoría se
confirma en SU PROPIA transacción, independiente de la operación de negocio. Esto es
intencional (queremos conservar el log aunque la operación haga rollback, p. ej. una
validación posterior que falla), pero tiene una contrapartida que queda aquí explícita
como riesgo:

- Si la transacción principal falla al hacer commit (p. ej. conflicto de `@Version` en
  `Material` detectado en el flush, DESPUÉS de que el método ya "retornó"), el registro de
  auditoría ya está confirmado y **puede quedar una fila en `audit_log` describiendo una
  operación que en realidad no se persistió** (el `id_registro` puede incluso apuntar a un
  id nunca generado). Se observó de hecho en la prueba de concurrencia de la Fase 6 y en
  las fases 7/8.
- Implica que **`audit_log` NO es una fuente 100% confiable de "operaciones persistidas"**;
  lo correcto es leerlo como historial de INTENTOS auditados (QUIÉN intentó QUÉ y CUÁNDO),
  y cruzar con las tablas de negocio para confirmar el estado real.
- Esto NO es nuevo de esta fase: viene del diseño original de auditoría (Fase 3). La Fase 9
  (Reportes) NO debe tratar `audit_log` como fuente única para trazabilidad; debe construirse
  sobre las tablas de negocio (`movimientos_inventario`, `ajustes_inventario`, etc.) y usar
  `audit_log` como complemento de contexto, no como fuente de verdad.

## D-07 — Autenticación sin contraseña (correo + código temporal)
- **Fecha:** 2026-08-31
- **Problema:** RF-008 define código temporal, pero el backlog mencionaba "cambiar
  contraseña".
- **Decisión:** Solo correo + código temporal (Hash del código, no texto plano). Se elimina
  US-07 y todo flujo de contraseña. Código con vigencia máx. 10 min, un solo uso, bloqueo
  temporal tras intentos fallidos. Se usa JWT con acceso corto + refresh para la sesión.
- **Motivo:** Alineado a RF-008 y a la decisión del usuario (H2). JWT es stateless,
  estándar en Spring, adecuado para React.
- **Impacto:** No hay tabla de passwords. El correo único en `usuarios` es la identidad de
  login.
- **PENDIENTE (Fase 3 — Seguridad):** hashear el código temporal (ej. SHA-256 con salt/
  HMAC, no texto plano) ANTES de insertarlo en `codigos_acceso`. La columna `codigo` está
  definida como `NVARCHAR(10)` en el script de BD, suficiente para almacenar el valor
  original; si se almacena un hash, revisar la longitud de la columna en la migración de la
  Fase 3 (el análisis del código no se ha implementado aún porque no existe backend).

## D-08 — Motores y migraciones (Flyway)
- **Fecha:** 2026-08-31
- **Problema:** ¿Qué herramienta de migraciones?
- **Opciones consideradas:** Flyway vs Liquibase.
- **Decisión:** **Flyway**.
- **Motivo:** Se integra nativamente con Spring Boot (starter autoconfigura Flyway),
  simple, usa `SQL` plano en `db/migration`, es la opción más común en el ecosistema
  Spring para un proyecto mono-motor (solo SQL Server).
- **Impacto:** Migraciones versionadas `V{n}__descripcion.sql`. Convención
  `V{timestamp}__...` para evitar conflictos entre desarrolladores.

## D-09 — Permisos de base de datos del login de la aplicación (`db_owner` sobre `tonuapp`)
- **Fecha:** 2026-09-01
- **Problema:** ¿Qué permisos dar al login SQL dedicado `tonuapp_app` para que la
  aplicación (y Flyway) funcionen? La opción "mínima" (solo `db_datareader` +
  `db_datawriter`) no basta para aplicar migraciones.
- **Opciones consideradas:**
  - Permisos mínimos de datos (`db_datareader` + `db_datawriter`) únicamente.
  - `db_owner` sobre solo la base `tonuapp`.
  - Separar dos logins: uno de migración (con DDL) y otro de runtime de la app (solo
    lectura/escritura), como se haría en producción.
- **Decisión:** `db_owner` sobre **solo** la base `tonuapp`, como elección consciente para
  el entorno de desarrollo actual.
- **Motivo:** Flyway necesita privilegios DDL (crear/modificar tablas, constraints, índices)
  para aplicar las migraciones `V{n}__*.sql`. Confinar el login a `db_owner` de únicamente
  `tonuapp` cumple dos cosas: (1) el login no puede tocar ninguna otra base ni el servidor
  (nunca `sa`, nunca permisos de servidor), y (2) permite a Flyway y a la app operar sobre
  el esquema sin duplicar configuración de usuarios.
- **Impacto / nota de producción:** en un entorno de producción real se preferiría separar
  un **usuario de migración** (con DDL, ejecutado por Flyway) de un **usuario de runtime**
  de la aplicación (solo lectura/escritura de datos, `db_datareader`/`db_datawriter`), para
  reducir la superficie de ataque de la app en ejecución. Esto es un refactor del `pom`/la
  configuración de conexión y de la creación de logins, no un cambio de esquema; se puede
  aplicar en una fase posterior (endurecimiento de seguridad). El costo de `db_owner` en
  desarrollo es aceptable porque la BD es local y los datos son de prueba.

## D-10 — Clave de firma JWT (configuración por entorno, nunca en el repositorio)
- **Fecha:** 2026-09-01
- **Problema:** ¿Dónde guardar la clave de firma de los JWT? Debe tratarse con la misma
  disciplina que la contraseña de la BD.
- **Decisión:** La clave se lee de la **variable de entorno** `TONUAPP_JWT_SECRET`
  (configurada como placeholder en `application.yml`, sin valor real). Como respaldo para
  desarrollo local se permite un `application-local.yml` (que ya está en `.gitignore`).
  **Nunca** se hardcodea en código ni se pone un valor real en un archivo versionado.
- **Regla de longitud:** la clave debe tener **mínimo 256 bits (32 bytes)** para HS256;
  si se provee una más corta, el arranque falla (validación explícita en `JwtSecret`
  / `JwtProperties`).
- **Motivo:** una firma débil o comprometida permite forjar tokens y suplantar cualquier
  rol; separarla del código y del control de versiones reduce ese riesgo.
- **Impacto:** para que la app arranque hay que definir `TONUAPP_JWT_SECRET` (o el valor
  en `application-local.yml`). Al igual que con `tonuapp_app`, si se rota la clave se
  invalidan todos los tokens emitidos.

## D-11 — Logout con JWT stateless
- **Fecha:** 2026-09-01
- **Problema:** Un JWT no se puede invalidar del lado del servidor por sí solo. El backlog
  pide "cerrar sesión de forma segura" (US-06). ¿Cómo se resuelve?
- **Opciones consideradas:**
  - (a) Persistir los refresh tokens en BD (p. ej. nueva tabla `refresh_tokens`) para
    revocarlos en el logout.
  - (b) Aceptar que el access token de corta duración es la mitigación real y que el
    logout es principalmente del lado del cliente (se descarta el token localmente).
- **Decisión:** **Opción (a) para los refresh tokens** + revocación explícita en logout,
  combinada con access tokens de **corta duración** (15 min). Si se agrega una tabla
  `refresh_tokens` para revocar refrescos en logout y rotarlos, se documenta aquí mismo.
- **Motivo:** equilibrar seguridad y usabilidad: el access token corto limita la ventana de
  exposición si se filtra, y la revocación de refresh tokens en logout impide que una
  sesión "cerrada" pueda renovarse. Queda **explícito** (no implícito) que el access token
  del lado del servidor no se desactiva en memória: su expiración corta es la defensa real.
- **Impacto:** se necesita persistir los refresh tokens; el endpoint de logout los invalida.
  Esto agrega una tabla y su migración Flyway.

## D-12 — Rate limiting en `POST /api/auth/request-code`
- **Fecha:** 2026-09-01
- **Problema:** mitigar el riesgo #3 de la Fase 0 (bombardeo de correos / abuso del envío
  de códigos), no cubierto por el plan inicial de Fase 3.
- **Decisión:** límite simple: **no se permite generar un código nuevo para el mismo correo
  hasta que pase un tiempo mínimo (cooldown)** desde la generación del último código activo
  para ese correo. Además: los códigos expiran a los 10 min, son de un solo uso y el número
  de intentos fallidos se controla (bloqueo temporal tras N). El cooldown es independiente
  del mecanismo de expiración y se valida en el backend (no en frontend).
- **Motivo:** evita el re-emailing continuo y el abuso de recursos; es simple de
  implementar y suficiente para el alcance de este proyecto. No se introduce una librería
  completa de rate limiting (Bucket4j, etc.) por ser innecesaria aquí.
- **Impacto:** `solicitar-codigo` (ver D-13) puede devolver 429 si se repite antes del
  cooldown. El valor exacto del cooldown es configurable por propiedad.

## D-13 — Convención de idioma: métodos y endpoints de la API en español
- **Fecha:** 2026-09-04
- **Problema:** Tras implementar la autenticación (Fase 3) había una mezcla de idiomas en
  los identificadores públicos: los métodos de negocio estaban en español (crear,
  actualizar, listar), pero la capa de `auth` usaba inglés (requestCode, verify, refresh,
  logout) y las URLs REST eran `/api/auth/request-code`, `/verify`, `/refresh`, `/logout`.
- **Decisión:** Unificar **métodos y endpoints de autenticación en español**. Mapeo:
  - `requestCode` → `solicitarCodigo` — `POST /api/auth/solicitar-codigo`
  - `verify` → `verificar` — `POST /api/auth/verificar`
  - `refresh` → `renovarToken` — `POST /api/auth/renovar-token`
  - `logout` → `cerrarSesion` — `POST /api/auth/cerrar-sesion`
  Los `requestMatchers` de SecurityConfig se actualizaron en consecuencia. Las URLs viejas
  ya no son públicas (devuelven 401).
- **Motivo:** coherencia con el resto del dominio de negocio (entidades, tablas, servicios)
  que ya usa español, y menor fricción para el equipo/frontend.
- **Alcance / fuera de alcance:** aplica a métodos y rutas REST de la capa de autenticación.
  **No** se renombraron: clases de infraestructura (Service, Controller, Security, Jwt),
  getters/setters de `@ConfigurationProperties` (ligados a las keys `tonuapp.jwt.*`) ni
  campos JSON de respuesta (accessToken, refreshToken), por ser convenciones técnicas y
  contrato estable.
- **Impacto:** cambia el contrato público de la API de autenticación. Aún no hay frontend ni
  documentación `docs/api` publicada, por lo que el impacto es nulo fuera del backend y de
  los tests. Si más adelante hubiera consumidores, debería documentarse la versión anterior
  o mantener alias.

## Inconsistencia #7 — FUN-04 (verificación de material recibido)
- **Fecha:** 2026-08-31
- **Problema:** La verificación de material recibido apareció mal etiquetada en la primera
  revisión como "RF-024" (identificador inventado). En el PPI original corresponde a
  **FUN-04**, que está (incorrectamente) vinculada a RF-004 aunque el texto de RF-004
  describe "Edición y eliminación".
- **Decisión:** No crear RF nuevo. Registrar como inconsistencia de trazabilidad entre el
  PPI y el RF-004. La funcionalidad de verificación de material recibido se implementa como
  parte del flujo de **entrada de inventario** (dentro de movimientos), validando la
  correspondencia del material antes de ingresarlo oficialmente (US-24).

## D-14 — Cardinalidad proveedor–material: N:M
- **Fecha:** 2026-09-04
- **Problema:** RF-010 define que un proveedor puede tener varios materiales asociados, pero
  no resuelve si un material puede tener más de un proveedor.
- **Decisión:** **N:M** mediante la tabla puente, con campos adicionales si aplican
  (precio_referencia, tiempo_entrega). Un material puede ser surtido por varios
  proveedores y un proveedor puede surtir varios materiales.
- **Motivo:** coherente con la recomendación de Fase 1; el escenario real (un mismo material
  con varias fuentes de suministro) queda cubierto sin perder trazabilidad.
- **Nota de nomenclatura (inconsistencia menor):** la decisión redactada por el usuario usa
  `proveedor_material`, pero la tabla puente ya fue creada en V1 con el nombre
  `material_proveedor` (mismo diseño). Se mantiene `material_proveedor` por coherencia con
  el resto del esquema y para no introducir una migración de renombrado sin beneficio.
- **Impacto:** para Fase 4 (catálogo de materiales) la relación se considera al exponer los
  datos, pero el CRUD completo de `material_proveedor` se consolida en la fase de proveedores.

## D-15 — Tabla `categorias` en vez de CHECK
- **Fecha:** 2026-09-04
- **Problema:** RF-001/RF-002 requieren la categoría del material; un CHECK fijo obligaría a
  migrar el esquema cada vez que cambie el catálogo de categorías.
- **Decisión:** tabla propia `categorias` (id, nombre, activo). Editable desde la aplicación
  (crear/renombrar/desactivar) sin migraciones.
- **Motivo:** flexibilidad administrativa y consistencia con el modelo relacional.
- **Impacto:** `materiales.id_categoria` es FK a `categorias`; se crea migración Flyway que
  puebla categorías iniciales (de prueba, no datos reales de la empresa).

## D-16 — Tabla `unidades_medida` en vez de CHECK
- **Fecha:** 2026-09-04
- **Problema:** RF-001 usa unidad de medida; un CHECK fijo limita cambios sin migración.
- **Decisión:** tabla catálogo `unidades_medida` (id, nombre, abreviatura), editable.
- **Motivo:** D-15 + facilita poblar un dropdown en el frontend sin hardcodear valores.
- **Impacto:** `materiales.id_unidad` es FK a `unidades_medida`; migración Flyway puebla
  unidades iniciales (m, m², m³, kg, un, l — de prueba).

## D-17 — Stock materializado en `materiales.stock`
- **Fecha:** 2026-09-04
- **Problema:** RF-003/RF-004 requieren consultas frecuentes de listado/búsqueda de
  materiales; recalcular stock sumando movimientos en cada request sería costoso y acopla la
  lectura del catálogo a la historia de movimientos.
- **Decisión:** `materiales.stock` es una **columna materializada**, inicializada en la
  creación del material (RF-001). La Fase 6 (movimientos de inventario) la mantiene
  actualizada (vía lógica de servicio/transaccional, no triggers, para no dispersar la regla
  de negocio — se documenta cuando se implemente).
- **Motivo:** balance entre rendimiento (lecturas frecuentes) y consistencia; la auditoría y
  los movimientos garantizan trazabilidad del origen del stock.
- **Impacto:** en Fase 4 el stock se expone/acepta como valor materializado; la consistencia
  con movimientos se garantiza desde Fase 6 con transacciones atómicas (movimiento +
  actualización de stock, AGENTS.md §7).

## D-18 — Semántica del movimiento `ajuste` (Fase 6) y anulación
- **Fecha:** 2026-09-04
- **Problema:** D-02 dice que `cantidad` de `movimientos_inventario` es siempre positiva y
  que "el signo del efecto lo determina el tipo". Para `entrada` (+), `salida_venta` (−) y
  `salida_merma` (−) es claro, pero `ajuste` puede corregir el stock al alza O a la baja, y
  RF-011 define su entrada como "cantidad física real contada" (stock objetivo).
- **Opciones consideradas:**
  1. Ajuste = stock objetivo: el servicio calcula `efecto = cantidadNueva − stock_actual`,
     guarda `cantidad = |efecto|` y fija el stock a `cantidadNueva`. Permite alza y baja.
  2. Ajuste con signo fijo (−): más simple, pero nunca podría AUMENTAR el stock.
  3. Ajuste con subtipos (`ajuste_entrada`/`ajuste_salida`): requiere migrar el CHECK y el
     dominio.
- **Decisión:** **Opción 1, confirmada por el usuario.** El request de ajuste envía
  `cantidadNueva` (>= 0) + motivo; el movimiento guarda `|diferencia|` (siempre > 0, D-02) y
  el stock queda en la cifra contada. Se rechaza un ajuste sin diferencia (400).
- **Anulación (US-22/D-04):** los movimientos se anulan (`estado='anulado'`), nunca se
  borran. `entrada`/`salida_venta`/`salida_merma` se revierten con el signo inverso de su
  tipo, siempre que el stock no quede negativo. **Un `ajuste` NO se anula aún**: para
  revertirlo limpiamente haría falta la cantidad anterior, que solo existirá con la tabla
  `ajustes_inventario` (fase "Ajustes y mermas"); hasta entonces se devuelve 409 y se sugiere
  registrar un ajuste inverso.
- **Optimistic locking (D-03, implementado en Fase 6):** se agregó columna `materiales.version`
  (migración `V4`) con `@Version` en la entidad `Material`. Dos movimientos concurrentes
  sobre el mismo material: el perdedor recibe `ObjectOptimisticLockingFailureException`,
  mapeada a 409 "Conflicto de concurrencia", y su movimiento hace rollback. Verificado E2E:
  dos salidas paralelas de 7 sobre stock 10 → una 201 y la otra 409; stock queda en 3.
- **Conteo RF-004:** el COUNT nativo de `MaterialRepository` fue reemplazado por la consulta
  normal `MovimientoInventarioRepository.countByMaterial_IdMaterial` (TODO Fase 6 resuelto).
- **Auditoría y rollback:** `AuditService` usa `REQUIRES_NEW`; por diseño (D-06) el log
  sobrevive al rollback. Consecuencia observada: si un movimiento falla por concurrencia
  después de insertar su fila, queda en `audit_log` el registro del INTENTO (correcto: se
  conserva QUÉ se intentó y CUÁNDO), aunque la fila de negocio no exista.
- **Impacto:** API `/api/movimientos` (solo Administrador): `POST` (entrada/salidas),
  `POST /ajuste` (stock objetivo), `POST /{id}/anular`, `GET` (historial con filtro
  `idMaterial`). La traza fina de `ajustes_inventario` (cantidad_anterior/nueva) se completa
  en la fase "Ajustes y mermas".

## D-19 — Anulación de ajustes con `ajustes_inventario` (Fase 7)
- **Fecha:** 2026-09-04
- **Problema:** D-18 dejó los movimientos `ajuste` SIN anulación: para revertirlos con
  precisión se necesitaba la cantidad anterior al ajuste, que no se guardaba.
- **Opciones consideradas:**
  1. Añadir a cada movimiento `ajuste` la cantidad anterior junto al registro
     (`ajustes_inventario.cantidad_anterior`, tabla ya definida en el esquema) y
     relacionarlo con el movimiento vía la FK `id_movimiento`. Es la única forma de
     reconstruir la reversa exacta si hubo varios ajustes consecutivos sobre el mismo
     material (el stock objetivo no conserva el historial anterior).
  2. Derivar la cantidad anterior restando el historial de movimientos: frágil (hay que
     recorrer todo el historial, se rompe si se anulan movimientos previos) e ineficiente.
  3. Prohibir anular ajustes para siempre: inconsistente con US-22 (anular movimientos).
- **Decisión:** **Opción 1.** Cada `POST /movimientos/ajuste` guarda, en la misma
  transacción, el movimiento `ajuste` en `movimientos_inventario` Y una fila en
  `ajustes_inventario` con `cantidad_anterior` (= stock vigente al ajustar),
  `cantidad_nueva` (= objetivo) y la FK `id_movimiento`. Relación 1:1 en la práctica
  (un ajuste → un detalle), aunque la FK es nullable por diseño del esquema.
- **Anulación (US-22):** `anular()` ahora distingue:
  - entrada / salida_venta / salida_merma → revierte con el signo inverso del tipo.
  - ajuste → lee `cantidad_anterior` de `ajustes_inventario` y **fija el stock a ese
    valor** (es un valor absoluto, no un delta). Si no existe el detalle (dato
    inconsistente) → 409; si revertir dejara el stock negativo → 409 (regla transversal).
  Se mantienen stock nunca negativo, `@Transactional`, `@Auditable` (MOVEMENT/ADJUST/
  UPDATE) y `@Version` de Material para concurrencia (D-03).
- **Nuevo endpoint de conciliación (RF-011):** `GET /api/movimientos/ajustes` devuelve el
  historial de conciliaciones (anterior/nueva, motivo, autor, fecha) para revisión.
- **Bug de auditoría detectado y corregido (Fase 7):** `AuditAspect.extractId` probaba
  `idUsuario` ANTES que `idMovimiento`, por lo que `id_registro` de movimientos quedaba
  con el id del usuario (1) y no con el id del movimiento. Se reordenaron los accessors
  candidatos para privilegiar la PK de la entidad auditada (`idMovimiento`, `idAjuste`,
  `idMaterial`, ... antes de `idUsuario`), y se añadió `idAjuste` al listado. Las filas
  históricas del entorno de desarrollo quedaron con el valor erróneo (id=1); las nuevas
  ya registran el id correcto. Pendiente de depuración en dev (ver resumen) sin tocar
  `audit_log` (append-only, D-06).
- **Impacto:** se cierra el TODO de D-18. `MovimientoService` recibe
  `AjusteInventarioRepository`; se agrega `AjusteInventario` (JPA), `AjusteRepository`
  (`findByMovimiento_IdMovimiento`), `AjusteResponse`/`AjusteMapper`. No hay migración:
  `ajustes_inventario` ya existía en el esquema.

## D-20 — Ciclo de vida de las alertas de bajo stock (Fase 8, RF-012)
- **Fecha:** 2026-09-04
- **Problema:** cuándo se CREA una alerta (`alertas_inventario`) y si se CIERRA sola cuando
  el stock vuelve a subir del mínimo o exige acción manual del Administrador.
- **Generación:** tras CADA operación que cambia el stock (entrada/salida/merma, ajuste,
  anulación) en la misma transacción que el movimiento (si el movimiento se revierte, la
  alerta también). Solo se crea UNA alerta activa por material y por episodio: si ya existe
  una `activa`, los movimientos que sigan bajo el mínimo NO duplican (se evita spam por
  cada movimiento). La creación de un material con stock ya bajo el mínimo NO genera alerta
  automática hasta el primer movimiento (suposición; ver listado de pendientes).
- **Resolución:** MANUAL por el Administrador (`POST /api/alertas/{id}/atender`). NO se
  auto-resuelve al recuperar el stock.
  - El `CHECK` del esquema (activa/atendida) coincide: 'atendida' implica que alguien
    confirmó/actuó. Auto-marcarla como 'atendida' mentiría en el historial (nadie la
    atendió) y haría desaparecer del panel la señal operativa que RF-012 quiere mostrar
    (rojo al llegar al límite).
  - Quién y cuándo la atendió queda en `audit_log` (D-06, entidad alertas_inventario,
    id_registro = id_alerta): la tabla no guarda columnas de atención.
  - Se conserva permanentemente (D-05): cada alerta queda como evento del historial de
    desabastecimientos, cerrada o no.
- **Auditoría de creación:** se registra EXPLICITAMENTE en `AlertaService` (no con
  `@Auditable`-AOP) para que una invocación que NO crea alerta (retorna null) no escriba
  filas basura en `audit_log`. Corregido tras detectar CREATEs con `id_registro` = id del
  material (filas históricas 44/46 en dev quedaron así; append-only, sin tocar).
- **Impacto:** endpoint `GET /api/alertas` (filtros `estado`, `idMaterial`, Admin) y
  `POST /api/alertas/{id}/atender` (Admin). Migración **V5** (de esta misma fase): índice
  único filtrado `uq_ajustes_movimiento` sobre `ajustes_inventario(id_movimiento)
  WHERE id_movimiento IS NOT NULL` — garantiza en BD el 1:1 del D-19 que antes dependía
  solo del service. Verificado: datos previos sin duplicados; insert duplicado rechazado
  (Msg 2601).

## D-21 — Reportes: consultas JSON + exportación pdf/xlsx (Fase 9, RF-014/US-32/US-33)
- **Fecha:** 2026-09-05
- **Problema:** cómo ofrecer los reportes de inventario (consultables y exportables según
  RF-014) y con qué librerías.
- **Opciones consideradas:** JasperReports (pesado, orientado a plantillas JRXML) vs
  construcción ligera de xlsx/pdf; para el PDF: iText vs OpenPDF.
- **Decisión:**
  - Dos caras: endpoints JSON de consulta (US-32, para las tablas del frontend) y
    exportación pdf/xlsx con registro en `reportes_generados` (RF-014/US-33).
  - Librerías: **Apache POI (`poi-ooxml` 5.2.5)** para XLSX y **OpenPDF
    (`com.github.librepdf:openpdf` 1.3.30)** para PDF. Generación sobre un formato común
    intermedio `TablaReporte` (título/subtítulo/encabezados/filas), de modo que cualquier
    tipo de reporte se exporta sin lógica duplicada.
- **Motivo:** POI es el estándar de facto para Excel en Java (sin reemplazo por código
  propio); OpenPDF es el derivado LGPL/MPL del iText 4 y evita la licencia AGPL de
  iText 5+; UML a la escala del proyecto (reportes tabulares) no justifica JasperReports.
- **Impacto:**
  - Endpoints: `GET /api/reportes/{inventario,bajo-stock,movimientos,resumen}`,
    `POST /api/reportes/exportar`, `GET /api/reportes/bitacora`,
    `GET /api/reportes/{id}/descargar` (todos solo Administrador; el rol Gerente sigue
    pendiente de RF-006).
  - Fuente de datos: SOLO tablas de negocio (`materiales`, `movimientos_inventario`,
    `ajustes_inventario`, `alertas_inventario`); `audit_log` NO se usa como fuente
    (ver aclaración de D-06).
  - `reportes_generados` es bitácora (quién/cuándo/tipo/formato/rango/ruta); el binario
    se guarda en disco (`tonuapp.reportes.directorio`, default `./reportes`) y se audita
    explícitamente (CREATE, id_registro = id_reporte) porque el id nace en la transacción.
  - Descarga re-valida que la ruta quede dentro del directorio (anti path traversal).
  - El logo real de la empresa queda como placeholder textual (no existe el asset);
    pendiente de confirmación del usuario.
  - PDF "de solo lectura": nota al pie y metadatos; es inherente al formato.

## D-22 — Fase 10: consulta de auditoría, bloqueo por cuenta y endurecimiento

- **Fecha:** 2026-09-05
- **Problema:** dotar a la auditoría de valor de negocio (consultarla con filtros),
  cumplir literalmente el "bloqueo temporal tras varios intentos fallidos" del RF-008
  (hasta la Fase 10 el bloqueo era solo por código), y endurecer la superficie HTTP/JWT.
- **Opciones consideradas**
  - Consulta de auditoría: varias queries derivadas vs una JPQL con filtros nulos
    (`:entidad IS NULL OR a.entidad = :entidad`) vs Specification. Se eligió la JPQL
    con filtros opcionales: suficiente para filtros simples y evita la capa Specification
    (AGENTS.md sección 3 solo la agrega si es necesaria).
  - Bloqueo: por cuenta vs por IP. Por cuenta: preciso, no penaliza a usuarios legítimos
    detrás de una NAT y no requiere estado por IP; es el mecanismo que describe RF-008.
    Por IP se descartó: un atacante distribuido lo elude y una IP corporativa puede
    quedar bloqueada silenciosamente.
  - Fail-fast del secret JWT: arrancar con secreto ausente o < 32 bytes (D-10) rota la
    firma en runtime; preferimos que la app NO arranque (validación en JwtProperties).
  - Headers HTTP: configurados en SecurityConfig (X-Content-Type-Options: nosniff,
    X-Frame-Options: DENY, Referrer-Policy: no-referrer, CSP default-src 'none').
- **Decisión:** se implementan (a) `GET /api/auditoria` (solo Administrador, filtros
  entidad/operacion/idUsuario/desde/hasta, ordenado desc), (b) bloqueo temporal POR CUENTA
  en `usuarios` (`intentos_login_fallidos`, `bloqueo_hasta`; migración V6) con umbral y
  duración configurables, (c) validación de arranque del secret JWT, (d) headers de
  seguridad y CORS por propiedad (`TONUAPP_CORS_ORIGINS`).
- **Trade-off del bloqueo por cuenta (aceptado y documentado):** cualquier persona que
  conozca el correo de un usuario registrado puede forzar su bloqueo temporal repitiendo
  códigos incorrectos, sin necesitar ninguna otra credencial. Es un vector de denegación
  de servicio **dirigido**, especialmente contra la cuenta del Administrador. Se acepta
  porque el registro de usuarios NO es público (RF-006: solo el Administrador crea
  cuentas), lo que ya exige que el atacante conozca de antemano un correo válido del
  sistema: reduce sustancialmente la superficie de ataque comparado con un sistema de
  registro abierto, y la mitigación (solicitar un nuevo código) es barata y a la vista.
  Mitigaciones activas en paralelo: cooldown de 60 s por código (D-12) e intentos
  fallidos a nivel de código, que encarecen la retención sin resolver el bloqueo.
- **Impacto:** nueva tabla de desarrollo nada (solo columnas V6 en `usuarios`);
  reinterpretación del "bloqueo temporal" de RF-008 ahora también por cuenta;
  `AuditAspect` conserva su comportamiento (valores_antes = argumento/DTO, no el estado
  previo real en BD): la verdad del inventario vive en `movimientos`/`ajustes`
  inmutables (D-03/D-04/D-19), así que un refactor AOP completo se documenta como
  limitación aceptada y no se implementa en esta fase.
  - Hallazgo transaccional (E2E): el registro de los fallos no puede vivir en la
    transacción de `verificar()`, porque esa operación termina lanzando una excepción y
    todo se revierte. Se persiste en transacción PROPIA (REQUIRES_NEW) mediante
    `FallosLoginService.registrarFallo(idCodigo, idUsuario)` — mismo patrón que
    AuditService (D-06) — que incrementa `codigos_acceso.intentos_fallidos` y
    `usuarios.intentos_login_fallidos` (o activa el bloqueo) con estado fresco de la BD.

## D-23 — Cierre del backend: ventana del access token y límite de consulta de auditoría

- **Fecha:** 2026-09-06
- **Punto 1 — ¿Qué pasa con un access token ya emitido cuando bloquean o desactivan la
  cuenta mientras el token sigue vigente?**
  - **Opciones consideradas:**
    1. Aceptar la ventana como riesgo residual acotado (el access token caduca solo en
       15 min, D-10/D-11) y no verificar contra la BD en cada petición.
    2. Que el filtro JWT valide en cada petición `usuarios.activo` y `usuarios.bloqueo_hasta`
       contra la BD, revocando el token efectivamente en el momento del bloqueo/desactivación.
  - **Decisión:** opción 1, sin cambio de código. El filtro JWT sigue siendo puramente
    stateless (firma HS256 + claims); el estado de la cuenta solo se valida en
    `verificar()` (login) y en `renovarToken()` (refresco).
  - **Motivo:** la ventana es de a lo sumo 15 minutos (vigencia del access token), un
    riesgo acotado y de baja probabilidad. Validar en cada petición obligaría a una
    consulta extra a la BD por request (el filtro ya es un cuello de botella natural),
    contradice el diseño stateless/JWT elegido (D-10/D-11) y no aporta valor real al
    alcance de este proyecto. Además, quien pierde el acceso por desactivación queda
    fuera en el ciclo de refresco siguiente. Se documenta igual que el DoS dirigido de
    D-22: riesgo residual aceptado con justificación explícita.
  - **Impacto:** ninguno en código. Todos los tokens siguen siendo revocables en la
    práctica por el refresh (rotación, D-11); el peor caso es un access token huérfano
    de 15 minutos para una cuenta desactivada/bloqueada.
- **Punto 2 — Límite en `GET /api/auditoria` (audit_log crece indefinidamente):**
  - **Decisión:** parámetro opcional `size` con default **100** y máximo **500**; valores
    fuera de 1..500 responden 400. La consulta siempre se limita a los N registros más
    recientes (ORDER BY fecha DESC ya existente) aplicando `PageRequest(0, size)` en el
    repositorio.
  - **Motivo:** impedir respuestas gigantes y abuso del endpoint; sin paginación, el
    cliente se lleva solo los últimos N; si en el futuro hace falta paginación completa
    se agrega `page` manteniendo compatibilidad.
  - **Impacto:** API.md documenta el parámetro; AuditoriaService valida y acota.

## D-24 — Filtros y paginación en listados de Materiales y Movimientos

- **Fecha:** 2026-09-06
- **Problema:** los wireframes aprobados de Materiales (lista) y Movimientos asumían filtros
  (tipo/estado/fecha) y paginación que el backend NO exponía: `GET /api/movimientos` solo
  aceptaba `?idMaterial=` y devolvía una lista completa, y `GET /api/materiales` devolvía
  todos los activos sin control de tamaño/página.
- **Opciones consideradas:**
  1. Exponer el objeto `Page<T>` de Spring Data directamente.
  2. Crear un envoltorio propio `PagedResponse<T>` (content, totalElements, totalPages,
     page, size) y usarlo en ambos listados.
- **Decisión:** opción 2. Se agrega `com.tonuapp.shared.PagedResponse<T>` y se usa en
  `GET /api/materiales` y `GET /api/movimientos`.
  - `GET /api/movimientos`: filtros opcionales `tipo`, `estado`, `desde`, `hasta` (además
    del `idMaterial` ya existente) mediante `JpaSpecificationExecutor` (Specification
    combinada, cada filtro nulo se ignora), orden fijo `fechaMovimiento` desc, y paginación
    `page`/`size`.
  - `GET /api/materiales`: paginación `page`/`size` sobre los activos, orden `nombre` asc.
  - Validación común en ambos servicios: `page >= 0`, `size` en 1..`MAX_PAGE_SIZE` (100),
    y `desde <= hasta` para rangos de fecha; violaciones → 400.
- **Motivo opción 2:** el JSON de `Page` de Spring es verboso y acopla la API a Spring
  Data; un DTO propio es más limpio, estable frente a cambios internos y suficiente para
  los controles de paginación de la UI.
- **Impacto:** `GET /api/materiales` y `GET /api/movimientos` cambian su respuesta de
  `List<...>` a `PagedResponse<...>` (breaking — solo las consume el frontend nuevo, aún
  en bootstrap). Se documenta en API.md; se agregan tests unitarios de servicio.
- **Ampliación inmediata (confirmada por el usuario):** también se paginaron
  `GET /api/materiales/buscar` (la pantalla de Materiales combina búsqueda + paginación en
  una misma vista) y `GET /api/proveedores`, con el mismo `PagedResponse<T>` y validación
  (`page >= 0`, `size` 1..100). Para proveedores, el conteo de materiales se limita a los
  ids de la página (`findByProveedorIdIn`) en lugar de cargar toda la tabla N:M.
- **Filtro `q` en proveedores (añadido por UX, NO por requisito):** `GET /api/proveedores`
  acepta `q` opcional que filtra por **nombre o NIT** (contiene, insensible a mayúsculas),
  vía Specification (igual patrón que materiales/buscar). **RF-010 y US-29 solo exigen
  consultar/ver el listado de proveedores — no hay requisito documentado de búsqueda por
  texto**; se agregó por consistencia de UX con la búsqueda de materiales (RF-003), y así
  queda marcado en API.md y en la matriz de trazabilidad (decisión técnica/UX, no RF).

## D-25 — Pantalla Materiales (Fase 11): lista + detalle + CRUD en modal

- **Fecha:** 2026-09-06
- **Problema:** la primera pantalla funcional del frontend (después del Login aprobado)
  debe cubrir Materiales (RF-001 a RF-004) sin rehacer el design system: hereda los tokens
  del Login (slate `#64748B` + naranja seguridad `#EA580C`, Fira Sans/Fira Code, Flat
  Design, fondo blueprint, D-24/D-25 del index.css).
- **Opciones consideradas:**
  1. Pantallas/rutas separadas para crear/editar (`/materiales/nuevo`, `/materiales/:id/editar`).
  2. **Modales** superpuestos a la lista para detalle, formulario y confirmación de desactivado.
- **Decisión:** opción 2, consistente con el flujo de Movimientos/Ajustes/Proveedores (los
  wireframes de esos módulos ya usan diálogos). Estructura: `MaterialesPage` (lista paginada)
  + `MaterialDetalleModal` (incluye proveedores N:M, RF-010) + formulario crear/editar +
  confirmación de soft delete (RF-004). El detalle también es modal (no ruta propia).
- **Regla de color (confirmada por el usuario):** el naranja de acento se reserva **solo**
  para CTA (Nuevo material, Guardar). El badge de estado usa **solo rojo destructivo**
  `#DC2626` para `BAJO` (stock < stockMinimo) y slate para `OK`; idem el aviso inline de
  bajo stock en el detalle. Motivo: si el badge usa el mismo color que los botones de
  acción, se confunde con una acción y le resta peso al CTA real.
- **Roles:** un `Cliente` ve lista, búsqueda y detalle en modo lectura (RF-001/RF-009); la
  UI oculta los botones de mutación (el backend rechaza igualmente con 403, RF-002/RF-004).
- **Formulario:** `stock` es editable tanto en creación como en edición porque el backend
  lo permite (`MaterialService.actualizar`, Fase 4 — D-17; la sincronización correcta del
  stock se mantiene vía Movimientos en las fases 6-7).
- **Impacto:** se agrega el layout autenticado (`AppLayout` con header TonuAPP + correo +
  cerrar sesión), la ruta protegida `/materiales`, la capa `services/materialService.ts`,
  tipos `types/materiales.ts`, util `utils/errores.ts` (extraerMensaje/extraerStatus,
  extraído del Login para no duplicar lógica) y `utils/formatos.ts` (cantidades es-CO). La
  paginación consume directamente `PagedResponse<T>` (D-24).

## D-26 — Rediseño interactivo Materiales (Fase 11, iteración 2): motion dial 4-5/10

- **Fecha:** 2026-09-06
- **Problema:** la pantalla Materiales aprobada en D-25 se percibía estática (retícula
  blueprint fija, sin microinteracciones). El usuario pidió más dinamismo e interactividad
  «dentro de la misma identidad industrial aprobada — sin copiar la mascota animada de
  CENTROVA (fondo oscuro, gradientes, personaje)».
- **Opciones consideradas:**
  1. Mascota/ilustración animada tipo CENTROVA -> **descartada** (tono B2B industrial).
  2. **Compás de dibujante SVG inline** cuyo brazo/aguja rota suavemente hacia el cursor
     (`mousemove` → `atan2` → `transform: rotate`), junto al emblema en el header.
  3. Fondo blueprint con **líneas maestras que se dibujan solas** (stroke-dashoffset en
     loop lento, `sine in-out`) + marcas de intersección `+`.
  4. Microinteracciones CSS: hover de fila con costado naranja, sello de estado al
     aparecer, conteo animado de stock, y **transición de salida en modales**.
- **Decisión:** combo 2+3+4 (sin librerías 3D). Dial de motion **4-5/10** (el Login usó
  3/10): el skill `ui-ux-pro-max` se ejecutó con `--motion 5 --density 7 --variance 5` y
  devolvió el mismo sistema color/Flat Design ya aprobado (no cambia tokens), con guías:
  animar el *wrapper* div y no el SVG (aceleración GPU), `exit faster than enter` en
  modales, y nunca usar `back.out` en tablas de datos denso (el overshoot se lee como
  sloppy).
- **Accesibilidad (exigencia del usuario):** el conteo animado de stock expone el número
  animado con `aria-hidden="true"` y el **valor final** en un `span.sr-only` — el lector
  de pantalla anuncia una sola vez el dato final, nunca los intermedios. No se usa
  `aria-live` para esto (evita regiones vivas competidoras por fila, ver
  ui-ux-pro-max `Contextual Live Badge Updates`). Todo respeta `prefers-reduced-motion`
  (compás estático, líneas al 100%, sin conteo) vía hook `usePrefiereReducido`
  (`useSyncExternalStore`, sin setState en efecto).
- **Componentes:** `components/Compas.tsx` (compás decorativo `aria-hidden`, rAF con
  acotación de velocidad para rotación suave y amortiguada), `components/Blueprint.tsx`
  (fondo fijo `pointer-events:none`), `hooks/usePrefiereReducido.ts`. `Modal.tsx` ahora
  tiene fase de cierre (clase `modal--cerrando`, salida 160ms < entrada 240ms) con limpieza
  de timer. `MaterialesPage.tsx` agrega `useConteoStock` y `StockContador`.
- **Regla de color inalterada (D-25):** naranja solo en CTA, badge BAJO solo rojo
  destructivo; el costado naranja del hover de fila es decorativo de ruta y no compite con
  acciones.
- **Impacto:** sin cambios de backend ni de BD. Respaldo manual del estado pre-rediseño en
  `.backup-materiales-2026-09-06/` (el usuario pidió poder revertir exacto).

## D-27 — Hallazgo de diagnóstico: «pérdida» aparente de ui-ux-pro-max y limpieza de datos E2E en BD dev

- **Fecha:** 2026-09-06
- **Problema 1 (skills):** el agente reportó que las skills de diseño `ui-ux-pro-max`,
  `design-system-builder` y `theme-factory` habían «desaparecido». Investigación: no se
  perdió nada. `ui-ux-pro-max` está íntegro en `frontend/.opencode/skills/ui-ux-pro-max/`
  (71 archivos, SKILL.md + data/ + scripts/ + tests). `design-system-builder` y
  `theme-factory` **nunca fueron directorios propios**: son los modos `--design-system
  --persist` (genera `design-system/<proyecto>/MASTER.md`) y el propio MASTER.md de
  ui-ux-pro-max. El error del agente fue buscar la skill solo en la caché de plugins
  (`~/.cache/opencode/...`) y en el glob raíz, ignorando la ruta local `frontend/.opencode/`.
  Además, opencode **no auto-descubre** `.opencode/skills/` locales en subdirectorios; la
  skill se invoca manualmente con `python scripts/search.py`. Lección para el proyecto:
  verificar rutas locales con `Get-ChildItem -Force` (no solo glob) antes de reportar
  pérdidas.
- **Problema 2 (datos):** cinco materiales de prueba «Cemento blanco/gris» (ids 5-9)
  aparecían bajo la categoría «Arena» en la BD dev, aunque la categoría «Cemento» (id=3)
  existía y era usada correctamente por «Concreto rapido». Diagnóstico: **no es bug de
  código** — `MaterialService.crear/actualizar` asignan `request.idCategoria()` tal cual
  (backend MaterialService.java:121,147) y el frontend envía el id seleccionado; los
  materiales se crearon el 4-sep (22:47-23:16) en la misma noche de los smoke tests E2E de
  la Fase 3 con patrón POST+PUT casi simultáneo = **basura de pruebas manuales**, no datos
  reales. Corrección directa en BD dev (`UPDATE materiales SET id_categoria=3`) tras
  verificar que los 14 movimientos asociados a esos ids no se rompen (solo referencia el
  id del material, no la categoría). Verificado por API: `categoria=3` devuelve 6
  materiales y `nombre=cemento&categoria=3` devuelve exactamente los 5 corregidos.
- **Impacto:** se descarta algoritmo de «derivación automática» de categoría (innecesario);
  se documenta el patrón de smoke tests E2E como origen de datos basura. La limpieza se
  hizo solo en la BD dev, nunca en migraciones ni seeds.

## D-28 — Bug de rediseño confirmado: celda fantasma en `<tr>` y migración a framer-motion (Fase 11, iteración 3)

- **Fecha:** 2026-09-07
- **Problema (bug real reportado por el usuario):** al pasar el ratón sobre una fila de la
  tabla Materiales, la tabla «se corre» (aparece una columna vacía a la izquierda y las
  columnas recalculan) y el wordmark TONUAPP desaparece del header. Sospecha inicial del
  usuario: el `::before` sobre `<tr>` del rediseño D-26.
- **Causa raíz confirmada con evidencia (no asumida):** el `::before` con `position:absolute`
  sobre un `<tr>` inserta una **celda fantasma** en Chromium: los pseudo-elementos sobre
  elementos de tabla tienen soporte inconsistente y la celda no participa del grid de
  columnas, desplazando el resto. Mediciones reales vía CDP (headless Chrome, app real en
  :5174): prueba estática `table/tr::before` desplazó la primera celda `24px→145px`
  (121px); en la app, el hover sobre la fila 3 recalculó las columnas de todas las filas
  (ej. celda `204px→148px`). El wordmark **no** se reprodujo en headless (viewport 743px y
  1400px, hover forzado con `CSS.forcePseudoState`): quedó siempre en `left:66` visible.
  No se afirmó su pérdida como reproducida; ambos mecanismos candidatos (`::before` en
  `<tr>` y el compás con `will-change`+rAF del header) se eliminan de todos modos con el
  rediseño.
- **Opciones consideradas:**
  1. Parche CSS local (mover el `::before` del `tr` a un `td`) manteniendo el resto del
     rediseño manual (compás rAF, conteo de stock).
  2. **Revertir al respaldo `.backup-materiales-2026-09-06/` y reconstruir las
     microinteracciones con framer-motion** (librería que el usuario ya usa en otro
     proyecto; sin 3D): hover de fila con `motion.tr` + variants, entrada/salida de modales
     con `AnimatePresence`/`motion`, barra de acento como `motion.span` **dentro** del
     primer `<td>` (nunca pseudo-elemento sobre `tr`).
  3. Reemplazar el compás (que no se leía como tal) por una **escuadra de ingeniero** SVG
     estática y decorativa, sin `will-change` ni rAF.
- **Decisión:** opción 2 completa + escuadra (opción 3) + **segundo acento teal
  `#0f766e`** (D-28) **solo en header/hero** (marca de la escuadra y numeral del título),
  para enriquecer la identidad sin que se vea «más plana»: la tabla de datos conserva
  íntegro el sistema slate/naranja (D-25) — el teal no toca la legibilidad de datos.
  Se **retira el conteo animado de stock** (era parte del rediseño roto): texto plano es
  más accesible (sin regiones vivas en competencia) y cumple la exigencia previa de anunciar
  solo el valor final.
- **Cómo se reconstruyó:**
  - `Modal.tsx`: `motion.div` overlay + panel, `initial/animate/exit`; **exit más rápido
    que enter** (160ms vs 240ms, dial motion D-26). `AnimatePresence` rodea cada modal en
    `MaterialesPage.tsx` (detalle, formulario, confirmación).
  - `MaterialesPage.tsx`: filas `motion.tr` con `variants` (`reposo`/`hover`) y la barra de
    acento como `motion.span.materiales__fila-acento` dentro del `td.materiales__primera`
    (`position:relative`), con `transform-origin:top`; `whileHover` propaga a la barra.
  - `AppLayout.tsx`: `MotionConfig reducedMotion="user"` (todo framer-motion respeta
    `prefers-reduced-motion` globalmente); se monta `Escuadra` tras el wordmark.
  - `components/Escuadra.tsx`: SVG de escuadra `aria-hidden`, estático.
  - CSS: se eliminan las animaciones CSS del modal (las maneja framer-motion) y el bloque
    `tr:hover`; se agregan `.escuadra`, `.escuadra--marca` y `--color-accent-2`.
  - Se eliminan `Compas.tsx`, `Blueprint.tsx`, `hooks/usePrefiereReducido.ts` (dejados fuera
    del rediseño; no se reemplazan).
- **Verificación en navegador real (CDP, app en :5174):** hover en fila → columnas idénticas
  antes/después (`TDS_IGUALES: true`), wordmark estable (`left:66`), barra de acento anima
  a `opacity:1`, fondo de fila cambia a slate. Modal: entrada opacity `0.84→1` y `y` →0 a
  los 100ms, salida por Escape/fondo opacity `1→0.10` y `NO_MODAL` tras 400ms; formulario
  abre con «Nuevo material». `npm run build` y `oxlint` limpios.
- **Regresión de la regla de color (D-25):** intacta — teal solo en header/hero, naranja
  solo CTA, badge BAJO solo rojo destructivo.
- **Impacto:** se corrige un bug de layout reproducido por medición; se descarta el CSS
  casero con pseudo-elementos sobre `<tr>`. Dependencia nueva: `framer-motion ^13.2.0`.
  El respaldo `.backup-materiales-2026-09-06/` sigue siendo el punto de revert exacto.
  Sin cambios de backend, BD ni migraciones.

## D-29 — Animaciones perceptibles según revisión de estándar emilkowalski (Fase 11, iteración 4)

- **Fecha:** 2026-09-07
- **Problema:** el usuario reportó que «no ve ninguna animación» en su navegador real,
  mientras que las mediciones CDP (D-28) mostraban que las animaciones SÍ se ejecutaban
  (hover con cambio de fondo, entrada de modal, barra de acento). El conflicto entre
  confirmación del usuario y evidencia de ejecución señalaba un problema de **percepción**,
  no de ejecución.
- **Causa raíz confirmada por medición (no asumida):** las animaciones eran demasiado
  sutiles para percibirse como movimiento:
  1. El hover de fila cambiaba de `#FFFFFF` a `--color-muted` (`#F2F3F4`): una diferencia
     de luminancia de ~1,5%, imperceptible a simple vista.
  2. La barra de acento de 3px solo aparecía al hover; un cambio de ese tamaño no se
     percibe como animación si el resto de la página arranca estática.
  3. El modal entraba con fade+desplazamiento (sin `scale`), por lo que se sentía plano.
  4. Ninguna animación en la carga de la página: todo aparecía estático al navegar.
  5. Los botones no tenían `:active` (press feedback), un hueco que el estándar pide cubrir.
- **Opciones consideradas:** (1) subir el «dial» de motion de forma arbitraria; (2) auditar
  contra un estándar de calidad de animación y corregir por categoría (recon → audit →
  vet → fix); (3) instalar las skills pedidas por el usuario (emilkowalski/skill,
  impeccable y taste-skill) y usarlas como referencia de criterio.
- **Decisión:** opción 2+3. Se aplica la guía de `review-animations`/`find-animation-opportunities`
  de emilkowalski (valores tomados de su STANDARDS.md): transform+opacity (GPU), UI
  `<300ms`, ease-out en entradas/salidas, `:active` press 100–160ms, reduced-motion
  respetado vía `MotionConfig`. Las 3 skills quedan instaladas en
  `frontend/.opencode/skills/` y `frontend/.agents/skills/` (se pide copia, no symlink:
  entorno Windows).
- **Cambios implementados:**
  - `MaterialesPage.tsx`: entrada de página con `motion.div` (`paginaVariants`, fade + y
    `8px`, 260ms ease-out); hover de fila con fondo `var(--color-row-hover)` (`#E8EDF2`,
    perceptible) y barra con `costadoVariants` (fade + `x:-8→0` + `scaleY`, 160ms); `EASE
    = [0.2,0,0,1]` (mismo que `--ease-flat` del design system, no curva paralela).
  - `Modal.tsx`: entrada con `scale(0.97)→1` + fade + y (presencia); salida asimétrica
    más rápida (160ms vs 240ms, el patrón «deliberate=lento, system=rapido» del estándar);
    overlay con la misma salida rápida.
  - `index.css`: nuevo token `--color-row-hover: #e8edf2`.
  - `materiales.css`: `:active:not(:disabled) { transform: scale(0.97) }` en `.btn` con
    transición `transform 160ms ease-out` (press feedback del estándar).
- **Verificación en navegador real (CDP, Brave real, login real, app :5174):** medición
  por frames de las 5 animaciones:
  - Entrada de página: `opacity 0→1` y `translateY 8→0` en ~250ms (antes: estático).
  - Hover de fila: fondo `rgb(232,237,242)` (=`#E8EDF2`) perceptible + barra `opacity→1`.
  - Modal entrada: `opacity 0.93→1` + `scale 0.998→1` + `y` (presencia).
  - Modal salida: asimétrica (~130ms), `NO_MODAL: true` al final.
  - Press feedback: `transform: matrix(0.978…)` = `scale(0.97)` en `:active`.
  `npm run build` y `oxlint` limpios.
- **Regresión de la regla de color (D-25):** intacta — el hover usa un slate de la gama,
  no toca el teal (único en header/hero) ni el naranja CTA ni el rojo destructivo.
- **Impacto:** la pantalla Materiales ahora comunica estados de forma perceptible con motion
  de baja frecuencia (frecuencia tens/day → crisp y rápido, sin rebote), preparando el
  terreno para la pantalla de inicio nueva (donde irá el elemento vistoso interactivo,
  mockup antes de construir). Sin cambios de backend, BD ni migraciones. Dependencias
  nuevas: ninguna (solo skills instaladas).
- **Pendiente (acordado con el usuario):** la página de inicio (landing) NUEVA y separada
  que va antes de llegar a Materiales (algo tipo landing/about con info + contacto) con el
  elemento visual vistoso/interactivo, con `taste-skill` para el lenguaje visual e imágenes
  de referencia ANTES de construir (mockup primero). El elemento vistoso NO va dentro de la
  tabla de datos.

## D-30 — Rediseño global "Premium utilitario" (Fase 11, iteración 5)

- **Fecha:** 2026-09-07
- **Problema:** tras la iteración 4 (D-29) el usuario seguía sin percibir mejoras: «veo todo
  igual, el mismo fondo cuadriculado que se ve feo, siguen sin verse las animaciones». La
  evidencia CDP confirmaba que D-29 SÍ corría en su navegador real; el problema real era
  **diseño**, no ejecución: el sistema visual Flat industrial (reticula blueprint + esquinas
  rectas + cero sombras, D-24/D-25) seguía leyéndose plano y sin carácter. El usuario pidió
  explícitamente usar **todas** las skills instaladas (impeccable, taste-skill, emilkowalski,
  superpowers) para un cambio profesional visible y autorizó instalar lo que faltara
  (no hizo falta: todas ya estaban en `frontend/.opencode/skills/`).
- **Opciones consideradas (dirección visual, consultada al usuario con la skill brainstorming):**
  1. **Premium utilitario (aprobada):** nivel Linear/Stripe/Notion — canvas hueso cálido,
     carbono profundo, teal+naranja como acentos escasos, radios crisp 8–10px, sombras ultra
     difusas, whitespace generoso, motion con física.
  2. Oscuro high-end (OLED + glass): más "wow" pero más cansancio en herramienta de uso diario.
  3. Editorial industrial (numeros gigantes/archivo): muy distinto pero menos "app".
- **Decisión:** opción 1. Se reconstruye el design system hacia "Premium utilitario" usando:
  - `impeccable` `craft-floor` (verificar/refuse) y `delight`/`animate` (thesis de motion,
    modo Operate);
  - `emilkowalski/skill` `animate` (curvas estándar `--ease-out: cubic-bezier(0.23,1,0.32,1)`,
    press 120ms, slap mordida en stagger 30ms, spring solo en tier first-use/hover);
  - `taste-skill` (`minimalist-ui`/`high-end-visual-design`): hueso calido `#F7F7F5`, hairline
    calido `#E6E4DF`, tipografía Fira (identidad, se conserva), pasteles para badges;
  - superpowers `frontend-design` para la ejecución React/CSS.
- **Cambios implementados:**
  - `index.css`: **se elimina la reticula blueprint** (el "cuadriculado feo"). Nuevos tokens:
    hueso `#F7F7F5`, carbono `#292524`, hairline calido, radios `--radius-sm/md/lg`
    (8/10/12px), sombras `--shadow-card`/`--shadow-pop` (offset+blur, nunca halos), curvas
    `--ease-out`/`--ease-in-out`, ambiente `--ambient-glow` (radiales teal/naranja al 0.05%
    sobre hueso, capa fija `background-attachment: fixed`, nunca scroll containers).
    **Superficies de navegador tematizadas** (craft-floor): `::selection` teal 0.18, `caret-color`
    teal en inputs, scrollbar thin con thumb `#DDD9D3`. Focus ring ahora teal (`--color-accent-2`).
  - `LoginPage` (`index.css`): tarjeta con `--radius-lg` + `--shadow-pop`, inputs con radio y
    anillo teal, CTA sin MAYÚSCULAS forzadas, press `scale(0.98)`/120ms, fondo hueso sin grid.
  - `MaterialesPage`/`materiales.css`: cabecera con título 30px + tracking cerrado, botones
    `--radius-sm` + press `scale(0.98)`, tabla en tarjeta `--radius-md` + `--shadow-card` con
    `overflow:hidden`, badges en pills (verde→gris neutro SUAVE con border hairline; BAJO sigue
    solo rojo), paginación/acciones/modal con radios del sistema, enlaces de fila hover teal,
    **entrada con stagger corto** (3 bloques +30ms, emilkowalski), overlay modal oscuro calido.
    Header: **navegación Inicio/Materiales** (`NavLink` con `.active` teal).
  - **`HomePage.tsx` NUEVA (página principal tras login, ruta `/`):** hero con wordmark H1
    "Inventario de materiales en tiempo real" + CTA "Ver inventario" (Link directo, el elemento
    vistoso NO va en la tabla), **3 burbujas animadas** (blobs radiales teal/naranja que derivan
    lento, capa `pointer-events:none`, GPU-safe), **stats REALES del backend** (total materiales,
    categorías, unidades — `listar(0,1)`/`listarCategorias`/`listarUnidades` vía `Promise.allSettled`,
    nunca datos inventados), grid de módulos (Materiales activo; Proveedores/Ubicaciones/
    Movimientos/Alertas/Reportes como "Próximamente" con su RF real del backlog), pie con
    "AGREGADOS EL TONUSCO S.A.S." (únicamente el nombre ya documentado; sin teléfonos/
    direcciones ficticios). Craft-floor cumplido: sin eyebrow sobre H1, sin números de sección,
    naranja SOLO en el CTA, teal en branding.
  - `AppRoutes.tsx`: el índice ya no redirige a `/materiales`; apunta a `HomePage`.
- **Verificación en navegador real (CDP, Brave real, login real, app :5174):**
  - Login: tarjeta `12px` + sombra `rgba(41,37,36,.08) 4px 12px`, fondo `rgb(247,247,245)`,
    `background-image` radial (no grid).
  - Home: H1 presente, 3 burbujas, nav activo; stats `[8, 7, 6]` = total materiales, categorias,
    unidades (fetch `/api/materiales` 200 OK); **burbuja en movimiento** (matrix de transform
    cambiando frame a frame); hover de card activa `transform translateY(-3.07px)` + `:hover: true`.
  - Materiales: tarjeta tabla `10px` + sombra, boton `8px`, nav activo "Materiales"; **stagger de
    entrada real** `[0.77,0.62,0.35] → [1,1,1]` (los 3 bloques entran secuencialmente); 8 filas.
  - Sin errores de consola; `npm run build` y `oxlint` limpios.
- **Regla de color (D-25) intacta:** naranja solo CTA, BAJO solo rojo, teal solo header/hero/
  escuadra/focus. Sin cambios de backend, BD ni migraciones. Dependencias nuevas: ninguna
  (skills ya instaladas en D-29).
- **Impacto:** la aplicación ahora "se ve distinta de verdad" (fondo hueso, radios, sombras),
  la página principal existe con datos reales y motion visible. Riesgo: no se mide el scroll en
  móvil (las burbujas usan `will-change` solo al animar; el ambiente usa `background-attachment:
  fixed`, correcto en desktop, degradado aceptable en iOS). Queda pendiente confirmar en el
  navegador real del usuario con hard-refresh (`Ctrl+Shift+R` en `http://localhost:5174`).

---

## D-31 — Elemento animado del hero (Fase 11, iteración 6) — **SUPERADA por D-33**

- **Fecha:** 2026-09-07
- **Problema:** con el rediseño de D-30 aprobado, el usuario pidió un elemento interactivo
  para el espacio al frente de "Inventario de materiales en tiempo real" (el hero).
- **Decisión original:** pila 3D de bloques de construcción (CSS 3D, arrastre). Implementada
  y verificada por CDP (arrastre con spring, autogiro, hint). Sin embargo, el usuario no quedó
  convencido y aportó un recurso de grúa cinemática SVG; en la misma iteración la D-31 fue
  sustituida por la D-33. Se eliminó `Pila3D.tsx` y sus estilos.
- **Nota de trazabilidad:** el recurso del usuario era un esqueleto incompleto (JSX vacío,
  `lucide-react` no instalado, `setInterval` con riesgo de ciclo solapado). Su concepto
  (estados moving_to_block/lifting/dropping/returning y variantes de pluma/carro/cable) sí se
  aprovechó como base de la D-33.

## D-33 — Grúa cinemática de construcción en el hero (Fase 11, iteración 6)

- **Fecha:** 2026-09-07
- **Problema:** el usuario no quedó convencido del 3D anterior (D-31) y facilitó un recurso:
  una escena SVG cinemática de grúa que levanta un bloque, lo traslada y lo deposita en una
  torre de obra, en bucle infinito. Preguntó si servía.
- **Evaluación del recurso (honesta):** el concepto es excelente (narrativa visible, coherente
  con la identidad "materiales de obra"), pero el código no era usable tal cual: `return ();`
  vacío (el SVG nunca se escribió), dependencia `lucide-react` ausente, bucle con
  `setInterval(cycle, 9000)` + ejecución inmediata (solapamiento), y `boomVariants` con
  ángulos arbitrarios. Se consultó al usuario (skill brainstorming): sustituir la Pila3D por
  la grúa, instalar `lucide-react`, bucle automático.
- **Decisión:** nuevo componente `frontend/src/components/GruaConstruccion.tsx`,
  **SVG propio articulado** + `lucide-react` (HardHat en la cabina como acento naranja
  decorativo, Truck de atrezo) + framer-motion. Correcciones técnicas frente al recurso:
  - **Bucle con `setTimeout` recursivo** (nunca `setInterval`: no solapa ciclos).
  - **Articulación coherente**: pluma, carro, cable y bloque viven en un mismo grupo cuyo
    pivote es la punta del mástil (126,46); el cable cuelga vertical bajo el carro y el
    bloque aterriza sobre el tope de la torre (colgada calculada por piso).
  - Estados: `moving_to_block → lifting → dropping → returning → idle`, duraciones
    CS 1500+1500+1500+1500+700 ≈ 6.7s. La torre crece 1 piso por ciclo (máx 6) y se
    regenera a 3 (`pisoRef` para no re-armar el efecto cuando cambia el estado).
  - `prefers-reduced-motion`: escena estática (sin ciclo).
- **Verificación CDP real** (login real, 1280x900):
  - `.grua` presente con leyenda "Obra simulada" (sin inventar datos), 2 iconos lucide,
    bloque teal presente, torre con 3 pisos al inicio.
  - **Movimiento frame a frame:** grupo del carro `translateX` 312→390→90 (recorrido
    completo), pluma con matrix de rotación cambiando (spring), cable con `height`
    44px→230px (baja a recoger y sube). La torre pasó de 3 a 5 pisos tras un ciclo.
  - Sin errores de consola; `npm run build` + `oxlint` limpios.
- **Regla de color (D-25) intacta:** carbono/hueso/teal dominan; naranja solo como acento
  decorativo (casco), no CTA.
- **Impacto:** el hero recupera el elemento visible/animado solicitado. Eliminada la Pila3D
  (D-31 superada). Dependencia nueva: `lucide-react`.

## D-32 — Módulo Proveedores en el frontend (Fase 11, iteración 6)

- **Fecha:** 2026-09-07
- **Problema:** tras D-30 solo existían Login, Home y Materiales. El usuario pidió continuar con
  el resto de pantallas; eligió Proveedores como primera después de Materiales.
- **Decisión:** implementar la pantalla completa en `frontend/src/pages/proveedores/`
  (`ProveedoresPage.tsx` + `proveedores.css` que **reutiliza** `materiales.css` vía `@import` —
  botones, cabecera, tabla, modal, formulario — definiendo solo lo específico). Tipos en
  `frontend/src/types/proveedores.ts` y capa de red centralizada en
  `frontend/src/services/proveedorService.ts` (nunca fetches dispersos). Ruta `/proveedores`
  en `AppRoutes.tsx` y link "Proveedores" en el nav del header.
- **Funcionalidad probada (contrato real del backend `ProveedorController`):** cabecera
  "02 Proveedores" + subtítulo RF-010 + CTA "＋ Nuevo proveedor" (solo rol admin), filtro por
  búsqueda, tabla proveedor/NIT/contacto/ubicación/materiales/acciones, paginación, **modal de
  detalle** (con materiales asociados N:M), **modal de formulario** (nombre*†, NIT*,
  contacto/teléfono/ubicación opcionales; `noValidate` + validación propia del cliente,
  mensaje "El nombre es obligatorio."), **modal de desactivación** (bloqueado si
  `cantidadMateriales > 0`, regla RF-010), y **modal de gestión de materiales** (select de
  materiales disponibles vía `materialService.listar(0, 500)` excluyendo los ya asociados;
  marcar/quitar principal con `PUT .../materiales/{idMaterial}?principal`; quitar asociación
  N:M).
- **Verificación CDP real:** tabla mostró "Agregados del Norte · 900123456 · Carlos ·
  Medellín · 1 material"; detalle mostró el material asociado "Gravilla media" con su fecha;
  formulario vacío produce error "El nombre es obligatorio."; modal de gestión abrió con su
  material asociado, botón de principal "○" y select con 1 opción disponible. Sin errores de
  consola; build + oxlint limpios.
- **Nota de trazabilidad:** la fila que aparecía "1 de 1" corresponde a datos **reales** ya
  presentes en la BD dev por pruebas previas (módulo Materiales), nunca inventados por el
  frontend.
- **Impacto:** queda demostrado el patrón de pantalla para los módulos restantes (Ubicaciones y
  lotes → Movimientos → Alertas → Reportes), reutilizando CSS y la capa `services`. Pendiente
  para feedback del usuario: hard-refresh real (`Ctrl+Shift+R`) y revisión visual de la pila 3D.

## D-34 — Módulo Ubicaciones y lotes: backend + indicador RF-015 (Fase 11, iteración 7)

- **Fecha:** 2026-09-07
- **Problema:** RF-015 (control de ubicaciones y lotes de acopio, prioridad baja) y US-16/US-17
  pedían gestionar zonas y lotes; las tablas `zonas_acopio` y `lotes` existían desde V1 pero no
  había entidades JPA, repositorios, servicios ni endpoints. Además el enlace material→zona
  (`materiales.id_zona`) existía en BD pero no estaba mapeado en `Material`, por lo que no se
  cumplía la salida de RF-015 (indicador de ubicación en la pantalla de consulta).
- **Decisión:** implementar el módulo backend completo en `com.tonuapp.ubicaciones`
  (`ZonaAcopio`/`Lote` en `domain`, `ZonaAcopioRepository`/`LoteRepository`, mappers, DTos,
  `ZonaAcopioService`/`LoteService`, `ZonaAcopioController` (`/api/zonas-acopio`) y
  `LoteController` (`/api/lotes`)), siguiendo el patrón de proveedores/materiales:
  - **RF-015:** `tipo_material_permitido` de la zona valida la categoría del material al
    asignarlo (`POST /api/zonas-acopio/{id}/materiales/{idMaterial}`, 409 si incompatible) y al
    cambiar el tipo de una zona con materiales asignados (409 + listado de incompatibles). La
    asignación se materializa en `materiales.id_zona` (FK ya existente, mapeada ahora en
    `Material` vía `@ManyToOne`).
  - **Indicador de ubicación:** `MaterialResponse` gana `idZona`/`zonaNombre` (llenados por el
    mapper desde la entidad; toque mínimo al módulo Materiales).
  - **Cantidad del lote solo informativa (confirmado por el usuario):** el stock real sigue
    derivándose solo de `movimientos_inventario` (D-02); la cantidad del lote no altera la
    existencia.
  - **Soft delete (D-04):** una zona no se desactiva si tiene materiales activos asignados o
    movimientos que la referencian; un lote no se desactiva si lo referencian movimientos.
  - **Material del lote inmutable tras la creación:** cambiar el material de un lote corrompería
    la trazabilidad de los movimientos que lo referencian (409).
  - **Nombre de zona único:** validación case-insensitive en el servicio + índice UNIQUE
    `uq_zonas_nombre` (migración **V7**) como defensa contra duplicados concurrentes.
  - **Hueco de integridad corregido (AGENTS §4):** `MovimientoService` seteaba `idLote`/`idZona`
    sin validar (hoy 500 por FK); ahora valida que existan activos → 404 limpio.
  - Auditoría: `@Auditable` en todas las mutaciones; `idZona` añadido a los candidatos del
    `AuditAspect`.
- **Impacto:** backend completo y verificado contra la BD real (CRUD zonas/lotes, asignación y
  compatibilidad RF-015, movimientos con lote/zona válidos e inválidos, bloqueos de soft-delete,
  duplicados). 153 tests verdes (16 nuevos `ZonaAcopioServiceTest` + 11 `LoteServiceTest` + 4 de
  movimientos + 1 mapper). Frontend en `frontend/src/pages/ubicaciones/` (`UbicacionesPage` con
  pestanas Zonas/Lotes, `ubicacionService.ts`, `types/ubicaciones.ts`, ruta `/ubicaciones` y
  enlace en el nav), verificado por CDP real (login Admin, tablas con datos, modal de asignación
  marcando 7 materiales incompatibles como deshabilitados, formularios de zona y lote). Pendiente:
  revisión visual del usuario y limpieza de datos de prueba creados durante la verificación.

## D-35 — Corrección de etiquetas RF en Home y subtítulos (verificación frente al PPI)

- **Fecha:** 2026-09-08
- **Problema:** la documentación propia numeraba RF-001..RF-015 siguiendo el PPI, pero las
  etiquetas visuales de `HomePage` y los subtítulos de pantalla estaban desplazadas respecto a ese
  requisito: la tarjeta Proveedores decía "RF-009 a RF-011" (correcto: **RF-010**), la de
  Movimientos "RF-005 a RF-007" (correcto: **RF-011, RF-013**) y el subtítulo de MovimientosPage
  repetía el rango equivocado.
- **Decisión:** alinear la UI con el PPI: Materiales "RF-001 a RF-004", Proveedores "RF-010",
  Ubicaciones y lotes "RF-015", Movimientos "RF-011, RF-013", Alertas "RF-012", Reportes "RF-014".
  RF-001 ("consulta en tiempo real de la disponibilidad", PPI) no es un error de la doc: está
  implementado en Materiales y en los reportes de inventario.
- **Impacto:** solo UI (Home + subtítulos); verificado por CDP en el Home (tarjetas + rutas). Sin
  cambios de backend.

## D-36 — Pantalla Movimientos (RF-011/RF-013) + login por API en las verificaciones CDP

- **Fecha:** 2026-09-08
- **Problema:** culminar la pantalla Movimientos (histórico, entradas/salidas/mermas, ajuste a
  stock objetivo RF-011, anulación, plegable Ubicación RF-015/D-34) y verificar por navegador
  real; el flujo de login por UI (escribir correo y el código del log) era frágil y lento.
- **Decisión:**
  - Pantalla en `frontend/src/pages/movimientos/` (patrón D-32: reutiliza `materiales.css`,
    `services` centralizada, solo Administrador igual que el backend).
  - **Verificación CDP por login directo a la API** (`POST /api/auth/solicitar-codigo` → leer el
    código del log del backend → `POST /api/auth/verificar` → inyectar la sesión en
    `localStorage['tonuapp.session']`): estable, sin depender del flujo visual del login.
- **Impacto:** pantalla desbloqueada en el Home con ruta `/movimientos`; verificación CDP en
  verde (tabla con datos, form 3 tipos, plegable ubicación, ajuste, entrada +5 registrada y
  anulada, filtros). Se corrigió además el parser de códigos del prober (`regex.exec` global →
  `matchAll` con el último match): antes tomaba el código más viejo del log.

## D-37 — Pantalla Alertas (RF-012, D-20)

- **Fecha:** 2026-09-08
- **Problema:** faltaba la pantalla para ver y cerrar las alertas de baja disponibilidad; el
  backend (generación en la misma transacción del movimiento + cierre manual D-20) ya existía y
  la UI no lo exponía.
- **Decisión:** `AlertasPage` en `frontend/src/pages/alertas/` con filtro por estado
  (activa/atendida/todas), tabla de avisos con el material responsable y su estado, y la acción
  "Marcar atendida" (solo sobre activas) que persiste el cierre manual. Solo Administrador.
- **Impacto:** tarjeta "Alertas" (RF-012) desbloqueada y navegable; verificación CDP en verde
  (filtro, badge Activa, cierre de una alerta: activas −1). La alerta cerrada queda como
  `atendida` en la BD (las demás siguen activas).

## D-38 — Pantalla Reportes (RF-014, D-21)

- **Fecha:** 2026-09-08
- **Problema:** los endpoints JSON de reportes (inventario, bajo stock, movimientos, resumen,
  bitácora y exportación pdf/xlsx) existían en el backend sin UI que los consumiera.
- **Decisión:** `ReportesPage` en `frontend/src/pages/reportes/` con pestanas Inventario / Bajo
  stock / Movimientos / Resumen / Bitácora. Las de rango validan "inicio y fin o ninguna"
  (calco `ReporteService`), la exportación descarga el binario (Content-Disposition) y la
  bitácora permite re-descargar. Solo Administrador.
- **Impacto:** tarjeta "Reportes" (RF-014) desbloqueada; las 6 tarjetas del Home quedan
  disponibles (todas las pantallas del backlog de Fase 11 culminadas). Verificación CDP en verde
  (inventario con 8 materiales, bajo stock con 2, resumen con 5, exportación PDF real 200 +
  fila en bitácora + re-descarga 200).

## Corrección mayor de UI detectada en revisión (fijada en esta fase)

En las tablas de Alertas y Reportes el nombre del material se escribió DENTRO del `<span>`
decorativo `.materiales__fila-acento` (la barra naranja absoluta de 3px que acompaña al primer
`<td>` de cada fila). El texto colapsaba dentro de esa franja y rompía las columnas ("remontado").
Se restructuró según el patrón correcto (D-28 revertido): el `<span>` del accento queda vacío y
`aria-hidden`, y el nombre es un elemento hermano (`.alertas__material` / `.reportes__material`).
Verificado en Materiales/Movimientos (invariante conservado) y corregido en Alertas + Reportes.

## D-39 — Envío real del código por correo (SMTP Gmail) manteniendo el log como respaldo

- **Fecha:** 2026-09-09
- **Problema:** el código de acceso (RF-008) solo se mostraba en el log del backend
  (`StubEmailSender`), no se entregaba por correo. El usuario pidió el envío real por Gmail
  conservando el log por si el correo falla.
- **Decisión:**
  - Se agrega `spring-boot-starter-mail` y una implementación real `SmtpEmailSender` que
    implementa la misma interfaz `EmailSender` (el contrato no cambió; `AuthService` no se tocó).
  - **El código SIEMPRE queda en el log** (`[DEV] Codigo de acceso para {}: {}`), igual que
    antes, y además se intenta entregar por SMTP Gmail (smtp.gmail.com:587, STARTTLS).
  - Si el envío falla (sin internet, credenciales inválidas) se **captura el error y NO se
    propaga**: el login no se rompe y el código ya está disponible en el log.
  - Configuración propia `tonuapp.mail.*` (`MailProperties` + `MailConfig`), sin depender de la
    autoconfiguración `spring.mail.*` de Spring Boot. Credenciales en `application-local.yml`
    (gitignored) o variables `TONUAPP_MAIL_*`. Por defecto `enabled=false`.
  - Se eliminó `StubEmailSender` (reemplazado).
- **Impacto:** los usuarios reciben el código real por correo; si no llega, se puede leer del
  log. Tests actualizados (4 nuevos en `SmtpEmailSenderTest`: deshabilitado, sin credenciales,
  envío OK, fallo no propagado) — 161 tests verdes en total. Requiere una **App Password** de
  Gmail (16 caracteres), no la contraseña normal (Google bloquea SMTP con la contraseña común).
