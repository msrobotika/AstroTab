> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Especificación: correcciones tras prueba en Alcatel 8063

Fecha: 06/11/2026. Prioridad P1. Fuente: informe de el equipo de cuatro defectos en Android 6; funcionamiento y unos 24 MB comunicados por una revisión externa, no medidos en esta sesión.

## Escenarios y aceptación

- AC1 · Privacidad: con ubicación manual, IP o ausente, la cabecera muestra exclusivamente «ubicación manual», «ubicación aproximada» o «sin ubicación», además de fecha/zona. Las coordenadas del observador solo aparecen en sus campos de Ajustes y su editor. Un origen desconocido se trata como aproximado, nunca se imprime su valor libre.
- AC2 · Pantalla completa: API 23 aplica IMMERSIVE_STICKY, HIDE_NAVIGATION, FULLSCREEN, LAYOUT_STABLE, LAYOUT_HIDE_NAVIGATION y LAYOUT_FULLSCREEN al crear, reanudar y recuperar foco, independientemente de Device Owner. Los diálogos comparten los flags.
- AC3 · Segmentos: constelaciones y rejilla rechazan segmentos con cualquier extremo bajo el horizonte, fuera del campo visible o no finito; rechazan distancia euclídea proyectada superior a la mitad de la dimensión menor del mapa. Se subdivide la rejilla para conservar segmentos cortos; no se unen extremos en la discontinuidad de acimut.
- AC4 · Etiquetas: estrellas, planetas, constelaciones y rejilla se dibujan horizontales incluso con roll. Los nombres de objetos se colocan primero por magnitud ascendente, evitando rectángulos solapados y fuera del mapa. Se mantienen selección táctil, rumbo e inclinación.
- AC5 · Ajustes: ScrollView con contenido de altura natural; a 1024×600 y a menor altura útil, Bortle, red y últimos botones son alcanzables mediante desplazamiento. Todos los botones conservan al menos 56 dp de altura.
- AC6 · Cierre: ejecutar `gradlew.bat testDebugUnitTest lintDebug assembleDebug`, registrar resultados, tamaño del APK y evidencia por criterio sin atribuir comprobaciones JVM a la tablet real.

## Aclaraciones

«Coordenadas» significa ubicación geográfica del observador; altura/acimut y AR/declinación astronómicos siguen siendo parte del mapa y las fichas. «Media pantalla» se concreta conservadoramente como media dimensión menor del mapa. No hay ambigüedades materiales pendientes. Inmersivo permite el gesto temporal del sistema propio de sticky.

## Constitución y alcance

Rige la constitución v1.1.0 existente: Kotlin puro en core, Android API 23, privacidad, offline, copia previa, evidencia honesta. JAVA_HOME y ANDROID_HOME son los solicitados. Sin adb, dispositivos, instalación, push ni consultas a proveedores. Sin cambios de dependencias, permisos ni aprovisionamiento.
