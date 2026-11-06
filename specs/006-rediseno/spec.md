> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Feature Specification: AstroTab 0.5.0 · Rediseño

2026-11-06. Spec Kit manual: CLI ausente, plantillas oficiales spec/plan/tasks/checklist leídas de la referencia local. Base protegida en backups/2026-11-06-rediseno-*.zip, SHA256 EA770A566D59C2FB34B8407FC5BAF1F6985B6DC2F3A81A7317DB018F7788F186. Sin repositorio Git existente.

## Escenarios y aceptación

- A1/P1 Diseño: tema único ui/theme con fondo #05070F, acento #7FB8FF, ámbar #FFB020, estados, tipografía 40/24/16/28, unidad menor, retícula 8 y radio 12. Iconos propios Canvas. Componentes dato, estado, nota, tiempo, fila y sección. Pestañas con iconos/activo. Información extensa en hojas ⓘ y redondeo útil.
- A2/P1 Inicio: primera pestaña y arranque HOME, 1024×600 sin scroll. Reloj local/UTC/sidéreo/fecha, nota y mejor noche de siete, barra puesta–salida con crepúsculos/Luna/nubes/ahora, Luna, Sol, tiempo/rocío/viento, Kp/umbral, planetas nocturnos y tres Messier enlazados. Sin ubicación ni meteorología: estados explícitos, sin datos inventados. Autorrojo opcional civil −6° hasta salida del Sol.
- A3/P1 Cielo: mw.json BSD-3 convertido por tools/ a recurso compacto; suelo/cardinales/rejilla, color B−V si conocido, planetas y fase lunar, constelaciones centradas, iconos Messier. Encuadre angular del equipo conmutable, deslizador ahora–amanecer/reset, selección/búsqueda Messier/planeta/estrella y orientación alt/az más guía de elevación sin brújula. Arrays reutilizados y presupuesto objetivo 50 ms/fotograma, sin afirmar fps físicos.
- A4/P1 Otras pantallas: lista Messier con icono/mini-curva/estado/hora óptima y ficha visual; noches y tiempo con tarjetas/medidores, gráficas/leyendas conservadas; fase/calendario lunar; rejilla de herramientas y pantallas individuales con +/−; ajustes por secciones.
- A5/P1 Validación: reparación Robolectric con dependencias resueltas por Gradle, ejecución normal independiente de shell previa. JVM para banda/cardinales/encuadre/tiempo/búsqueda/formato/rojo; layout Inicio API23 1024×600, capturas nativas si soportadas, diferenciando API y simulación. Regresiones del motor intactas. testDebugUnitTest lintDebug assembleDebug, versión 0.5.0, README/TAREAS/Acerca de y evidencia.

## Aclaraciones y límites

La noche mantiene los cálculos existentes mediodía–mediodía; la barra recorta a puesta/salida si existen. Inicio prepara la próxima noche si ya salió el Sol y aún no es mediodía; Forecast admite esa referencia separada del instante real para conservar frescura de caché y todas sus fórmulas. Amanecer del deslizador: siguiente salida solar; sin cruce polar, intervalo explícito de 24 h. Autorrojo usa reloj real, no simulación del cielo; sobreescritura manual desactiva automatismo. Búsqueda por selector local con teclado propio, sin IME/red. El catálogo conserva sus posiciones y unidades originales. No se cambia ninguna fórmula del motor. PNG nativo requiere API26+ en Robolectric; API23 se valida por layout y pruebas legacy. Sin adb/dispositivos/instalaciones/push; red solo Gradle/Maven y datos d3-celestial.

## Éxito medible

Todos los criterios anteriores requieren evidencia en verification.md. Ningún descendiente visible de Inicio fuera de 1024×600; sin ScrollView en Inicio. Paleta roja con R≥G y R≥B. Datos ausentes nunca producen nota meteorológica. Objetivo ≥20 fps y memoria real pendientes de hardware, excluido expresamente.
