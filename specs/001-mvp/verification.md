> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

﻿# Verificación del MVP · 06/11/2026

Alcance: T01–T12 de código y pruebas sin hardware. Spec Kit aplicado manualmente con sus plantillas oficiales locales. No se ha ejecutado adb, conectado a servicios de datos, instalado software, accedido a dispositivos, publicado ni hecho push.

Actualizada tras las correcciones de cabecera, inmersivo, mapa y Ajustes. Informe de bugs y contraste AC1–AC6: [002-tablet-fixes](../002-tablet-fixes/bugs/test.md). La validación real comunicada por el equipo corresponde al APK anterior; estas correcciones no se han probado físicamente.

## Resultado reproducible

Desde PowerShell en la raíz:

```powershell
. .\tools\env.ps1
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
. .\tools\verify-apk.ps1
```

La compilación final terminó con **BUILD SUCCESSFUL en 41 s**. Registro: [TABLET_BUILD_VALIDATION.log](../../docs/TABLET_BUILD_VALIDATION.log). Las 42 pruebas de core se ejecutaron tras el último cambio del núcleo y se conservan los XML; las 9 pruebas de app se ejecutaron de nuevo en la compilación final tras ajustar el fixture de métricas del texto simulado.

- **51 pruebas JVM**, cero fallos, cero errores y cero omitidas: [resumen](../../docs/TABLET_TEST_SUMMARY.json), [XML](../../docs/tablet-test-results/).
- **Lint: 0 errores, 4 avisos**: [informe](../../docs/TABLET_LINT_DEBUG.txt). Avisos: versiones más recientes de Gradle/Robolectric, orientación fija en Android 16 y filtro ARMv7 frente a ChromeOS. Se mantienen las versiones probadas y el objetivo de la tablet. Sin baseline ni supresión global. Las excepciones locales de constructores Canvas y fallback de Atrás están explicadas en código; API 33+ registra su callback de Atrás.
- **APK**: `./app\build\outputs\apk\debug\app-debug.apk`, **1 144 485 bytes (1,09 MiB)**. SHA-256: `BB4A7D35E6E9CEF91939D799CEEAE74FF757334BDDEF10400ADEE7E1ADEA8629`.
- minSdk **23**, compile/targetSdk **36**, horizontal, único permiso **INTERNET**. HOME/launcher y receptor protegido por BIND_DEVICE_ADMIN presentes. Sin cámara, GPS, Bluetooth ni almacenamiento amplio.
- ZIP sin entradas `lib/`: ninguna biblioteca nativa, ninguna exigencia ARM64; bytecode compatible en arquitectura con ARMv7. Catálogo, MIT, BSD-3 íntegra y Apache-2.0 incluidos.
- `apksigner verify --verbose --min-sdk-version 23`: firma **v1 y v2 válidas**. Un aviso sobre `META-INF/com/android/build/gradle/app-metadata.properties` fuera de la cobertura v1; es metadato de AGP, sin código ejecutable. La firma v2 sí protege el APK completo.
- [Inspección](../../docs/APK_INSPECTION.json), [manifest](../../docs/APK_MANIFEST.txt), [badging](../../docs/APK_BADGING.txt), [dependencias de ejecución](../../docs/DEPENDENCIAS_RUNTIME.txt). Solo core, kotlin-stdlib y anotaciones JetBrains; Robolectric/AndroidX Test/org.json/JUnit son de pruebas y no se empaquetan.

## Trazabilidad por criterio

| Criterio / tareas | Evidencia de código y pruebas | Pendiente real |
|---|---|---|
| A1 · T01/T02/T04/T08 | Ajustes sin ubicación ficticia, coma decimal y límites; reinicio de Settings y caché; API 23 simulado sin ubicación/red; catálogo offline completo. | Instalar, arrancar/reiniciar sin Wi-Fi en Alcatel. |
| A2 · T05/T06 | Geometría, selección próxima y orientación; rumbo no modificado por sensor; remapeo de rotación. Test Android simulado comprueba registro en Cielo, baja al abrir Luna y pausa de actividad. | Signo/calibración y sensación de inclinación/roll en tablet; selección visual y respuesta táctil reales. |
| A3 · T03/T05/T07 | Diez valores Meeus con tolerancias originales; fases conocidas, cambio de fecha, UTC/DST, hemisferios, paralaje y cruces sin salida/puesta. Controles −1 h/ahora/+1 h y fichas dinámicas en código. | Contraste visual del cielo y de los eventos en lugar/fecha reales. |
| A4 · T08/T09/T10 | JSON local, UTC/ordenación/tipos, cobertura/frescura, fallos TLS/desconexión inyectados y límite de tamaño; red apagada y cadencia de 30 min incluso tras cargar caché; IP explícita. Nota sin nubes nula; ventanas futuras contiguas; polar, GC >10°, oscuridad −18°; Kp por hemisferios y fuera de escala. | Contratos y TLS con los servicios reales, especialmente certificados del Android 6. |
| A5 · T04/T07 | Recuentos reales por magnitud/horizonte, Bortle monotónico y límite mag. 6. Siete pantallas medidas a 1024×600/mdpi en Robolectric; botones ≥56 dp. Márgenes de 4+4 dp, foco por estados y paleta roja; brillo 0,03 y teclado numérico propio comprobados. | Revisión visual humana en pantalla física, legibilidad, foco, luz residual del panel y ajustes del sistema. |
| A6 · T11 | PinGate: sal/hash, persistencia, validación y espera; política sin owner/permisos/PIN/mantenimiento; DPM simulado prueba lista lock task/owner y desactivación. HOME y DeviceAdminReceiver en APK. | Aprovisionar, reiniciar, salir/volver, matar proceso y recuperar equipo real. |
| T12 | Compilación, pruebas, lint e inspección estática satisfactoria. | PSS <100 MB tras 10 min, 15–30 fps y estabilidad térmica/energética. |

## Referencias Meeus ejecutadas

Valores del `selfTest` de AstroCYD MS; tolerancias originales, no resultados heredados del ESP32:

| Valor | Referencia | Tolerancia |
|---|---:|---:|
| Sol AR (13/10/1992) | 198,38083° | 0,05° |
| Sol Dec | −7,78507° | 0,05° |
| Luna AR (12/04/1992) | 134,68847° | 0,15° |
| Luna Dec | 13,76837° | 0,15° |
| Luna distancia | 368 409,7 km | 1 500 km |
| Venus AR (20/12/1992) | 316,17273° | 0,05° |
| Venus Dec | −18,88801° | 0,05° |
| GMST (10/04/1987) | 197,693195° | 0,05° |
| Altura, Washington | 15,1249° | 0,1° |
| Acimut, Washington | 248,0337° | 0,1° |

`AstroTest`: 14 casos; `CatalogTest`: 3; `SettingsGeometryTest`: 5; `NetworkTest`: 8; `NightAuroraKioskTest`: 5; `DisplayRegressionTest`: 7; `ScreensTest`: 9. Todos aprobados. El dibujo de las pantallas se invoca en el Android simulado de Robolectric; **no es una captura ni una prueba visual sobre el dispositivo**. El test adicional de etiquetas suministra métricas sintéticas porque Paint legacy API 23 devuelve ascent/descent=0; comprueba orden, horizontalidad y selección, no tipografía real.

## Decisiones y ajustes durante la convergencia

- El proceso restringido de Java usaba inicialmente un directorio raíz como home. Se configuraron home, cachés y temporales autorizados en `tools/env.ps1`; compilador Kotlin dentro del proceso de Gradle. No se instalaron JDK/SDK ni programas.
- La firma de depuración predeterminada no podía bloquear su archivo externo. Se genera una clave local de pruebas en `.gradle/astrotab-debug.keystore`, mediante el keytool del JDK disponible. No usarla para producción; conservarla si se requiere actualizar una tablet aprovisionada con este APK.
- Se corrigieron colisiones de nombres con propiedades View al aplicar colores; se añadió el callback de Atrás de API 33+ sin incorporar AndroidX a la ejecución.
- Se evitó parar lock task cuando no está activo, se actualizaron efemérides de fichas de planetas, se evitó duplicar mantenimiento al volver de ajustes y se conservaron los intervalos de red tras cargar caché.
- La caché usa sustitución atómica de archivo en Android; en Windows tiene respaldo para actualizar un archivo existente y recuperarlo si falta el principal. Prueba de escritura/actualización/reinicio y corrupción en JVM.
- Se rechazaron peticiones por fotograma. La red solo se llama desde el ejecutor, con 8 s de conexión/lectura, HTTPS validado, sin redirecciones y máximo 512 KiB. Los errores de transporte se verifican por inyección; no se han hecho peticiones a los proveedores.

## Riesgos y límites

Pruebas Android **simuladas**, no instrumentación ni ejecución Dalvik/ART real. El siguiente paso de el equipo es la lista física de T11-H/T12-H y [KIOSCO.md](../../docs/KIOSCO.md). Sin esa evidencia no se afirma estabilidad, rendimiento, brillo ni recuperación real.

Cálculos aproximados de Schlyter; estrellas J2000 sin precesión (~0,4° en 2026), sin refracción ni relieve. Fases con resolución de una hora; nota heurística sin seeing/transparencia y umbral Kp no probabilístico. No apto para guiado. Android 6 puede rechazar TLS por certificados antiguos; no se relaja su validación. Las pantallas de ajustes del sistema están fuera de la paleta de AstroTab. Device Owner exige conservar PIN, copia y método de recuperación antes de activarlo. Cámara/Messier/planificador/Bluetooth permanecen fuera de este MVP.

## Base protegida y coherencia

Originales sin modificar en `backups/2026-11-06-inicial`:

- `PLAN.md`: SHA-256 `7A6736C7CF3976DF57CFF24563FE6D9D00EC93B94B521A3DADE480F6EF044E28`.
- `TAREAS.md`: `F95822D15625574B3C1557DA7E4F9997820BBE4832DA13B91132BD26B84093B3`.
- `boceto.html`: `46EE496BAD527C6517B3E72986C268BB18359D309A4E9E8E34AA5652A7008327`.

No se inicializó un repositorio ni se destruyó trabajo previo. Catálogo y fuente local trazados en [CATALOGO.md](../../docs/CATALOGO.md). Constitución, especificación, tareas y checklist revisados tras implementar: A1–A6 cubiertos en el alcance sin hardware, A7/A8 separados. T11/T12 conservan pendientes sus casillas físicas; el desglose C verificado acredita la entrega autorizada.
