> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Especificación: astrofotografía offline

Fecha: 06/11/2026. Entrega 003, sin rama (carpeta sin Git). Spec Kit manual con las plantillas oficiales locales; CLI no disponible. Base: backups/2026-11-06-astrofoto-045110.zip, SHA-256 749CFCC854AB78821C79A10130FAA797B7D03E1F3AAC1A68E65FFC4D2CCC370A.

## Escenarios y criterios de aceptación

- AC1 / P1: consultar 110 Messier offline, buscar número/nombre con teclado propio, filtrar galaxias/nebulosas/cúmulos y ordenar por utilidad nocturna. Fuente d3-celestial; ausencias «—»; M31, M42 y M13 a menos de 0,1° de referencias J2000.
- AC2 / P1: ficha con metadatos, altura/acimut, tránsito superior, horas útiles bajo Sol <−18°, Luna y curva nocturna; mínimo ajustable de 30°. Marcas seleccionables en Cielo. Probar circumpolares, invisibles y cambios de hora.
- AC3 / P1: Herramientas sustituye Cámara. Equipo persistente: focal, apertura, sensor y píxel. Calcular 500/NPF con declinación, encuadre/escala/ocupación y reloj polar norte/sur documentado y contrastado.
- AC4 / P1: núcleo galáctico >10° en oscuridad, máximo y acimut, meses comparados por latitud; horario solar/lunar y ventanas sin Luna con barra temporal.
- AC5 / P1: sesión manual de luces, pausas, darks y flats; duración/fin y cuenta atrás roja monotónica, sonido opcional y pantalla encendida. Sin permiso ni captura de cámara.
- AC6 / P1: Open-Meteo añade temperatura, humedad, rocío y viento con caché fechada. Rocío alto si T−Td <3 °C. Factores explicados en nota. Red desactivada: meteorología «sin datos».
- AC7 / P1: español de España, identificadores ingleses, modo rojo, controles ≥56 dp, desplazamiento a 1024×600, coordenadas solo en Ajustes. Curvas precalculadas y reutilizadas durante un minuto; sin cálculos astronómicos en onDraw. Objetivo PSS <60 MB, pendiente de hardware.
- AC8 / P1: testDebugUnitTest lintDebug assembleDebug exitosos; pruebas JVM de cada cálculo, licencia empaquetada, README/TAREAS/Acerca de actualizados; informar pruebas, lint, APK y límites reales.

## Decisiones y casos límite

Noche: mediodía local a mediodía siguiente, anclada a la fecha anterior antes del mediodía. Duración real UTC (23/25 h en cambios de hora). Muestreo de curvas cada 2 minutos y eventos por bisección; no prometer precisión de observatorio. Culminación superior geométrica siguiente al inicio, incluso diurna. Sin oscuridad/visibilidad, intervalos vacíos y puntuación cero. Equipo inicial orientativo explícito, editable; tamaños de sensor convencionales, móvil configurable. NPF simplificada (35N+30p)/(f cos δ), válida con |δ|<89°; cerca del polo se indica límite del modelo. Reloj polar visto directamente hacia el polo, cenit arriba y acimut creciente a la derecha; visor inversor requiere adaptar orientación. Sin seeing ni transparencia inventados.

## Entidades

MessierObject (J2000 y campos opcionales), NightTimeline (muestras/eventos/intervalos UTC), ObjectPlan (factores y score), Equipment, SessionPlan/SessionTimer, Cloud con campos meteorológicos opcionales.
