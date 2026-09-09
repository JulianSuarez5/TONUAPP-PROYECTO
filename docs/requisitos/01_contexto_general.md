# TONUAPP — Contexto General

## Organización
Agregados el Tonusco S.A.S. — empresa del sector minero-constructivo dedicada a la extracción,
transformación y comercialización de materiales de playa para la construcción (arena, gravilla, etc.).
Ubicada en el km 4 vía Santa Fe de Antioquia, barrio El Paso.

Actualmente los procesos de inventario son manuales o de bajo nivel de automatización (formatos físicos,
herramientas ofimáticas básicas), sin integración entre almacén, producción y administración.

## Problemática
El proceso de gestión de inventarios se realiza de forma manual, lo que genera:
- Información desactualizada del stock real.
- Demoras en la atención al cliente.
- Errores en el control de stock.
- Dificultades en la planificación de producción y ventas.
- Ausencia de alertas automáticas de bajo stock (riesgo de desabastecimiento o acumulación innecesaria).

## Formulación del problema
¿Cómo la implementación de una aplicación web permitiría mejorar la gestión y el control del inventario
de materiales de construcción, optimizando el registro y consulta de información en tiempo real, y
facilitando la toma de decisiones y la atención al cliente?

## Objetivo general
Implementar una aplicación web para optimizar la gestión y el control del inventario de materiales de
construcción en Agregados el Tonusco S.A.S., automatizando registro, actualización y consulta en tiempo
real.

## Objetivos específicos
1. Analizar el proceso actual de gestión de inventario para identificar necesidades y problemáticas.
2. Diseñar una aplicación web que gestione inventario, usuarios y movimientos de entrada/salida.
3. Desarrollar funcionalidades para registrar, consultar, actualizar y eliminar información del inventario.
4. Evaluar el funcionamiento de la aplicación mediante pruebas de sus funcionalidades.

## Requisitos generales por módulo

### 1. Gestión de usuarios
Registrar, consultar, modificar, eliminar usuarios · iniciar/cerrar sesión · cambiar contraseña ·
gestionar roles · controlar permisos.

### 2. Gestión de materiales
Registrar, consultar, modificar, eliminar materiales · buscar por nombre/tipo/cantidad · consultar
disponibilidad en tiempo real · registrar/consultar/modificar ubicaciones · controlar lotes de acopio.

### 3. Gestión de movimientos de inventario
Registrar entradas · registrar salidas · consultar movimientos · actualizar movimientos · eliminar o
anular movimientos · consultar historial.

### 4. Control y seguimiento del inventario
Verificar correspondencia del material recibido antes de ingresarlo · ajustar y conciliar inventario ·
generar alertas por baja disponibilidad · registrar salidas por mermas y desperdicios.

### 5. Gestión de proveedores
Registrar, consultar, modificar, eliminar proveedores · gestionar información de materiales suministrados.

## Características no funcionales mencionadas en el PPI
- Multiplataforma: computadores, tabletas, móviles, con interfaz adaptada a cada dispositivo.
- Autenticación por correo electrónico con código de acceso temporal (no password tradicional descrito
  como mecanismo principal, aunque el backlog sí menciona "cambiar contraseña" — ver inconsistencia
  registrada en AGENTS.md).
- Validación de datos en tiempo real y mensajes de retroalimentación.
- Accesibilidad: navegadores/dispositivos distintos, contraste adecuado.
- Rendimiento: respuesta rápida y estable, uso concurrente por múltiples usuarios.
