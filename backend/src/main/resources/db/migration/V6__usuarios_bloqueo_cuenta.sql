-- -------------------------------------------------------------------------------------
-- V6: bloqueo temporal por cuenta (RF-008 / D-22). Columnas en usuarios:
--  - intentos_login_fallidos: verificaciones fallidas consecutivas (sesion); con
--    CONSTRAINT que impide valores negativos.
--  - bloqueo_hasta: fecha-hasta del bloqueo activo; NULL = cuenta sin bloqueo.
-- El contador se resetea con un login exitoso o al fijar el bloqueo; el bloqueo se
-- levanta solo (bloqueo_hasta en el pasado) o con un login exitoso posterior.
-- El trade-off (DoS dirigido a correos conocidos) esta documentado en D-22.
-- Nota: la ADD CONSTRAINT CHECK va en un batch SEPARADO (GO) porque SQL Server
-- compila el batch antes de ejecutarlo y la columna todavia no existe en el mismo batch.
-- -------------------------------------------------------------------------------------
IF COL_LENGTH('dbo.usuarios', 'intentos_login_fallidos') IS NULL
BEGIN
    ALTER TABLE dbo.usuarios ADD intentos_login_fallidos INT NOT NULL CONSTRAINT df_usuarios_intentos_login DEFAULT 0;
END
GO

IF COL_LENGTH('dbo.usuarios', 'intentos_login_fallidos') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'ck_usuarios_intentos_login_positivo')
BEGIN
    ALTER TABLE dbo.usuarios ADD CONSTRAINT ck_usuarios_intentos_login_positivo CHECK (intentos_login_fallidos >= 0);
END
GO

IF COL_LENGTH('dbo.usuarios', 'bloqueo_hasta') IS NULL
BEGIN
    ALTER TABLE dbo.usuarios ADD bloqueo_hasta DATETIME2 NULL;
END
GO