> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Diseño, datos y presupuesto · 006

## Motor y presentación

No se modifican las fórmulas de Astro, AstroPhoto, Forecast, Night, ProAstro, óptica, caché ni política TLS/meteorológica. Forecast.week añade una referencia nocturna opcional, cuyo valor por defecto conserva exactamente la conducta anterior; el instante real sigue controlando frescura y cobertura meteorológica. Inicio la usa después de la salida del Sol para preparar la próxima noche. Home usa Forecast.week y Forecast.best, no extrapola meteorología ni rellena horas. Top Messier: puntuación astronómica de la noche completa, independiente del tiempo meteorológico, puede incluir horas pasadas. El mejor instante de la lista es la muestra de mayor altura dentro de oscuridad y altura mínima; resolución de 10 min.

Paleta/tipografía/espaciado/radios, colores de series/estrellas/planetas y transformación ARGB se definen en core/ui/theme/Design.kt. Android usa app/ui/theme/Widgets.kt. Los iconos son trazos originales MIT. Rojo transforma todos los canales preservando alfa y garantizando R≥G y R≥B; las series conservan variaciones de intensidad. IME propio existente conservado. Las hojas usan brillo rojo e inmersión.

## Vía Láctea y geometría

mw.json de d3-celestial, BSD-3 ya incluida. tools/generate-milkyway.cjs descarga solo si no existe la copia fuente, simplifica por RDP 0,2° en coordenadas AR/dec desenvueltas y conserva los 202 anillos, huecos y cinco niveles. 30.676 puntos originales → 3.237; MW01 big endian: magic, uint16 anillos/puntos; anillo uint8 (nivel*2+hueco), uint16 longitud; pares uint16 AR*65536/360 e int16 dec*100. Recuentos/hashes en milkyway-source.json. No se altera el catálogo estelar: B−V/10 según comentario de la fuente AstroCYD existente; 127/−128 se trata como ausente, blanco.

Proyección cilíndrica heredada de 125°×85°, roll del acelerómetro. La banda se desenvuelve por anillo y usa Path EVEN_ODD por nivel; suelo oculta lo situado bajo el horizonte. El rectángulo usa el campo exacto de Optics.field: 2atan(sensor/(2focal)); representación sobre escala angular de esta proyección, aproximada en campos amplios/alturas extremas. No sustituye una solución astrométrica de placa ni guiado. Búsqueda precesa Messier como sus marcadores; estrellas mantienen J2000; planetas/Luna usan fecha simulada. Rumbo manual siempre.

## Presupuesto de rendimiento (objetivo, no medición de hardware)

50 ms por fotograma para ≥20 fps: proyección/culling ≤20 ms, banda/trazos ≤15 ms, símbolos/etiquetas ≤10 ms y margen ≤5 ms. 5.044 estrellas + 3.237 vértices + 893 puntos de trazos; arrays planos reutilizados, selección y nombres priorizados, Path reutilizado. Alt/az se calculan en worker al cambiar minuto/ubicación/catálogo, no al arrastrar. Tamaño/color estelar y orden por brillo precalculados. No hay List/Selection/Path/Paint/strings nuevos dentro de SkyView.onDraw. Se descartan estrellas bajo horizonte/magnitud/campo y anillos según límites angulares antes de proyectar; Canvas recorta polígonos. Geometría adicional de banda aproximadamente 90 KiB residente y 52 KiB transitoria por actualización, binario 13.562 bytes; sin bitmaps de cielo ni dependencias runtime nuevas.

La medición de FPS, PSS/RSS, energía, temperatura, brillo, sensores y tacto reales permanece excluida por la política del usuario. Las pruebas nativas son Windows/JVM y no permiten afirmar el rendimiento MT8127.

## Robolectric

Configuraciones independientes de Gradle para SDK23,28 y35: una sola configuración provocaba selección de la versión mayor y eliminaba 23 por resolución de conflictos. Sync a app/build/robolectric-sdk y resolver local offline; no Maven interno de Robolectric ni dependencia de user.home. SDK35 necesario para recursos de compileSdk36 con Robolectric4.14.1; SDK28 para gráficos nativos (API23 solo soporta legacy). Wrapper .bat prepara rutas privadas si no se han indicado. Recursos nativos de pruebas expandidos en build/native-runtime para evitar ZipFS/create=true y AccessDenied en Windows restringido. Nada de ello se empaqueta en el APK.
