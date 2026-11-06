> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Checklist y coherencia 003

- [x] Trabajo previo leído y respaldado con rutas relativas conservadas; hash registrado antes de implementar.
- [x] Constitución actualizada; Spec Kit manual declarado y plantillas oficiales consultadas; sin CLI instalada ni delegación automática.
- [x] AC1–AC8 cubren los escenarios solicitados y están vinculados a tareas F01–F09.
- [x] Decisiones sobre noche/DST, fórmulas, muestras, ausencias y orientación polar explícitas; sin ambigüedad material pendiente.
- [x] Lógica de negocio y cálculos en core sin Android; adaptación Android para Canvas, sonido, preferencias y reloj monotónico.
- [x] Fuentes/transformación/hash/BSD-3 conservados; red de desarrollo limitada a dependencias y al JSON indicado.
- [x] No se añaden permisos ni dependencias; sin adb, dispositivos, instalaciones del sistema ni push.
- [x] Documentación diferencia evidencia JVM/Android simulado de mediciones físicas; objetivo de memoria sin certificar.
- [x] Validación de cada cálculo y revisión de extremos polares, DST, caché y sesión con pruebas; resultados concretos en verification.md.
- [ ] Validación física de esta entrega: memoria, tacto, fuentes, sonido, rendimiento, brillo y comportamiento del sistema. Excluida de la sesión.
