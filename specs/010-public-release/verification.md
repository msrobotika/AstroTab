# Verificación de la copia pública 0.7.1

Fecha: 2026-11-06. Proceso GitHub Spec Kit manual con plantillas oficiales locales; CLI no disponible. Origen tratado como solo lectura y base selectiva respaldada fuera del repositorio antes de editar.

## Matriz de aceptación

| Criterio | Evidencia | Estado |
| --- | --- | --- |
| A1 Compilar y probar | Ejecución final desde la raíz del destino: `gradlew.bat clean testDebugUnitTest lintDebug assembleDebug --no-daemon`, BUILD SUCCESSFUL en 6 min 44 s; 61 tareas ejecutadas. XML: 155 pruebas (115 core, 40 Android simulado), cero fallos, errores u omisiones. Lint: cero errores, cuatro avisos heredados. | Correcto |
| A2 Privacidad | Auditoría con `git grep -n -i -P` sobre archivos versionados del árbol de trabajo: cero coincidencias de identidad privada, rutas locales absolutas, redes domésticas, identificadores, coordenadas privadas, direcciones de red privadas o contactos. Números científicos y receptores Kotlin se distinguen por su sintaxis. | Correcto; auditados también todos los archivos candidatos con git grep --no-index |
| A3 Selección y origen | 588 archivos del origen inventariados con SHA-256, fuera de cachés/builds/copias: cero cambios al comparar. Copia por lista permitida; cero artefactos excluidos en el índice. | Correcto; comparación final: cero cambios |
| A4 Documentación | README español y resumen inglés, siete rutas y textos alternativos de imágenes pendientes; CONTRIBUTING, plantilla de bug, ROADMAP, guía de kiosco y documentación de importadores. | Correcto |
| A5 Git local | main inicializada, identidad pública configurada exclusivamente en este repo, sin remotos. El entorno deniega crear el bloqueo del índice por permisos de solo lectura en `.git`. | Bloqueado: todavía no hay commit |

## Regresión lunar pública

Se retiran las coordenadas privadas y el oráculo basado en el informe físico. El test `moonAboveShowsPreviousRiseAndNextSetInMadrid` usa Madrid y la fecha UTC `2026-11-06T05:01:00Z` (06:01 CET).

Recalculado localmente con las ecuaciones Schlyter del motor: altura **10,880471782230163°**. Un evaluador JavaScript separado usó una malla de un minuto y bisección a un milisegundo para los cruces del horizonte geométrico con paralaje:

| Evento | Cálculo de referencia UTC | Hora CET |
| --- | --- | --- |
| Última salida | 2026-11-06 03:59:30.387 | 04:59:30 |
| Próxima puesta | 2026-11-06 15:19:58.179 | 16:19:58 |

La primera ejecución real del test Kotlin da altura **10,880471782230167°** y los mismos segundos para salida y puesta. Las aserciones usan tolerancia de un segundo para los eventos y comprueban el signo de la altura antes/después y la ausencia de cruces ficticios. Es una regresión del modelo con búsqueda de cruces separada; no una validación independiente de su precisión astronómica ni una prueba física.

## Portabilidad y artefactos

El wrapper Windows usa las variables estándar y no contiene rutas personales. Los cinco importadores conservados reciben entradas locales explícitas, no descargan datos implícitamente y pasan `node --check`. Las capturas sintéticas se generan en `app/build/screens/`; benchmarks y temporales quedan bajo build o la caché local ignorada. Se crean sus directorios al ejecutar las pruebas, sin depender de capturas históricas.

El PKCS12 TLS de pruebas solo identifica `CN=localhost, O=AstroTab TEST ONLY`, con contraseña pública de test. No es una clave de firma de la aplicación. Las raíces y cadenas PEM son material público. Se conservan MIT, BSD-3, Apache-2.0 y las atribuciones; se añade la licencia MIT de las plantillas GitHub Spec Kit.

No se ejecutó ningún comando de dispositivos, instalación de sistema, publicación o envío Git. La red externa de preparación se limita a Gradle/Maven; las pruebas HTTPS locales usan exclusivamente loopback. Las consultas de servicios se simulan o usan fixtures; no se presentan como consultas reales.

## Límites y bloqueo Git

Las pruebas Android usan Robolectric, incluidas renderizaciones sintéticas. No acreditan brillo, sensores, táctil, rendimiento, alarmas ni kiosco en hardware. Los Markdown anteriores son históricos saneados y no acreditan una nueva ejecución.

La copia no está lista para publicar hasta crear y revisar el único commit inicial. El índice inicial precede a los últimos ajustes: es obligatorio volver a añadir todos los archivos antes de confirmar. Con permiso de escritura en `.git`, desde la raíz:

```sh
git add -A
git commit -m "AstroTab 0.7.1: primera versión pública"
```

La identidad solicitada ya está configurada en este repositorio. Estos comandos no añaden remotos ni publican nada. No se marca A5 como cumplido mientras el entorno impida ejecutarlos.

## Resultado final de compilación y selección

APK debug: **1457946 bytes**, SHA-256 `8ff6e1c833d77dfcc189fefb8e29be7954803a06bc87cab306dfd3de1fa65372`. Generado en `app/build/outputs/apk/debug/app-debug.apk`; no se versiona.

Contenido público: **193 archivos**, aproximadamente **1,20 MiB** antes de añadir metadatos Git. Carpetas públicas: `.github/`, `.specify/`, `app/`, `core/`, `docs/`, `gradle/`, `specs/` y `tools/`. Las cachés `.gradle/`, `.kotlin/` y los directorios build se han generado al verificar, están ignorados y no forman parte del contenido público.

Auditoría final: cero coincidencias por categoría, tanto en archivos versionados del árbol de trabajo como en los candidatos completos, incluidos los Markdown nuevos. Cero archivos excluidos en la selección. El índice inicial necesita actualizarse antes del commit; los permisos del entorno impiden hacerlo.
