> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Diagnóstico

Síntomas aportados por una revisión externa en Alcatel 8063: Objetos 60–90 s; cabecera Europe/Amsterdam tras reset. No se ha reproducido físicamente aquí.

Código 003: photoData invalida caché cada minuto, ejecuta 110 planes y eventos incluso para Herramientas; malla 2 min; eventos barren 14 veces la noche; galacticMonths crea 36 mallas con Luna innecesaria. Un único executor comparte red y CPU, susceptible de esperar timeouts. La zona está fijada a TimeZone.getDefault(). Severidad alta: flujo principal de fotografía bloqueado y horas confusas.

Remedio: malla común de 10 min, cruces refinados, caché estable de noche/ubicación/zona/mínimo, entregas parciales, separar trabajo de red, meses solo solar/núcleo. Ajuste zona persistente y formatos explícitos. Verificar presupuesto JVM completo, precisión frente a malla fina y DST; prueba UI simulada; físico pendiente.
