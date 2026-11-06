> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Plan de implementación · 007

Kotlin 2.1.20/JDK17, Android Views/Canvas API23 y módulo core JVM; sin nuevas dependencias. Misma paleta, widgets, iconos y hojas ⓘ de 006.

## Revisión constitucional previa

Conforme: base respaldada y hash registrado; offline primero, TLS validado, sin datos inventados, solo INTERNET. No hardware/adb/instalación/push/agentes. Spec Kit manual por ausencia CLI. Fuentes externas limitadas a Vallado/CelesTrak/NASA citadas por el usuario. Revisar nuevamente al cerrar.

## Diseño y secuencia

1. Diagnosticar regresiones, añadir pruebas de síntoma y arreglar formato, referencia común de noche/caché, diversidad, curvas y fondo solar.
2. Incorporar motor SGP4 puro desde referencia oficial, parser TLE validado y caché/repository desacoplado; contrastar vectores antes de integrar pasos y rendimiento.
3. Calendario puro con búsqueda de raíces/extremos y tabla estática NASA; cachear meses por ubicación/zona.
4. Perfiles en Settings con migración, plan de sesión puro y persistencia limitada, alarmas puras más adaptador Android.
5. Pantallas nuevas, polar/cronograma y enlaces, Más sin reducir controles, rojo en todos los elementos.
6. JVM/Robolectric, capturas reales del render simulado, lint/APK, auditoría requisitos y documentos.

## Riesgos y mitigaciones

SGP4 de espacio profundo requiere validar ramas oficiales; no usar Kepler como sustituto. Pasos breves requieren malla temporal y refinamiento de cruces; precalcular Sol/observador comunes y filtrar geometría antes de trabajo fino. Alarmas modernas restringidas por SO: probar API23 y documentar alternativa sin permisos. Eclipse solar exige geometría además de altura: información aproximada explícita. Trabajo largo fuera de main, publicación por generación/caché y resultados parciales. Bench PC con factor conservador explícito, sin afirmar medida ARM.
