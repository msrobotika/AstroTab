> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Bug Assessment: privacidad, inmersivo, mapa y Ajustes

- Slug: tablet-display-fixes
- Fecha: 2026-11-06
- Fuente: informe pegado por el equipo, sin capturas adjuntas a esta sesión.
- Veredicto: valid para privacidad/inmersivo/mapa; likely valid, needs reproduction para compresión de Ajustes.
- Severidad: high (privacidad y legibilidad).

## Síntomas y diagnóstico

1. `MainActivity.updateHeader` imprime latitude/longitude y origen libre. Defecto directamente confirmado por código.
2. `immersive` carece de LAYOUT_* y no existe `onWindowFocusChanged`; onResume sí lo llama. Sin garantía de restablecimiento al cerrar diálogo o regresar foco.
3. `SkyView.onDraw` rota Canvas y todos los nombres. Las constelaciones solo comprueban horizonte y distancia X; no distancia Y, longitud euclídea ni campo visible. Rejilla dibuja líneas completas sin filtro. El salto ±180° produce trazos largos.
4. `settingsScreen` ya usa `scroll(c)`; helper sin LayoutParams explícitos. El informe de botón aplastado no queda demostrado solo por código. Reproducir medidas en Robolectric, incluyendo menor altura útil; hacer explícito el contrato de medidas y verificar acceso al final.

## Reproducción y remedio

Configurar latitud 40,4168 y longitud -3,7038; abrir distintas pestañas y revisar cabecera. Borrar flags simulando pérdida de foco, recuperar foco y reanudar. Probar segmentos con extremos invisibles, fuera de campo, verticales/diagonales largos y salto de acimut; probar rotación y colisiones de nombres. Medir Ajustes a 1024×600 y 1024×480 y desplazar hasta red/licencias.

Remedio preferido y ficheros: formato privado en Settings/core y uso en MainActivity; máscara inmersiva completa reutilizada en ventana/diálogo; medidas explícitas de ScrollView; filtro de segmentos y colocación de etiquetas reutilizables en SkyGeometry/core; proyección rotada por punto en SkyView con Canvas sin rotación y selección en coordenadas de pantalla.

## Verificación y límites

Regresiones JVM y Android simulado API 23, más testDebugUnitTest/lintDebug/assembleDebug. Comprobar síntoma visual mediante Canvas simulado cuando sea posible. No se reproducirá Android 6 físico ni su compositor; veredicto final físico necesariamente parcial. No se ha medido de nuevo memoria ni fps. Sin cambios de dependencias ni instalaciones.
