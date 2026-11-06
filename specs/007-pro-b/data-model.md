> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Modelos y contratos

core/satellites: Tle (identidad, época, dos líneas verificadas), Sgp4.State (TEME km/km/s), SatellitePoint (tiempo, altura/acimut, iluminación/oscuridad), SatellitePass (inicio/máximo/fin/trayectoria). SatelliteRepository separa transporte y caché; solo refresh(enabled=true) inicia red.

core/astro: SkyEvent y Eclipse para calendario; TargetRequest, ObservingSession, SessionBlock y ObservingPlan para sesiones. SessionPlanner es puro y recibe ventanas ya calculadas, exposición, pausa y equipo. Devuelve bloques sin solapamiento y IDs no programados.

core/data: EquipmentProfile y Settings resuelven equipo activo con migración. SessionStore guarda listas de sesiones en CacheStore acotado. AlarmKind/AppAlarm/AlarmPolicy/AlarmStore separan decisión de programación del adaptador AndroidAlarms. Receiver usa exclusivamente ID persistente de PendingIntent explícito.

Errores de red nunca reemplazan TLE válidos guardados. No hay magnitudes satelitales sintéticas. Sin ubicación, no se calculan pasos ni ventanas ni altura local de eclipse. Datos de prueba solo bajo src/test y specs/.
