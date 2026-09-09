-- =====================================================================================
-- Flyway: V3__materiales_catalogos.sql - Catalogos de materiales
--
-- Aplica las decisiones D-15 (tabla categorias), D-16 (tabla unidades_medida) y D-17
-- (materiales.stock materializado). Reemplaza las columnas de texto tipo / unidad_medida
-- por claves foraneas a los catalogos.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 1. categorias - D-15 (tabla propia, editable desde la aplicacion)
-- -------------------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.categorias', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.categorias (
        id_categoria INT IDENTITY(1,1) NOT NULL,
        nombre       NVARCHAR(100)       NOT NULL,
        activo       BIT                 NOT NULL CONSTRAINT df_categorias_activo DEFAULT 1,
        CONSTRAINT pk_categorias PRIMARY KEY (id_categoria),
        CONSTRAINT uq_categorias_nombre UNIQUE (nombre)
    );
END
GO

-- -------------------------------------------------------------------------------------
-- 2. unidades_medida - D-16 (tabla catalogo para dropdowns, sin hardcodear)
-- -------------------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.unidades_medida', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.unidades_medida (
        id_unidad   INT IDENTITY(1,1) NOT NULL,
        nombre      NVARCHAR(50)      NOT NULL,
        abreviatura NVARCHAR(10)      NOT NULL,
        CONSTRAINT pk_unidades_medida PRIMARY KEY (id_unidad),
        CONSTRAINT uq_unidades_medida_nombre UNIQUE (nombre)
    );
END
GO

-- -------------------------------------------------------------------------------------
-- 3. Datos iniciales (referencia generica, NO son datos reales de la empresa)
-- -------------------------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM dbo.categorias)
BEGIN
    INSERT INTO dbo.categorias (nombre) VALUES
        (N'Arena'),
        (N'Gravilla'),
        (N'Cemento'),
        (N'Hierro'),
        (N'Bloques'),
        (N'Tuberia'),
        (N'Agregados');
END
GO

IF NOT EXISTS (SELECT 1 FROM dbo.unidades_medida)
BEGIN
    INSERT INTO dbo.unidades_medida (nombre, abreviatura) VALUES
        (N'Metro', N'm'),
        (N'Metro cuadrado', N'm2'),
        (N'Metro cubico', N'm3'),
        (N'Kilogramo', N'kg'),
        (N'Unidad', N'un'),
        (N'Litro', N'l');
END
GO

-- -------------------------------------------------------------------------------------
-- 4. dbo.materiales: renombrar cantidad_disponible -> stock (D-17)
--    Se separan los DROP (antes), el sp_rename (en su propio batch) y los nuevos
--    constraints (despues), porque T-SQL compila cada batch completo y 'stock' no existe
--    hasta que el sp_rename se haya ejecutado.
-- -------------------------------------------------------------------------------------
IF COL_LENGTH(N'dbo.materiales', N'cantidad_disponible') IS NOT NULL
   AND COL_LENGTH(N'dbo.materiales', N'stock') IS NULL
BEGIN
    ALTER TABLE dbo.materiales DROP CONSTRAINT ck_materiales_cantidad;
    ALTER TABLE dbo.materiales DROP CONSTRAINT df_materiales_cantidad;
END
GO

IF COL_LENGTH(N'dbo.materiales', N'cantidad_disponible') IS NOT NULL
   AND COL_LENGTH(N'dbo.materiales', N'stock') IS NULL
BEGIN
    EXEC sp_rename N'dbo.materiales.cantidad_disponible', N'stock', N'COLUMN';
END
GO

IF COL_LENGTH(N'dbo.materiales', N'stock') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'ck_materiales_stock')
BEGIN
    ALTER TABLE dbo.materiales ADD CONSTRAINT ck_materiales_stock CHECK (stock >= 0);
END
GO

IF COL_LENGTH(N'dbo.materiales', N'stock') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.default_constraints WHERE name = N'df_materiales_stock')
BEGIN
    ALTER TABLE dbo.materiales ADD CONSTRAINT df_materiales_stock DEFAULT 0 FOR stock;
END
GO

-- -------------------------------------------------------------------------------------
-- 5. dbo.materiales: id_categoria (D-15) e id_unidad (D-16) con FKs
-- -------------------------------------------------------------------------------------
IF COL_LENGTH(N'dbo.materiales', N'id_categoria') IS NULL
BEGIN
    ALTER TABLE dbo.materiales ADD id_categoria INT NULL;
END
GO

IF COL_LENGTH(N'dbo.materiales', N'id_unidad') IS NULL
BEGIN
    ALTER TABLE dbo.materiales ADD id_unidad INT NULL;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'fk_materiales_categoria')
BEGIN
    ALTER TABLE dbo.materiales ADD CONSTRAINT fk_materiales_categoria FOREIGN KEY (id_categoria)
        REFERENCES dbo.categorias (id_categoria);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'fk_materiales_unidad')
BEGIN
    ALTER TABLE dbo.materiales ADD CONSTRAINT fk_materiales_unidad FOREIGN KEY (id_unidad)
        REFERENCES dbo.unidades_medida (id_unidad);
END
GO

-- -------------------------------------------------------------------------------------
-- 6. Indices para las nuevas FKs
-- -------------------------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_materiales_categoria' AND object_id = OBJECT_ID(N'dbo.materiales'))
BEGIN
    CREATE INDEX ix_materiales_categoria ON dbo.materiales (id_categoria);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_materiales_unidad' AND object_id = OBJECT_ID(N'dbo.materiales'))
BEGIN
    CREATE INDEX ix_materiales_unidad ON dbo.materiales (id_unidad);
END
GO

-- -------------------------------------------------------------------------------------
-- 7. Reemplazar uq (nombre, tipo) por uq (nombre, id_categoria) y eliminar las columnas
--    de texto tipo / unidad_medida (sustituidas por los catalogos D-15/D-16).
--    La tabla esta vacia en el entorno de desarrollo, por lo que el DROP es seguro.
-- -------------------------------------------------------------------------------------
IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'uq_materiales_nombre_tipo' AND object_id = OBJECT_ID(N'dbo.materiales'))
BEGIN
    ALTER TABLE dbo.materiales DROP CONSTRAINT uq_materiales_nombre_tipo;
END
GO

-- ix_materiales_busqueda depende de las columnas tipo/unidad_medida; se elimina antes
-- de dropear dichas columnas y se recrea al final (seccion 8).
IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_materiales_busqueda' AND object_id = OBJECT_ID(N'dbo.materiales'))
BEGIN
    DROP INDEX ix_materiales_busqueda ON dbo.materiales;
END
GO

IF COL_LENGTH(N'dbo.materiales', N'tipo') IS NOT NULL
BEGIN
    ALTER TABLE dbo.materiales DROP COLUMN tipo;
END
GO

IF COL_LENGTH(N'dbo.materiales', N'unidad_medida') IS NOT NULL
BEGIN
    ALTER TABLE dbo.materiales DROP COLUMN unidad_medida;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'uq_materiales_nombre_categoria' AND object_id = OBJECT_ID(N'dbo.materiales'))
BEGIN
    ALTER TABLE dbo.materiales ADD CONSTRAINT uq_materiales_nombre_categoria UNIQUE (nombre, id_categoria);
END
GO

-- -------------------------------------------------------------------------------------
-- 8. Indice de busqueda por nombre (RF-003/RF-004). Reemplaza el indice (nombre, tipo).
-- -------------------------------------------------------------------------------------
IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_materiales_busqueda' AND object_id = OBJECT_ID(N'dbo.materiales'))
BEGIN
    DROP INDEX ix_materiales_busqueda ON dbo.materiales;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_materiales_busqueda' AND object_id = OBJECT_ID(N'dbo.materiales'))
BEGIN
    CREATE INDEX ix_materiales_busqueda ON dbo.materiales (nombre);
END
GO