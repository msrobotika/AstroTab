> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Validación local

Desde PowerShell limpia en la raíz:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Wrapper: JAVA_HOME=<JDK_17> y ANDROID_HOME=<ANDROID_SDK> si faltan variables. Sin instalar nada ni conectar dispositivos. Pruebas core: SatelliteTest, ProBTest, ProBRegressionTest y CelestrakTlsTest; Android simulado: ProBScreensTest más regresiones históricas.

Capturas pro-b-*.png: Robolectric API28 nativo a 1024×600; API23 verifica navegación, AlarmManager/receiver y caché. Fixtures TLE descargadas no se empaquetan en producción. Fuentes, scripts reproducibles y logs en esta carpeta. Ver verification.md para el estado real, sin atribuir las capturas al hardware.
