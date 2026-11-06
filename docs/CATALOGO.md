# Catálogo local

Extraído de los bloques stars.h de AstroCYD MS, sin red. Reproducir: `node tools/generate-catalog.cjs <AstroCYDMS.ino>` con una copia local de la fuente.

5044 estrellas, 893 puntos, 150 polilíneas, 89 etiquetas/entradas de constelaciones (Serpiente dividida), 156 nombres. Catálogo J2000 hasta magnitud 6. No incluye precesión ni extinción atmosférica.

Fuente SHA-256: 81942329b2b10b77603efd3fd85104852fa7a94cedb75474b50039c6816d9cfc

Binario SHA-256: 05035a52e52d1a8efe41d8d5d496c0932ce4719fda5ed07294a7e77f8bac86df (47231 bytes).

Formato ATC1 big-endian: seis recuentos uint16; estrella uint16 AR, int16 declinación, int8 magnitud, int8 B−V, uint8 constelación/nombre (255 ausente); puntos uint16/int16; polilíneas uint8/uint16/uint8; etiquetas uint16/int16/uint8; cadenas con longitud uint16 y UTF-8. AR ×360/65536, declinación /100, magnitud /10. BSD-3 íntegra en THIRD_PARTY_NOTICES.md y Acerca de.
