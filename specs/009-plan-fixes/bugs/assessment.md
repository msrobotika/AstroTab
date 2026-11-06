> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Assess — diagnóstico

Confirmados por inspección de 0.7.0:

- SessionPlanner.propose recibe exposición global; solo avisa de falta de
  seguimiento, nunca invoca Optics.npf. Optics.npf ya implementa la fórmula
  utilizada por Herramientas. La UI imprime exposición global en cabecera.
- automatic repite tres TargetRequest de 30 min. propose centra cada bloque
  en tránsito, desplazando inicios y dejando huecos aunque haya ventana útil.
  No existe margen entre objetos. SessionTimeline ya dibuja start/end reales.
- MainActivity comienza con screen=Inicio, pero singleTask reutiliza actividad:
  onNewIntent solo actualiza intent y alarmas. HOME conserva pantalla anterior.

Reproducción sin dispositivos: añadir fixtures JVM de equipo y noche de 11 h,
y Robolectric con MAIN/HOME tras seleccionar otra pestaña. Ejecutar antes del
arreglo y conservar salida; luego repetir los mismos síntomas.

Ámbito: SessionPlanner.kt, ProBScreens.kt, ProBCharts.kt, MainActivity.kt,
app/build.gradle.kts, pruebas nuevas y documentación 009. No tocar motor
astronómico, satélites, alarmas ni perfiles. Riesgo: respetar pausas/recuento
de frames al limitar NPF y al repartir duración; probarlo explícitamente.
