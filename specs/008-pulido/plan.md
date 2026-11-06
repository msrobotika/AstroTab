> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Implementation Plan: Pulido
**Date**: 2026-11-06 · **Spec**: [spec.md](spec.md)

## Summary / Technical Context
Kotlin puro core + Android View/Canvas API23, JDK17, sin dependencias nuevas.
Conservar SGP4; detección con malla gruesa y cota orbital conservadora;
refinar horizonte, máximo y visibilidad solo alrededor de pasos nocturnos.
ISS prioritaria, worker orbital dedicado para no esperar al catálogo.
Caché compartida de predicción, con progreso coalescido y claves TLE/lugar/tiempo.
Alarmas: añadir enabled con migración true. Plan: selección por calidad y variedad
con comprobación del resultado sin solapes. UI reutiliza Widgets/Palette.

## Constitution Check
PASS: copia previa creada, sin git disponible; red desarrollo solo Gradle/Maven;
sin adb, dispositivos, instalación, push, agentes ni extensiones. UI española,
rojo integral, cálculo offline, evidencia física explícitamente pendiente.
Revisión posterior de diseño: PASS, almacenamiento local existente y sin permisos nuevos.

## Project Structure
core/src/main: satélites, almacén alarmas, planificador y eventos.
app/src/main: MainActivity, ProBScreens, ProBCharts, HomePanel, Widgets.
core/src/test y app/src/test: regresión JVM, recreación y capturas Robolectric.
docs/screens: PNG; specs/008-pulido: proceso y evidencia.

## Diagnosis (bugs)
0.6 acumula trabajo orbital/catálogo en worker único, progreso cada 10 objetos;
pasos cortados por visibilidad; iconos TOOLS/SETTINGS idénticos; filas Button
centradas; plan vacío sin acción automática; alarmas sin estado editable.
Verificar síntomas mediante pruebas específicas antes de cerrar cada tarea.
