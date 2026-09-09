-- =====================================================================
-- TONUAPP - Esquema de Base de Datos
-- Agregados el Tonusco S.A.S. - Gestion de Inventario
-- Motor objetivo: MySQL 8.0+ (portable a SQL Server con ajustes menores
-- señalados en comentarios: AUTO_INCREMENT -> IDENTITY, ENUM -> CHECK, etc.)
-- Basado en: PPI_mayo.docx (RF-001 a RF-015), TONUAPP.docx, Product_Backlog_TONUAPP.docx
-- =====================================================================

CREATE DATABASE IF NOT EXISTS tonuapp
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE tonuapp;

-- ---------------------------------------------------------------------
-- 1. ROLES Y USUARIOS
-- Cubre: RF-006 (gestion de usuarios con roles), RF-008 (autenticacion
-- segura por codigo), RF-009 (control de permisos por rol)
-- ---------------------------------------------------------------------

CREATE TABLE roles (
    id_rol          INT AUTO_INCREMENT PRIMARY KEY,
    nombre_rol      VARCHAR(30)  NOT NULL UNIQUE,   -- 'Administrador', 'Cliente'
    descripcion     VARCHAR(150)
);

-- RF-006: "Solo se pueden asignar los roles: Administrador y Cliente"
INSERT INTO roles (nombre_rol, descripcion) VALUES
    ('Administrador', 'Acceso completo al sistema: inventario, usuarios, proveedores, reportes'),
    ('Cliente', 'Solo puede consultar informacion y realizar solicitudes, sin modificar datos');

CREATE TABLE usuarios (
    id_usuario      INT AUTO_INCREMENT PRIMARY KEY,
    nombre          VARCHAR(100) NOT NULL,
    correo          VARCHAR(150) NOT NULL UNIQUE,   -- RF-006: correo unico en el sistema
    id_rol          INT NOT NULL,
    estado          ENUM('activo','inactivo') NOT NULL DEFAULT 'activo',
    fecha_creacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    es_admin_principal BOOLEAN NOT NULL DEFAULT FALSE, -- RF-006: no se elimina el admin principal
    CONSTRAINT fk_usuario_rol FOREIGN KEY (id_rol) REFERENCES roles(id_rol)
);

-- RF-008: Autenticacion sin password tradicional -> codigo temporal por correo.
-- Se guarda historico de codigos para trazabilidad y bloqueo por intentos fallidos.
CREATE TABLE codigos_acceso (
    id_codigo           INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario          INT NOT NULL,
    codigo              VARCHAR(10) NOT NULL,
    fecha_generacion    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_expiracion    DATETIME NOT NULL,     -- RF-008: vigencia maxima 10 minutos
    usado               BOOLEAN NOT NULL DEFAULT FALSE,  -- RF-008: un solo uso
    intentos_fallidos   INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_codigo_usuario FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario)
        ON DELETE CASCADE
);

-- RF-005 / RF-007: preferencias personales de visualizacion por usuario
CREATE TABLE preferencias_usuario (
    id_usuario          INT PRIMARY KEY,
    panel_inicio        VARCHAR(50)  DEFAULT 'dashboard',
    filtros_favoritos   JSON         NULL,   -- SQL Server: usar NVARCHAR(MAX) en su lugar
    orden_default       VARCHAR(30)  DEFAULT 'nombre_asc',
    tema_visual         VARCHAR(20)  DEFAULT 'claro',
    CONSTRAINT fk_pref_usuario FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario)
        ON DELETE CASCADE
);

-- ---------------------------------------------------------------------
-- 2. PROVEEDORES
-- Cubre: RF-010 (registro y gestion de proveedores)
-- ---------------------------------------------------------------------

CREATE TABLE proveedores (
    id_proveedor    INT AUTO_INCREMENT PRIMARY KEY,
    nombre          VARCHAR(150) NOT NULL,
    nit             VARCHAR(20)  NOT NULL UNIQUE,  -- RF-010: no NIT duplicado
    contacto        VARCHAR(100),
    telefono        VARCHAR(20),
    ubicacion       VARCHAR(200),
    estado          ENUM('activo','inactivo') NOT NULL DEFAULT 'activo',
    fecha_registro  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------------
-- 3. ZONAS DE ACOPIO / UBICACIONES
-- Cubre: RF-015 (control de ubicaciones y lotes de acopio)
-- ---------------------------------------------------------------------

CREATE TABLE zonas_acopio (
    id_zona                 INT AUTO_INCREMENT PRIMARY KEY,
    nombre_zona              VARCHAR(100) NOT NULL,   -- ej. 'Patio Sur, Lote 1'
    capacidad_maxima         DECIMAL(12,2) NOT NULL,
    tipo_material_permitido  VARCHAR(100),             -- RF-015: zona no compartida entre materiales incompatibles
    estado                   ENUM('activa','inactiva') NOT NULL DEFAULT 'activa'
);

-- ---------------------------------------------------------------------
-- 4. MATERIALES (nucleo del inventario)
-- Cubre: RF-001, RF-002, RF-003, RF-004, RF-015
-- ---------------------------------------------------------------------

CREATE TABLE materiales (
    id_material         INT AUTO_INCREMENT PRIMARY KEY,
    nombre              VARCHAR(100) NOT NULL,
    tipo                VARCHAR(50)  NOT NULL,
    unidad_medida       VARCHAR(20)  NOT NULL,        -- m3, ton, kg, etc.
    cantidad_disponible DECIMAL(12,2) NOT NULL DEFAULT 0 CHECK (cantidad_disponible >= 0),
    stock_minimo        DECIMAL(12,2) NOT NULL DEFAULT 0,  -- RF-012: umbral de alerta
    id_proveedor        INT NULL,
    id_zona              INT NULL,
    estado               ENUM('activo','inactivo') NOT NULL DEFAULT 'activo',
    fecha_registro       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion  DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_material_proveedor FOREIGN KEY (id_proveedor) REFERENCES proveedores(id_proveedor),
    CONSTRAINT fk_material_zona FOREIGN KEY (id_zona) REFERENCES zonas_acopio(id_zona),
    -- RF-002: "no se permiten materiales duplicados con el mismo nombre y tipo"
    CONSTRAINT uq_material_nombre_tipo UNIQUE (nombre, tipo)
);

CREATE INDEX idx_materiales_busqueda ON materiales (nombre, tipo, cantidad_disponible);

-- ---------------------------------------------------------------------
-- 5. MOVIMIENTOS DE INVENTARIO
-- Cubre: RF-002 (entradas), RF-004 (salidas/ediciones), RF-013 (mermas)
-- ---------------------------------------------------------------------

CREATE TABLE movimientos_inventario (
    id_movimiento     INT AUTO_INCREMENT PRIMARY KEY,
    id_material       INT NOT NULL,
    tipo_movimiento   ENUM('entrada','salida_venta','salida_merma','ajuste') NOT NULL,
    cantidad          DECIMAL(12,2) NOT NULL,
    id_usuario        INT NOT NULL,          -- quien ejecuta el movimiento
    motivo            VARCHAR(200),          -- RF-013: causa de merma (clima, transporte, derrame...)
    fecha_movimiento  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    observaciones     VARCHAR(300),
    CONSTRAINT fk_mov_material FOREIGN KEY (id_material) REFERENCES materiales(id_material),
    CONSTRAINT fk_mov_usuario FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario)
);

CREATE INDEX idx_movimientos_material_fecha ON movimientos_inventario (id_material, fecha_movimiento);

-- ---------------------------------------------------------------------
-- 6. AJUSTES Y CONCILIACION (auditoria dedicada)
-- Cubre: RF-011 (ajuste y conciliacion de inventario, requiere log con
-- fecha y autor, y no permite inventario negativo)
-- ---------------------------------------------------------------------

CREATE TABLE ajustes_inventario (
    id_ajuste           INT AUTO_INCREMENT PRIMARY KEY,
    id_material         INT NOT NULL,
    cantidad_anterior   DECIMAL(12,2) NOT NULL,
    cantidad_nueva      DECIMAL(12,2) NOT NULL CHECK (cantidad_nueva >= 0),
    motivo              VARCHAR(200) NOT NULL,   -- ej. 'error de registro'
    id_usuario          INT NOT NULL,
    fecha_ajuste        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ajuste_material FOREIGN KEY (id_material) REFERENCES materiales(id_material),
    CONSTRAINT fk_ajuste_usuario FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario)
);

-- ---------------------------------------------------------------------
-- 7. ALERTAS DE INVENTARIO
-- Cubre: RF-012 (alertas por baja disponibilidad)
-- ---------------------------------------------------------------------

CREATE TABLE alertas_inventario (
    id_alerta       INT AUTO_INCREMENT PRIMARY KEY,
    id_material     INT NOT NULL,
    fecha_generada  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado          ENUM('activa','atendida') NOT NULL DEFAULT 'activa',
    mensaje         VARCHAR(200) NOT NULL,
    CONSTRAINT fk_alerta_material FOREIGN KEY (id_material) REFERENCES materiales(id_material)
);

-- ---------------------------------------------------------------------
-- 8. REPORTES GENERADOS (bitacora de exportaciones)
-- Cubre: RF-014 (exportacion de reportes)
-- ---------------------------------------------------------------------

CREATE TABLE reportes_generados (
    id_reporte          INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario          INT NOT NULL,
    tipo_reporte        VARCHAR(50) NOT NULL,        -- 'inventario', 'movimientos', etc.
    formato             ENUM('pdf','xlsx') NOT NULL,
    fecha_generacion    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    rango_fecha_inicio  DATE,
    rango_fecha_fin     DATE,
    ruta_archivo        VARCHAR(300),
    CONSTRAINT fk_reporte_usuario FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario)
);

-- =====================================================================
-- TRIGGER: mantiene materiales.cantidad_disponible sincronizada
-- cada vez que se inserta un movimiento de inventario.
-- (Alternativa: manejar esta logica en la capa de servicios/backend;
-- se deja aqui como respaldo a nivel de datos.)
-- =====================================================================

DELIMITER $$

CREATE TRIGGER trg_actualizar_stock
AFTER INSERT ON movimientos_inventario
FOR EACH ROW
BEGIN
    IF NEW.tipo_movimiento = 'entrada' THEN
        UPDATE materiales
        SET cantidad_disponible = cantidad_disponible + NEW.cantidad
        WHERE id_material = NEW.id_material;
    ELSEIF NEW.tipo_movimiento IN ('salida_venta','salida_merma') THEN
        UPDATE materiales
        SET cantidad_disponible = cantidad_disponible - NEW.cantidad
        WHERE id_material = NEW.id_material;
    END IF;

    -- RF-012: genera alerta si el stock queda por debajo del minimo
    INSERT INTO alertas_inventario (id_material, mensaje)
    SELECT id_material,
           CONCAT('Stock bajo: ', nombre, ' (', cantidad_disponible, ' ', unidad_medida, ' disponibles)')
    FROM materiales
    WHERE id_material = NEW.id_material
      AND cantidad_disponible <= stock_minimo
      AND NOT EXISTS (
          SELECT 1 FROM alertas_inventario
          WHERE id_material = NEW.id_material AND estado = 'activa'
      );
END$$

DELIMITER ;

-- =====================================================================
-- DATOS SEMILLA MINIMOS PARA EMPEZAR A DESARROLLAR/PROBAR
-- =====================================================================

INSERT INTO usuarios (nombre, correo, id_rol, es_admin_principal)
VALUES ('Admin Tonusco', 'admin@tonusco.com', 1, TRUE);

INSERT INTO zonas_acopio (nombre_zona, capacidad_maxima, tipo_material_permitido)
VALUES ('Patio Sur - Lote 1', 500.00, 'Arena'),
       ('Patio Norte - Lote 1', 500.00, 'Gravilla');
