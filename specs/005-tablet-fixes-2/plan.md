> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Implementation Plan · 005

Kotlin 2.1.20, JDK 17, Android API 23+, View/Canvas; dependencias existentes JUnit/Robolectric. Sin nuevas dependencias de producción.

## Constitution Check

Pasa: copia previa, permisos actuales, funcionamiento offline, TLS seguro, avisos públicos, sin hardware/publicación ni agentes. Revisar de nuevo al entregar.

## Diseño y orden

1. Diagnóstico: OpenSSL s_client con SNI/TLS1.2, cadenas reales como fixtures. Raíces autofirmadas oficiales X2/Amazon1/GTSR4 y huellas. Registrar límites de la inferencia sobre Android del fabricante.
2. Core/data: X509TrustManager compuesto sistema + raíces; SSLSocketFactory por conexión, TLS1.2 explícito y verificador estándar de nombre HttpsURLConnection compatible API23, sin cambiar defaults globales. Inyectar raíces desde Android res/raw. Clasificador de errores y almacenamiento de diagnóstico separado de datos/caché; callback Android Log.w con Throwable saneado de URL/coordenadas.
3. Core/astro: última salida real por búsqueda retrospectiva; null sin eventos. MainActivity presenta resultado de 24 h.
4. App/ui: conservar click estándar en botones; arbitraje vertical/horizontal y ACTION_CANCEL en gráficas. Estado vacío explícito antes de dibujar bandas/series.
5. Nuevas regresiones JVM y Robolectric API23; ejecutar suite pedida; documentar fix/test, revisar trazabilidad de aceptación, avisos y versión/tamaño APK.

Archivos: core/data/{NetworkRepository,SecureTls}.kt; core/astro/Astro.kt; MainActivity.kt; ui/{ProCharts,ProScreens}.kt; pruebas core/app y fixtures; res/raw; build.gradle.kts; avisos; README; specs/005. Ampliaciones solo si diagnóstico exige y se documentan.
