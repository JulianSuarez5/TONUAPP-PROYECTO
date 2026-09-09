-- =====================================================================================
-- TONUAPP - Prueba de Creacion desde Cero (Fase 1, entregable #5)
-- 1) Dropear BD si existe (simula entorno desde cero)
-- 2) Ejecutar el script definitivo tonuapp_database.sql
-- 3) Verificar tablas, constraints, indices
-- 4) Consultas de prueba
-- =====================================================================================
SET NOCOUNT ON;

-- 1) Borrar por si acaso existe (entorno limpio)
IF DB_ID(N'tonuapp') IS NOT NULL
BEGIN
    ALTER DATABASE tonuapp SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE tonuapp;
END
GO
PRINT N'[OK] Base tonuapp eliminada (entorno desde cero).';
GO

-- 2) Ejecutar el script definitivo
:r C:\Users\juako\Documents\tonuapp\database\tonuapp_database.sql
GO

USE tonuapp;
GO

-- =====================================================================================
-- 3) VERIFICACION DE ESTRUCTURA
-- =====================================================================================
PRINT N'=== TABLAS ===';
SELECT t.name AS tabla
FROM sys.tables t
WHERE t.schema_id = SCHEMA_ID(N'dbo')
ORDER BY t.name;
GO

PRINT N'=== CONSTRAINTS (PK, FK, UNIQUE, CHECK) ===';
SELECT
    t.name  AS tabla,
    kc.type_desc AS tipo,
    kc.name AS constraint_name
FROM sys.key_constraints kc
JOIN sys.tables t ON t.object_id = kc.parent_object_id
WHERE t.schema_id = SCHEMA_ID(N'dbo')
  AND kc.type IN ('PK','UQ')
UNION
SELECT
    t.name,
    'FK',
    fk.name
FROM sys.foreign_keys fk
JOIN sys.tables t ON t.object_id = fk.parent_object_id
UNION
SELECT
    t.name,
    'CHECK',
    cc.name
FROM sys.check_constraints cc
JOIN sys.tables t ON t.object_id = cc.parent_object_id
ORDER BY tabla, tipo, constraint_name;
GO

PRINT N'=== INDICES (no PK) ===';
SELECT
    t.name AS tabla,
    i.name AS indice,
    i.type_desc
FROM sys.indexes i
JOIN sys.tables t ON t.object_id = i.object_id
WHERE t.schema_id = SCHEMA_ID(N'dbo')
  AND i.is_primary_key = 0
  AND i.name IS NOT NULL
ORDER BY t.name, i.name;
GO

PRINT N'=== DATOS SEMILLA ===';
SELECT nombre_rol FROM dbo.roles;
SELECT nombre, correo, es_admin_principal, activo FROM dbo.usuarios;
SELECT nombre_zona, capacidad_maxima, tipo_material_permitido FROM dbo.zonas_acopio;
GO

-- =====================================================================================
-- 4) CONSULTAS DE PRUEBA
-- =====================================================================================
PRINT N'=== Prueba 1: Insertar material y proveedor, asociar (N:M) ===';
INSERT INTO dbo.proveedores (nombre, nit) VALUES (N'Proveedor Prueba A', N'900000001-1');
INSERT INTO dbo.proveedores (nombre, nit) VALUES (N'Proveedor Prueba B', N'900000002-1');
INSERT INTO dbo.materiales (nombre, tipo, unidad_medida, cantidad_disponible, stock_minimo, id_zona)
VALUES (N'Arena de Rio', N'Arena', N'm3', 100.00, 20.00, (SELECT id_zona FROM dbo.zonas_acopio WHERE nombre_zona LIKE N'Patio Sur%'));
INSERT INTO dbo.material_proveedor (id_material, id_proveedor, es_principal)
VALUES (
    (SELECT id_material FROM dbo.materiales WHERE nombre = N'Arena de Rio'),
    (SELECT id_proveedor FROM dbo.proveedores WHERE nit = N'900000001-1'), 1
),
(
    (SELECT id_material FROM dbo.materiales WHERE nombre = N'Arena de Rio'),
    (SELECT id_proveedor FROM dbo.proveedores WHERE nit = N'900000002-1'), 0
);
SELECT m.nombre, COUNT(mp.id_proveedor) AS num_proveedores
FROM dbo.materiales m
JOIN dbo.material_proveedor mp ON mp.id_material = m.id_material
WHERE m.nombre = N'Arena de Rio'
GROUP BY m.nombre;
GO

PRINT N'=== Prueba 2: Movimiento de entrada (fuente de verdad) ===';
INSERT INTO dbo.movimientos_inventario (id_material, id_usuario, tipo_movimiento, cantidad, motivo)
VALUES (
    (SELECT id_material FROM dbo.materiales WHERE nombre = N'Arena de Rio'),
    (SELECT id_usuario FROM dbo.usuarios WHERE correo = N'admin.prueba@tonusco.test'),
    N'entrada', 30.00, NULL
);
SELECT id_movimiento, tipo_movimiento, cantidad, estado FROM dbo.movimientos_inventario;
GO

PRINT N'=== Prueba 3: CHECK - capacidad de zona negativa debe FALLAR ===';
BEGIN TRY
    INSERT INTO dbo.zonas_acopio (nombre_zona, capacidad_maxima) VALUES (N'Zona Invalida', -5.00);
    PRINT N'[FALLO] El CHECK de capacidad no detuvo el valor negativo.';
END TRY
BEGIN CATCH
    PRINT N'[OK] CHECK de zona rechazo valor negativo: ' + ERROR_MESSAGE();
END CATCH;
GO

PRINT N'=== Prueba 4: CHECK - cantidad de movimiento negativa debe FALLAR ===';
BEGIN TRY
    INSERT INTO dbo.movimientos_inventario (id_material, id_usuario, tipo_movimiento, cantidad)
    VALUES (
        (SELECT id_material FROM dbo.materiales WHERE nombre = N'Arena de Rio'),
        (SELECT id_usuario FROM dbo.usuarios WHERE correo = N'admin.prueba@tonusco.test'),
        N'entrada', -10.00
    );
    PRINT N'[FALLO] El CHECK de cantidad no detuvo el valor negativo.';
END TRY
BEGIN CATCH
    PRINT N'[OK] CHECK de cantidad rechazo valor negativo: ' + ERROR_MESSAGE();
END CATCH;
GO

PRINT N'=== Prueba 5: UNIQUE - correo duplicado debe FALLAR ===';
DECLARE @rol_admin INT = (SELECT id_rol FROM dbo.roles WHERE nombre_rol = N'Administrador');
BEGIN TRY
    INSERT INTO dbo.usuarios (nombre, correo, id_rol) VALUES (N'Dup', N'admin.prueba@tonusco.test', @rol_admin);
    PRINT N'[FALLO] UNIQUE de correo no detuvo el duplicado.';
END TRY
BEGIN CATCH
    PRINT N'[OK] UNIQUE de correo rechazo duplicado: ' + ERROR_MESSAGE();
END CATCH;
GO

PRINT N'=== Prueba 6: UNIQUE - material (nombre+tipo) duplicado debe FALLAR ===';
BEGIN TRY
    INSERT INTO dbo.materiales (nombre, tipo, unidad_medida) VALUES (N'Arena de Rio', N'Arena', N'm3');
    PRINT N'[FALLO] UNIQUE de material no detuvo el duplicado.';
END TRY
BEGIN CATCH
    PRINT N'[OK] UNIQUE de material rechazo duplicado: ' + ERROR_MESSAGE();
END CATCH;
GO

PRINT N'=== Prueba 7: DELETE proveedor con material asociado (debe fallar por FK RESTRICT) ===';
BEGIN TRY
    DELETE FROM dbo.proveedores WHERE nit = N'900000001-1';
    PRINT N'[INFO] DELETE de proveedor con asociacion paso (sin bloqueo) - revisar.';
END TRY
BEGIN CATCH
    PRINT N'[OK] DELETE de proveedor con material asociado bloqueado por FK: ' + ERROR_MESSAGE();
END CATCH;
GO

PRINT N'=== Prueba 8: RF-004 - DELETE material con movimiento registrado (debe fallar por FK) ===';
BEGIN TRY
    DELETE FROM dbo.materiales WHERE nombre = N'Arena de Rio';
    PRINT N'[FALLO] DELETE de material con movimiento paso (sin bloqueo) - revisar.';
END TRY
BEGIN CATCH
    PRINT N'[OK] DELETE de material con movimiento bloqueado por FK: ' + ERROR_MESSAGE();
END CATCH;
GO

PRINT N'=== Prueba 9: Consulta de disponibilidad en tiempo real (simula RF-001) ===';
SELECT m.nombre, m.tipo, m.unidad_medida, m.cantidad_disponible,
       CASE WHEN m.cantidad_disponible <= m.stock_minimo THEN N'CRITICO' ELSE N'OK' END AS estado
FROM dbo.materiales m;
GO

PRINT N'=== FIN DE LA PRUEBA ===';
GO
