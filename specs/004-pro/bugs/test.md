> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Verificación de reparaciones

Síntoma original físico aportado por una revisión externa: Objetos 60–90 s en Alcatel, cabecera Amsterdam tras reset. No se ha conectado una tablet ni repetido físicamente.

Cambios: malla de 10 min común, sidéreo compartido, cruces de eventos refinados solo en intervalos con cambio, caché por noche/lugar/zona/mínimo, progreso 22/44/66/88/110. Herramientas omite 110 planes, meses omite Luna, CPU separado del transporte. Calendario/semana cancelan al cambiar de pantalla. Mapa calcula fuera del hilo principal una vez/minuto.

Pruebas: plan completo 17,2547 ms PC (<300), meses 11,9612 ms; precisión comparada con malla fina, caché/progreso comprobados con Android simulado. Zona persistente, sistema por defecto, selección Canarias, formatos explícitos y DST comprobados. Comando final y 86 pruebas sin fallos en [verificación](../verification.md).

Estado: reparación implementada y verificada en JVM/Android simulado; **objetivo físico <3 s y cabecera en tablet tras reset pendientes**, no cerrados por extrapolación del PC. Cambiar zona de la app en Ajustes resuelve el caso de configuración sin modificar Android; el valor por defecto sigue siendo el sistema por petición del usuario.
