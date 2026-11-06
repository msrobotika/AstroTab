> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Verificación AstroTab 0.6.0 · Pro B

2026-11-06. **BUILD SUCCESSFUL** desde PowerShell nueva sin perfiles ni variables Java/Android heredadas: `gradlew.bat testDebugUnitTest lintDebug assembleDebug` (build-final.log, 2 min 53 s). **129 pruebas: 99 core +30 app, 0 fallos/errores/omitidas. Lint: 0 errores, 4 avisos heredados** (versiones Gradle/Robolectric disponibles, orientación Android16 y ABI ChromeOS). Solo evidencia de PC/JVM/Robolectric; sin adb, dispositivos, instalaciones ni push.

APK debug **0.6.0/code7**, **1.658.328 bytes (1,58 MiB)**, SHA256 `D05DBC5ED544F3CFE6B301A21D3A17D1D5EEA9499D445A292B2963D74CF2E675`. minSdk23, solo INTERNET, sin bibliotecas nativas, firma v1/v2 verificada por apksigner para API23. Aviso de apksigner sobre metadatos de compilación META-INF no cubiertos por firma JAR: conservado en apk-validation.log; v2 verificada. No se ha instalado. 20 capturas nuevas, hashes en screens.json y [galería](../../docs/screens/pro-b.html).

## Trazabilidad

| Criterio | Evidencia | Alcance y límite |
|---|---|---|
| R1 cero | ProBRegressionTest.roundedZeroHasNoSign, bugs/before.log | Antes falla, después pasa; formato común también en ejes/cursor |
| R1 caché | ProBScreensTest.objectsReuseSameCacheAndArraysAcrossTabsAndHome y ScreensTest.plannerCachesWholeNightAndPublishesPartialBatches | Misma identidad tras cambiar pestaña, sin progreso; invalidación por mínimo y clave noche/lugar/zona |
| R1 Top/curvas/día | ProBTest.diverseTargetsAreSeparatedAndCurvesHaveAllSamples; ProBRegressionTest de paleta; PNG Inicio/Cielo | ≥2°, tipos diversos, >600 muestras a 2 min, rojo y día renderizados |
| A SGP4 | SatelliteTest.officialValladoVectorsNearAndDeepSpace | 491 estados oficiales de 23 satélites, 00005 incluido; 1 m / 1 mm/s, cercano y profundo |
| A TLE/TLS | SatelliteTest parser/repositorio/revocación; CelestrakTlsTest | Checksum/límites/edad, offline/reinicio/fallo, cancelación antes de segunda petición; PKIX con raíz documentada y manipulación rechazada |
| A pasos | SatelliteTest fiveDayBenchmarkAndVisibility e interpolación; ProBScreensTest, PNG lista/polar/Cielo | Cinco días, 150 TLE, parciales; ISS directo vs interpolado ≤1 s/0,1° en fixture; sin magnitudes. No garantiza fragmentos visibles <30 s |
| A rendimiento | benchmark.txt | Primera llamada y tres cálculos completos posteriores, factor ARMv7 ×15 sin calibración; distinguir objetivo sostenido y primera llamada. Ver apartado siguiente |
| B eventos/NASA | ProBTest nasaTableCompleteAndGlobalMaximumHorizonHonest y calendarPhasesExtremaAndConjunctionsAreCalculated | 45 eclipses, TD−ΔT→UTC, fases/extremos/separaciones y altura local; precisión orientativa declarada. Altura solar no confirma la franja local |
| C plan | ProBTest sessionBlocksStayInsideWindowsDoNotOverlapAndWarnMeridian; ProBScreensTest plannedTimerAutomaticallyWritesCompletedObjectToLog | Ventanas sin solapar, integración, tomas, meridiano, sesiones persistentes; completado escribe una vez con reloj simulado. Plan de Messier; no controla cámara |
| D alarmas | ProBTest alarmsCrossingsKpExpirationAndPersistence; ProBScreensTest api23SchedulesExactReceiverAndOnlyInternetPermission | Política, cruce refinado, Kp ausente/caducado, PendingIntent/RTC_WAKEUP/cancelación/receiver/diálogo. Sonido y despertar físicos sin verificar |
| E equipos | ProBTest equipmentMigrationActiveRestartAndIndependentProfiles | Migración, activo persistente, perfiles separados, sensor/tamaño; herramientas y mapa consumen el getter activo |
| V tema/layout | ProBScreensTest.nativeProBScreensAndRedAt1024x600 más RedesignScreensTest | 20 PNG nuevos API28; botones ≥56 dp; paleta roja por píxel; Inicio sin scroll y todos sus hijos dentro de 1024×600 |
| V compilación | build-final.log y test-summary.json | Terminal nueva PowerShell -NoProfile; variables Java/Android retiradas, wrapper fija rutas del usuario |
| V APK/licencias | APK_INSPECTION.json, APK_BADGING.txt, APK_MANIFEST.txt, fuentes/hashes y notices empaquetados | Inspección estática sin instalación; versión/permisos/recursos/firma/tamaño |

## Rendimiento y precisión

PC Intel Xeon E5-2650 v2 2,60 GHz/JDK17. La prueba vuelve a calcular todo en cada repetición (no usa la caché de la app). Último benchmark: primera llamada **332,694 ms**; posteriores 230,695 / 230,999 / 260,030 ms; mediana **230,999 ms**. Con factor hipotético ×15: **3,46 s sostenidos y 4,99 s para primera llamada**. La última estimación cumple <5 s, con holgura mínima en frío; una ejecución anterior del mismo algoritmo dio 419,174 ms en frío (6,29 s extrapolados). Por esta variabilidad no se acredita una cota estricta de arranque frío ARMv7 <5 s. Android6 ART/AOT difiere del JIT del PC, y el factor no está calibrado con hardware. Se conserva todo el historial y el último resultado íntegro en benchmark.txt.

Pasos con SGP4 de referencia y Hermite entre estados; observador/Sol interpolados, malla de visibilidad 30 s, máximo a 5 s y refinamiento de extremos subsegundo. No implica exactitud temporal física: épocas TLE antiguas, maniobras y horizonte real dominan. Los resultados de muy corta duración pueden faltar. Magnitudes ausentes de forma deliberada.

Eventos con modelo aproximado existente: resolución numérica 1 min, exactitud orientativa de horas y ~1°; no se afirma validación de precisión de almanaque para todos los eventos. Meteoros son climatología. NASA aporta máximo global, no máximo local. En solares se deja «visibilidad local por confirmar» aun con Sol arriba; circunstancias besselianas exactas quedan fuera de esta implementación.

## No verificado y límites restantes

- Hardware: rendimiento ARMv7, memoria, tacto, acelerómetro, brillo, sonido, despertar desde detrás/Doze, arranque HOME/reinicio y TLS real de Android6.
- No se garantiza una cota <5 s para primera predicción en frío: la última extrapolación es 4,99 s, pero hubo 6,29 s en otra ejecución. Estimación sostenida 3,46 s.
- Visibilidad local exacta/totalidad de eclipse solar, pasos satelitales iluminados/rasantes <30 s y magnitud satelital no calculada.
- Alarmas API31+ están protegidas por canScheduleExactAlarms y fallback inexacto, sin nuevos permisos; no se ensayan en sistema físico. Sin permiso de arranque se reprograman al abrir la app. Kp no consulta en segundo plano y exige datos vigentes.

Revisión constitucional posterior: copia protegida; fuentes oficiales y licencias citadas; ninguna dependencia runtime ni permiso añadido; rojo y controles mantenidos; sin acciones de publicación/hardware. No hay repositorio Git y no se creó uno.
