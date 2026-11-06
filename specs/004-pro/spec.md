> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# AstroTab PRO 0.4.0

06/11/2026. Spec Kit manual: CLI `specify` ausente; plantillas oficiales locales revisadas. Carpeta sin Git. Base respaldada en `backups/2026-11-06-pro-052431.zip`, SHA256 `0DC61E92867ACE41393F68EBE251BB44CB37ADD77235CAD5733D7243E3DBDD4D`.

## Escenarios y aceptación

- AC1/P1: abrir Objetos sin esperar 60–90 s. Una malla Sol/Luna por noche, paso 10 min, cruces refinados, caché noche/lugar/zona/mínimo y progreso por lotes. Plan completo de 110 objetos, incluyendo malla y eventos, <300 ms JVM PC tras calentamiento. Objetivo tablet lista <3 s, cambio de pestaña <1 s y RSS <70 MB: pendiente medición física, nunca inferido del PC.
- AC2/P1: elegir zona de app persistente (sistema por defecto, Madrid/Canarias/Lisboa y todas paginadas); toda hora civil, gráfica, calendario y exportación usa esa zona. UTC interno, DST real.
- AC3/P1: una petición Open-Meteo 7 días con todas las variables solicitadas, opcionales nulas, caché fechada/timeout 8 s/límite 512 KiB. Tiempo: 48 h, capas apiladas, T/Td y rocío <3 °C, viento/rachas, lluvia; cursor, oscuridad y Luna sobre horizonte. Siete tarjetas de noche y mejor disponible; sin datos o cobertura parcial explícitos. Turbulencia por 250 hPa, estimación sin seeing medido.
- AC4/P1: Noche con curvas Luna y Mercurio–Saturno, tabla de salida/tránsito/puesta/altura/magnitud/elongación y ficha con curva. No ampliar a Urano/Neptuno: el motor solo admite 5 planetas. Auroras con 3 días observados y previsión diferenciados y umbral local. Luna con calendario mensual, fase/% y oscuridad sin Luna, siguientes nueva/llena. Ficha Messier marca ahora/ventana útil.
- AC5/P1: lluvias principales, máximos y THZ orientativos con fuente y radiante esta noche/Luna. Fechas climatológicas, no efemérides anuales exactas.
- AC6/P1: hiperfocal/DoF, timelapse, trazos en ambos sentidos, mosaico, SNR/integración/calibración con entradas editables y pruebas JVM. Linterna roja a pantalla completa, brillo regulable, toque sale. Bitácora local con fecha/objeto/equipo/tomas/exposición/notas mediante teclado propio, lista y CSV escapado por ACTION_CREATE_DOCUMENT sin permiso.
- AC7/P1: ocho pestañas, controles ≥56 dp en 1024×600, rojo global, coordenadas solo Ajustes, solo INTERNET, cero dependencias nuevas. Trabajo pesado en worker, Canvas dibuja datos precalculados. README/TAREAS/avisos/Acerca de/versionado actualizados.
- AC8/P1: `gradlew.bat testDebugUnitTest lintDebug assembleDebug` exitoso, pruebas de cada cálculo y JSON ampliado con muestra. Informe de pruebas/lint/APK y límites.

## Decisiones y presupuesto

Malla máxima normal 151 muestras en noche DST de 25 h, frente a 751; ≈16 000 transformaciones de objetos sin volver a calcular Sol/Luna. Cruces de eventos por intervalos ya muestreados + bisección solo donde hay cambio; ascensión/declinación Messier precesadas una vez por objeto. Caché acotada a una noche/plan y un año de meses; no fotos/bitmaps. Progreso cada 22 objetos. Curvas 10 min, ventanas útiles con intersección interpolada de cruces; puntuación por cuadratura aproximada. Presupuesto PC 300 ms con calentamiento, tablet presupuesto estimado 2 s cálculo + 1 s UI, no garantía sin hardware.

Capas nubosas pueden solaparse físicamente: apilado normalizado si suma >100 %, valores originales en cursor. Sin interpolar valores meteorológicos ausentes. Nota 7 noches exige cobertura oscura completa; parcial no compite a mejor noche. Noches sin oscuridad se identifican. Modelos planetarios geométricos aproximados existentes, no guiado. THZ ideal cenital, actividad variable, todas las fechas aproximadas.

## Restricciones

Sin adb/dispositivos, instalaciones, push ni nueva dependencia. Red de esta sesión exclusivamente Gradle/Maven. Fuentes documentadas mediante referencias existentes/conocimiento estático, sin consulta remota ni prueba de APIs reales.
