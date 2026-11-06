# Colaborar con AstroTab

Abre una incidencia con el problema o la mejora propuesta antes de un cambio grande. El proyecto es abierto, no comercial y pensado para tablets con pocos recursos y Android 6. No necesita servicios de Google.

Usa GitHub Spec Kit para cambios de software: revisa la [constitución](.specify/memory/constitution.md), escribe especificación y criterios, plan, tareas y checklist en `specs/`, implementa y registra verificación real. Para errores, documenta síntoma, diagnóstico, reparación y comprobación. Las etapas pueden ser breves; no requieren instalar extensiones ni usar agentes adicionales.

Prueba desde la raíz con `gradlew.bat testDebugUnitTest lintDebug assembleDebug` (o `./gradlew` en Linux/macOS). Distingue pruebas JVM/Robolectric de comprobaciones en hardware. Mantén red opcional, ausencia de ubicación ficticia y atribuciones de datos. Usa ciudades públicas para ubicaciones de prueba.

No adjuntes rutas personales, credenciales, redes domésticas, ubicación privada, diagnósticos íntegros ni capturas sin sanear. No subas builds, cachés, claves, `local.properties` ni registros de compilación. Escribe textos de interfaz y documentación en español; identificadores en inglés. Conserva MIT y los avisos de terceros; tus contribuciones propias se aportan bajo MIT.
