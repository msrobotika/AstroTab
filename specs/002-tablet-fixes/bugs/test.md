> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Bug Test: tablet-display-fixes

Fecha: 06/11/2026. Resultado: **partial para reproducción física; verified para alcance de código/JVM autorizado**.

## Resultado reproducible

```powershell
. .\tools\env.ps1
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain
. .\tools\verify-apk.ps1
```

JAVA_HOME=`<JDK_17>`; ANDROID_HOME=`<ANDROID_SDK>`. Home, cachés y temporales locales mediante el helper existente.

- **BUILD SUCCESSFUL en 41 s**: [registro](../../../docs/TABLET_BUILD_VALIDATION.log). La ejecución anterior recompiló app/core, ejecutó las 42 pruebas core y generó el APK; falló solo el test gráfico por las métricas nulas de Paint legacy. La final reejecutó las 9 pruebas app tras corregir su fixture, y reutilizó core/APK sin cambios de producción entre ambas.
- **51 pruebas JVM: 0 fallos, 0 errores, 0 omitidas** (42 core y 9 Android simulado API 23). [Resumen](../../../docs/TABLET_TEST_SUMMARY.json), [XML](../../../docs/tablet-test-results/).
- **Lint: 0 errores, 4 avisos**, mismos del MVP (versiones Gradle/Robolectric, orientación Android 16, ABI ChromeOS). [Informe](../../../docs/TABLET_LINT_DEBUG.txt). Sin supresión nueva ni baseline.
- **APK: 1 144 485 bytes (1,09 MiB)**. SHA-256 `BB4A7D35E6E9CEF91939D799CEEAE74FF757334BDDEF10400ADEE7E1ADEA8629`. Ruta `app/build/outputs/apk/debug/app-debug.apk`. Firma v1/v2 válida para API 23; único permiso INTERNET; sin bibliotecas nativas. [Inspección](../../../docs/APK_INSPECTION.json), [firma](../../../docs/TABLET_APK_VALIDATION.log). Se conserva el aviso de AGP sobre metadatos no cubiertos por firma v1; v2 válida.

## Contraste de criterios

| Criterio | Evidencia | Límite |
|---|---|---|
| AC1 | `locationStatusNeverFormatsCoordinatesOrFreeText`; `observerCoordinatesAppearOnlyInSettingsFields` recorre siete pestañas con 40,4168/-3,7038. La regresión previa fallaba y ahora pasa. | Sin captura física nueva. |
| AC2 | `immersiveLayoutReturnsOnFocusAndResumeWithoutDeviceOwner`: máscara completa al iniciar, borrar flags, foco falso/verdadero, pausa/resume y diálogo. Antes fallaba y ahora pasa. | Comprueba flags/lifecycle en API 23 simulado, no el compositor de Alcatel. |
| AC3 | 4 pruebas de segmentos (horizonte/bordes, extremos fuera, longitud horizontal/vertical/diagonal, no finitos/salto acimut), más roll en `SkyProjection`. Canvas de prueba comprueba todos los trazos emitidos en cuatro rumbos: dentro de 640×420 y ≤210 px. | No prueba rendimiento físico ni estética de la rejilla. |
| AC4 | `skyDrawsHorizontalNamesShortSegmentsAndSelectsRotatedObjects`: nombres efectivamente dibujados, matriz sin giro para cada texto, magnitudes en orden y selección táctil en cuatro rumbos con roll. `LabelLayout` rechaza colisiones/bordes y conserva prioridad anterior. | Paint de prueba con métricas sintéticas explícitas porque legacy ascent/descent=0. No prueba fuentes ni legibilidad física. |
| AC5 | `settingsScrollsToLastButtonWithoutCompressingControls`: 1024×600 y 1024×480, contenido mayor que viewport, desplazamiento hasta red/último botón, Bortle presente, todos los botones ≥56 dp. `allMvpScreensInflateOnApi23AndSkyDraws` mantiene mínimo en siete pantallas. | Esta regresión de Ajustes también pasaba antes; no se ha reproducido el aplastamiento observado en tablet. |
| AC6 | Comando completo con éxito, resumen/XML, lint, APK/firma y actualización de TAREAS/specs. | No adb, dispositivos, instalación, consultas a proveedores ni git push. |

## Convergencia y revisión final

AC1–AC6 tienen implementación y evidencia dentro del alcance autorizado; B01–B06 completadas. Sin cambios de astronomía, permisos, dependencias ni requisitos de kiosco. Constitución v1.1 y checklist previo conformes. Los logs, XML y hash corresponden al código final. Las 40 pruebas históricas siguen incluidas y pasan.

El informe externo de una revisión externa sobre funcionamiento y unos 24 MB se conserva como información aportada por el equipo, no como medición ejecutada aquí. No cierra la prueba de 10 minutos/PSS/fps ni acredita estas correcciones. Recomendación: revisar este APK en la Alcatel en una sesión autorizada posterior, especialmente barra de navegación, métricas visuales de etiquetas y desplazamiento de Ajustes. No se declara verificación física completa.
