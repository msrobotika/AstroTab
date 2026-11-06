> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Plan de implementación

Contexto: Kotlin puro/core, Android View/Canvas API23, JDK17/SDK locales.
Sin nuevas dependencias, permisos o cambios de diseño.

1. Diagnosticar y respaldar antes de editar; revisar constitución y plantillas.
2. Añadir regresiones del síntoma y ejecutar contra 0.7.0 para evidencia roja.
3. Centralizar exposición por objeto en SessionPlanner; repartir tiempo real
   (exposición + pausas), ordenar sin huecos artificiales y aplicar margen.
   Búsqueda acotada de combinaciones por cobertura, calidad lunar/altura,
   diversidad y meridiano; límites explícitos y resultado determinista.
4. Añadir control de margen con preferencias existentes, exposición por ficha,
   razones en ⓘ y etiquetas de inicio/fin reales en barra. Conservar +/−.
5. Tratar intent de entrada en MainActivity sin resetear onResume.
6. Reejecutar regresiones; después aceptación completa desde PowerShell nuevo
   -NoProfile con clean previo, registrar XML, lint y APK. Revisar cada AC.

Constitution check: cumple alcance, offline, respaldo, evidencia honesta y
restricciones. Checklist de calidad revisado antes de implementar.
