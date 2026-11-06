> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Robolectric · reparación

app/build.gradle.kts declara SDKs en configuraciones separadas, los sincroniza y pasa robolectric.offline=true y ruta absoluta. No depende del .m2 particular de la sesión anterior. gradlew.bat añade valores locales por defecto para JDK/SDK/caché/tmp si faltan, sin instalar nada. El caso inicial con una única configuración falló porque Gradle retuvo solo SDK35; corregido usando una por SDK. Los primeros logs preservan el fallo y su corrección.

Gráficos nativos: ZipFS intenta abrir el jar de recursos con create=true y falla por AccessDenied incluso con copia de ruta corta. La tarea prepareNativeRuntime expande recursos de pruebas con Gradle y coloca el directorio en classpath. No se alteran permisos de Windows, JDK ni dependencias.
