> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Reparación de síntomas

- Cero: DisplayFormat normaliza el resultado redondeado; MainActivity y ejes/cursor de gráficas lo reutilizan. La regresión previa falla para −0,1°, registrada en before.log; después pasa junto con negativos reales y datos ausentes.
- Caché: photoData e Inicio usan la misma nightReference. El cambio de pestaña cancela publicación anterior, no el cálculo válido que llena la caché. Robolectric exige identidad de PhotoData y lista al alternar Objetos → Inicio → Objetos, sin progreso visible; la prueba histórica conserva invalidación al cambiar mínimo.
- Top: TargetChoice impone ≥2° y bonifica tipo no repetido. Prueba geométrica y de variedad con los 110 Messier de la noche.
- Curvas: ObjectPlan incluye miniAltitudes a 2 min (721 en una noche normal, ajustado a DST), preparadas una vez con sidéreo compartido e incluidas en caché. MiniAltitude crea Path cúbico al dimensionar; no hace astronomía en onDraw.
- Día: fondo puro SkyControls.background según altura solar y tema; SkyView usa altura ya calculada y leyenda diurna. Pruebas de paleta y captura diurna normal/roja. Ninguna captura se presenta como tablet.

La prueba real de 0.5 aportada por el equipo es el origen de los síntomas; esta reparación se verifica en JVM/Robolectric. No se usaron adb ni dispositivos.
