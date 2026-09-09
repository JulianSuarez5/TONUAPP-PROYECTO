-- =====================================================================================
-- Flyway: V2__security_auth.sql - Seguridad y autenticacion (Fase 3)
--
-- Cambios:
--  1. codigos_acceso.codigo se amplia de NVARCHAR(10) a NVARCHAR(64): a partir de esta
--     fase el codigo temporal se almacena HASHEADO (SHA-256 hex, 64 caracteres), nunca en
--     texto plano (pendiente D-07, ver docs/decisions/DECISIONES.md).
--  2. usuarios.version: columna para optimistic locking (@Version) y evitar ediciones
--     concurrentes de un mismo usuario sin perder cambios.
--  3. Tabla refresh_tokens: persistencia de refresh tokens para permitir revocacion en
--     logout (decision D-11). Se almacena el HASH del token, no el token en claro.
-- =====================================================================================

-- 1. Ampliar columna de codigo para almacenar hash (SHA-256 hex = 64 chars)
ALTER TABLE dbo.codigos_acceso ALTER COLUMN codigo NVARCHAR(64) NOT NULL;
GO

-- 2. Version para optimistic locking en usuarios
IF COL_LENGTH(N'dbo.usuarios', N'version') IS NULL
BEGIN
    ALTER TABLE dbo.usuarios ADD version INT NOT NULL CONSTRAINT df_usuarios_version DEFAULT 0;
END
GO

-- 3. Refresh tokens (revocables en logout, D-11)
IF OBJECT_ID(N'dbo.refresh_tokens', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.refresh_tokens (
        id              BIGINT IDENTITY(1,1) NOT NULL,
        id_usuario      INT               NOT NULL,
        token_hash      NVARCHAR(64)      NOT NULL,
        expiracion      DATETIME2(0)      NOT NULL,
        revocado        BIT               NOT NULL CONSTRAINT df_refresh_revocado DEFAULT 0,
        fecha_creacion  DATETIME2(0)      NOT NULL CONSTRAINT df_refresh_fecha_creacion DEFAULT SYSDATETIME(),
        CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
        CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
        CONSTRAINT fk_refresh_tokens_usuario FOREIGN KEY (id_usuario)
            REFERENCES dbo.usuarios (id_usuario)
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_refresh_usuario' AND object_id = OBJECT_ID(N'dbo.refresh_tokens'))
BEGIN
    CREATE INDEX ix_refresh_usuario ON dbo.refresh_tokens (id_usuario);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_refresh_revocado_expiracion' AND object_id = OBJECT_ID(N'dbo.refresh_tokens'))
BEGIN
    CREATE INDEX ix_refresh_revocado_expiracion ON dbo.refresh_tokens (revocado, expiracion);
END
GO