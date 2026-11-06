> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# AstroTab 0.6.0 · Pro B

2026-11-06. Estado: especificada, pendiente de implementación y evidencia. Spec Kit aplicado manualmente: CLI no disponible; plantillas oficiales locales spec/plan/tasks/checklist leídas. Base ZIP en backups/*-pro-b-base.zip, SHA256 2FA2E7C2187B32B2787C0C0BEBC8A0B0E8CB76A86656A874ED0E178A39AD86CD. No existe repositorio Git.

## Escenarios y criterios de aceptación

- R1/P1: cualquier número redondeado a cero se presenta sin signo negativo. Volver a Objetos reutiliza los 110 resultados y curvas sin progreso si noche/lugar/zona/altura no cambian; Inicio usa la misma referencia de noche. Top tres separados ≥2° con preferencia por tipos diferentes. Mini-curvas con muestras suficientes y suavizado precalculado. Cielo diurno cambia de fondo según altura solar, texto «De día: el Sol está a X°», incluido rojo.
- A/P1: TLE ISS y visual de CelesTrak mediante HTTPS y confianza existente, red opcional, caché fechada, ≤512 KiB y ≤200 objetos; aviso si época o descarga supera tres días. SGP4 Kotlin puro, procedencia/licencia documentadas, vectores oficiales con tolerancias explícitas. Pasos visibles cinco días, comienzo/máximo/fin con alt/az/cardinal, sombra y Sol del observador; sin magnitud inventada. Lista/polar y trayectoria próxima/en curso en Cielo. Cálculo worker con parciales. Benchmark ~150 satélites y factor ARMv7 documentado; estimación separada de medición física.
- B/P1: lista mensual de fases, perigeo/apogeo, conjunciones <5°, oposiciones y elongaciones; motor existente y precisión declarada. Tabla NASA solar/lunar 2026–2035 íntegra, máximo convertido TD→UTC con ΔT de tabla; altura local. Meteoros integrados. Próximo destacado en Inicio.
- C/P1: selección desde Objetos y Buscar; varias sesiones persistentes; integración y exposición de Sesión, orden y bloques dentro de ventanas ≥altura mínima y oscuridad, preferencia culminación/Luna, aviso meridiano para ecuatorial. Iniciar temporizador con objeto y guardar resultado al finalizar en Bitácora.
- D/P1: alarmas de fin crepúsculo astronómico, cruce de altura de objeto, ISS −5 min, Kp umbral y salida lunar. AlarmManager setExact API23 y receiver propio sin permisos nuevos, pantalla completa temática y sonido opcional; lista/cancelación en Ajustes. Programación probada sin hardware.
- E/P1: perfiles locales con nombre, focal, f/, sensor, píxel, seguimiento y montura; uno activo compartido por herramientas/mapa/plan. Migrar equipo 0.5 sin pérdida; sensores comunes y tamaños astronómicos.
- V/P1: solo INTERNET, sin dependencias runtime nuevas, botones ≥56 dp, 1024×600, rojo integral, coordenadas solo Ajustes, navegación agrupada Más si hace falta. testDebugUnitTest lintDebug assembleDebug desde shell limpia; PNG Robolectric en docs/screens; README/TAREAS/notices/Acerca de actualizados. Cada criterio con evidencia y límites honestos.

## Aclaraciones resueltas

Oscuridad de satélites: Sol del observador <−6°, satélite sobre horizonte geométrico e iluminado (sombra cónica terrestre). Altura mínima del plan: ajuste de Objetos; oscuridad astronómica <−18°. Sin ventana, informar objeto no programado, nunca forzar un bloque. Montura ecuatorial se elige explícitamente en el perfil; seguimiento es independiente. Alarmas Kp se evalúan con datos vigentes al refrescar mientras la app funciona; no hay sondeo en segundo plano. Persistencia de alarmas no implica ejecución tras reinicio del dispositivo (sin permiso BOOT). En Android moderno sin permiso exacto se admite aviso en primer plano y programación inexacta indicada, sin añadir permiso. Altura solar positiva NO basta para demostrar visibilidad de eclipse solar: se mostrará como condición de horizonte, y geometría local de discos aproximada en el máximo global, sin prometer totalidad local. No se inventan magnitudes de satélites.

## Límites y éxito

No adb, dispositivos, instalación de sistema ni push. Red de desarrollo solo Gradle/Maven e inspección de fuentes públicas citadas, certificados y formatos. Datos de prueba identificados; no distribuir TLE de verificación como actuales. Aceptación requiere verificar cada función y no solo compilar.
