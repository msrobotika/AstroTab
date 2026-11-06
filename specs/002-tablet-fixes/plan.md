> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Plan de reparación

Spec Kit aplicado manualmente: CLI ausente; plantillas oficiales locales y guía `GitHub-Spec-Kit/docs/guides/bugfix.md` consultadas. Sin instalar extensión. Se conserva assess → fix → test en `bugs/` de esta especificación porque `.specify` es de solo lectura en la sesión. No se altera su constitución vigente. Proyecto sin repositorio Git; respaldo previo: `backups/2026-11-06-tablet-fixes-031446.zip`. La copia `2026-11-06-tablet-fixes-031446-structured.zip` conserva esos mismos bytes previos con rutas app/core completas para facilitar restauración.

1. Diagnosticar los cuatro síntomas contra código y registrar límites de reproducción.
2. Añadir regresiones JVM para formato privado, flags/lifecycle, segmentos, etiquetas y desplazamiento de Ajustes. Ejecutarlas antes del arreglo para comprobar los fallos reproducibles.
3. Centralizar estado privado de ubicación en core. Completar flags inmersivos y reaplicación al recuperar foco. Hacer explícitas medidas ScrollView MATCH_PARENT y contenido WRAP_CONTENT.
4. Proyectar puntos ya rotados en coordenadas de pantalla (también para selección); dibujar texto sin rotar Canvas. Reutilizar arrays de selección y rectángulos de etiquetas. Filtrar segmentos en core y aplicar a rejilla subdividida y constelaciones. Ordenar etiquetas por brillo y probar posiciones alternativas sin colisiones.
5. Ejecutar JVM, lint y APK; revisar coherencia y cada AC, documentar pendientes físicos y tamaño. Actualizar TAREAS.md y evidencia del MVP con enlace a esta reparación.

## Revisión previa

- [x] AC1–AC6 trazados a tareas y evidencia prevista.
- [x] Diagnóstico limitado al informe; Ajustes ya tiene ScrollView y requiere regresión de medidas, no afirmar que faltaba.
- [x] Sin cambios astronómicos, de red, permisos ni dependencias.
- [x] Sin preguntas bloqueantes; definición conservadora de media pantalla documentada.
- [x] Constitución, alcance, respaldo y restricciones compatibles.
