> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Verificación — AstroTab 0.7.1

2026-11-06. Resultado: aceptación completa en terminal limpia, JVM y
Robolectric. Sin adb, dispositivos, instalaciones, push ni red ajena a Gradle/Maven.

## Ejecución real

`powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/verify-plan-fixes.ps1`

El script fija JAVA_HOME al JDK 17.0.13+11 y ANDROID_HOME al SDK suministrado,
limpia variables de sesiones anteriores, ejecuta `gradlew.bat clean` y después
`gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain`.
Ambos comandos finalizaron con código 0. Aceptación: **BUILD SUCCESSFUL en
3 min 34 s; 59 tareas ejecutadas**, sin tareas de aceptación UP-TO-DATE.
Log íntegro: [build-clean-terminal.log](build-clean-terminal.log).

**155 pruebas: 115 JVM/core + 40 Robolectric/app; 0 fallos, 0 errores,
0 omitidas.** Doce pruebas nuevas (8 + 4). Resumen de todos los XML:
[test-summary.json](test-summary.json). XML de las nuevas regresiones conservados
en esta carpeta; los restantes, en los informes de build de cada módulo.

Lint: **0 errores, 4 avisos heredados** (Gradle/Robolectric más recientes,
orientación fija en Android16, ABI ChromeOS). Sin nuevos avisos ni supresiones.
[Informe](lint-debug.txt). No se actualizan dependencias por este arreglo.

## Evidencia por criterio

| Criterio | Antes | Después y evidencia |
|---|---|---|
| AC1, exposición | JVM esperaba 4,54 s y recibía 30,0 s | NPF 4,54 s a δ=0°; 9,08 s a δ=60°. Con seguimiento 30 s; entrada de 2 s se conserva. Frames, integración y tiempo con pausas contrastados. Robolectric abre ⓘ de M31 y encuentra el motivo solicitado. |
| AC2, cobertura | 16,38 % en noviembre; regresiones de cobertura/margen fallaban | Cobertura respecto a toda oscuridad astronómica de la tabla inferior. Cada bloque está dentro de una ventana altura/oscuridad; separados por el margen. Casos sintéticos: 11 h, ventanas fragmentadas, más de tres objetos, márgenes 0/10/30 min, polo, ausencia de objetos y cobertura físicamente imposible. Máximo automático ≤4 h. |
| AC2, controles/barra | No había margen configurable | Robolectric cambia margen 10→15, navega y comprueba persistencia; +5/−5 cambia y restaura integración. Native Graphics API28 verifica píxeles en ambos extremos y huecos de bloques con duración distinta. [Barra](timeline.png), [pantalla propuesta](plan-propuesto.png), revisadas visualmente. |
| AC3, entrada | HOME dejaba Herramientas seleccionada | MAIN/HOME y MAIN/LAUNCHER van a Inicio; frío y recreación también. Pausa/parada/reanudación y otro intent conservan pestaña. Robolectric/API23. |
| AC4, entrega | 0.7.0 | 0.7.1/code9, comando completo correcto, APK y lint abajo. |

| Noche Madrid, altura mínima 30° | Sin seguimiento | Con seguimiento |
|---|---:|---:|
| 06/11/2026 | 96,87 % | 96,85 % |
| 20/03/2027 | 96,21 % | 96,10 % |
| 01/07/2027 | 93,15 % | 93,00 % |

Tres objetos en esas seis combinaciones, pausas de 5 s y margen de 10 min.
Luna mantiene el factor orientativo del plan; meridiano participa en calidad
y conserva el aviso de volteo. No se ha cambiado el cálculo de las ventanas.
La cobertura de reloj incluye exposición y pausas, excluye el margen.
Las duraciones manuales guardadas se conservan; «Nueva sesión» permite obtener
la propuesta distribuida. +/− sigue ajustando integración manualmente.

## APK

`app/build/outputs/apk/debug/app-debug.apk`: **1.457.330 bytes (1,39 MiB)**.
SHA256 `701C852085E1F5C8C5D3BEEE0908477F6A711B1432FFAB08AAA4F90D79496FCF`.
AAPT confirma versionName 0.7.1, code9 y minSdk23. Inspección local con
`tools/verify-apk.ps1`: firma v1/v2 válida para API23, solo permiso INTERNET,
sin bibliotecas nativas; recursos/catalogos/licencias presentes.
[Inspección](APK_INSPECTION.json), [salida](apk-validation.log).
Apksigner mantiene el aviso de metadatos Gradle en META-INF sin protección
JAR; no impide la verificación v1/v2.

## Alcance y límites

Spec Kit manual oficial: constitución 1.8.0, spec, plan, tareas, checklist
revisado, diagnóstico, evidencia roja y repetición del síntoma. Copia y hash
en spec.md. [Cambios existentes](changed-existing-source.txt): exclusivamente
plan/UI, entrada, validación de exposición corta y versión/constitución;
dos clases de regresión nuevas. Sin cambios en satélites, astronomía,
alarmas, catálogo, permisos, dependencias ni diseño general.

Las pruebas existentes generan capturas y un benchmark bajo rutas históricas.
Se conserva la evidencia pertinente en 009 y se restauran esos resultados
históricos desde la copia para no atribuirles mediciones de esta versión.
Informe favorable de tablet/Skyfield de 0.7.0: aportado por el equipo/una revisión externa;
no se ha repetido sobre un dispositivo ni se declara comprobado en 0.7.1.
