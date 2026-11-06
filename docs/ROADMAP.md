# Hoja de ruta

AstroTab 0.7.1 ya incluye el catálogo de 110 Messier, herramientas de astrofotografía, planificador, satélites, eventos, equipos y alarmas. La planificación distribuye la oscuridad útil y limita exposición sin seguimiento según NPF; permite ajustar integración y margen entre objetos.

Pendientes que requieren pruebas reales y no quedan acreditados por JVM/Robolectric:

- Medir arranque, fluidez, memoria y autonomía en tablets Android 6 con 1 GB; revisar el cielo durante sesiones largas y cambios de pestaña.
- Comprobar brillo rojo, inclinación, gestos, alarmas y comportamiento con el proceso detenido o al reiniciar.
- Validar HOME, PIN, entrada/salida de kiosco, mantenimiento y recuperación antes de dedicar un dispositivo.
- Incorporar capturas reales saneadas en `docs/img/` y revisar accesibilidad y legibilidad en distintos paneles.

Propuestas posteriores, todavía sin implementar:

- **Intervalómetro de cámara:** primero comprobar capacidades Camera2, permisos y nivel LEGACY. No prometer RAW, controles manuales ni larga exposición donde el hardware no los admita.
- **Control Bluetooth con CYD:** acordar roles y protocolo antes de programarlo; el disparo HID existente no equivale a un servicio GATT de control.
- Mantener catálogos, fuentes orbitales y certificados con procedencia/licencias; contrastar fórmulas y publicar límites de precisión.

Cada propuesta debe pasar por la constitución y el proceso Spec Kit; esta lista no promete fechas ni autoriza instalaciones, cambios de firmware o publicación.
