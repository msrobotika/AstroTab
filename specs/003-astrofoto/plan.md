> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Plan de implementación 003

## Contexto y constitución

Kotlin 2.1.20/JDK 17, core JVM puro y app View/Canvas API 23. Sin dependencias ni permisos nuevos. Constitución revisada: se autoriza exclusivamente la descarga Messier indicada además de Gradle/Maven. Sin adb, hardware, instalaciones ni push. Copia completa de fuentes previa a cambios; plantillas oficiales revisadas manualmente.

## Diseño y secuencia

1. Descargar la fuente autorizada y convertir con tools/generate-messier.cjs a TSV compacto reproducible; conservar original/hash/licencia. Cargar 110 objetos en core.
2. Modelar noche UTC con límites de calendario local y precalcular Sol/Luna; reutilizar muestras para todos los objetos. Precesión IAU 1976 desde J2000 para Messier, núcleo y referencia polar; posiciones medias sin nutación ni movimiento propio.
3. Implementar planificador, óptica, polar, Vía Láctea, eventos y sesión monotónica en core; probar referencias, extremos y errores.
4. Ampliar el contrato meteorológico existente sin romper cachés antiguas. Nota conserva nubes obligatorias y añade factores conocidos de rocío/viento; factores ausentes se explican.
5. Integrar Objetos/Herramientas, Canvas con datos precalculados, teclado propio, ajustes persistentes y marcas. Cachear por minuto/ubicación/fecha/mínimo; trabajo pesado en worker. Timer independiente del reloj civil y desplazamiento del cielo.
6. Ejecutar pruebas, lint y APK, revisar criterios, actualizar documentos y registrar pendientes físicos sin marcarlos como verificados.

## Revisión previa de coherencia

AC1–AC2 → F01–F03; AC3–AC5 → F04–F06; AC6 → F07; AC7–AC8 → F08–F09. Todos tienen prueba o inspección asignada. No hay ambigüedades materiales que requieran detener el trabajo. Los valores de equipo son ejemplos editables, la puntuación una heurística explicada. PSS, sonido real y legibilidad física necesitan tablet y quedan pendientes. Riesgo: datos incompletos, DST, eventos polares y callbacks Android; tratar con null/listas vacías y tokens de pantalla.
