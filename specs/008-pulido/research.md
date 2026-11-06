> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Decisiones y modelo
Proceso Spec Kit manual: CLI specify no disponible. Plantillas oficiales locales
spec-template.md, plan-template.md y tasks-template.md leídas de
plantillas oficiales de GitHub Spec Kit. Sin consultar red.
Copia: backups/2026-11-06-*-pulido-base.zip (nombre exacto en verification.md),
SHA256 A87755C80A8CA9C9235112F679E602FA6AEB0D9E85E845E3D035BBC2FB007D71.

SatellitePass conserva inicio/máximo/fin sobre horizonte, track incluye banderas
visible/illuminated/dark. Política de lista usa máximo visible. Cota de latitud
usa inclinación plegada y horizonte a apogeo con margen conservador.
PredictionCache es de memoria, compartida durante navegación; incluye TLE completos,
coordenadas y cobertura temporal. No persistir resultados con precisión serializada.
AppAlarm añade enabled (ausente en JSON antiguo = true).
Propuesta devuelve solicitudes y bloques calculados con ventanas ya existentes.
Campo de visión deriva de sensor y focal con fórmula 2 atan(dimensión/(2 focal)).
