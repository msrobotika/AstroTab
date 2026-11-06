> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Correcciones del plan y entrada — AstroTab 0.7.1

Fecha: 2026-11-06. Base: 0.7.0, sin repositorio Git. Spec Kit manual:
CLI `specify` ausente; plantillas oficiales locales spec/plan/tasks/checklist y
guía `GitHub-Spec-Kit/docs/guides/bugfix.md` (assess → fix → test), sin instalar extensiones.
Copia previa: `backups/2026-11-06-145601-plan-fixes-base.zip`, SHA256
`CBE3892F81BCA4F81C0BEFE97CEBC81F63D7A4BEAEE06E33E34E3433EF2D752D`.

## Escenarios y criterios de aceptación

1. P1 / AC1: sin seguimiento, cada bloque usa min(exposición de Sesión, NPF
   del equipo para la declinación del objeto). Con seguimiento conserva Sesión.
   Duración, tomas e integración usan esa exposición. Se muestra en la ficha
   y ⓘ explica «limitada por NPF: sin seguimiento» cuando se aplica el límite.
   JVM: 50 mm, f/2,8, 4,3 µm, ecuador ~4,54 s, distintas declinaciones,
   exposición menor que NPF y seguimiento. NPF no definida cerca del polo:
   conserva el valor del usuario con explicación, sin inventar un límite.
2. P1 / AC2: propuesta automática aprovecha ≥70 % de oscuridad útil cuando
   ventanas, número de objetos y límites lo permiten. Sin solapes, bloques
   completamente contenidos en ventanas astronómicas con altura ≥ mínima.
   Mantiene factores lunares, proximidad al meridiano y aviso de volteo.
   Margen configurable, inicialmente 10 min (0–60); máximo automático de
   240 min de tiempo real por objeto. Busca tres objetos variados y añade
   otros si son necesarios para cubrir huecos. +/− conserva ajuste manual
   de integración; el límite de 4 h es de propuesta automática.
   JVM: noche de ~11 h, varias estaciones, ventanas fragmentadas/cortas,
   márgenes y casos sin oscuridad/objetos. No promete cobertura imposible.
   La barra y accesibilidad expresan los intervalos reales, incluidas pausas.
3. P1 / AC3: creación de actividad y entradas MAIN/HOME o MAIN/LAUNCHER
   muestran Inicio. Volver desde otra app sin nuevo intent conserva pestaña.
   Robolectric API23: frío, recreación, HOME, launcher y pausa/reanudación.
4. AC4: versionName 0.7.1; terminal nueva sin perfil ejecuta con éxito
   `gradlew.bat testDebugUnitTest lintDebug assembleDebug`; informar pruebas,
   lint y bytes del APK. Sin adb, dispositivos, instalaciones, push ni red
   salvo Gradle/Maven; conservar el resto de 0.7.0.

## Decisiones y límites

Oscuridad útil = unión de ventanas de los objetos disponibles; también se
contrasta cobertura respecto a toda oscuridad astronómica en noches con oferta.
Luna es un factor orientativo existente, no una nueva prohibición de observar.
Meridiano prioriza calidad sin dejar vacía la noche; sigue avisando del volteo,
sin inventar restricciones de la montura. No se modifica astronomía ni satélites.
Los límites de 4 h y 10 min son decisiones de implementación razonables del
ejemplo solicitado; no requieren formularios al usuario. Sin ambigüedad bloqueante.
Informe de tablet/Skyfield de 0.7.0: evidencia externa aportada por el equipo;
esta sesión solo acredita JVM/Robolectric/compilación, nunca uso físico de 0.7.1.
