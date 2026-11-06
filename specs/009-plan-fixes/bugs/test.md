> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Test — resultado

**verified** para los criterios JVM/Robolectric/terminal solicitados.

1. Reproducción previa real: tests-before-core.xml confirma 30 s frente a
   4,54 s y cobertura 0,163835779; tests-before-app.xml confirma HOME en
   Herramientas. Fallaban 4/5 regresiones core y 1/2 de navegación.
2. Repetición posterior de esos mismos síntomas: pasan. Se añaden casos
   de margen, ventanas cortas, ajuste manual y píxeles de la barra.
3. Comprobación completa tras clean en PowerShell nuevo: 155/155, sin
   omisiones; lint 0 errores/4 avisos heredados; assembleDebug correcto.
4. AAPT/apksigner y tamaño del APK verificados localmente.

Evidencia y correspondencia completa: [verification.md](../verification.md).
No se han usado dispositivos: la verificación física queda fuera de esta
sesión por instrucción expresa. No se confunde Robolectric con una tablet.
