> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Robolectric · diagnóstico

Síntoma reportado: SDK23 jar no presente en .gradle/robolectric-home fuera de sesión previa. app/build.gradle.kts fuerza user.home y deja la descarga del SDK al resolver interno Maven de Robolectric, separado de la caché/gestión de Gradle. La resolución depende del contexto/home y no está declarada como entrada de Gradle.

Reparación: declarar SDKs instrumentados en una configuración Gradle, sincronizar jars a build/robolectric-sdk y activar resolver offline con ruta absoluta. Gradle descarga únicamente desde Maven y prepara automáticamente antes de Test. Verificar con terminal nueva y pruebas forzadas; no confundir dependencias de Maven en caché con ejecución de tests.
