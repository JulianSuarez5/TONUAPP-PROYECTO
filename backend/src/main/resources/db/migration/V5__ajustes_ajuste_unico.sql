-- -------------------------------------------------------------------------------------
-- V5: garantiza el 1:1 del detalle de ajuste con su movimiento (D-19). Se usa un indice
-- unico filtrado para permitir id_movimiento NULL (constraint ck/fk original lo permite)
-- pero impedir que dos ajustes_inventario apunten al mismo movimiento.
-- -------------------------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = N'uq_ajustes_movimiento' AND object_id = OBJECT_ID(N'dbo.ajustes_inventario'))
BEGIN
    CREATE UNIQUE INDEX uq_ajustes_movimiento ON dbo.ajustes_inventario (id_movimiento)
        WHERE id_movimiento IS NOT NULL;
END
GO