> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Especificación: MVP de AstroTab

Creada: 06/11/2026. Estado: implementada y verificada sin hardware (partes físicas pendientes). Identificador: 001-mvp (sin rama Git; carpeta inicial sin repositorio).

Reparaciones posteriores al informe de la tablet: [002-tablet-fixes](../002-tablet-fixes/spec.md). Sus AC1–AC6 concretan privacidad de cabecera, inmersivo sin Device Owner, filtrado de trazos, etiquetas horizontales y desplazamiento de Ajustes; prevalecen sobre cualquier formulación menos precisa de esta base.

## Escenarios de usuario y pruebas

### US1 · Observar sin conexión (P1)

Con coordenadas manuales válidas y el reloj del sistema, abrir Cielo, arrastrar el rumbo, inclinar o usar controles táctiles, desplazar una hora y tocar un objeto para ver su ficha. Abrir Luna y comparar Bortle. Sin coordenadas, pedirlas sin dibujar un cielo ficticio. Aceptación A1–A3/A5, T01–T07.

Prueba independiente: diez valores Meeus con tolerancias originales, catálogo de 5044 estrellas, conversión de coordenadas, proyección/selección, fases/cruces, hemisferios y orientación. La ejecución visual y los sensores reales quedan pendientes.

### US2 · Preparar la noche y consultar auroras (P1)

Activar voluntariamente red para Open-Meteo (coordenadas) y NOAA (IP). Consultar nota explicada, ventana futura de dos horas, oscuridad y centro galáctico; distinguir Kp observado/estimado/previsto. Sin nubes utilizables no hay nota. IP solo al pulsar y confirmar. Aceptación A4, T08–T10.

Prueba independiente: muestras locales JSON, fechas UTC y ordenación, caché reiniciada, desconexión/TLS/tamaño máximo con transporte inyectado. Desactualizado desde 60 minutos; no aviso actual ni nota con datos de 3 horas o fuera de cobertura. Kp >9 necesario permanece fuera de escala.

### US3 · Usar una tablet dedicada (P1)

Sin Device Owner, app normal. Con propietario, PIN configurado y activación explícita: HOME persistente y lock task. PIN permite mantenimiento, Wi-Fi/reloj y desactivación. Aceptación A6, T11–T12 solo código/pruebas sin hardware.

Prueba independiente: política de entrada/salida, PIN sin valor por defecto, bloqueo de intentos, manifest y APK. Aprovisionamiento/reinicio/recuperación reales pendientes de el equipo.

## Requisitos funcionales

- FR01: pantalla horizontal de referencia 1024×600 a 160 dpi, controles ≥56 dp separados ≥8 dp, paleta del boceto y rojo global con brillo por ventana.
- FR02: ubicación manual validada (coma decimal, signos, límites), Bortle 1–9, origen/fecha persistidos, fecha/zona visibles.
- FR03: módulo core sin Android; Sol, Luna, Mercurio–Saturno, paralaje lunar, fases, horizonte y eventos. Catálogo completo con nombres/trazos y límite mag. 6 declarado.
- FR04: sensor filtrado solo para elevación/roll; rumbo siempre manual. Pausa al salir del cielo o de la actividad.
- FR05: HTTPS validado, sin red por fotograma, límites de tamaño/tiempo y caché fechada por fuente y ubicación. Activación automática como máximo cada 30 minutos mientras esté en primer plano.
- FR06: notas y Kp con factores, procedencia, cobertura y antigüedad; sin probabilidad falsa ni sustitución de datos ausentes.
- FR07: DeviceAdminReceiver, HOME y lock task API 23; PIN con hash y sal, salida y recuperación documentada sin ejecutar comandos.
- FR08: Cámara muestra «Próximamente». MIT y BSD-3 completos en distribución; Open-Meteo CC BY 4.0 no comercial, NOAA y privacidad IP explicados.

## Entidades y casos límite

Ubicación (latitud, longitud, origen, fecha); ajustes locales; efeméride UTC; catálogo cuantizado; serie meteorológica asociada a coordenadas; muestra Kp con tipo y periodo; credencial de mantenimiento.

Rechazar NaN, infinito y campos vacíos. No confundir ausencia de cruce con ausencia de noche: contemplar noche polar y día polar. No elegir ventanas pasadas ni cruzar huecos de nubes. Ignorar caché de otra ubicación. No convertir Kp estimado en observado.

## Criterios medibles de éxito

SC01: gradlew.bat testDebugUnitTest lintDebug assembleDebug finaliza correctamente; diez referencias Meeus respetan tolerancias y 5044 registros se validan en JVM.
SC02: APK <20 MB, minSdk 23 y ninguna biblioteca nativa de otra ABI; permisos solo INTERNET en MVP.
SC03: cada T01–T12 dispone de evidencia o pendiente explícito. Ninguna prueba simulada se etiqueta como real.
SC04: PSS <100 MB a los 10 minutos y 15–30 fps son objetivos pendientes de tablet; no bloquean la entrega de código autorizada.

## Aclaraciones y supuestos

Sin ambigüedades materiales pendientes: sin PIN inicial, creación local antes de activar kiosco; sin red inicial, sin ubicación inicial; reloj/zona del sistema; iconos y gráficos Canvas propios. La ventana se exige completamente futura. No se harán consultas a los servicios durante el desarrollo por la restricción de red de el equipo. La revisión de requisitos es previa a implementar.
