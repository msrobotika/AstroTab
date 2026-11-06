> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Bug Assessment: tablet fixes 2

Slug: 005-tablet-fixes-2 (explícito). Fecha: 2026-11-06. Verdict: valid, con causas físicas inferidas donde se indica.

## Síntomas / reproducción

Informe externo una revisión externa en Alcatel 8063 API23: red falla con conectividad IP; Luna muestra salida futura estando arriba; gesto vertical acciona Bortle; bandas sin datos. No repetir físicamente en esta sesión.

## Diagnóstico

- HttpsTransport usa solo trust store del sistema. Captura OpenSSL PC con SNI/TLS1.2: Open-Meteo *.open-meteo.com → YE2 → Root YE → ISRG Root X2; NOAA → Amazon RSA 2048 M01 → Amazon Root CA1; ipapi.co → WE1 → GTS Root R4. Todos negocian TLS1.2. Raíces recientes no garantizadas en firmware API23. No es falta de soporte TLS1.2 de los servidores; incompatibilidad del trust store es probable, no demostrable por host sin excepción de tablet. Red pierde toda excepción en catch(_: Exception).
- Luna llama solo nextCross futuro; estando arriba ofrece la salida de la siguiente lunación diaria. nextCross devuelve null si no hay cruce; no hay sustitución explícita por límite en esta versión. Corregir semántica de última salida y añadir prueba de ausencia de cruce.
- Botones MainActivity/ProScreens usan setOnClickListener estándar, sin ACTION_DOWN propio. No atribuir aún activaciones al código de botones; verificar gesto a través de ScrollView. SeriesChart sí bloquea interceptación desde DOWN y no diferencia scroll vertical.
- SeriesChart pinta oscuridad/Luna aunque todas las series sean NaN: estado vacío no tiene mensaje estático.

## Proposed Remediation / contrato

Aplicar plan.md T003–T008: raíces mínimas de proveedores oficiales más sistema; TLS1.2 y nombre HTTPS; pruebas cadenas reales/autofirmado/nombre erróneo. Diagnóstico por servicio persistente, español y Log.w saneado. Última salida pasada y futura puesta con ventanas24h. Mantener click estándar; corregir captura vertical de gráficas si se confirma. Estado vacío no dibuja bandas ni series.

## Files likely to change / Tests / Risks

Rutas en plan.md. Pruebas JVM trust compuesto y cadenas reales congeladas a fecha de captura, error/caché, eventos; Robolectric gesto300px/300ms, click/cancel, Canvas vacío y poblado. Riesgos: rotación futura de CA requiere mantenimiento; certificados fixtures caducan y deben verificarse a fecha capturada; no inferir trust store exacto de Alcatel desde PC; tacto físico pendiente.
