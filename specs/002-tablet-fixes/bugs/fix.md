> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Bug Fix: tablet-display-fixes

Fecha: 06/11/2026. Assessment: [assessment.md](assessment.md). Estado: applied.

## Cambios

- `Settings.kt`: `LocationStatus.label` emite tres estados cerrados sin latitud, longitud ni origen libre; `MainActivity.updateHeader` lo utiliza.
- `MainActivity.kt`: máscara API 23 con IMMERSIVE_STICKY/HIDE_NAVIGATION/FULLSCREEN y tres LAYOUT_*; aplicada también a diálogos. Reaplicación en onResume existente y nuevo onWindowFocusChanged(true), independiente del kiosco.
- Helper ScrollView: viewport MATCH_PARENT y contenido WRAP_CONTENT explícitos, conservando fillViewport y botones de 56 dp. Ajustes ya usaba desplazamiento antes del arreglo.
- `SkyGeometry.kt`: filtro común de horizonte/campo/valores no finitos/longitud euclídea (mitad de la dimensión menor); proyección con roll sobre posiciones y colocación reutilizable de rectángulos de etiquetas.
- `SkyView.kt`: rejilla dividida en segmentos de 5° y filtro también en constelaciones. Canvas sin rotación; objetos y selección comparten posiciones rotadas. Nombres ordenados por magnitud y cuatro posiciones candidatas; se omiten si colisionan o desbordan. Constelaciones/rejilla se etiquetan después de objetos brillantes.
- Pruebas: 7 regresiones core y 4 Android simuladas adicionales (privacidad en todas las pestañas, foco/resume/diálogo, desplazamiento a 600/480 px, trazos/nombres/selección con roll).

## Diagnóstico durante la reparación

La reproducción previa confirmó 2 fallos en 3 pruebas UI: cabecera y flags. Ajustes ya pasaba la prueba de desplazamiento y altura en JVM antes del cambio; no se afirma que el problema de la captura física esté reproducido. Registro: `docs/TABLET_REGRESSION_BEFORE.log`.

El primer intento de ejecución requirió fijar GRADLE_USER_HOME y JAVA_TOOL_OPTIONS (home/temporales); se usa el `tools/env.ps1` existente. No hubo instalación.

La comprobación del mapa detectó ascent/descent=0 en Paint legacy de Robolectric API 23: ningún rectángulo de texto tenía altura en esa simulación. Se suministra un Paint de prueba con métricas sintéticas explícitas, solo mediante reflexión en el test. No se cambia el cálculo de texto de producción para acomodarlo al simulador. Esa prueba acredita orden, matriz horizontal y selección con métricas sintéticas; no acredita tipografía ni legibilidad física.

## Desviaciones y alcance

Ninguna ampliación de producto. Se documenta expresamente la reproducción inconclusa de Ajustes y la limitación del Paint simulado. La revisión física posterior y nuevas medidas de memoria/fps quedan pendientes. Sin cambios de dependencias, permisos, red ni Device Owner.
