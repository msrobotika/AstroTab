> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Feature Specification: Pulido AstroTab 0.7.0

**Created**: 2026-11-06 · **Status**: Accepted for implementation
**Input**: Ronda de pulido de el equipo; siete áreas y aceptación Gradle completa.

## User Scenarios & Testing

### US1 — Preparar un paso visible (P1)
Con TLE y ubicación, ver primero la ISS, luego los satélites brillantes por hora.
Aceptación: filtro visible ≥10° por defecto, seleccionable 10/20/30°; inicio,
máximo y fin geométricos con cardinal; tramo visible diferenciado y sombra;
fila con icono/hora/píldora/polar, ficha polar y acceso al cielo; tarjeta ISS en Inicio.
No inventar magnitudes. Conservar precisión SGP4 verificada en 007.
Objetivos físicos: ISS <2 s, 156 satélites <15 s. Medir JVM sin presentarlo como tablet.
Caché al volver, invalidada por TLE/ubicación y caducidad temporal; refresco TLE >24 h
solo con red habilitada en la app, sin ejecutar peticiones externas en desarrollo.

### US2 — Reconocer avisos (P1)
Cada alarma muestra Activa/Inactiva, próxima fecha local (Hoy cuando procede),
permite activar/desactivar y borrar. Estado persiste al recrear app y preferencias;
alarmas desactivadas no se programan ni disparan.

### US3 — Planificar sin empezar desde cero (P1)
Estado vacío visual con icono y «Planificar esta noche». Propone hasta tres objetos
de calidad y tipos variados con ventanas útiles, sin solapes. Quitar/cambiar objetos,
barra nocturna por colores y aviso visible de meridiano cuando corresponda.

### US4 — Leer y configurar con claridad (P2)
Eventos agrupados por semana con Hoy y resúmenes útiles o sin subtítulo.
Iconos distintos: herramientas llave/regla, más cuatro puntos, ajustes engranaje,
equipos cámara, eventos calendario, alarmas campana. Equipos: toque activa tarjeta,
datos/FOV y hoja de edición con +/−. Revisar todas las pantallas a 1024×600,
jerarquía/izquierda/iconos/redondeo; regenerar e inspeccionar cada PNG nuevo.

## Requirements / Success Criteria
- SC1: pruebas de filtro/orden/caché y precisión orbital pasan.
- SC2: prueba de persistencia de alarmas y programación desactivada pasa.
- SC3: propuesta automática diversa sin solapes verificada.
- SC4: iconos distintos comprobados en render nativo; PNG revisados visualmente.
- SC5: terminal nueva: gradlew.bat testDebugUnitTest lintDebug assembleDebug.
- SC6: registrar pruebas, lint, bytes APK, capturas y límites sin hardware.

## Assumptions / Clarifications
Sin preguntas materiales pendientes. «Visible ≥10°» se aplica al máximo del tramo
visible, conservando también máximo geométrico. Horizonte de cinco días; caché
reutilizada hasta cambio de TLE/lugar o fin de cobertura (renovación diaria para
mantener cinco días). Tres objetivos cuando existan tres ventanas suficientes;
sin noche astronómica se explica la ausencia. App sin conexión por defecto.
Informe una revisión externa de 0.6.0: evidencia externa aportada por el equipo, no ejecución propia.
