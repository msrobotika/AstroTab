> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Plan de implementación: MVP

06/11/2026 · especificación: [spec.md](spec.md). Derivado de las plantillas oficiales de Spec Kit; proceso manual.

## Resumen y contexto técnico

Gradle 8.14.3 local, AGP 8.11.0 local, Kotlin 2.1.20 local, JDK 17; compile/targetSdk 36, minSdk 23, build-tools 36.0.0. Un módulo Android app y un módulo core Kotlin/JVM con JUnit 4.13.2. Sin AndroidX salvo necesidad demostrada. JSON en core con org.json de Maven (misma interfaz de plataforma en app), solo dependencia de pruebas para JVM. Sin SDK remotos propietarios.

Persistencia: SharedPreferences para ajustes y credenciales, ficheros privados atómicos para caché. Un ejecutor para peticiones y cálculos pesados; Canvas mantiene arrays de posiciones y recalcula astronomía a 1 Hz. Render a demanda limitado durante gestos/sensor. Diseño: barra superior, contenido y siete pestañas como el boceto, panel de ficha independiente y rojo global.

## Comprobación constitucional

Conforme: respaldo de PLAN/TAREAS/boceto en backups/2026-11-06-inicial; dependencia de datos únicamente local; ningún permiso de hardware ni publicación; criterios de código y dispositivo separados. Se conserva la fuente local sin modificarla. No se necesita CLI ni instalación de extensiones.

## Estructura y secuencia

1. T01: wrapper/configuración, licencias; T02: ajustes y validación.
2. T03–T04: core/astro y core/catalog, conversor reproducible local y pruebas de referencia.
3. T05–T07: core/ui (geometría/orientación), app/ui (Canvas y pantallas), acelerómetro y rojo.
4. T08–T10: core/data y evaluadores; app/data para HTTPS/caché, pruebas con muestras sin red real.
5. T11: core/kiosk (PIN y política), app/kiosk (DPC/HOME), docs/KIOSCO.md.
6. T12: pruebas, lint, compilación, inspección del APK, evidencia por criterio y README.

## Contratos de datos

- Tiempo: milisegundos UTC en fronteras; dayNumber usa segundos UTC /86400 −10956. Grados para AR/Dec/acimut, Luna en radios terrestres y resto UA.
- Catálogo binario: cabecera ATC1, recuentos; registros big-endian de 8 bytes, puntos de 4, polilíneas de 4, etiquetas de 5; tablas UTF-8 de nombres y constelaciones.
- Red: transporte inyectable HTTPS, series ordenadas/deduplicadas; Kp observed/estimated/predicted separado. Cobertura horaria sin interpolar huecos. Caducidad respecto a consulta y periodo de muestra.
- Kiosco: permiso de inicio = owner + PIN + activado + fuera de mantenimiento. Parada solo tras PIN; restauración al volver de ajustes externos en mantenimiento controlado.

## Validación

Ver TAREAS.md y verification.md. Se revisarán casos normales y extremos con JVM. Lint sin supresión global, inspección de manifest y ZIP. Las pruebas Android físicas no se ejecutan en esta sesión. Sin infracciones constitucionales conocidas.

## Revisión tras implementación

Constitución y checklist conformes. Robolectric 4.14.1 y AndroidX Test se usan solo en pruebas; el informe `docs/DEPENDENCIAS_RUNTIME.txt` confirma que el APK depende únicamente de core, kotlin-stdlib y anotaciones JetBrains. Temporales, bibliotecas auxiliares de Gradle y clave debug se mantienen en rutas autorizadas. No se instaló SDK/CLI ni extensión alguna. La captura de datos de servicios se sustituyó por muestras locales debido al límite de red solicitado; no se presenta como validación de conexión real.
