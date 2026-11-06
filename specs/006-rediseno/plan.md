> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Implementation Plan: 006 rediseño

Kotlin 2.1.20/JDK17, Views y Canvas API23, motor core conservado. Sin bibliotecas runtime nuevas. Fuente d3-celestial mw.json exclusivamente; script Node reproduce binario y metadatos hash. Gradle gestiona SDKs instrumentados Robolectric mediante configuración resoluble y tarea Sync, no depende de .m2 particular.

## Constitución y diseño

Revisión previa: offline, sin datos ficticios, permisos INTERNET existentes, cálculos aproximados conservados, copias y atribución, prohibición hardware/push/instalación. Conforme. Tema puro en core/ui/theme para pruebas JVM y widgets Android en app/ui/theme. Inicio consume timeline/Forecast/planes de motor en worker con token de generación; datos preparados fuera de dibujo. Cielo reutiliza arrays y Path para proyección; gestos no ejecutan efemérides. Fotos PNG desde pruebas API28 nativas, jamás representan tablet real.

## Orden

1. Tema/modelos/componentes y resolución Robolectric.
2. Inicio y shell/autor rojo.
3. Banda/cielo/encuadre/tiempo/selección.
4. Resto de pantallas y hojas de información.
5. Pruebas, revisión visual, correcciones y entrega.

Riesgos: altura limitada en Inicio, worker obsoleto, proyección en bordes de banda, dependencias pesadas de SDK de test. Mitigar layout exacto, tokens, subdivisión/recorte geométrico, resolución Gradle reproducible. 20fps solo objetivo de presupuesto, no medición física.
