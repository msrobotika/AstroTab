> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Verificación 003

06/11/2026. Implementación y revisión completadas. Spec Kit aplicado manualmente con las plantillas oficiales locales; CLI no disponible. Constitución 1.2, especificación, plan, tareas, investigación y checklist revisados. Respaldo previo: `backups/2026-11-06-astrofoto-045110.zip`, SHA-256 `749CFCC854AB78821C79A10130FAA797B7D03E1F3AAC1A68E65FFC4D2CCC370A`.

## Resultado reproducible

Entorno preparado con `tools/env.ps1`: JDK 17.0.13+11 y SDK local indicados por el equipo. Comando final exacto: `gradlew.bat testDebugUnitTest lintDebug assembleDebug`. **BUILD SUCCESSFUL**, 1 min 11 s. Registro: [build-validation.log](build-validation.log), también `docs/ASTROFOTO_BUILD_VALIDATION.log`.

**70 pruebas JVM, 0 fallos, 0 errores, 0 omitidas**: 58 en core Kotlin puro + 12 Android simuladas con Robolectric/API 23. De ellas, 19 nuevas (16 de cálculos/datos y 3 de pantallas) respecto a las 51 de 002. Resumen: [test-summary.json](test-summary.json); detalle por método en `core/build/test-results/test/` y `app/build/test-results/testDebugUnitTest/`. Los resultados sin cambios se reutilizan por Gradle; las pruebas Android se repitieron tras la última revisión.

Lint: **0 errores y 4 avisos**, sin baseline ni supresión global. [Informe](lint-debug.txt): versiones más recientes disponibles de Gradle/Robolectric, orientación fija en Android 16 y ABI x86_64 de ChromeOS. Se mantienen las versiones reproducibles y el destino Android 6/ARMv7; el APK no incluye código nativo. Se corrigieron las nuevas asignaciones de formateadores/fechas en onDraw: las etiquetas quedan precalculadas.

APK: `app/build/outputs/apk/debug/app-debug.apk`, versión 0.3.0/code 3, **1 194 517 bytes (1,14 MiB)**; SHA-256 `82A2C085F4B5C9A0F9160A6A33B0064F8BE35CDCF877707DD03F21C66FAA6DF0`. minSdk 23, solo INTERNET, sin bibliotecas nativas, firma v1/v2 válida para API 23. Se inspeccionaron estrellas, Messier, licencia y avisos empaquetados mediante `tools/verify-apk.ps1 -OutputDirectory specs/003-astrofoto`. [Inspección](APK_INSPECTION.json), [badging](APK_BADGING.txt), [manifest](APK_MANIFEST.txt).

Apksigner emite un aviso sobre `META-INF/com/android/build/gradle/app-metadata.properties` sin protección JAR individual; verificación v1 y v2 exitosa. Gradle también avisa de características obsoletas para Gradle 9; no es un aviso de lint ni impide esta compilación.

## Evidencia por criterio

| Criterio | Evidencia realizada | Límite |
|---|---|---|
| AC1: catálogo, búsqueda y filtros | `completeMessierWithIndependentJ2000References`: 110 IDs únicos/secuenciales; M31 (10,6847°,41,2688°), M42 (83,8221°,−5,3911°), M13 (250,4235°,36,4613°), tolerancia 0,1°. Búsqueda acentuada/alias/número y tipos. UI `messierSearchUsesOwnKeyboardAndDetailHasPrecomputedChart`. Script regenerado con hash idéntico. | Constelaciones ausentes → «—»; sin validación exhaustiva externa de todos los metadatos. |
| AC2: planificador y mapa | `usefulDurationAndScoreFactorsCanBeReconstructed` contrasta duración y factores de los 110 objetos y altura de tránsito. `circumpolarNeverRisingAndNoAstronomicalNight`, `dstNightsHaveRealDurationsAndMonotonicSamples` (23 y 25 h en Madrid). Pruebas Android de ficha/curva y `messierSkyMarkersCanBeSelected`. | Cruces de ventanas/curvas con paso 2 min. Culminación siguiente al inicio, puede ser diurna. No mide tacto real. |
| AC3: óptica/equipo/polar | `rule500AndNpfReferenceAndPoleLimit`: completo 50 mm, f/2,8, 4,3 µm → 10 s (500), 4,54 s (NPF δ=0°), 9,08 s (δ=60°). `fieldScaleAndMessierFit`: campo 39,59775°, escala 17,738773 ″/píxel; M31 cabe a 50 mm y no a 2000 mm. `equipmentAndMinimumSurviveRestart`. `precessionIdentityAndMeeusReference`, `polarClockJ2000IndependentSiderealReferenceBothHemispheres`. | NPF simplificada, sin garantía de estrellas puntuales. Ver fórmulas y orientación del reloj en research.md. |
| AC4: núcleo y horario | `galacticCoreLatitudesMonthsAndVisibility`: verano en Madrid, imposibilidad a 80° N y orden de 12 meses. `solarTwilightEventsMatchThresholdsAndMoonlessWindows`: orden, cruce solar −18° y muestras de ventanas sin Luna. UI comprueba reloj y barra precalculada. | Meses = promedio de tres noches representativas, sin meteorología. Eventos geométricos con las convenciones declaradas. |
| AC5: sesión manual | `sessionDurationBoundariesAndMonotonicCatchUp`: 40 s de luces/darks/flats/pausas y recuperación desde reloj inyectado. `sessionNoFinalPauseStopAndInvalidPlans`: sin pausa final, parada y entradas inválidas. UI inicia/para, muestra TOMA/cuenta roja y comprueba KEEP_SCREEN_ON. | Sonido/tiempos de reacción físicos pendientes. Sin ejecución garantizada en segundo plano ni tras matar proceso. |
| AC6: rocío, viento y caché | `dewThresholdAndWindFactors` comprueba límite estricto 3 °C. `weatherParserCacheOfflineAndNightPenalty`: campos, ausencias, caducidad, ubicación y nota. `weatherRequestAndCacheRetainOptionalFieldsWithoutExtraCalls`: URL con cuatro campos + km/h, 10 refrescos → 1 consulta meteorológica, persistencia y red apagada. | Transporte inyectado; no se ha contactado Open-Meteo, NOAA ni ipapi.co. Campos ausentes dejan factores sin evaluar, explicado en pantalla. |
| AC7: UI y coste | Pantallas anteriores y nuevas infladas/dibujadas en API 23, 1024×600; nuevas herramientas desplazables también a 480 px, controles ≥56 dp de alto y ausencia de EditText. Rojo atenúa ventana; cuenta roja global. Privacidad de ubicación cubierta. Revisión del código: caché de noche/curvas por minuto, lugar, zona y mínimo; cálculo en worker, Canvas sin astronomía ni red. | No certifica fuentes, blancos del sistema, áreas táctiles reales, PSS o fps. Los datos de 110 curvas ocupan ~0,63 MiB como máximo (25 h), no es una medición de memoria de la app. |
| AC8: entrega | Build, tests, lint, inspección APK, README, TAREAS, PLAN y Acerca de actualizados; solo casillas de código/JVM cerradas. | Validación física excluida. |

## Fuente y método

JSON autorizado descargado desde d3-celestial con TLS validado usando Node existente, después de fallos de TLS de PowerShell/curl de Windows. No se han cambiado certificados. Original: [messier-source.json](messier-source.json), 21 568 bytes, SHA-256 `b576a9ef2ff1e3e81af49779c76ceecdf1e2f88b7bd7d21736fa9d590c9cb2b4`. `tools/generate-messier.cjs` produce TSV UTF-8 de 5160 bytes; normaliza AR, conserva alias originales y campos ausentes. Se traduce la presentación de nombres/tipos, sin inferir constelaciones. BSD-3 íntegra conservada y empaquetada.

Fórmulas completas, referencia polar calculada en J2000, precesión IAU 1976, reglas ópticas, puntuaciones y convenciones de crepúsculos: [research.md](research.md). El reloj polar no depende de sensores. La sesión usa `SystemClock.elapsedRealtime`; callbacks ajustados al próximo límite de fase hasta un máximo de 200 ms; cambios de hora civil no cambian la duración.

## Incidencias de desarrollo resueltas

- Caché global Gradle no escribible: se copió la caché existente a `.gradle/user-home` dentro del proyecto; entornos Android/JVM también locales. No se instaló nada en el sistema.
- Variables Android heredadas incompatibles: se alinearon ANDROID_USER_HOME/ANDROID_PREFS_ROOT bajo el mismo directorio local.
- El informe HTML experimental de problemas de Gradle anterior no se puede reemplazar/mover por permisos Windows. `org.gradle.problems.report=false` desactiva únicamente ese informe redundante; errores de consola, tests y lint completos permanecen activos. No se ocultan problemas de lint.
- Primera referencia polar falló por una comparación exacta de Doubles con diferencia ~6×10⁻¹⁴°; corregida tolerancia 10⁻¹⁰° para esa equivalencia. Referencias astronómicas mantenidas en 0,02°.
- Se corrigió la nulabilidad del array de permisos en la prueba Android y se repitió el conjunto. También se precalcularon etiquetas Canvas, se conservaron posiciones de scroll al refrescar y se colocó la cuenta atrás antes del formulario.

## Pendiente sin verificar

En Alcatel 8063: instalación/arranque de esta versión, memoria PSS **<60 MB**, fluidez, tacto/legibilidad de tarjetas y teclado, orientación del buscador óptico concreto, brillo rojo y diálogos del sistema, volumen/pitidos y precisión del temporizador bajo carga real. La versión anterior funcionaba con unos 26 MB según el equipo; no se extrapola esa cifra a 0.3. Servicios remotos reales/TLS en Android 6 y caché meteorológica obtenida en tablet pendientes. Kiosco/HOME/reinicio mantienen sus comprobaciones físicas históricas pendientes.

Sin adb, dispositivos, instalaciones, publicación ni git push. La única descarga de datos de desarrollo ha sido el catálogo solicitado; las demás conexiones permitidas son dependencias Gradle/Maven.
