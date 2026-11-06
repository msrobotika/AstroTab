> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Verificación de AstroTab 0.5.0 · 06/11/2026

Entrega de software verificada en Windows/JVM. Spec Kit manual con plantillas oficiales locales, constitución 1.5.0, especificación, plan, tareas, checklist y diagnóstico/reparación/pruebas de Robolectric. CLI ausente, sin extensiones/hook configurados ni agentes adicionales. Base previa protegida en `backups/2026-11-06-rediseno-074744.zip`; SHA-256 en spec.md.

## Aceptación reproducible

Desde terminal nueva, sin dot-source de env.ps1, sin JAVA_TOOL_OPTIONS/GRADLE_OPTS heredados y sin daemon previo:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon --console=plain
```

`build-acceptance.log`: **BUILD SUCCESSFUL in 2m 54s**, salida 0. **110 pruebas: 84 core + 26 app; 0 fallos, 0 errores, 0 omitidas**. Incluye 13 pruebas nuevas (9 core y 4 Android simuladas). XML de suites nuevas y `test-summary.json` archivados. La regresión del planificador vacía explícitamente su caché antes de comprobar publicación 22/44/66/88/110, porque Inicio ahora la precalcula.

`lint-debug.txt`: **0 errores, 4 avisos heredados**: versiones más nuevas de Gradle y Robolectric, orientación fija ante Android16 y ABI ChromeOS. Los avisos nuevos de textos/accesibilidad se corrigieron con recursos y detección estándar de click. Solo supresión local ViewConstructor en vistas programáticas, con explicación; sin baseline ni supresión global. Gradle conserva su aviso previo sobre obsolescencias ante Gradle9.

## Evidencia por criterio

| Criterio | Evidencia y resultado | Límites |
|---|---|---|
| A1 Sistema visual | Tokens y transformación en core/ui/theme/Design.kt; componentes/iconos originales en app/ui/theme/Widgets.kt. Tarjetas, medidor, píldoras, mini-curvas, secciones/hojas y pestaña activa. `usefulRoundingAndDurationsCarryMinutes`, `everyPaletteAndArgbTransformHasNoDominantGreenOrBlue`; revisión de PNG. | Legibilidad/brillo físicos pendientes. |
| A2 Inicio | `homeInflatesAt1024x600NoScrollAndEveryVisibleChildFits` API23; `nativeHomeWithDewWarningFitsAndMoonCellsOpenDetails` API28 nativa, con datos sintéticos no persistidos. Relojes local/UTC/TSL, nota/mejor noche, barra y tarjetas; no ScrollView ni descendientes fuera del panel. Caso T8/Td7 muestra aviso; 96,6% se muestra 97%. `automaticRedCivilDuskAndSunriseAndPreference` y `homeMorningPreparesUpcomingNightWithoutMakingWeatherFresh`. | Sin captura ni arranque HOME físico. Las capturas de entrega están sin meteorología, no muestran fixtures como datos reales. |
| A3 Mapa | `milkyWayCountsAndGalacticCentre`: 202 anillos, 3237 vértices, cinco niveles; centro galáctico dentro, polo galáctico fuera. `allEightCardinalsProjectAtTheirOwnBearingAndWrap`, `cameraFrameMatchesExactAngularField`, `timeSliderEndpointsMidpointClampAndPolarFallback`, `selectedObjectUsesSameAltitudeAzimuthAsExistingEngine`. `skySearchCentresTargetAndSliderAndFrameToggleWork` API23. PNG Cielo, arrays/Path reutilizados y presupuesto en research.md. | No medición de 20fps/MT8127. Encuadre aproximado sobre proyección cilíndrica, especialmente a gran altura/campo amplio. |
| A4 Otras pantallas | 25 PNG de pantallas y 9 rojos, todos 1024×600. Ficha/lista/mini-curvas, noches con medidores y gráficas conservadas, Luna con rejilla completa y detalle por día, herramientas/calculadoras individuales y plan de tomas con +/−, ajustes por secciones. Regresiones de navegación/scroll, búsqueda, gestos de 300px, sensores pausados, timer, teclado, CSV y kiosco simulados pasan. | Los contenedores desplazables se capturan en su posición inicial. Tacto, sonido, sensores y SAF físicos no probados. |
| A5 Reproducibilidad/entrega | SDK23/28/35 declarados por Gradle, preparación automática y resolver Robolectric offline con ruta absoluta; recursos nativos preparados en build. `build-clean-terminal.log`, `build-clean-final.log`, `build-delivery.log` y aceptación final. README, TAREAS, Acerca de 0.5.0, avisos y licencia empaquetados. | Maven/Gradle deben tener red autorizada en la primera preparación, o caché completa para --offline. |

### Cálculos conservados

`source-preservation.json` compara SHA-256 contra la copia anterior: Astro, AstroPhoto, Night, ProAstro, ProTools, PhotoSession, NetworkRepository, SecureTls, Catalog y Messier son **idénticos**. Se conservan todas sus pruebas anteriores. Forecast solo añade `nightReference=now` opcional para separar la noche presentada del instante real de frescura; fórmulas y comportamiento por defecto conservados. Inicio prepara la próxima noche tras la salida del Sol por la mañana. No se fabrica meteorología ni se rejuvenece la caché.

## Capturas y render

**34 PNG**, revisados, manifiesto con dimensiones/tamaños/hashes en `screens.json`; [galería](../../docs/screens/index.html), [condiciones](../../docs/screens/README.md). Rutas `docs/screens/00-Inicio.png` a `24-Meteoros.png`, y `26-Inicio-rojo.png` a `34-Ajustes-rojo.png`. Los nueve paneles rojos pasan comparación de **todos** sus píxeles: R≥G y R≥B. Las gráficas con datos sintéticos y Kp conservan pruebas de trazados/cursor/rojo existentes.

Render nativo de Robolectric **API28**, no tablet real. API23 se comprueba con backend legacy/layout; no es posible obtener de ese backend capturas raster fieles como las nativas. Las métricas de nombres del antiguo test de Canvas API23 siguen siendo sintéticas y explícitas; las capturas API28 añaden fuentes/render nativos. Ninguna de estas pruebas acredita el brillo o el rendimiento del firmware real.

## APK

- `app/build/outputs/apk/debug/app-debug.apk`: **1.481.634 bytes**, aproximadamente 1,41 MiB.
- SHA-256: `F257DF2D68BA895C15347DF619DD4D2C1C3A102420B369F6CAC87CC966BF3524`.
- versionName **0.5.0**, versionCode **6**, minSdk23, único permiso **INTERNET**, sin bibliotecas nativas.
- Firma v1/v2 válida para API23 mediante apksigner; aviso heredado de app-metadata.properties bajo META-INF preservado en apk-validation.log.
- Contiene estrellas/Messier/Vía Láctea compacta, MIT, BSD-3 y avisos sincronizados. Inspección en APK_INSPECTION.json, APK_BADGING.txt y APK_MANIFEST.txt.

## Pendiente de verificación

Instalar/arrancar 0.5.0 como HOME en Alcatel8063, ≥20fps en gesto, memoria/PSS/RSS, sensores, tacto/legibilidad, rojo/brillo, sonido, exportación SAF y servicios de red Android6. El informe físico de 0.4.1 se conserva como contexto externo y no se atribuye a 0.5.0. El presupuesto de 50ms y los bytes de arrays son estimaciones de diseño, no mediciones de la tablet.

Sin adb, dispositivos, instalación del sistema, publicación ni git push. Red de esta sesión solo Gradle/Maven y descarga de mw.json de d3-celestial BSD-3. No GET reales a meteorología, NOAA o IP; pruebas con fixtures/transporte inyectado y loopback local del conjunto anterior.
