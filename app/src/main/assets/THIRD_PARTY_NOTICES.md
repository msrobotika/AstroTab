# Avisos de terceros de AstroTab

## Pro B · 0.6.0 · satélites y eventos

**SGP4:** David A. Vallado, Paul Crawford, Richard Hujsak y T. S. Kelso, «Revisiting Spacetrack Report #3», AIAA 2006-6753. Referencia pública: https://celestrak.org/publications/AIAA/2006-6753/ ; archivo oficial https://celestrak.org/publications/AIAA/2006-6753/AIAA-2006-6753.zip . Port Kotlin de AstroTab a partir de la traducción Java de Shawn E. Gano (19 junio 2009) incluida en ese archivo, con fórmulas cercanas y de espacio profundo. Se conservan la procedencia en el código y los vectores oficiales; no se incorpora una dependencia externa. Condiciones publicadas por los autores: uso libre para cualquier fin con petición de citar y enlazar la fuente, sin licencia específica asociada: https://celestrak.org/publications/AIAA/2006-6753/faq.php . Estas condiciones se mantienen para el material de referencia; los cambios y adaptación propios de AstroTab son MIT. El script tools/port-sgp4.cjs reproduce la conversión y la precalculación de constantes; la referencia original se obtiene del archivo oficial citado y no se redistribuye como material de trabajo.

**CelesTrak:** TLE de la Estación Espacial, https://celestrak.org/NORAD/elements/gp.php?CATNR=25544&FORMAT=TLE ; grupo visual, https://celestrak.org/NORAD/elements/gp.php?GROUP=visual&FORMAT=TLE . Los datos orbitales pertenecen a sus fuentes; CelesTrak no respalda AstroTab. Consulta opcional por el usuario, caché con fecha, sin TLE de prueba preinstalados. Aviso «datos viejos» desde más de tres días. No se deduce magnitud a partir de TLE.

**NASA/GSFC, Fred Espenak:** catálogos «Five Millennium Catalog of Solar Eclipses, 2001 to 2100» https://eclipse.gsfc.nasa.gov/SEcat5/SE2001-2100.html y «Five Millennium Catalog of Lunar Eclipses, 2001 to 2100» https://eclipse.gsfc.nasa.gov/LEcat5/LE2001-2100.html . Tabla numérica estática de los 45 eclipses de 2026–2035, material de NASA de dominio público. Se extraen fecha, tipo, número de catálogo y ΔT para convertir el máximo TD a UTC, sin imágenes/logotipos ni textos del catálogo. Importador tools/import-eclipses.cjs, tablas originales disponibles en los enlaces citados. La altura local es cálculo propio aproximado: en un eclipse solar que el Sol esté arriba no demuestra que la sombra alcance la ubicación. No se anuncian circunstancias locales exactas ni totalidad local. NASA no respalda AstroTab.

**Suplemento TLS CelesTrak:** USERTrust RSA Certification Authority, The USERTRUST Network / Sectigo. Certificado CA público autofirmado, obtenido de la cadena autenticada de https://celestrak.org/ el 06/11/2026; sin claves privadas. SHA-256 del DER: `E7:93:C9:B0:2F:D8:AA:13:E2:1C:31:22:8A:CC:B0:81:19:64:3B:74:9C:89:89:64:B1:74:6D:46:C3:D4:CB:D2`. Cadena observada: celestrak.org → Sectigo Public Server Authentication CA DV R36 → Sectigo Public Server Authentication Root R46 (cruzada) → USERTrust RSA. Esta cuarta raíz complementa las tres anteriores para sistemas cuyo almacén no la contenga. Se mantienen validación de fechas/cadena/nombre y TLS1.2; no es pinning ni se confía en certificados arbitrarios. La cadena pública usada en las pruebas está en core/src/test/resources/tls/celestrak.org-chain.pem; prueba PKIX positiva y manipulación rechazada en CelestrakTlsTest.

## Certificados públicos HTTPS · 0.4.1

Material público de confianza, descargado el 06/11/2026 de los repositorios oficiales de sus emisores. Se empaquetan exclusivamente estas tres raíces CA autofirmadas en `res/raw`, sin claves privadas. Se añaden al almacén del sistema; se conservan validación de cadena, fechas y nombre HTTPS. No constituyen pinning de hojas ni sustituyen las CA del dispositivo. No se incluyen ISRG Root X1 ni Starfield porque no son necesarias para las cadenas observadas. Deben revisarse si cambian los proveedores o sus cadenas.

| Raíz / proveedor | Procedencia oficial | SHA-256 del certificado DER |
|---|---|---|
| ISRG Root X2 · Internet Security Research Group / Let's Encrypt | https://letsencrypt.org/certs/isrg-root-x2.pem | `69:72:9B:8E:15:A8:6E:FC:17:7A:57:AF:B7:17:1D:FC:64:AD:D2:8C:2F:CA:8C:F1:50:7E:34:45:3C:CB:14:70` |
| Amazon Root CA 1 · Amazon Trust Services | https://www.amazontrust.com/repository/AmazonRootCA1.pem | `8E:CD:E6:88:4F:3D:87:B1:12:5B:A3:1A:C3:FC:B1:3D:70:16:DE:7F:57:CC:90:4F:E1:CB:97:C6:AE:98:19:6E` |
| GTS Root R4 · Google Trust Services | https://pki.goog/repo/certs/gtsr4.pem | `34:9D:FA:40:58:C5:E2:63:12:3B:39:8A:E7:95:57:3C:4E:13:13:C8:3F:E6:8F:93:55:6C:D5:E8:03:1B:3C:7D` |

Correspondencia comprobada en PC mediante TLS1.2/SNI: Open-Meteo → YE2 → Root YE → ISRG Root X2; NOAA SWPC → Amazon RSA 2048 M01 → Amazon Root CA 1; ipapi.co → WE1 → GTS Root R4. Detalle, fixtures públicos de las cadenas y limitaciones en `specs/005-tablet-fixes-2/`. Las marcas y certificados pertenecen a sus emisores; estos no respaldan AstroTab.

## Ampliación 0.4 PRO

Open-Meteo (https://open-meteo.com/): previsión horaria de nubes totales/bajas/medias/altas, probabilidad de precipitación, temperatura, punto de rocío, humedad relativa, viento/rachas a 10 m, visibilidad, presión al nivel del mar y viento a 250 hPa. Datos CC BY 4.0, API gratuita para uso no comercial. Atribución y fecha de consulta en Tiempo; gráficas, normalización de capas, notas y estimación de turbulencia propias. No se distribuye una previsión real predescargada; las muestras de pruebas son sintéticas y no se usan en producción.

International Meteor Organization, Meteor Shower Calendar: https://www.imo.net/resources/calendar/. Referencia para hechos numéricos climatológicos de lluvias principales (fechas/THZ/radiantes redondeados); selección y presentación propias. No se copia un calendario, texto ni efemérides anuales, y no se ha descargado contenido en esta entrega. Fechas y tasas aproximadas, no predicción de actividad local ni revisión de máximos exactos de 2026/2027. IMO no respalda AstroTab.

Gráficas Canvas, cálculos fotográficos, estimación heurística de turbulencia y bitácora: código propio MIT. Sin dependencias ni bibliotecas de gráficas nuevas. Modelos y límites: `specs/004-pro/research.md`.

El catálogo J2000 (5044 estrellas, nombres y trazos) procede de d3-celestial de Olaf Frohn a través de stars.h de AstroCYD MS. Se conserva su licencia íntegra debajo. Los cálculos astro.h de AstroCYD MS y su adaptación Kotlin: MIT, Copyright (c) 2026 MS Robotika. Método aproximado de Paul Schlyter; referencias de validación: Astronomical Algorithms de Jean Meeus.

Open-Meteo: https://open-meteo.com/ · datos bajo CC BY 4.0, https://creativecommons.org/licenses/by/4.0/ · API gratuita para uso no comercial (https://open-meteo.com/en/terms). AstroTab es un proyecto abierto y no comercial. Nubes horarias mostradas con fecha de consulta; se adapta su presentación, sin garantía meteorológica.

NOAA Space Weather Prediction Center: https://www.swpc.noaa.gov/ · Kp observado y previsto de https://services.swpc.noaa.gov/ · datos públicos con atribución; NOAA no respalda AstroTab. Las estimaciones geomagnéticas de la app no son probabilidades ni garantías de visibilidad.

ipapi.co: https://ipapi.co/ · consulta opcional y explícita, envía la IP al proveedor; puede errar decenas de kilómetros. No se distribuye su base de datos. Revisar términos del servicio antes de distribuir cambios comerciales.

Texto completo Apache-2.0 en `app/src/main/assets/licenses/APACHE-2.0.txt`, incluido en el APK y en Acerca de.

Kotlin y kotlin-stdlib 2.1.20: Copyright JetBrains y colaboradores, Apache-2.0 (https://www.apache.org/licenses/LICENSE-2.0). Gradle 8.14.3 y Android Gradle Plugin 8.11.0: herramientas de compilación, Apache-2.0. JUnit 4.13.2 (EPL-1.0) y org.json 20240303 (dominio público) son solo herramientas/dependencias de pruebas; no se empaquetan.

No se incluyen bibliotecas de ESP32, certificados privados, imágenes remotas ni servicios de Google.
## Licencia de d3-celestial

```text
Copyright (c) 2015, Olaf Frohn
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

JetBrains annotations 13.0: Apache-2.0. Pruebas JVM: Robolectric 4.14.1 y dependencias AndroidX Test bajo Apache-2.0, sin inclusión en el APK. AndroidX no es una dependencia de ejecución de AstroTab.
# Catálogo Messier añadido en AstroTab 0.3

Fuente: d3-celestial, Olaf Frohn, archivo `data/messier.json`:
https://raw.githubusercontent.com/ofrohn/d3-celestial/master/data/messier.json

Descargado el 06/11/2026; 110 objetos; SHA-256 original:
`b576a9ef2ff1e3e81af49779c76ceecdf1e2f88b7bd7d21736fa9d590c9cb2b4`.
Licencia BSD de tres cláusulas, texto íntegro conservado arriba en los avisos existentes de d3-celestial.
Conversión reproducible mediante `node tools/generate-messier.cjs <messier.json>` a `catalog/messier.tsv`.
AR normalizada de −180…180 a 0…360; posiciones J2000, dimensiones en minutos de arco.
Los nombres españoles son traducciones editoriales de los alias de la fuente. Los alias originales se conservan para buscar.
La fuente no contiene constelaciones: se muestran «—». No se deducen campos ausentes.

# Recurso de Vía Láctea · AstroTab 0.5.0

`catalog/milkyway.bin` deriva de `data/mw.json` de d3-celestial, de Olaf Frohn, bajo la licencia BSD de tres cláusulas íntegra incluida a continuación. Fuente: https://github.com/ofrohn/d3-celestial/blob/master/data/mw.json. Conversión propia MIT en `tools/generate-milkyway.cjs`: simplificación RDP 0,2°, 202 anillos y cinco niveles. La entrada de trabajo no se distribuye; el importador recibe un archivo local explícito e imprime su SHA-256. No añade ninguna biblioteca runtime.


# Plantillas de desarrollo: GitHub Spec Kit

Las plantillas Markdown de .specify/templates proceden de GitHub Spec Kit, https://github.com/github/spec-kit, bajo MIT. Son recursos de desarrollo; no una dependencia de ejecución.

MIT License

Copyright GitHub, Inc.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
