> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Bug Fix: tablet fixes 2

- Slug: 005-tablet-fixes-2 (ruta explícita del usuario).
- Fixed: 2026-11-06.
- Assessment: ./assessment.md.
- Status: applied (verificación física excluida; botones no reproducidos en JVM).

## Summary

Se añade confianza TLS compatible con almacenes antiguos sin retirar validaciones, diagnóstico persistente de fallos, semántica correcta de eventos lunares, arbitraje de scroll en gráficas y estado meteorológico vacío explícito. Versión 0.4.1, versionCode 5.

## Changes

| Archivo | Cambio | Motivo |
|---|---|---|
| core/data/SecureTls.kt | nuevo | TrustManager sistema + raíces, TLS1.2 por socket, conserva nombre HTTPS |
| res/raw/*.pem | tres raíces oficiales | X2, Amazon1 y GTSR4 para cadenas capturadas; huellas en avisos |
| core/data/NetworkFailure.kt | nuevo | categorías en español y Throwable saneado que conserva causas/tipos/stack sin URL/coordenadas |
| core/data/NetworkRepository.kt | modificado | transporte inyectable, causas por servicio/IP guardadas en network_errors, conserva caché y error durante throttle |
| MainActivity.kt | modificado | inyección de raíces/Log.w, causas en Ajustes y Auroras sin caché, diálogo IP, Luna y textos0.4.1 |
| core/astro/Astro.kt | modificado | lastCross y horizonEvents, ventanas24h y null sin evento |
| ui/ProCharts.kt | modificado | estado vacío antes de pintar bandas/paths, scroll vertical libre, cancel/slop para cursor/click |
| ui/ProScreens.kt | modificado | mensaje vacío visible y diagnóstico aun con red desactivada |
| pruebas JVM core/app + fixtures | nuevas | TLS/cadenas, errores/reinicio/log, Luna, gestos, Canvas sin datos |
| avisos/README/specs | actualizados | procedencia, huellas, restricciones y evidencia |

## Tests Added or Updated

`TabletFixesTest` (6): raíces exactas/cadenas reales a fecha de captura con sistema reducido; confianza del sistema conservada; rechazo de autofirmado y firma alterada; HTTPS de loopback válido localhost y rechazo127.0.0.1 con mismo certificado, TLS1.2; causa/reinicio/throttle/IP/recuperación y saneado; Luna con fecha/lugar del informe, referencia externa11,5° y puesta16:11; sin cruces, nunca límite.

`TabletRegressionTest` (4, Robolectric API23): tres arrastres300px/300ms en Clase·+ sin cambios y toque incrementa una vez, CANCEL y navegación; scroll vertical sobre gráfica/horizontal cursor; red apagada/caché ausente/caducada sin paths/bandas con mensaje Canvas; Tiempo/Auroras/Ajustes y Log.w real con Throwable sin coordenadas. `ScreensTest` actualizado: la gráfica vacía ya no consume gestos ni exige toque para avisar de ausencia de datos.

## Local Verification

Primera suite completa exitosa: build-validation.log, 74 core +22 app, lint0errores/4advertencias preexistentes y assembleDebug. Último contraste tras textos0.4.1/clasificador preciso en build-final.log (consultar test.md/verification.md). OpenSSL verificó cadena/nombre con solo cada raíz y TLS1.2; también forzando AES128-GCM, sin depender de ChaCha20.

## Deviations from Assessment

Clasificador separado en NetworkFailure.kt para aislar el saneado de mensajes. No se reemplazan los botones correctos por listeners táctiles: el gesto reportado no los activa en JVM/API23, ya tenían click estándar. La causa física de sus tres activaciones no queda acreditada; se añade regresión y se corrige la captura vertical demostrada en SeriesChart. El verificador de nombre es el estándar de HttpsURLConnection, evitando APIs SSLParameters posteriores a API23. El supuesto límite lunar era un cruce futuro; se corrige su semántica, no se inventa una sustitución que el código no tenía.

## Follow-ups

Comprobar externamente en la tablet el HTTPS y el gesto original al instalar el APK por decisión del usuario. No efectuado ni autorizado en esta sesión. Revisar raíces si rotan las cadenas. Los tests de fixtures usan fecha histórica; producción siempre valida fechas reales.
