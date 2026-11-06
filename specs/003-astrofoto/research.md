> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Decisiones técnicas y fórmulas

Sin consultas web adicionales: red limitada por el equipo a Maven/Gradle y la fuente Messier. Se reutiliza Astro/Schlyter existente y fórmulas explícitas contrastadas en JVM.

## Tiempo y planificación

Mediodía local → siguiente mediodía mediante Calendar; cada muestra UTC representa los siguientes 2 min. Error de borde de cada ventana hasta 2 min; curvas recalculadas como máximo una vez por minuto y compartidas entre los 110 objetos. Puntuación = redondeo(100 × min(horas útiles/6,1) × media ponderada(sen altura) × media ponderada(factor lunar)). Solo integra Sol <−18° y altura ≥mínimo. Factor lunar = 1−iluminación × (1−separación/180) × max(0,sen altura lunar), solo con Luna sobre horizonte. No incluye meteorología, transparencia ni brillo superficial.

Precesión media J2000 a fecha, IAU 1976 / Meeus cap. 21, T en siglos julianos: ζ=(2306,2181T+0,30188T²+0,017998T³)/3600; z=(2306,2181T+1,09468T²+0,018203T³)/3600; θ=(2004,3109T−0,42665T²−0,041833T³)/3600. Se rota el vector equatorial; sin nutación, aberración ni movimiento propio. Contraste con ejemplo θ Persei de Meeus. GMST aproximado existente de Schlyter. Tránsito por H=0 iterado, altura superior 90−|lat−dec|. Sin refracción ni relieve.

## Óptica

500/(focal real × recorte diagonal). Recorte=diagonal completo/diagonal sensor. NPF simplificada=(35N+30p)/(f cos δ), N=número f, p=µm, f=mm; no válida cerca de polos (|δ|≥89°). No equivale a garantizar estrellas puntuales; no es NPF completa con criterios de tolerancia personales. Campo=2atan(sensor/(2f)), grados. Escala=206,264806p/f segundos de arco/píxel. Ocupación=tamaño angular/ campo angular; ejes mayor/menor alineados, sin margen.

## Buscador polar

Polaris J2000: AR 37,95456067°, dec +89,26410897°. Sigma Octantis J2000: AR 317,195°, dec −88,9565°. Ambas se precesan a fecha. H=rev(GMST+longitud−AR), ángulo horario positivo hacia oeste; horas=H/15. Radio=90−|dec|. Esfera de reloj: mirar directamente hacia el polo, cenit arriba, acimut creciente hacia derecha. Desde las 12, sentido horario: norte −H, sur +H. La referencia es el meridiano superior local, no un norte magnético ni un visor óptico particular; giro/inversión de buscador deben ajustarse por el usuario.

Referencia independiente calculada en J2000 (01/01/2000 12:00 UTC), longitud 0°, GMST convencional 280,46061837°: Polaris H=242,50605770°, reloj 117,49394230°, radio 0,73589103°; Sigma H=323,26561837°, reloj 323,26561837°, radio 1,0435°. Tolerancia 0,02° para GMST aproximado. No es una validación de guiado fino ni de retículas comerciales.

## Vía Láctea y meteorología

Núcleo J2000 (266,4051°, −28,9362°), precesado; ventanas altura >10° y Sol <−18°. Máximo dentro de esas ventanas, resolución 2 min. Meses: promedio de noches 5,15,25 para el año/ubicación/zona configurados, ordenado por horas útiles; no predicción de nubes.

Crepúsculos civil −6°, náutico −12°, astronómico −18°; dorada [−4,+6]°, azul [−6,−4]° (convenciones explícitas). Salida/puesta solar centro a −0,833°. Luna centro topocéntrico a 0°, sin refracción ni semidiámetro. Cruces por bisección; ausencia de cruce no significa imposibilidad permanente.

Nota nocturna anterior × factor rocío (0,65 si T−Td<3 °C) × factor viento (0,70 desde 15 km/h; 0,40 desde 30 km/h). Heurística, no índice profesional; campos ausentes quedan sin evaluar, no se fabrican. Las nubes siguen siendo obligatorias para emitir nota. Datos opcionales con red desactivada se ocultan incluso si existe caché válida.

## Sesión

Duración=(luces+darks)×exposición + flats×exposiciónFlat + (tomas−1)×pausa. Estado obtenido de elapsedRealtime absoluto respecto al comienzo; nunca suma retrasos del Handler. Cambios de reloj civil solo afectan a la etiqueta de fin. Callback cada 200 ms, sin cámara ni servicio de fondo. Rotación/recreación retiene objeto de timer; matar proceso cancela. No se reproducen avisos saltados al salir; sin pausas, un doble pitido representa fin e inicio coincidentes. Validación acústica física pendiente.
