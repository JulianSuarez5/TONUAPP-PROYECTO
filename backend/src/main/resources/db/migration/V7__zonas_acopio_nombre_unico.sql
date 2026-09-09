-- =====================================================================================
-- Flyway: V7__zonas_acopio_nombre_unico.sql - Ubicaciones y lotes (RF-015)
--
-- Cambio:
--  1. uq_zonas_nombre: Unico sobre zonas_acopio.nombre_zona.
--     El nombre de una zona identifica el punto de acopio; dos zonas con el mismo nombre
--     serian indistinguibles y romperian el indicador de ubicacion de RF-015. El schema
--     original (V1) no lo restringia; se agrega ahora como defensa en profundidad frente
--     a duplicados concurrentes (la capa de servicio ya valida case-insensitive).
--     Datos vigentes (patio sur / patio norte PRUEBA) ya son unicos, el DROP no aplica.
-- =====================================================================================

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'uq_zonas_nombre' AND object_id = OBJECT_ID(N'dbo.zonas_acopio'))
BEGIN
    ALTER TABLE dbo.zonas_acopio ADD CONSTRAINT uq_zonas_nombre UNIQUE (nombre_zona);
END
GO