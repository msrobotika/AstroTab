# Regenerar datos sin rutas personales

Los recursos necesarios ya están incluidos en `core/src/main/resources/` y las raíces públicas HTTPS en `app/src/main/res/raw/`. No necesitas estos importadores para compilar. Requieren Node.js, no paquetes npm; se ejecutan desde la raíz y leen archivos locales que tú suministras. Ninguno descarga datos automáticamente.

| Script | Entrada local y procedencia |
| --- | --- |
| `node tools/generate-catalog.cjs <AstroCYDMS.ino>` | Fuente AstroCYD MS con bloques STARS/CPOINTS/CPOLYS/CLABELS y nombres, derivados de d3-celestial. Produce `catalog/stars.bin` y `docs/CATALOGO.md`. |
| `node tools/generate-messier.cjs <messier.json>` | GeoJSON `data/messier.json` de d3-celestial; produce `catalog/messier.tsv`. |
| `node tools/generate-milkyway.cjs <mw.json>` | GeoJSON `data/mw.json` de d3-celestial; produce `catalog/milkyway.bin` e imprime procedencia y hashes. |
| `node tools/import-eclipses.cjs <directorio>` | `solar.html` y `lunar.html` de los catálogos NASA/GSFC 2001–2100 citados en THIRD_PARTY_NOTICES; produce `events/eclipses.tsv` para 2026–2035. |
| `node tools/port-sgp4.cjs <directorio-JAVA-SGP4-v2>` | Fuentes Java Vallado/Gano 2009 del archivo oficial AIAA 2006-6753; regenera el port Kotlin. Ejecuta después los vectores oficiales de SatelliteTest y revisa el diff. |

Las fuentes externas de trabajo no se distribuyen en `specs/`. Sus enlaces, atribuciones y huellas disponibles están en [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) y [CATALOGO.md](../docs/CATALOGO.md). Suministra entradas temporales fuera del árbol versionado y revisa todo cambio generado.

Los PEM empaquetados son certificados de autoridades públicas, no claves de firma. El único PKCS12 de pruebas contiene una identidad sintética `localhost` con contraseña pública `test-only`: solo sirve para comprobar TLS en loopback, nunca para producción. No se distribuyen claves de publicación ni debug.
