# Kiosco de AstroTab · API 23+

**Opcional y difícil de revertir:** Device Owner puede requerir un restablecimiento de fábrica para retirarlo, con pérdida de datos. Prueba antes la salida y conserva copias.

Los comandos siguientes son ejemplos para quien administre su propia tablet; no se han ejecutado durante la preparación pública.

## Preparación manual en la tablet

1. Conserva una copia de los datos y un método de recuperación del fabricante. El restablecimiento borra datos; esta guía no lo realiza.
2. Para Device Owner usa una tablet recién restablecida, sin cuentas ni usuarios adicionales y con depuración USB autorizada en tu PC. No actives el kiosco hasta probar el PIN y la salida.
3. Instala el APK de depuración (para producción, compila y firma con una clave propia guardada fuera del proyecto). Comprueba primero AstroTab como app normal, sin red, y guarda coordenadas manuales.
4. En Ajustes → Mantenimiento crea y confirma un PIN de 6–12 cifras. No hay PIN por defecto. Se guarda sal aleatoria y derivación PBKDF2; cinco fallos imponen una espera de 30 s. Guarda el PIN fuera de la tablet.

```powershell
# Solo ejemplos para ejecutar por tu cuenta cuando autorices hardware.
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell dpm set-device-owner org.msrobotika.astrotab/.kiosk.AstroAdminReceiver
```

Si dpm rechaza el rol por cuentas/usuarios/propietario previo, resuelve la causa antes de continuar. No borres cuentas ni restablezcas el equipo sin copia. No se ofrece un bypass.

## Activación y retorno

Abre AstroTab → Ajustes → Mantenimiento, introduce el PIN y pulsa «Activar kiosco y volver». La app comprueba Device Owner, configura `setLockTaskPackages`, asigna HOME persistente con `addPersistentPreferredActivity`, verifica `isLockTaskPermitted` e inicia `startLockTask`. Sin propietario no intenta fijar pantalla: funciona como app normal y explica el estado.

Al reiniciar, Android debe lanzar el HOME persistente; `onResume` vuelve a lock task si están guardados activación y PIN. No se necesita un receptor BOOT_COMPLETED ni servicios en segundo plano. **Este comportamiento requiere validación en la Alcatel**, incluidos selector HOME, reinicio, botón Inicio, Recientes y apagado/encendido.

## Mantenimiento y recuperación

- Ajustes → Mantenimiento (también Atrás estando en kiosco) solicita el PIN. Tras validarlo, `stopLockTask` permite abrir Wi-Fi, fecha/hora y exportar ajustes/diagnóstico con el selector de documentos. El diagnóstico incluye coordenadas; no incluye PIN ni hash.
- «Volver a AstroTab» reactiva lock task si el kiosco sigue habilitado. Al volver de ajustes externos se muestra mantenimiento y hay que pulsar ese botón. Si Android mata el proceso durante mantenimiento, la siguiente apertura exige de nuevo el PIN para salir.
- «Desactivar kiosco y HOME persistente» limpia el HOME persistente y la lista de paquetes permitidos. El rol de propietario permanece. «Abrir inicio del sistema» desactiva primero el kiosco y abre el selector de inicio del sistema.
- Si olvidas el PIN, no existe PIN maestro. Con USB previamente autorizado y lock task detenido, puedes reinstalar una compilación de recuperación firmada con **la misma clave** que mantenga el acceso a los datos y retire las políticas mediante DevicePolicyManager. Conserva el APK y su clave antes de aprovisionar.
- Como último recurso, usa el procedimiento de recuperación/restablecimiento del fabricante; borra los datos. El modo seguro y la recuperación dependen del fabricante y no se han probado.

En un equipo de laboratorio, con depuración autorizada, estos comandos pueden ayudar al diagnóstico; no sustituyen el PIN ni garantizan retirar Device Owner:

```powershell
adb shell dumpsys device_policy
adb shell dumpsys activity activities
adb shell am start -n org.msrobotika.astrotab/.MainActivity
```

`dpm remove-active-admin` **no retira normalmente un Device Owner de producción**. El APK no declara `testOnly`, no añade un receptor público para saltarse el PIN y no ejecuta comandos shell desde la aplicación. La retirada completa del propietario requiere una ruta válida del DPC o restablecimiento. Prueba la recuperación antes de dedicar la tablet.

## Validación física pendiente

Registrar: arranque offline normal, HOME tras reinicio, entrada y salida con PIN correcto/erróneo, espera tras intentos, Wi-Fi/reloj, exportación, retorno de ajustes, pérdida del proceso, desactivación de HOME y recuperación. Las pruebas JVM validan la política/PIN y la integración Android simulada; no acreditan estas acciones sobre la tablet.

La clave de esta compilación debug está en .gradle/astrotab-debug.keystore (excluida del control de versiones). Conserva una copia antes de aprovisionar si vas a necesitar actualizar o recuperar esta instalación. Las claves de publicación deben ser propias.
