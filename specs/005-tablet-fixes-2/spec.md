> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Bug Specification: AstroTab 0.4.1 · tablet fixes 2

Fecha: 2026-11-06. Estado: implementada y validada en PC/JVM; reproducción física excluida y pendiente. Identificador/ruta explícitos del usuario: 005-tablet-fixes-2.

Proceso Spec Kit manual: CLI `specify` ausente; plantillas oficiales locales spec/plan/tasks/checklist y comandos oficiales extensions/bug/commands/speckit.bug.{assess,fix,test}.md leídos, sin instalar extensiones ni agentes. La ruta de bugs se adapta a la solicitada por el usuario, `specs/005-tablet-fixes-2/bugs/`. No hay `.specify/extensions.yml` ni hooks.

Base respaldada antes de cambios: `backups/2026-11-06-tablet-fixes-2-060532.zip`, SHA-256 `7C3490DCDC48AF457B81BEE293B52ED020667B61DEEDC894F758BAD9D9421BDA`. Proyecto sin repositorio Git. Se conserva el trabajo existente.

## Escenarios y criterios de aceptación

- A1/P1 Red: sistema más tres raíces públicas mínimas en res/raw; TLS 1.2 habilitado en API 23; nombre verificado por HTTPS, nunca trust-all. Capturar cadenas de api.open-meteo.com, services.swpc.noaa.gov e ipapi.co desde PC. Pruebas JVM de esas cadenas archivadas, rechazo de autofirmado y de nombre erróneo; confianza del sistema conservada. Huellas SHA-256 y procedencia en avisos y diagnóstico.
- A2/P1 Errores: conservar causa por servicio, mostrar texto español claro en Tiempo, Auroras y Ajustes, incluida IP. Log.w con excepción y sin coordenadas/URL de consulta. Persistir diagnóstico entre reinicios; conservar caché.
- A3/P2 Luna: estando arriba, última salida en las 24 h anteriores y próxima puesta en las 24 h siguientes; debajo, próxima salida y puesta. Sin cruce, null y texto «sin salida en 24 h»/«sin puesta en 24 h». Nunca devolver el límite como sustituto. Regresión 06/11/2026 06:01 Europe/Madrid con ubicación pública de Madrid; valores recalculados en la preparación pública. Mantener horizonte geométrico y paralaje del motor.
- A4/P2 Tacto: arrastre vertical 300 px/300 ms sobre Clase · + y otros controles desplazables no cambia valores ni navega; toque normal ejecuta una vez. Cancelación y slop estándar; revisar filas y gráficas, permitir desplazamiento vertical sobre gráfica.
- A5/P2 Gráficas: red desactivada, caché ausente/caducada o ninguna muestra finita ⇒ «sin datos meteorológicos» visible encima, sin series ni bandas que parezcan meteorología. Con datos siguen series/cursor.
- A6 Entrega: versionName 0.4.1, versionCode creciente, `gradlew.bat testDebugUnitTest lintDebug assembleDebug` exitoso; evidencia por criterio, APK y tamaño. Sin adb, dispositivos, instalaciones ni push. Red solo Gradle/Maven y certificados públicos.

## Aclaraciones resueltas

Fecha del informe interpretada en Europe/Madrid (05:01 UTC). La última salida se busca hacia atrás 24 h, nunca 24 h futuras si ya está arriba. Certificados observados prevalecen sobre hipótesis de nombres de raíces. La validación del nombre corresponde a HTTPS/SSLSocket, no a X509TrustManager aislado. Pruebas Android JVM simuladas no acreditan ejecución física. Si el fallo de botones no reproduce con clicks estándar existentes, registrar ese límite y probar el gesto completo sin inventar causa.
