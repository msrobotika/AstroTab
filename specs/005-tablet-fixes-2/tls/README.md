> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Captura TLS pública · 2026-11-06

OpenSSL existente `openssl`. No instalaciones, dispositivos, adb ni llamadas HTTP a APIs reales. SNI obligatorio. Las cadenas completas originales están en `*-tls12.txt`; las fixtures PEM extraídas en `core/src/test/resources/tls/*-chain.pem`. Las hojas reales son válidas entre octubre/enero (Open-Meteo e IP) y septiembre/marzo (NOAA); las pruebas históricas fijan fecha 06/11/2026 mediante PKIXBuilderParameters. Producción usa siempre el reloj real y verifica vigencia.

| Host | Cadena servida en TLS1.2 | Raíz suplementaria mínima | Cipher negociado en PC |
|---|---|---|---|
| api.open-meteo.com | *.open-meteo.com → YE2 → Root YE → X2 cruzada por X1 | ISRG Root X2 autofirmada | ECDHE-ECDSA-AES256-GCM-SHA384 |
| services.swpc.noaa.gov | services.swpc.noaa.gov → Amazon RSA 2048 M01 → Amazon CA1 cruzada por Starfield | Amazon Root CA 1 autofirmada | ECDHE-RSA-AES128-GCM-SHA256 |
| ipapi.co | ipapi.co → WE1 → GTS R4 cruzada por GlobalSign | GTS Root R4 autofirmada | ECDHE-ECDSA-CHACHA20-POLY1305 |

Los certificados cruzados enviados por el servidor no obligan a confiar en X1/Starfield/GlobalSign: la raíz autofirmada con la misma clave permite terminar antes la ruta. `*-verified.txt` acredita verificación de cadena y nombre usando solo la raíz correspondiente (`-CAfile`, `-no-CApath`, `-no-CAstore`), sin desactivar verificaciones.

`*-aes128.txt` repite la comprobación forzando ECDHE-ECDSA-AES128-GCM-SHA256 (Open-Meteo/IP) y ECDHE-RSA-AES128-GCM-SHA256 (NOAA): los tres aceptan TLS1.2 con AES128, sin requerir el ChaCha20 negociado por defecto en PC para IP.

## Comandos reproducibles

```powershell
'' | & 'openssl' s_client -connect api.open-meteo.com:443 -servername api.open-meteo.com -showcerts -tls1_2
'' | & 'openssl' s_client -connect api.open-meteo.com:443 -servername api.open-meteo.com -tls1_2 -verify_hostname api.open-meteo.com -verify_return_error -CAfile app/src/main/res/raw/isrg_root_x2.pem -no-CApath -no-CAstore
```

Repetir con cada host y su raíz de la tabla. Todos: `Verification: OK`, `Verify return code: 0 (ok)`, TLS1.2. Huellas DER, URLs oficiales y emisores en `THIRD_PARTY_NOTICES.md` (y su copia empaquetada). Tres raíces, no un almacén de CA completo.

## Diagnóstico y límites

El transporte 0.4.0 dependía exclusivamente del almacén del firmware y descartaba todas las excepciones. La conectividad IP de la tablet informada por una revisión externa no acredita confianza TLS. La incompatibilidad del almacén es la explicación probable, especialmente para X2 (2020) y GTS R4 (2016), posteriores a Android6; Amazon CA1 (2015) puede faltar en firmware sin actualizar. Las rutas cruzadas antiguas pueden funcionar en algunos Android6: no se ha inspeccionado el almacén exacto de la Alcatel. No afirmar una excepción concreta de esa tablet por host sin su traza. Sí se demuestra desde PC que los servidores soportan TLS1.2 y que el suplemento valida las cadenas reales ante un almacén que no posee esas raíces. El nuevo diagnóstico permite distinguir certificado, nombre, fecha, conectividad y timeout en la próxima ejecución física.

En pruebas locales HTTPS se utiliza `localhost-test.p12`, una identidad autofirmada sintética con SAN `localhost`, clave y contraseña **solo de test** (`test-only`), generada localmente y nunca empaquetada. La confianza de producción la rechaza. Se confía expresamente en ella solo en el servidor/cliente de loopback de pruebas para demostrar que un nombre correcto funciona y `127.0.0.1` falla con el mismo certificado. Esto comprueba la verificación HTTPS real sin red externa.
