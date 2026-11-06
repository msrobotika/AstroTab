> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Verificación 008 · AstroTab 0.7.0

Entrega local del 06/11/2026. VersionName **0.7.0**, versionCode **8**.
Proceso Spec Kit manual con plantillas oficiales locales; CLI ausente. Constitución
1.7.0, especificación/decisiones, plan, tareas y checklist revisados antes de implementar.
No hay repositorio git en esta carpeta. Base protegida antes del código:
`backups/2026-11-06-100923-pulido-base.zip`, SHA256
`A87755C80A8CA9C9235112F679E602FA6AEB0D9E85E845E3D035BBC2FB007D71`.

## Resultado ejecutado

PowerShell nuevo **-NoProfile**, variables de sesión reconstruidas por
[tools/verify-pulido.ps1](../../tools/verify-pulido.ps1), JDK 17.0.13+11 / SDK local.
Tras `gradlew.bat clean`, ejecución exacta:

```text
gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain
BUILD SUCCESSFUL in 3m 12s
59 actionable tasks: 59 executed
```

[Log íntegro](build-clean.log). **143 pruebas: 107 core + 36 app**, sin fallos,
errores ni omitidas; **14 pruebas nuevas**. Inventario de suites en test-results.json.
Resultados originales en core/build/test-results/test y
app/build/test-results/testDebugUnitTest. Regresión oficial SGP4: 491 vectores,
tolerancias 1 m / 1 mm/s; no se modificó el propagador Vallado.

**Lint: 0 errores, 4 avisos heredados**: AndroidGradlePluginVersion,
NewerVersionAvailable, ChromeOsAbiSupport y DiscouragedApi.
Informe original app/build/reports/lint-results-debug.txt.
Gradle informa además de APIs obsoletas de su propia compilación; no se han
actualizado dependencias fuera del alcance de esta ronda.

APK debug: **1.450.541 bytes (1,38 MiB)** en
`app/build/outputs/apk/debug/app-debug.apk`.
SHA256 `372E6438D6BF4432A9B8461A893C73863EC89141075136BC19C83D25C9A77409`.
[Inspección estática](apk/APK_INSPECTION.json): API mínima 23, solo INTERNET,
sin bibliotecas nativas, recursos y licencias presentes. apksigner verifica
v1/v2 para API23; emite el aviso de metadatos Gradle en META-INF sin proteger
por firma v1, conservado en [log de inspección](apk-inspection.log).
No se ha instalado ni ejecutado el APK en un dispositivo.

## Contraste por criterio

| Criterio | Evidencia | Resultado |
|---|---|---|
| SC1 filtro visible 10/20/30 y orden | PolishTest.visibleHeightFilterAndChronologicalOrderUseVisiblePart | Pasa; usa altura del tramo visible, no un máximo en sombra |
| SC1 caché y prioridad ISS | cacheReusesIdentityAndInvalidatesTlePlaceAndTime; issIsPublishedFirstAndCompletePassHasShadowTransition; PolishScreensTest.navigatingBackReusesOrbitalCacheAndFiltersWithoutPrediction | Pasa; identidad conservada al navegar, invalida TLE/coordenadas/24 h |
| SC1 precisión y descarte | 491 vectores oficiales; comparación fast/direct ISS; malla SGP4 independiente de 10 s para ISS + 5 satélites, un día, sin perder muestras visibles ≥10°; impossibleInclinationCullsOnlyUnreachableLatitudes | Pasa en las muestras comprobadas; no prueba exhaustiva de todos los TLE/latitudes |
| Refresco automático >1 día | automaticTleRefreshHonoursOneDayAndNetworkAndBackoff | Pasa con Transport simulado, sin peticiones reales |
| Paso completo, polar, sombra, Inicio y Cielo | nativePolishEmptyAndPopulatedScreensAndEquipmentSheet; selectedPassOpensItsTrackAtVisibleMaximumInSky; pro-b-02 / pulido-09 / pulido-09b / Inicio | Pasa en Robolectric; fecha simulada y trayectoria seleccionada |
| SC2 estado de alarmas y reinicio app | alarmsEnabledStateSurvivesFreshStoreAndMigratesLegacy; alarmsSurviveActivityRestartDisabledStaysUnscheduledAndDeleteWorks | Pasa; nueva instancia del almacén y actividad, activa/inactiva persistente; desactivada no programada ni disparada; borrado |
| SC3 plan automático | automaticPlanContainsThreeDiverseCompleteNonOverlappingObjects | Tres objetos, ≥2 tipos, integración de 30 min completa, dentro de ventanas, sin solapes: noviembre/marzo/julio en Madrid; sin ventanas devuelve vacío |
| SC3 interacción y meridiano | emptyPlanProposesThreeAndEquipmentCardActivatesAndSheetSaves; tests de SessionPlanner; pulido-01/02/12 | Pasa; quitar objeto, barra y aviso visible; cambio disponible por lista de alternativas |
| SC4 iconos, equipos y eventos | navigationIconsHaveDifferentMappingsAndDifferentPixels; prueba de activar tarjeta/editar/guardar perfil; 70 PNG revisados | Pasa en las pantallas generadas; calendario por lunes, Hoy según zona local y resúmenes calculados |
| SC5 terminal limpia | build-clean.log | Pasa, limpieza y las tres tareas completas |
| SC6 artefactos y límites | Este documento, APK_INSPECTION.json y screens-review.md | Documentados |
| ISS <2 s / 156 <15 s en tablet | No se utilizan dispositivos en esta sesión | **Pendiente de medida física** |

## Rendimiento medido y cambios del contrato

[Benchmark 156](benchmark.txt), JVM PC: primera publicación ISS **56,45 ms**;
156 satélites ×5 días **701,44 ms**. Incluye búsqueda/refinado y parciales del motor,
no construcción de filas Android ni red. [Benchmark 150](benchmark-150.txt):
primera llamada 548,33 ms; mediana de tres cálculos posteriores 476,53 ms.
La medida depende del orden/calor JIT y carga del PC. No se aplica un factor
arbitrario a ARMv7 ni se afirma cumplimiento de tiempos reales.

Detección con malla de 60 s y Hermite a 180 s en LEO casi circular; refinado SGP4
directo de horizonte, máximo y cambios de visibilidad cerca de los pasos;
observadores/Sol compartidos e interpolados. Cota de apogeo/inclinación conservadora
con margen de 2°. Se evita detallar trayectorias cuyo máximo geométrico no alcanza
9,9°: la lista admite solo máximo visible ≥10°. Pueden escapar rasantes breves de
la malla; no se certifican todos los escenarios orbitales posibles.

Ahora start/maximum/end describen el **paso geométrico completo**. La prueba antigua
que exigía maximum.visible se actualizó a exigir un tramo visible; se conservan
orden, máximos, orientación y comparación SGP4 directa. Los límites del intervalo
pueden recortar un paso: se incluyen sus extremos al elegir el máximo. La píldora
de lista usa máximo visible y la ficha muestra el máximo geométrico.

ISS se calcula/publica primero en un worker orbital separado del catálogo; si se
navega durante el cálculo, se reutilizan el trabajo y sus parciales. La caché es
de memoria durante la actividad, independiente del filtro de presentación; tras
recrear la app se conserva el TLE y se recalculan pasos. Renovación a 24 h para
evitar agotar cobertura. La caché TLE de disco sigue disponible offline.

## Revisión visual y límites

**70 PNG** regenerados por Robolectric/API28 a **1024×600**, abiertos y revisados
uno a uno (láminas de cuatro a tamaño original y vistas individuales de cambios).
[Galería](../../docs/screens/pulido.html) y [registro con hashes](screens-review.md).
Se corrigieron filas centradas de datos, iconos duplicados, interruptor azul en
modo rojo, espacio de alarmas, posición de botones de ficha y solape del rótulo
orbital con la escala de altura. La barra conserva etiquetas e intensidades
distintas en rojo. Las listas largas se desplazan; una fila parcial al borde del
viewport no indica pérdida de contenido.

No verificado: tiempos/RSS/FPS de tablet, arranque físico, tacto, sensores, brillo,
sonido, despertar en segundo plano/Doze ni reinicio real de dispositivo. La prueba
de reinicio es recreación de actividad y lectura de preferencias en Robolectric,
no muerte real de proceso Android. Tras reiniciar el dispositivo hay que abrir la
app para reprogramar avisos. No se ha repetido Skyfield ni se han consultado
CelesTrak/Open-Meteo/NOAA/NASA en red; fixtures y transportes simulados para estas
pruebas. El informe favorable de una revisión externa pertenece a 0.6.0 y es evidencia externa.

Sin adb, dispositivos, instalaciones del sistema, push, publicación, extensiones
ni agentes. Red de desarrollo limitada a Gradle/Maven. Copia y proyectos anteriores
conservados; no quedan requisitos de implementación pendientes, salvo las
comprobaciones físicas expresamente no disponibles en esta sesión.
