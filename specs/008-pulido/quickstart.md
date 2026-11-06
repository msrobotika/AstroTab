> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Validación local

Desde ./, sin dispositivos ni instalaciones:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/verify-pulido.ps1
```

El script establece solo variables del proceso con JDK17/SDK proporcionados,
limpia salidas de Gradle y ejecuta exactamente:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain
```

Revisar core/build/test-results/test y app/build/test-results/testDebugUnitTest,
app/build/reports/lint-results-debug.txt, APK en app/build/outputs/apk/debug.
Capturas API28 a 1024×600 en docs/screens; galería pulido.html.
Las pruebas de refresco usan Transport simulado; no consultan CelesTrak.
Inspección estática opcional local: tools/verify-apk.ps1 -OutputDirectory specs/008-pulido/apk.
Objetivos <2 s ISS / <15 s total precisan tablet; no extrapolar JVM a ARMv7.
