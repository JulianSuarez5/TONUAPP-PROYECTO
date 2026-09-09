# TONUAPP — Backend (Spring Boot)

Aplicación web para **Agregados el Tonusco S.A.S.** que optimiza la gestión y control de
inventario de materiales de construcción.

Este README documenta cómo **levantar el entorno desde cero** en una máquina nueva
(idealmente la de un compañero de desarrollo). Se asume Windows + SQL Server Express.

> Referencias de diseño y decisiones: `docs/decisions/DECISIONES.md`, `docs/database/TONUAPP_Base_de_Datos.md`.

---

## 1. Requisitos previos

| Herramienta | Versión | Notas |
|---|---|---|
| **JDK** | **21 (LTS)** (funciona también con 17) | Temurin/Adoptium recomendado. Verificar con `java -version`. |
| **Maven** | 3.8+ | Verificar con `mvn -version`. |
| **SQL Server** | 2019+ (Express es suficiente) | Instancia local, p.ej. `.\SQLEXPRESS`. |
| **sqlcmd** | — | Herramienta de línea de comandos para crear el login (SSMS también vale). |

**Variables de entorno (opcional):** el backend lee `JAVA_HOME` para compilar con Maven.

---

## 2. Configurar SQL Server (una sola vez por máquina)

### 2.1 Habilitar autenticación mixta (SQL Server + Windows)

El backend se conecta con un **login SQL dedicado** (no Windows, para que funcione en
cualquier máquina). Primero verifica si el modo mixto está activo. Desde una consola con
`sqlcmd` (o SSMS, query sobre `master`):

```sql
EXEC xp_instance_regread N'HKEY_LOCAL_MACHINE',
    N'Software\Microsoft\MSSQLServer\MSSQLServer', N'LoginMode';
```

- Si devuelve `LoginMode 2` → **ya está activo el modo mixto**. Sigue al paso 2.2.
- Si devuelve `LoginMode 1` (solo Windows) → hay que habilitarlo manualmente:
  1. Abre **SSMS** con autenticación Windows.
  2. Clic derecho sobre el servidor → **Properties** → página **Security**.
  3. Marca **SQL Server and Windows Authentication mode** → **OK** → reinicia el servicio
     `MSSQL$SQLEXPRESS`.

### 2.2 Crear la base de datos y el login de la aplicación

El backend usa el login `tonuapp_app`, con permisos **solo** sobre la base `tonuapp`
(nunca `sa`, nunca permisos de servidor). Ejecuta con una sesión **Windows** (admin):

```sql
-- 1) Crear la base de datos (si no existe)
IF DB_ID(N'tonuapp') IS NULL CREATE DATABASE tonuapp;
GO

-- 2) Crear el login SQL dedicado (cambia la contrasena por una propia)
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = N'tonuapp_app')
    CREATE LOGIN tonuapp_app
    WITH PASSWORD = N'TuContrasenaSegura',   -- <-- cambia esto
         CHECK_POLICY = ON,
         DEFAULT_DATABASE = tonuapp;
GO

-- 3) Mapear el login a un usuario en la base tonuapp y darle solo dbo de esa BD
USE tonuapp;
GO
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'tonuapp_app')
    CREATE USER tonuapp_app FOR LOGIN tonuapp_app;
GO
ALTER ROLE db_owner ADD MEMBER tonuapp_app;   -- necesita DDL para Flyway
GO
```

> `db_owner` sobre **solo** `tonuapp`: da a Flyway el permiso de crear tablas y a la app
> el de leer/escribir, pero el login no puede tocar ninguna otra base ni el servidor.

### 2.3 Verificar que el login funciona

```powershell
sqlcmd -S .\SQLEXPRESS -U tonuapp_app -P "TuContrasenaSegura" -d tonuapp -Q "SELECT DB_NAME() AS bd"
```

Debe devolver `bd = tonuapp`.

---

## 3. Configurar credenciales locales (NUNCA se commitean)

Copia la plantilla a tu archivo local y completa la contraseña:

```powershell
cd backend\src\main\resources
copy application-local.example.yml application-local.yml
```

Luego edita `application-local.yml` y pon tu contraseña real. Este archivo está en
`.gitignore`, así que no se sube al repositorio.

> **Alternativa (recomendada):** no pongas la contraseña en el archivo y usa la variable
> de entorno `TONUAPP_DB_PASSWORD` al correr la app (ver paso 4). El `application.yml`
> base solo tiene placeholders.

---

## 4. Correr la aplicación

Desde `backend/`:

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

O, empaquetando y lanzando el jar:

```powershell
mvn clean package -DskipTests
java -jar target\tonuapp-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

> Si prefieres usar variables de entorno en vez de `application-local.yml`:
> ```powershell
> $env:TONUAPP_DB_PASSWORD = "TuContrasenaSegura"
> mvn spring-boot:run
> ```

**Qué se espera al arrancar:**
- Flyway aplica las migraciones (`db/migration/V1__init.sql`, `V2__...`, `V3__materiales_catalogos.sql`, `V4__materiales_version.sql`, `V5__ajustes_ajuste_unico.sql`)
  sobre una BD vacía: crea las 16 tablas, constraints, índices y datos semilla (roles,
  categorías, unidades de medida) y deja el estado en `flyway_schema_history`.
- La app queda escuchando en `http://localhost:8080`.

**Verificar que quedó arriba:**
```powershell
Invoke-WebRequest -Uri http://localhost:8080/actuator/health
```
Debe responder `200` con `{"status":"UP"}`. Si el estado es `DOWN`, revisa la conexión a
SQL Server (credenciales, servicio corriendo).

---

## 5. Estructura del proyecto (backend)

```
backend/
├── pom.xml
└── src
    ├── main
    │   ├── java/com/tonuapp
    │   │   ├── TonuappApplication.java
    │   │   ├── auth/                        # autenticacion (correo + codigo temporal)
    │   │   │   ├── AuthController, AuthService, AuthDtos, JwtTokens
    │   │   │   ├── FallosLoginService            # bloqueo por cuenta (REQUIRES_NEW, Fase 10)
    │   │   ├── catalogos/                   # categorias y unidades de medida (solo lectura)
    │   │   ├── config/WebConfig.java        # CORS
    │   │   ├── domain/                      # entidades JPA (Usuario, Categoria, Material, ...)
    │   │   ├── materiales/                  # CRUD materiales (controller/service/mapper/dto)
    │   │   ├── movimientos/                 # Movimientos de inventario (entries/salidas/ajustes/anular)
    │   │   ├── alertas/                      # Alertas de bajo stock (RF-012)
    │   │   ├── reportes/                     # Reportes: consultas JSON + export pdf/xlsx (RF-014)
    │   │   ├── auditoria/                    # GET /api/auditoria: quien/hace/que/cuando (Fase 10)
    │   │   ├── repository/                  # repositorios Spring Data
    │   │   ├── security/                    # JWT filter + entrada de seguridad
    │   │   ├── shared/                      # errores/API base/auditoria
    │   │   └── util/                        # hashing de codigos (PBKDF2)
    │   └── resources
    │       ├── application.yml                # placeholders, sin secretos
    │       ├── application-local.example.yml  # plantilla (se versiona)
    │       ├── application-local.yml          # credenciales (NO versiona)
    │       └── db/migration/V1__init.sql ...  # migraciones Flyway
    └── test/java/.../                         # pruebas unitarias
```

---

## 6. Pruebas

```powershell
cd backend
mvn test
```

Hay una prueba de creación desde cero de la BD en
`docs/testing/test_creacion_desde_cero.sql` (ejecutar contra SQLEXPRESS con `sqlcmd`).
