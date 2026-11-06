> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Diagnóstico 0.5

Informe externo de tablet aportado por el equipo: cinco síntomas de R1; no reproducido en hardware en esta sesión.

1. DisplayFormat.number usa String.format directamente: −0,1 con cero decimales produce «-0». Normalizar el valor después de redondear.
2. Inicio usa SkyControls.nightReference y photoData usa now directamente. Antes de mediodía con Sol arriba, alternan dos claves y sustituyen caché. Además, cancelar al navegar abandona cálculos válidos. Unificar referencia y finalizar trabajo de caché aunque no se publique en pantalla anterior.
3. HomePanel ordena solo por score/take(3), sin separación/tipo.
4. MiniAltitude une muestras con segmentos rectos. Conservar muestras de diez minutos completas en caché y suavizar Path precalculado al dimensionar; comprobar número real de muestras y no convertir a una sinusoide ficticia.
5. SkyView pinta fondo constante aunque ya calcula altura solar.

Verificación prevista: regresiones JVM del formato, clave/diversidad y curvas, Robolectric navegación/caché y mapa diurno/nocturno/rojo. No se acredita la tablet.
