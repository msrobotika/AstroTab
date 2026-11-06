# Plan: copia pública

## Resumen y contexto técnico

Kotlin 2.1.20, Android/Canvas API 23+, Gradle 8.14.3, AGP 8.11.0, JDK 17, SDK/build-tools 36. JUnit y Robolectric para las pruebas. Base selectiva respaldada fuera del repositorio antes de editar; origen de solo lectura.

## Comprobación de constitución

Cumplir privacidad, fuentes/licencias, offline por defecto y evidencia honesta. Sin dispositivos, instalación, remotos, push ni agentes adicionales. Red de esta preparación solo Gradle/Maven; ningún script de importación se ejecuta contra servicios externos.

## Diseño

1. Selección por lista permitida, registro de hashes del origen y respaldo externo de la base.
2. Constitución pública vigente; especificación, plan, tareas y checklist antes de cambios.
3. Sanear documentación y coordenadas en pruebas; sustituir dependencias de informes físicos por valores calculados. Wrapper usa variables estándar sin rutas personales. Importadores reciben archivos externos explícitos y no descargan implícitamente.
4. README y colaboración públicos; resumir pendientes reales en ROADMAP. Conservar licencias y datos científicos necesarios.
5. Ejecutar Gradle desde el destino, revisar resultados XML/lint, auditar archivos versionados, inicializar main y crear el único commit cuando converja.

## Verificación

Cada A1–A5 tendrá evidencia en verification.md. Revisión previa: requisitos y tareas cubren el mismo alcance, sin ambigüedades materiales pendientes. No se agregan dependencias ni funciones de producto.
