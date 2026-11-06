> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Fix — cambios aplicados

- SessionPlanner reutiliza Optics.npf por declinación; exposición, tomas,
  integración y duración coherentes. Aviso NPF exacto y polo explícito.
- Sustituye tres solicitudes fijas de 30 min por reparto de tiempo real,
  búsqueda acotada de cobertura con calidad/diversidad/meridiano y máximo
  4 h. Ventanas unidas para calcular disponibilidad sin duplicarla.
  propose comienza al estar disponible, sin esperar artificialmente al
  tránsito; ventanas que cierran antes tienen prioridad. Margen 0–60 min.
- Plan muestra exposición por objeto e información; margen persistente
  con +/− y porcentaje de oscuridad ocupado. Barra escala oscuridad
  astronómica y dibuja start/end reales; accesibilidad incluye ambos.
- onNewIntent trata MAIN/HOME y MAIN/LAUNCHER. onResume no resetea pestaña.
- Versión 0.7.1, versionCode 9; sin nuevas dependencias ni permisos.

## Desviación justificada del diagnóstico inicial

Al revisar el recorrido «Iniciar Sesión», PhotoSession exigía exposición
≥0,1 s. NPF puede dar 0,0227 s con la focal máxima de 10.000 mm permitida
en Equipos; el nuevo bloque habría fallado al abrir el temporizador.
Se amplía solo el límite inferior de luces/darks a 0,001 s (resolución del
reloj); controles manuales y flats conservan sus límites. Regresión JVM
específica. No cambia el comportamiento del equipo de 50 mm informado.

Evidencia roja previa: tests-before-core.xml (4 fallos de 5) y
tests-before-app.xml (HOME conserva Herramientas, 1 fallo de 2).
Verificación final en bugs/test.md y verification.md; no se acredita hardware.
