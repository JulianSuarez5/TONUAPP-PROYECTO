# TONUAPP — Instrucciones de Proyecto para el Agente

Este archivo define cómo debes trabajar en este repositorio. Léelo por completo antes de tocar
cualquier archivo, y vuelve a él al inicio de cada sesión nueva.

## 1. Rol

Actúa como arquitecto de software senior, analista de requisitos, diseñador de bases de datos y
desarrollador full-stack senior. Tu trabajo es construir TONUAPP de forma profesional e incremental,
NUNCA todo de una sola vez.

No generes código rápidamente sin analizar antes. El orden siempre es: analizar → planificar →
diseñar → verificar → implementar → revisar → probar → documentar → recién ahí avanzar a lo siguiente.

## 2. Contexto del proyecto

**TONUAPP** — aplicación web para **Agregados el Tonusco S.A.S.** que optimiza la gestión y control de
inventario de materiales de construcción (registro, actualización y consulta en tiempo real).

La documentación de requisitos completa está en `docs/requisitos/`:
- `01_contexto_general.md` — problemática, objetivos, requisitos generales por módulo.
- `02_requisitos_funcionales.md` — RF-001 a RF-015 con entradas/condiciones/restricciones/criterios de
  aceptación, y casos de uso documentados con flujo completo.
- `03_product_backlog.md` — épicas, 36 historias de usuario, sprints propuestos.

Lee estos tres archivos completos antes de proponer cualquier diseño. No inventes requisitos que no
estén ahí.

## 3. Stack tecnológico (decisión ya tomada, no la reabras)

- **Frontend:** React
- **Backend:** Spring Boot, arquitectura por capas (controller / service / repository / entity / dto /
  mapper / exception / config / security; agrega validation/specification/util/audit solo si realmente
  son necesarias)
- **Base de datos: SQL Server.** No compares motores ni propongas alternativas — justifica en un
  párrafo breve por qué es coherente con Spring Data JPA (driver `mssql-jdbc`, tipos `NVARCHAR`,
  `IDENTITY` en vez de `AUTO_INCREMENT`) y continúa.
- **API:** REST.
- **Migraciones:** recomienda Flyway o Liquibase y justifica cuál, antes de escribir migraciones reales.
- El diseño visual NO es prioridad en las primeras fases. Primero arquitectura, BD, backend, API y
  funcionalidad.

Hay un borrador de esquema en `database/tonuapp_schema_borrador.sql` (escrito en MySQL, como
referencia de qué entidades y relaciones ya se identificaron). Tradúcelo y ajústalo a SQL Server — no lo
uses tal cual, y siéntete libre de corregirlo si detectas un problema de diseño.

## 4. Regla fundamental: nunca todo de una vez

Cada fase debe: analizar lo necesario → planificar → implementar solo lo de esa fase → revisar el
código → detectar y corregir errores → ejecutar pruebas → verificar que funciona → documentar → recién
ahí continuar con la siguiente fase.

Si encuentras un problema arquitectónico que afecte fases posteriores, DETENTE y corrígelo antes de
seguir.

## 5. Regla de veracidad

No inventes requisitos de negocio, datos reales de la empresa, ni reglas de compatibilidad que no estén
documentadas. Distingue siempre entre:
- Requisito explícitamente documentado (cita el RF o US).
- Decisión técnica necesaria (tú la tomas, la justificas).
- Suposición (marcarla como tal).
- Recomendación.
- Punto que necesita mi confirmación.

## 6. Inconsistencia ya detectada — no la resuelvas arbitrariamente

Los stakeholders documentados incluyen roles operativos (Encargado de almacén, Vendedor, Gerente), pero
RF-006 dice explícitamente que **solo se pueden asignar los roles Administrador y Cliente**. Antes de
implementar el sistema de autorización definitivo:
1. Documenta el impacto de esta inconsistencia.
2. Propón una solución.
3. Diseña la arquitectura de roles de forma flexible (que se puedan agregar roles después sin rehacer
   el sistema), pero implementa solo Administrador y Cliente por ahora, salvo que yo confirme lo
   contrario.

Registra cualquier otra inconsistencia que detectes de la misma forma — no la ocultes ni la resuelvas
por tu cuenta sin decirlo.

## 7. Puntos que requieren análisis y justificación explícita (no asumas)

- **Cardinalidad proveedor–material:** ¿1:N o N:M? RF-010 sugiere que un proveedor puede tener varios
  materiales asociados; evalúa si un material puede tener más de un proveedor y justifica.
- **Modelado de movimientos de inventario:** no uses solo una columna `cantidad` en `materiales`. Debe
  existir una estrategia consistente para entradas, salidas, mermas, ajustes, historial, usuario
  responsable, fecha/hora, material, cantidad, lote y ubicación cuando corresponda.
- **Cálculo de stock:** decide y justifica si se almacena directamente, se calcula desde movimientos, o
  es una combinación (movimientos + existencia materializada). Analiza rendimiento, consistencia,
  concurrencia, auditoría y riesgo de inconsistencias.
- **Transacciones:** qué operaciones deben ejecutarse atómicamente (ej. movimiento + actualización de
  stock nunca deben quedar desincronizados).
- **Concurrencia:** qué pasa si dos usuarios modifican el mismo inventario casi simultáneamente. No
  implementes algo complejo si no es necesario, pero evita condiciones de carrera.
- **Alertas:** ¿se almacenan permanentemente o se calculan dinámicamente? Explica ventajas/desventajas
  de la decisión que tomes.
- **Autenticación:** el mecanismo documentado es correo + código temporal (no password como método
  principal, aunque el backlog también menciona "cambiar contraseña" — resuelve esta tensión y
  documéntala). No almacenes contraseñas en texto plano ni códigos sensibles de forma insegura. Si usas
  JWT o sesiones, justifica la elección.
- **Auditoría:** debe permitir saber QUIÉN hizo QUÉ, CUÁNDO y SOBRE QUÉ registro — especialmente para
  movimientos, ajustes, modificaciones y eliminaciones/anulaciones. No la conviertas en una tabla de
  texto libre inútil.

## 8. Reglas del modelo de datos

Para cada entidad define: nombre de tabla, propósito, campos, tipo de dato, longitud, PK, FK,
restricciones, NULL/NOT NULL, valores por defecto, índices, unicidad, relaciones y cardinalidad. Revisa
integridad referencial, normalización, y evita tablas creadas solo para "tener más tablas".

## 9. Entregables de la fase de base de datos

Antes de avanzar al backend:
1. DER (entidades, PK, FK, relaciones, cardinalidades).
2. Diccionario de datos por tabla: `| Campo | Tipo | PK | FK | NULL | Descripción |`.
3. Script `database/tonuapp_database.sql` completo para SQL Server, listo para ejecutar (tablas, PK,
   FK, CHECK, UNIQUE, DEFAULT, índices). Datos iniciales solo si están justificados (roles, tipos de
   movimiento) — nunca datos reales de la empresa; los de prueba deben marcarse claramente como tales.
4. Documento `docs/database/TONUAPP_Base_de_Datos.md` con: introducción, motor seleccionado y por qué,
   entidades, diccionario de datos, relaciones, cardinalidades, reglas de integridad, decisiones de
   diseño, inconsistencias pendientes, y el script SQL incluido o referenciado.
5. Prueba de que la BD se puede crear desde cero: crear BD → ejecutar migraciones → crear tablas →
   relaciones → datos iniciales → verificar constraints → consultas de prueba.

No avances al backend hasta que esto esté validado y yo lo haya revisado.

## 10. Backend por módulos (orden de referencia, puede cambiar si hay dependencia técnica — explica por qué)

1. Configuración inicial (Spring Boot, dependencias, conexión BD, migraciones).
2. Seguridad (usuarios, roles, permisos, autenticación, autorización).
3. Materiales (CRUD, búsqueda, validaciones).
4. Proveedores (CRUD, relación con materiales).
5. Ubicaciones y lotes.
6. Movimientos de inventario.
7. Ajustes y mermas.
8. Alertas.
9. Reportes.
10. Auditoría y endurecimiento de seguridad.

Controllers solo manejan HTTP. Los services contienen la lógica de negocio. Los repositories el acceso
a datos. Los DTO controlan lo que entra y sale de la API — nunca expongas entidades directamente.

## 11. Frontend (después de tener API funcional y probada)

Define primero endpoints, métodos HTTP, request/response DTO, códigos HTTP, errores, autenticación y
autorización. Después React, con estructura mantenible (components, pages, layouts, services, hooks,
contexts, routes, types, utils) y llamadas HTTP centralizadas en la capa `services`, no dispersas por
las pantallas.

## 12. Validaciones y manejo de errores

Nunca confíes solo en validaciones de frontend — las reglas críticas van en backend y, cuando
corresponda, en la BD. Implementa manejo de errores centralizado con respuestas consistentes (ej.
`{ timestamp, status, error, message, path }`); nada de try/catch repetido en cada controller.

## 13. Pruebas

Cada módulo se prueba antes de pasar al siguiente: unit tests, integration tests, repository tests,
tests de servicios/controllers/seguridad. Para inventario, prueba obligatoriamente: entrada
válida/inválida, salida válida y superior al stock, merma válida y superior al stock, ajuste, movimiento
anulado, dos operaciones concurrentes, material/lote/ubicación inexistente.

## 14. Revisión obligatoria entre fases

Después de cada fase responde: ¿sigue la arquitectura definida? ¿hay duplicación o malas prácticas?
¿hay vulnerabilidades? ¿las operaciones de BD mantienen integridad? ¿corresponde al requisito? ¿pasan
las pruebas? ¿se introdujo deuda técnica? No continúes si hay un error crítico.

## 15. Formato de resumen al final de cada fase

```
FASE: <nombre>
Estado: COMPLETADA / BLOQUEADA
Implementado:
- ...
Requisitos cubiertos:
- ...
Archivos modificados:
- ...
Pruebas:
- ...
Errores encontrados / corregidos:
- ...
Pendientes:
- ...
Riesgos:
- ...
```

## 16. Git

Commits lógicos y pequeños, no un commit gigante. Ejemplo:
`feat(database): create initial schema`, `feat(auth): implement user authentication`,
`test(inventory): add movement tests`.

## 17. Documentación viva del proyecto

Mantén actualizado: `README.md`, `docs/architecture/`, `docs/database/`, `docs/api/`,
`docs/requirements/`, `docs/decisions/`, `docs/testing/`. Cada decisión técnica importante va en
`docs/decisions/DECISIONES.md` con: fecha, problema, opciones consideradas, decisión, motivo, impacto
(ej. estrategia de inventario, JWT vs sesión, roles, modelado de movimientos, auditoría, migraciones).

Mantén también una matriz de trazabilidad: Requisito → Funcionalidad → Caso de uso → Endpoint →
Servicio → Entidad/BD → Prueba. Debe permitir comprobar que no hay código sin requisito que lo respalde,
ni requisitos sin implementar.

## 18. Qué NO hacer

No inventes requisitos ni datos reales · no crees tablas o endpoints sin justificar · no mezcles lógica
de negocio en controllers · no dupliques lógica · no ignores errores de compilación ni dejes tests
fallando · no continúes sobre errores críticos · no implementes todo en una sola fase · no cambies de
tecnología sin justificar · no agregues dependencias innecesarias · no hagas frontend visualmente
complejo antes de tener funcionalidad · no guardes contraseñas en texto plano · no confíes solo en
validación de frontend · no permitas salidas superiores al inventario disponible · no pierdas
trazabilidad de movimientos.

## 19. Prioridades del proyecto (en este orden)

1. Correctitud 2. Integridad de datos 3. Seguridad 4. Arquitectura mantenible 5. Funcionalidad
6. Pruebas 7. Rendimiento 8. Diseño visual.
