> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Robolectric · verificación del arreglo

Estado: PASS en Windows/JVM. Verificación física no aplicable al fallo de dependencias; hardware excluido de esta sesión.

El síntoma original está documentado en TAREAS: faltaba el SDK23 en el home Maven particular. tests-first.log reproduce ausencia de SDK23 al resolver tres versiones en una configuración (solo quedaba SDK35). La reparación definitiva usa configuraciones independientes y tarea Sync con ruta absoluta; se verifican jars23/28/35 en app/build/robolectric-sdk. Las ejecuciones completas desde terminal nueva, sin dot-source de tools/env.ps1 ni JAVA_TOOL_OPTIONS/GRADLE_OPTS heredados, pasan los tests API23 y nativos API28. build-clean-terminal.log, build-clean-final.log y build-delivery.log conservan ejecuciones exitosas; build-acceptance.log corresponde al cierre: 110 pruebas, 0 fallos/omitidas y lint 0 errores/4 avisos heredados.

No hay SDK remoto resuelto por Robolectric durante Test: robolectric.offline=true, dependencia local preparada por Gradle. No se depende de .gradle/robolectric-home/.m2. Red de desarrollo solo para Gradle/Maven y datos d3-celestial. Windows requiere expandir recursos nativos de pruebas para evitar apertura ZipFS/create=true del jar; tarea automática prepareNativeRuntime. No se cambia la seguridad del sistema ni se instala software.

Regresiones verificadas: 26 tests app (18 existentes, 4 correcciones tablet y 4 rediseño), con fuentes nativas para capturas/layout y backend legacy API23 para la plataforma objetivo. 84 tests core, incluidas 9 pruebas nuevas de diseño/geometría. Las pruebas Android son simuladas: ninguna mide comportamiento de una tablet física. Los logs de intentos intermedios conservan fallos corregidos; nunca se presentan como aceptaciones.
