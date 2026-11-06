> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Verificación de AstroTab 0.4.1 · 2026-11-06

Entrega de software y aceptación automatizada completas. Comprobación física no realizada por restricción expresa. Proceso Spec Kit manual con copia previa, constitución1.4.0, spec/plan/tareas/checklist y assess/fix/test. No CLI, extensiones ni agentes instalados. No adb, dispositivos, instalación de sistema ni push.

## Resultado reproducible

```powershell
. ./tools/env.ps1
./gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain
./tools/verify-apk.ps1 -OutputDirectory specs/005-tablet-fixes-2
```

`build-final.log`: **BUILD SUCCESSFUL in 1m 48s**, 57 tareas, salida0. **96 pruebas: 74 core y22 app, 0 fallos/errores; 10 pruebas nuevas**. `test-summary.json` lista suites/casos; XML de las dos suites nuevas preservados aquí. Los tests de Android son Robolectric/API23; las conexiones HTTPS de prueba son loopback local, no servicios reales. `build-validation.log` conserva la primera aceptación completa previa a últimos ajustes.

`lint-debug.txt`: **0 errores, 4 advertencias**, las mismas que004: actualización disponible de Gradle y Robolectric, orientación fija ante Android16, ABI ChromeOS. No se han actualizado dependencias ni suprimido avisos. Gradle también avisa de características obsoletas ante Gradle9. `apk-validation.log` verifica firma v1/v2 para API23; conserva aviso preexistente de apksigner sobre app-metadata.properties bajo META-INF.

## Criterios y evidencia

| Criterio | Resultado / evidencia | Límite |
|---|---|---|
| A1 TLS | OpenSSL TLS1.2, SNI, nombre y cadena verificados por host con solo su raíz; también AES128-GCM. Fixtures reales en core/test; `exactPublicRootsAndRealHostChainsValidateOnlyWithSupplement`, `shippedTrustRejectsSelfSignedAndTamperedRealChain`, `httpsTransportKeepsHostnameVerificationAndTls12`. Sistema preservado, sin defaults globales ni verificador permisivo. | El trust store concreto del firmware no se inspeccionó; causalidad por host probable, no traza real de tablet. |
| A2 Errores | `networkFailuresSurviveThrottleRestartAndAreSafeToLog`: certificado, conectividad, timeout, fecha/nombre/TLS, IP, persistencia, intervalo30min y recuperación. `failedNetworkCauseVisibleOnWeatherAuroraAndSettingsWithoutCache`: textos en las tres pantallas y tres Log.w con Throwable sin coordenadas. | Transporte fallido inyectado en JVM; ningún GET real de meteorología/IP. |
| A3 Luna | El escenario privado y sus cifras del informe físico se retiraron en la preparación pública. Regresión recalculada para Madrid en `moonAboveShowsPreviousRiseAndNextSetInMadrid`; evidencia vigente en `specs/010-public-release/verification.md`. | Prueba de cálculo JVM, sin hardware. |
| A4 Tacto | `vertical300px300msOnBortleDoesNotClickButTapClicksOnce`: tres arrastres, clase sigue5; CANCEL sin click, toque cambia a6 una vez, otro botón no navega al arrastrar. `verticalDragOnChartScrollsAndHorizontalDragSelectsWithoutClick`: scroll vertical, cursor horizontal, cancel sin click. Auditoría: botones/filas MainActivity y ProScreens solo setOnClickListener; únicos onTouch propios son SkyView (paneo intencional) y SeriesChart (corregida). | Las activaciones del botón reportadas por una revisión externa **no se reproducen** en JVM. No atribuirles una causa demostrada ni afirmar su corrección física. |
| A5 Gráficas | `weatherEmptyStatesDrawMessageWithoutSeriesOrBands`: red apagada, sin caché y caché caducada; cuatro gráficos muestran mensaje en Canvas/accesibilidad y texto estático, cero paths y solo rectángulo de fondo, ninguna banda. Prueba poblada existente conserva paths, color/cursor y series. | Captura Canvas/JVM, no render físico de tablet. |
| A6 Entrega | APK estático versionName0.4.1/code5, minSdk23, único permisoINTERNET, firma válida, sin bibliotecas nativas. Contiene exactamente3 `res/raw/*.pem` y avisos sincronizados. No contiene localhost-test.p12 ni cadenas fixture. | Instalación y ejecución excluidas. |

## Diagnóstico de red por host

| Host | Cadena observada y diagnóstico |
|---|---|
| api.open-meteo.com | `*.open-meteo.com → YE2 → Root YE → ISRG Root X2` (servida cruzada por X1). La raíz necesaria actual es **X2**, no X1. Compatible con fallo de confianza en firmware anterior a2020. TLS1.2/ECDHE-ECDSA-AES128-GCM-SHA256 verificado. |
| services.swpc.noaa.gov | `services.swpc.noaa.gov → Amazon RSA 2048 M01 → Amazon Root CA1` (cruzada por Starfield). Añadir Amazon1 permite terminar la cadena sin depender de la ruta Starfield del firmware. TLS1.2/ECDHE-RSA-AES128-GCM-SHA256 verificado. La ausencia concreta de CA en Alcatel no está acreditada. |
| ipapi.co | `ipapi.co → WE1 → GTS Root R4` (cruzada por GlobalSign). La raíz actual es **GTS R4**, no una raíz de Let's Encrypt. Compatible con almacén anterior a2016. TLS1.2/ECDHE-ECDSA-AES128-GCM-SHA256 verificado. |

El fallo original ocultaba excepciones; ping no prueba confianza HTTPS. Los tres servidores soportan el protocolo y un cipher utilizable por clientes TLS1.2. Se corrige la dependencia exclusiva del almacén antiguo y se añade diagnóstico para que cualquier causa distinta quede visible. Capturas, comandos, fuentes y límites en `tls/README.md`; huellas SHA-256 DER en THIRD_PARTY_NOTICES.md y copia empaquetada. Certificados descargados por HTTPS verificado con Node al fallar Schannel/PowerShell en el entorno PC; nunca se usó `--insecure` ni trust-all.

## Artefacto y copia

- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Tamaño: **1.363.867 bytes** (1,364 MB decimales / 1,301 MiB).
- SHA-256: `49FF70074F449A38F178967C24202BB0F694BF879BE539AA7227FD4F879B1A4E`.
- Inspección: APK_INSPECTION.json, APK_BADGING.txt, APK_MANIFEST.txt, apk-validation.log.
- Copia previa: `backups/2026-11-06-tablet-fixes-2-060532.zip`, 845 entradas, incluye fuentes/constitución originales. SHA-256 en spec.md. No repositorio Git existente.

El estado global del informe de bugs es **partial** por reproducción física excluida y botón no reproducido; todos los criterios automatizados pasan. No existe bloqueo para entregar el APK. Queda comprobación externa en la tablet de HTTPS y gesto original, sin haberla ejecutado ni simulado como real.
