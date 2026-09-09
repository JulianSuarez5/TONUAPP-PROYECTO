-- =====================================================================================
-- TONUAPP - Base de Datos Definitiva (SQL Server)
-- Agregados el Tonusco S.A.S. - Gestion de Inventario de Materiales de Construccion
--
-- Motor: Microsoft SQL Server 2019+ (probado en SQLEXPRESS)
-- Referencia funcional: RF-001 a RF-015 (docs/requisitos/02_requisitos_funcionales.md)
-- Historias: US-01 a US-36 (docs/requisitos/03_product_backlog.md)
--
-- ESQUEMA FINAL CONSOLIDADO: incluye TODAS las migraciones Flyway (V1 a V7).
-- Nota: el backend (Spring Boot) declara ddl-auto=none, el esquema lo gobierna Flyway con
-- migraciones incrementales en backend/src/main/resources/db/migration/. Este script es la
-- version "manual" equivalente, en el estilo del proyecto, lista para crear la BD y las
-- 17 tablas de una sola vez (tambien se usa como documentacion del DER).
--
-- Adaptado desde: tonuapp_schema_borrador_mysql.sql (MySQL -> SQL Server)
-- Cambios clave de traduccion:
--   AUTO_INCREMENT           -> IDENTITY(1,1)
--   ENUM(...)                -> NVARCHAR(n) + CHECK
--   BOOLEAN                  -> BIT
--   JSON                     -> NVARCHAR(MAX)
--   ON UPDATE CURRENT_TIMESTAMP -> trigger en dbo (idioma neutro)
--   DATETIME DEFAULT CURR... -> DATETIME2 DEFAULT SYSDATETIME()
--
-- Decisiones de diseno (ver docs/decisions/DECISIONES.md):
--   D-01: Proveedor <-> Material es N:M (tabla intermedia material_proveedor)
--   D-02: Movimientos como fuente de verdad + existencia materializada en materiales
--   D-03: Stock = movimientos + cache materializada, sincronizado en el backend,
--         NO via trigger (control explicito y testeable en el servicio)
--   D-04: Soft delete en entidades criticas (columna 'activo' / 'estado')
-- =====================================================================================

-- =====================================================================================
-- 0. BASE DE DATOS
-- =====================================================================================
IF DB_ID(N'tonuapp') IS NULL
BEGIN
    CREATE DATABASE tonuapp;
END
GO

USE tonuapp;
GO

-- =====================================================================================
-- 1. CATALOGOS / TABLAS BASE
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 1.1 roles - RF-006 (gestion de usuarios con roles)
-- Tabla flexible: se pueden agregar roles sin rehacer el sistema.
-- Por ahora solo se siembran 'Administrador' y 'Cliente' (decision H1).
-- -------------------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.roles', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.roles (
        id_rol          INT IDENTITY(1,1) NOT NULL,
        nombre_rol      NVARCHAR(30)      NOT NULL,
        descripcion     NVARCHAR(150)     NULL,
        CONSTRAINT pk_roles PRIMARY KEY (id_rol),
        CONSTRAINT uq_roles_nombre UNIQUE (nombre_rol)
    );
END
GO

-- -------------------------------------------------------------------------------------
-- 1.2 usuarios - RF-006, RF-008, RF-009
-- Autenticacion por correo + codigo temporal (NO password). El campo correo es unico.
-- 'activo' = soft delete: los usuarios inactivados no se borran fisicamente.
-- -------------------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.usuarios', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.usuarios (
        id_usuario          INT IDENTITY(1,1) NOT NULL,
        nombre              NVARCHAR(100)     NOT NULL,
        correo              NVARCHAR(150)     NOT NULL,
        id_rol              INT               NOT NULL,
        activo              BIT               NOT NULL CONSTRAINT df_usuarios_activo DEFAULT 1,
        es_admin_principal  BIT               NOT NULL CONSTRAINT df_usuarios_es_admin_principal DEFAULT 0,
        fecha_creacion      DATETIME2(0)      NOT NULL CONSTRAINT df_usuarios_fecha_creacion DEFAULT SYSDATETIME(),
        fecha_actualizacion DATETIME2(0)      NULL,
        -- Bloqueo temporal por cuenta (RF-008/D-22, migracion V6)
        intentos_login_fallidos INT            NOT NULL CONSTRAINT df_usuarios_intentos_login DEFAULT 0,
        bloqueo_hasta          DATETIME2       NULL,
        -- version = optimistic locking (V2, D-03): evita ediciones concurrentes
        version                INT             NOT NULL CONSTRAINT df_usuarios_version DEFAULT 0,
        CONSTRAINT pk_usuarios PRIMARY KEY (id_usuario),
        CONSTRAINT uq_usuarios_correo UNIQUE (correo),
        CONSTRAINT ck_usuarios_intentos_login_positivo CHECK (intentos_login_fallidos >= 0),
        CONSTRAINT fk_usuarios_rol FOREIGN KEY (id_rol)
            REFERENCES dbo.roles (id_rol)
    );
END
GO

CREATE INDEX ix_usuarios_rol ON dbo.usuarios (id_rol);
GO

-- -------------------------------------------------------------------------------------
-- 1.3 codigos_acceso - RF-008 (autenticacion por codigo temporal)
-- Vigencia maxima 10 minutos, un solo uso, bloqueo por intentos fallidos.
-- El codigo se almacena HASHEADO (SHA-256 hex, 64 caracteres), nunca en texto plano (V2).
-- -------------------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.codigos_acceso', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.codigos_acceso (
        id_codigo           INT IDENTITY(1,1) NOT NULL,
        id_usuario          INT               NOT NULL,
        codigo              NVARCHAR(64)      NOT NULL,
        fecha_generacion    DATETIME2(0)      NOT NULL CONSTRAINT df_codigos_fecha_generacion DEFAULT SYSDATETIME(),
        fecha_expiracion    DATETIME2(0)      NOT NULL,
        usado               BIT               NOT NULL CONSTRAINT df_codigos_usado DEFAULT 0,
        intentos_fallidos   INT               NOT NULL CONSTRAINT df_codigos_intentos DEFAULT 0,
        CONSTRAINT pk_codigos_acceso PRIMARY KEY (id_codigo),
        CONSTRAINT fk_codigos_usuario FOREIGN KEY (id_usuario)
            REFERENCES dbo.usuarios (id_usuario)
            ON DELETE CASCADE
    );
END
GO

CREATE INDEX ix_codigos_usuario ON dbo.codigos_acceso (id_usuario);
GO

-- -------------------------------------------------------------------------------------
-- 1.4 refresh_tokens - RF-008 (sesiones largas y revocables, D-11)
-- Se guarda el HASH del token, no el token en claro. 'revocado' permite cerrar la
-- sesion desde el backend (logout). Vigencia de refresh = 7 dias.
-- -------------------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.refresh_tokens', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.refresh_tokens (
        id              BIGINT             NOT NULL IDENTITY(1,1),
        id_usuario      INT                NOT NULL,
        token_hash      NVARCHAR(64)       NOT NULL,
        expiracion      DATETIME2(0)       NOT NULL,
        revocado        BIT                NOT NULL CONSTRAINT df_refresh_revocado DEFAULT 0,
        fecha_creacion  DATETIME2(0)       NOT NULL CONSTRAINT df_refresh_fecha_creacion DEFAULT SYSDATETIME(),
        CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
        CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
        CONSTRAINT fk_refresh_tokens_usuario FOREIGN KEY (id_usuario)
            REFERENCES dbo.usuarios (id_usuario)
    );
END
GO

CREATE INDEX ix_refresh_usuario ON dbo.refresh_tokens (id_usuario);
CREATE INDEX ix_refresh_revocado_expiracion ON dbo.refresh_tokens (revocado, expiracion);
GO

-- -------------------------------------------------------------------------------------
-- 1.5 preferencias_usuario - RF-005 (preferencias de visualizacion por usuario)
-- Relacion 1:1 con usuarios. 'filtros_favoritos' se guarda como JSON (NVARCHAR(MAX)).
-- -------------------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.preferencias_usuario', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.preferencias_usuario (
        id_usuario          INT                NOT NULL,
        panel_inicio        NVARCHAR(50)       NOT NULL CONSTRAINT df_preferencias_panel DEFAULT N'dashboard',
        filtros_favoritos   NVARCHAR(MAX)      NULL,
        orden_default       NVARCHAR(30)       NOT NULL CONSTRAINT df_preferencias_orden DEFAULT N'nombre_asc',
        tema_visual         NVARCHAR(20)       NOT NULL CONSTRAINT df_preferencias_tema DEFAULT N'claro',
        CONSTRAINT pk_preferencias_usuario PRIMARY KEY (id_usuario),
        CONSTRAINT fk_preferencias_usuario FOREIGN KEY (id_usuario)
            REFERENCES dbo.usuarios (id_usuario)
            ON DELETE CASCADE
    );
END
GO

-- -------------------------------------------------------------------------------------
-- 1.6 proveedores - RF-010 (registro y gestion de proveedores)
-- NIT unico. 'activo' para soft delete (no se borran fisicamente, RF-010: no eliminar
-- proveedores con materiales asociados).
-- -------------------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.proveedores', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.proveedores (
        id_proveedor    INT IDENTITY(1,1) NOT NULL,
        nombre          NVARCHAR(150)     NOT NULL,
        nit             NVARCHAR(20)      NOT NULL,
        contacto        NVARCHAR(100)     NULL,
        telefono        NVARCHAR(20)      NULL,
        ubicacion       NVARCHAR(200)     NULL,
        activo          BIT               NOT NULL CONSTRAINT df_proveedores_activo DEFAULT 1,
        fecha_registro  DATETIME2(0)      NOT NULL CONSTRAINT df_proveedores_fecha_registro DEFAULT SYSDATETIME(),
        CONSTRAINT pk_proveedores PRIMARY KEY (id_proveedor),
        CONSTRAINT uq_proveedores_nit UNIQUE (nit)
    );
END
GO

-- -------------------------------------------------------------------------------------
-- 1.7 zonas_acopio - RF-015 (control de ubicaciones y lotes de acopio)
-- 'tipo_material_permitido': permite validar incompatibilidad de materiales en una misma
-- zona (RF-015). 'activo' para soft delete.
-- -------------------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.zonas_acopio', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.zonas_acopio (
        id_zona                 INT IDENTITY(1,1) NOT NULL,
        nombre_zona             NVARCHAR(100)     NOT NULL,
        capacidad_maxima        DECIMAL(12,2)     NOT NULL,
        tipo_material_permitido NVARCHAR(100)     NULL,
        activo                  BIT               NOT NULL CONSTRAINT df_zonas_activo DEFAULT 1,
        CONSTRAINT pk_zonas_acopio PRIMARY KEY (id_zona),
        CONSTRAINT uq_zonas_nombre UNIQUE (nombre_zona),
        CONSTRAINT ck_zonas_capacidad CHECK (capacidad_maxima >= 0)
    );
END
GO

-- =====================================================================================
-- 2. MATERIALES Y RELACIONES
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 2.1 categorias - D-15 (RF-001, RF-002)
-- Catalogo editado desde la aplicacion. Evita re-migrar el esquema al agregar categorias.
-- -------------------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.categorias', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.categorias (
        id_categoria INT IDENTITY(1,1) NOT NULL,
        nombre       NVARCHAR(100)     NOT NULL,
        activo       BIT               NOT NULL CONSTRAINT df_categorias_activo DEFAULT 1,
        CONSTRAINT pk_categorias PRIMARY KEY (id_categoria),
        CONSTRAINT uq_categorias_nombre UNIQUE (nombre)
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM dbo.categorias)
BEGIN
    INSERT INTO dbo.categorias (nombre) VALUES
        (N'Arena'), (N'Gravilla'), (N'Cemento'), (N'Hierro'), (N'Bloques'), (N'Tuberia'), (N'Agregados');
END
GO

-- -------------------------------------------------------------------------------------
-- 2.2 unidades_medida - D-16
-- Catalogo de unidades; puebla los dropdowns del frontend sin hardcodear valores.
-- -------------------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.unidades_medida', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.unidades_medida (
        id_unidad    INT IDENTITY(1,1) NOT NULL,
        nombre       NVARCHAR(50)      NOT NULL,
        abreviatura  NVARCHAR(10)      NOT NULL,
        CONSTRAINT pk_unidades_medida PRIMARY KEY (id_unidad),
        CONSTRAINT uq_unidades_nombre UNIQUE (nombre)
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM dbo.unidades_medida)
BEGIN
    INSERT INTO dbo.unidades_medida (nombre, abreviatura) VALUES
        (N'Metro', N'm'), (N'Metro cuadrado', N'm2'), (N'Metro cubico', N'm3'),
        (N'Kilogramo', N'kg'), (N'Unidad', N'un'), (N'Litro', N'l');
END
GO

-- -------------------------------------------------------------------------------------
-- 2.3 materiales - RF-001, RF-002, RF-003, RF-004, RF-011, RF-012, RF-015
-- NUCLEO del inventario.
--   stock               = existencia materializada (D-03/D-17). Se sincroniza con los
--                         movimientos en el backend, dentro de la misma transaccion.
--   stock_minimo        = umbral para alertas (RF-012).
--   NO hay id_proveedor aqui: la relacion con proveedores es N:M via material_proveedor.
--   'activo' para soft delete (RF-004: no eliminar materiales con movimientos).
-- -------------------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.materiales', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.materiales (
        id_material         INT IDENTITY(1,1) NOT NULL,
        nombre              NVARCHAR(100)     NOT NULL,
        id_categoria        INT               NOT NULL,
        id_unidad           INT               NOT NULL,
        stock               DECIMAL(12,2)     NOT NULL CONSTRAINT df_materiales_stock DEFAULT 0,
        stock_minimo        DECIMAL(12,2)     NOT NULL CONSTRAINT df_materiales_stock_minimo DEFAULT 0,
        id_zona             INT               NULL,
        activo              BIT               NOT NULL CONSTRAINT df_materiales_activo DEFAULT 1,
        fecha_registro      DATETIME2(0)      NOT NULL CONSTRAINT df_materiales_fecha_registro DEFAULT SYSDATETIME(),
        fecha_actualizacion DATETIME2(0)      NULL,
        version             INT               NOT NULL CONSTRAINT df_materiales_version DEFAULT 0,
        CONSTRAINT pk_materiales PRIMARY KEY (id_material),
        CONSTRAINT uq_materiales_nombre_categoria UNIQUE (nombre, id_categoria),
        CONSTRAINT ck_materiales_stock CHECK (stock >= 0),
        CONSTRAINT ck_materiales_stock_minimo CHECK (stock_minimo >= 0),
        CONSTRAINT fk_materiales_categoria FOREIGN KEY (id_categoria)
            REFERENCES dbo.categorias (id_categoria),
        CONSTRAINT fk_materiales_unidad FOREIGN KEY (id_unidad)
            REFERENCES dbo.unidades_medida (id_unidad),
        CONSTRAINT fk_materiales_zona FOREIGN KEY (id_zona)
            REFERENCES dbo.zonas_acopio (id_zona)
    );
END
GO

CREATE INDEX ix_materiales_busqueda ON dbo.materiales (nombre);
CREATE INDEX ix_materiales_categoria ON dbo.materiales (id_categoria);
CREATE INDEX ix_materiales_unidad ON dbo.materiales (id_unidad);
CREATE INDEX ix_materiales_zona ON dbo.materiales (id_zona);
GO

-- -------------------------------------------------------------------------------------
-- 2.4 material_proveedor - RF-010 (relacion N:M proveedor <-> material, D-01)
-- 'es_principal' identifica el proveedor principal de un material si se necesita.
-- -------------------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.material_proveedor', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.material_proveedor (
        id_material     INT          NOT NULL,
        id_proveedor    INT          NOT NULL,
        es_principal    BIT          NOT NULL CONSTRAINT df_mat_prov_es_principal DEFAULT 0,
        fecha_asociacion DATETIME2(0) NOT NULL CONSTRAINT df_mat_prov_fecha DEFAULT SYSDATETIME(),
        CONSTRAINT pk_material_proveedor PRIMARY KEY (id_material, id_proveedor),
        -- NO ON DELETE CASCADE: la relacion proveedor<->material es critica.
        -- RF-010 prohibe eliminar proveedores (o materiales) con asociaciones.
        -- El borrado se bloquea a nivel de FK (NO ACTION) y se valida en el backend.
        CONSTRAINT fk_mat_prov_material FOREIGN KEY (id_material)
            REFERENCES dbo.materiales (id_material),
        CONSTRAINT fk_mat_prov_proveedor FOREIGN KEY (id_proveedor)
            REFERENCES dbo.proveedores (id_proveedor)
    );
END
GO

CREATE INDEX ix_mat_prov_proveedor ON dbo.material_proveedor (id_proveedor);
GO

-- -------------------------------------------------------------------------------------
-- 2.5 lotes - RF-015, US-17 (control de lotes de acopio asociados a materiales)
-- Un material puede tener multiples lotes. 'cantidad' = cantidad del lote.
-- 'activo' para soft delete.
-- -------------------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.lotes', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.lotes (
        id_lote         INT IDENTITY(1,1) NOT NULL,
        id_material     INT               NOT NULL,
        codigo_lote     NVARCHAR(50)      NOT NULL,
        cantidad        DECIMAL(12,2)     NOT NULL,
        fecha_ingreso   DATETIME2(0)      NOT NULL CONSTRAINT df_lotes_fecha_ingreso DEFAULT SYSDATETIME(),
        activo          BIT               NOT NULL CONSTRAINT df_lotes_activo DEFAULT 1,
        CONSTRAINT pk_lotes PRIMARY KEY (id_lote),
        CONSTRAINT uq_lotes_codigo UNIQUE (codigo_lote),
        CONSTRAINT ck_lotes_cantidad CHECK (cantidad >= 0),
        CONSTRAINT fk_lotes_material FOREIGN KEY (id_material)
            REFERENCES dbo.materiales (id_material)
    );
END
GO

CREATE INDEX ix_lotes_material ON dbo.lotes (id_material);
GO

-- =====================================================================================
-- 3. MOVIMIENTOS Y CONTROL DE INVENTARIO
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 3.1 movimientos_inventario - RF-002 (entradas), RF-004 (salidas/ediciones),
--     RF-013 (mermas), US-18 a US-23.
-- FUENTE DE VERDAD del inventario (D-02). Cada operacion (entrada, salida_venta,
-- salida_merma, ajuste) se registra aqui. La cantidad es SIEMPRE positiva; el signo lo
-- define 'tipo_movimiento'. 'estado' permite anular movimientos sin borrarlos (US-22).
-- 'id_lote'/'id_zona' opcionales segun corresponda.
-- -------------------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.movimientos_inventario', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.movimientos_inventario (
        id_movimiento   INT IDENTITY(1,1) NOT NULL,
        id_material     INT               NOT NULL,
        id_usuario      INT               NOT NULL,
        tipo_movimiento NVARCHAR(20)      NOT NULL,
        cantidad        DECIMAL(12,2)     NOT NULL,
        id_lote         INT               NULL,
        id_zona         INT               NULL,
        motivo          NVARCHAR(200)     NULL,
        observaciones   NVARCHAR(300)     NULL,
        estado          NVARCHAR(10)      NOT NULL CONSTRAINT df_movimientos_estado DEFAULT N'activo',
        fecha_movimiento DATETIME2(0)     NOT NULL CONSTRAINT df_movimientos_fecha DEFAULT SYSDATETIME(),
        CONSTRAINT pk_movimientos PRIMARY KEY (id_movimiento),
        CONSTRAINT ck_movimientos_tipo CHECK (tipo_movimiento IN (N'entrada', N'salida_venta', N'salida_merma', N'ajuste')),
        CONSTRAINT ck_movimientos_cantidad CHECK (cantidad > 0),
        CONSTRAINT ck_movimientos_estado CHECK (estado IN (N'activo', N'anulado')),
        CONSTRAINT fk_movimientos_material FOREIGN KEY (id_material)
            REFERENCES dbo.materiales (id_material),
        CONSTRAINT fk_movimientos_usuario FOREIGN KEY (id_usuario)
            REFERENCES dbo.usuarios (id_usuario),
        CONSTRAINT fk_movimientos_lote FOREIGN KEY (id_lote)
            REFERENCES dbo.lotes (id_lote),
        CONSTRAINT fk_movimientos_zona FOREIGN KEY (id_zona)
            REFERENCES dbo.zonas_acopio (id_zona)
    );
END
GO

CREATE INDEX ix_movimientos_material_fecha ON dbo.movimientos_inventario (id_material, fecha_movimiento);
CREATE INDEX ix_movimientos_usuario ON dbo.movimientos_inventario (id_usuario);
CREATE INDEX ix_movimientos_tipo ON dbo.movimientos_inventario (tipo_movimiento);
GO

-- -------------------------------------------------------------------------------------
-- 3.2 ajustes_inventario - RF-011 (ajuste y conciliacion de inventario)
-- Requiere 'motivo' y registra 'cantidad_anterior'/'cantidad_nueva'. No permite inventario
-- negativo (cantidad_nueva >= 0). Referencia al movimiento 'ajuste' que lo origino.
-- -------------------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.ajustes_inventario', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.ajustes_inventario (
        id_ajuste           INT IDENTITY(1,1) NOT NULL,
        id_material         INT               NOT NULL,
        id_usuario          INT               NOT NULL,
        id_movimiento       INT               NULL,
        cantidad_anterior   DECIMAL(12,2)     NOT NULL,
        cantidad_nueva      DECIMAL(12,2)     NOT NULL,
        motivo              NVARCHAR(200)     NOT NULL,
        fecha_ajuste        DATETIME2(0)      NOT NULL CONSTRAINT df_ajustes_fecha DEFAULT SYSDATETIME(),
        CONSTRAINT pk_ajustes PRIMARY KEY (id_ajuste),
        CONSTRAINT ck_ajustes_cantidad_nueva CHECK (cantidad_nueva >= 0),
        CONSTRAINT fk_ajustes_material FOREIGN KEY (id_material)
            REFERENCES dbo.materiales (id_material),
        CONSTRAINT fk_ajustes_usuario FOREIGN KEY (id_usuario)
            REFERENCES dbo.usuarios (id_usuario),
        CONSTRAINT fk_ajustes_movimiento FOREIGN KEY (id_movimiento)
            REFERENCES dbo.movimientos_inventario (id_movimiento)
    );
END
GO

CREATE INDEX ix_ajustes_material ON dbo.ajustes_inventario (id_material);
GO

-- Indice UNICO filtrado: un ajuste solo puede referenciar su propio movimiento (D-19).
-- Permite NULL en id_movimiento, pero impide que dos ajustes apunten al mismo movimiento.
CREATE UNIQUE INDEX uq_ajustes_movimiento ON dbo.ajustes_inventario (id_movimiento)
    WHERE id_movimiento IS NOT NULL;
GO

-- -------------------------------------------------------------------------------------
-- 3.3 alertas_inventario - RF-012 (alertas por baja disponibilidad)
-- Se almacenan permanentemente (D-H4). 'estado' = activa / atendida. Se generan al quedar
-- 'cantidad_disponible <= stock_minimo' tras un movimiento (logica en el backend).
-- -------------------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.alertas_inventario', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.alertas_inventario (
        id_alerta       INT IDENTITY(1,1) NOT NULL,
        id_material     INT               NOT NULL,
        fecha_generada  DATETIME2(0)      NOT NULL CONSTRAINT df_alertas_fecha DEFAULT SYSDATETIME(),
        estado          NVARCHAR(20)      NOT NULL CONSTRAINT df_alertas_estado DEFAULT N'activa',
        mensaje         NVARCHAR(200)     NOT NULL,
        CONSTRAINT pk_alertas PRIMARY KEY (id_alerta),
        CONSTRAINT ck_alertas_estado CHECK (estado IN (N'activa', N'atendida')),
        CONSTRAINT fk_alertas_material FOREIGN KEY (id_material)
            REFERENCES dbo.materiales (id_material)
    );
END
GO

CREATE INDEX ix_alertas_material ON dbo.alertas_inventario (id_material, estado);
GO

-- =====================================================================================
-- 4. AUDITORIA Y REPORTES
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 4.1 reportes_generados - RF-014 (bitacora de exportaciones)
-- Registra cada exportacion generada (quien, cuando, formato, rango, ruta).
-- -------------------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.reportes_generados', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.reportes_generados (
        id_reporte          INT IDENTITY(1,1) NOT NULL,
        id_usuario          INT               NOT NULL,
        tipo_reporte        NVARCHAR(50)      NOT NULL,
        formato             NVARCHAR(10)      NOT NULL,
        fecha_generacion    DATETIME2(0)      NOT NULL CONSTRAINT df_reportes_fecha DEFAULT SYSDATETIME(),
        rango_fecha_inicio  DATE              NULL,
        rango_fecha_fin     DATE              NULL,
        ruta_archivo        NVARCHAR(300)     NULL,
        CONSTRAINT pk_reportes PRIMARY KEY (id_reporte),
        CONSTRAINT ck_reportes_formato CHECK (formato IN (N'pdf', N'xlsx')),
        CONSTRAINT fk_reportes_usuario FOREIGN KEY (id_usuario)
            REFERENCES dbo.usuarios (id_usuario)
    );
END
GO

CREATE INDEX ix_reportes_usuario ON dbo.reportes_generados (id_usuario);
GO

-- -------------------------------------------------------------------------------------
-- 4.2 audit_log - RF-011 y requisito general de auditoria (D-06 / H6)
-- Append-only. Registra QUIEN hizo QUE, CUANDO y SOBRE QUE registro.
-- 'valores_antes'/'valores_despues' como JSON para cambios de entidades.
-- Esta tabla la alimenta un interceptor AOP en el backend (no un trigger).
-- -------------------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.audit_log', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.audit_log (
        id              BIGINT IDENTITY(1,1) NOT NULL,
        entidad         NVARCHAR(100)        NOT NULL,
        id_registro     NVARCHAR(50)         NOT NULL,
        operacion       NVARCHAR(20)         NOT NULL,
        id_usuario      INT                  NULL,
        fecha           DATETIME2(0)         NOT NULL CONSTRAINT df_audit_fecha DEFAULT SYSDATETIME(),
        valores_antes   NVARCHAR(MAX)        NULL,
        valores_despues NVARCHAR(MAX)        NULL,
        CONSTRAINT pk_audit PRIMARY KEY (id),
        CONSTRAINT ck_audit_operacion CHECK (operacion IN (N'CREATE', N'UPDATE', N'DELETE', N'MOVEMENT', N'ADJUST')),
        -- FK opcional (nullable) sobre usuarios, sin CASCADE/NO ACTION explicito
        -- (default, suficiente). Con soft delete (D-04) el usuario nunca se borra
        -- fisicamente, por lo que la FK no produce efectos secundarios.
        CONSTRAINT fk_audit_usuario FOREIGN KEY (id_usuario)
            REFERENCES dbo.usuarios (id_usuario)
    );
END
GO

CREATE INDEX ix_audit_entidad_registro ON dbo.audit_log (entidad, id_registro);
CREATE INDEX ix_audit_fecha ON dbo.audit_log (fecha);
GO

-- =====================================================================================
-- 5. DATOS SEMILLA (solo los justificados, NUNCA datos reales de la empresa)
-- Los datos de PRUEBA estan claramente marcados como tales.
-- =====================================================================================

-- 5.1 Roles (decision H1: solo Administrador y Cliente por ahora)
IF NOT EXISTS (SELECT 1 FROM dbo.roles WHERE nombre_rol = N'Administrador')
BEGIN
    INSERT INTO dbo.roles (nombre_rol, descripcion)
    VALUES (N'Administrador', N'Acceso completo al sistema: inventario, usuarios, proveedores, reportes, alertas'),
           (N'Cliente', N'Solo puede consultar informacion y realizar solicitudes, sin modificar inventario ni configuracion');
END
GO

-- 5.2 Usuario administrador principal de PRUEBA (marcado como tal; NO es dato real)
IF NOT EXISTS (SELECT 1 FROM dbo.usuarios WHERE correo = N'admin.prueba@tonusco.test')
BEGIN
    DECLARE @id_rol_admin INT = (SELECT id_rol FROM dbo.roles WHERE nombre_rol = N'Administrador');
    INSERT INTO dbo.usuarios (nombre, correo, id_rol, es_admin_principal)
    VALUES (N'Admin Principal (PRUEBA)', N'admin.prueba@tonusco.test', @id_rol_admin, 1);
END
GO

-- 5.3 Zonas de acopio de PRUEBA (marcadas como tales; NO son datos reales)
IF NOT EXISTS (SELECT 1 FROM dbo.zonas_acopio WHERE nombre_zona = N'Patio Sur - Lote 1 (PRUEBA)')
BEGIN
    INSERT INTO dbo.zonas_acopio (nombre_zona, capacidad_maxima, tipo_material_permitido)
    VALUES (N'Patio Sur - Lote 1 (PRUEBA)', 500.00, N'Arena'),
           (N'Patio Norte - Lote 1 (PRUEBA)', 500.00, N'Gravilla');
END
GO

PRINT N'Base de datos TONUAPP creada correctamente.';
GO
