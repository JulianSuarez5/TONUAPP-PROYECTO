-- =====================================================================================
-- Flyway: V1__init.sql - Esquema inicial TONUAPP (SQL Server)
--
-- Adaptado de database/tonuapp_database.sql. A diferencia de ese script, aqui NO se crea
-- la base de datos (Flyway se ejecuta dentro del datasource ya conectado) ni se usa
-- CREATE DATABASE/USE.
--
-- Migraciones Flyway se ejecutan UNA sola vez por version; los guards IF OBJECT_ID
-- protegen contra re-ejecucion parcial manual.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 1.1 roles - RF-006 (gestion de usuarios con roles) - tabla flexible
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
        CONSTRAINT pk_usuarios PRIMARY KEY (id_usuario),
        CONSTRAINT uq_usuarios_correo UNIQUE (correo),
        CONSTRAINT fk_usuarios_rol FOREIGN KEY (id_rol)
            REFERENCES dbo.roles (id_rol)
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_usuarios_rol' AND object_id = OBJECT_ID(N'dbo.usuarios'))
BEGIN
    CREATE INDEX ix_usuarios_rol ON dbo.usuarios (id_rol);
END
GO

-- -------------------------------------------------------------------------------------
-- 1.3 codigos_acceso - RF-008 (autenticacion por codigo temporal)
-- -------------------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.codigos_acceso', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.codigos_acceso (
        id_codigo           INT IDENTITY(1,1) NOT NULL,
        id_usuario          INT               NOT NULL,
        codigo              NVARCHAR(10)      NOT NULL,
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

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_codigos_usuario' AND object_id = OBJECT_ID(N'dbo.codigos_acceso'))
BEGIN
    CREATE INDEX ix_codigos_usuario ON dbo.codigos_acceso (id_usuario);
END
GO

-- -------------------------------------------------------------------------------------
-- 1.4 preferencias_usuario - RF-005 (relacion 1:1 con usuarios)
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
-- 1.5 proveedores - RF-010 (NIT unico, soft delete)
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
-- 1.6 zonas_acopio - RF-015
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
        CONSTRAINT ck_zonas_capacidad CHECK (capacidad_maxima >= 0)
    );
END
GO

-- -------------------------------------------------------------------------------------
-- 2.1 materiales - RF-001, RF-002, RF-003, RF-004, RF-011, RF-012, RF-015
-- -------------------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.materiales', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.materiales (
        id_material         INT IDENTITY(1,1) NOT NULL,
        nombre              NVARCHAR(100)     NOT NULL,
        tipo                NVARCHAR(50)      NOT NULL,
        unidad_medida       NVARCHAR(20)      NOT NULL,
        cantidad_disponible DECIMAL(12,2)     NOT NULL CONSTRAINT df_materiales_cantidad DEFAULT 0,
        stock_minimo        DECIMAL(12,2)     NOT NULL CONSTRAINT df_materiales_stock_minimo DEFAULT 0,
        id_zona             INT               NULL,
        activo              BIT               NOT NULL CONSTRAINT df_materiales_activo DEFAULT 1,
        fecha_registro      DATETIME2(0)      NOT NULL CONSTRAINT df_materiales_fecha_registro DEFAULT SYSDATETIME(),
        fecha_actualizacion DATETIME2(0)      NULL,
        CONSTRAINT pk_materiales PRIMARY KEY (id_material),
        CONSTRAINT uq_materiales_nombre_tipo UNIQUE (nombre, tipo),
        CONSTRAINT ck_materiales_cantidad CHECK (cantidad_disponible >= 0),
        CONSTRAINT ck_materiales_stock_minimo CHECK (stock_minimo >= 0),
        CONSTRAINT fk_materiales_zona FOREIGN KEY (id_zona)
            REFERENCES dbo.zonas_acopio (id_zona)
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_materiales_busqueda' AND object_id = OBJECT_ID(N'dbo.materiales'))
BEGIN
    CREATE INDEX ix_materiales_busqueda ON dbo.materiales (nombre, tipo);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_materiales_zona' AND object_id = OBJECT_ID(N'dbo.materiales'))
BEGIN
    CREATE INDEX ix_materiales_zona ON dbo.materiales (id_zona);
END
GO

-- -------------------------------------------------------------------------------------
-- 2.2 material_proveedor - RF-010 (relacion N:M). FKs NO ACTION (D-01 / RF-010).
-- -------------------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.material_proveedor', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.material_proveedor (
        id_material     INT           NOT NULL,
        id_proveedor    INT           NOT NULL,
        es_principal    BIT           NOT NULL CONSTRAINT df_mat_prov_es_principal DEFAULT 0,
        fecha_asociacion DATETIME2(0) NOT NULL CONSTRAINT df_mat_prov_fecha DEFAULT SYSDATETIME(),
        CONSTRAINT pk_material_proveedor PRIMARY KEY (id_material, id_proveedor),
        CONSTRAINT fk_mat_prov_material FOREIGN KEY (id_material)
            REFERENCES dbo.materiales (id_material),
        CONSTRAINT fk_mat_prov_proveedor FOREIGN KEY (id_proveedor)
            REFERENCES dbo.proveedores (id_proveedor)
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_mat_prov_proveedor' AND object_id = OBJECT_ID(N'dbo.material_proveedor'))
BEGIN
    CREATE INDEX ix_mat_prov_proveedor ON dbo.material_proveedor (id_proveedor);
END
GO

-- -------------------------------------------------------------------------------------
-- 2.3 lotes - RF-015, US-17
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

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_lotes_material' AND object_id = OBJECT_ID(N'dbo.lotes'))
BEGIN
    CREATE INDEX ix_lotes_material ON dbo.lotes (id_material);
END
GO

-- -------------------------------------------------------------------------------------
-- 3.1 movimientos_inventario - RF-002, RF-004, RF-013 (fuente de verdad, D-02)
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

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_movimientos_material_fecha' AND object_id = OBJECT_ID(N'dbo.movimientos_inventario'))
BEGIN
    CREATE INDEX ix_movimientos_material_fecha ON dbo.movimientos_inventario (id_material, fecha_movimiento);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_movimientos_usuario' AND object_id = OBJECT_ID(N'dbo.movimientos_inventario'))
BEGIN
    CREATE INDEX ix_movimientos_usuario ON dbo.movimientos_inventario (id_usuario);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_movimientos_tipo' AND object_id = OBJECT_ID(N'dbo.movimientos_inventario'))
BEGIN
    CREATE INDEX ix_movimientos_tipo ON dbo.movimientos_inventario (tipo_movimiento);
END
GO

-- -------------------------------------------------------------------------------------
-- 3.2 ajustes_inventario - RF-011
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

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_ajustes_material' AND object_id = OBJECT_ID(N'dbo.ajustes_inventario'))
BEGIN
    CREATE INDEX ix_ajustes_material ON dbo.ajustes_inventario (id_material);
END
GO

-- -------------------------------------------------------------------------------------
-- 3.3 alertas_inventario - RF-012
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

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_alertas_material' AND object_id = OBJECT_ID(N'dbo.alertas_inventario'))
BEGIN
    CREATE INDEX ix_alertas_material ON dbo.alertas_inventario (id_material, estado);
END
GO

-- -------------------------------------------------------------------------------------
-- 4.1 reportes_generados - RF-014
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

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_reportes_usuario' AND object_id = OBJECT_ID(N'dbo.reportes_generados'))
BEGIN
    CREATE INDEX ix_reportes_usuario ON dbo.reportes_generados (id_usuario);
END
GO

-- -------------------------------------------------------------------------------------
-- 4.2 audit_log - RF-011 y auditoria general (append-only)
-- FK id_usuario nullable, sin CASCADE (default NO ACTION). Soft delete (D-04) => el
-- usuario nunca se borra fisicamente, la FK no genera efectos secundarios.
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
        CONSTRAINT fk_audit_usuario FOREIGN KEY (id_usuario)
            REFERENCES dbo.usuarios (id_usuario)
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_audit_entidad_registro' AND object_id = OBJECT_ID(N'dbo.audit_log'))
BEGIN
    CREATE INDEX ix_audit_entidad_registro ON dbo.audit_log (entidad, id_registro);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_audit_fecha' AND object_id = OBJECT_ID(N'dbo.audit_log'))
BEGIN
    CREATE INDEX ix_audit_fecha ON dbo.audit_log (fecha);
END
GO

-- -------------------------------------------------------------------------------------
-- 5. DATOS SEMILLA (solo los justificados, NUNCA datos reales de la empresa)
-- -------------------------------------------------------------------------------------

-- 5.1 Roles (H1: solo Administrador y Cliente)
IF NOT EXISTS (SELECT 1 FROM dbo.roles WHERE nombre_rol = N'Administrador')
BEGIN
    INSERT INTO dbo.roles (nombre_rol, descripcion)
    VALUES (N'Administrador', N'Acceso completo al sistema: inventario, usuarios, proveedores, reportes, alertas'),
           (N'Cliente', N'Solo puede consultar informacion y realizar solicitudes, sin modificar inventario ni configuracion');
END
GO

-- 5.2 Usuario administrador de PRUEBA
IF NOT EXISTS (SELECT 1 FROM dbo.usuarios WHERE correo = N'admin.prueba@tonusco.test')
BEGIN
    DECLARE @id_rol_admin INT = (SELECT id_rol FROM dbo.roles WHERE nombre_rol = N'Administrador');
    INSERT INTO dbo.usuarios (nombre, correo, id_rol, es_admin_principal)
    VALUES (N'Admin Principal (PRUEBA)', N'admin.prueba@tonusco.test', @id_rol_admin, 1);
END
GO

-- 5.3 Zonas de acopio de PRUEBA
IF NOT EXISTS (SELECT 1 FROM dbo.zonas_acopio WHERE nombre_zona = N'Patio Sur - Lote 1 (PRUEBA)')
BEGIN
    INSERT INTO dbo.zonas_acopio (nombre_zona, capacidad_maxima, tipo_material_permitido)
    VALUES (N'Patio Sur - Lote 1 (PRUEBA)', 500.00, N'Arena'),
           (N'Patio Norte - Lote 1 (PRUEBA)', 500.00, N'Gravilla');
END
GO
