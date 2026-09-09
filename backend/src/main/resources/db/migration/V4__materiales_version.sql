-- =====================================================================================
-- Flyway: V4__materiales_version.sql - Movimientos de inventario (Fase 6)
--
-- Cambio:
--  1. materiales.version: columna para optimistic locking (@Version, decisión D-03).
--     Evita condiciones de carrera cuando dos movimientos llegan casi en paralelo
--     sobre el mismo material (AGENTS.md §7 "Concurrencia"). Mismo patron que
--     usuarios.version creado en V2.
-- =====================================================================================

-- 1. Version para optimistic locking en materiales
IF COL_LENGTH(N'dbo.materiales', N'version') IS NULL
BEGIN
    ALTER TABLE dbo.materiales ADD version INT NOT NULL CONSTRAINT df_materiales_version DEFAULT 0;
END
GO