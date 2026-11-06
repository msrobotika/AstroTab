> Histórico saneado para distribución pública. No acredita una nueva ejecución; registros, capturas y fuentes de trabajo citados pueden estar excluidos. Verificación vigente: specs/010-public-release/verification.md.

# Bug Verification: tablet fixes 2

- Slug: 005-tablet-fixes-2.
- Tested: 2026-11-06.
- Assessment: ./assessment.md.
- Fix: ./fix.md.
- Result: **partial** (aceptación PC/JVM completa; reproducción física no realizada).

## Summary

Las reparaciones y las regresiones automatizadas pasan. No se acredita resolución end-to-end de una tablet no conectada: red real Android6 y activación accidental del botón requieren contraste externo. No se ha reproducido la activación de Bortle en los gestos completos de la JVM; sus botones ya tenían click estándar. Sí se corrige la interceptación vertical propia de las gráficas.

## Checks Performed

| Comprobación | Comando / acción | Resultado | Notas |
|---|---|---|---|
| Reproducción física | tablet/adb | not-run | Prohibido expresamente por usuario. |
| Reproducción automatizada equivalente | TabletFixesTest / TabletRegressionTest | pass | Cadenas reales archivadas, HTTPS de loopback, eventos con fecha/lugar reales, ScrollView y Canvas API23. |
| Nuevas pruebas | 6 core +4 app | pass | Autenticidad/hostname/autofirmado, errores, Luna, gestos y vacío. |
| Suite completa | gradlew.bat testDebugUnitTest lintDebug assembleDebug | pass | build-final.log, salida0, 96 pruebas, 0 fallos/errores. |
| Lint | lintDebug | pass | 0 errores y4 advertencias preexistentes, sin supresiones nuevas. |
| TLS PC | openssl s_client con SNI/TLS1.2/verify_hostname/solo raíz/AES128 | pass | Tres hosts, verificación0; sin GETs reales a APIs. |
| APK | tools/verify-apk.ps1 | pass | 0.4.1/code5, firma API23 válida, 1.363.867bytes; tres CA y avisos incluidos, material de pruebas excluido. |

## Output Excerpts

`BUILD SUCCESSFUL in 1m 48s`; `96 pruebas, 0 fallos, 0 errores`; lint `0 errors, 4 warnings`. OpenSSL: `Verification: OK` y `Verify return code: 0 (ok)` por host. Luna: altura11,417842°, última salida06/11 04:54:19 CET, puesta06/11 16:11:19 CET. Detalle por criterio y archivos en ../verification.md.

## Residual Risks

- Almacén concreto y excepciones del firmware Alcatel desconocidos; el diagnóstico de confianza es probable. Nuevas causas visibles/persistentes permiten distinguirlo después.
- Gesto físico original de botones no reproducido en simulación; no cerrarlo como acreditado en tablet.
- CA pueden rotar; fixtures verificadas a fecha histórica, producción con fecha real.

## Recommendation

Entregar el APK con aceptación automatizada completada y conservar pendiente el contraste físico externo. No marcar como probado lo no ejecutado. Ningún hook configurado en .specify/extensions.yml (archivo ausente); no se instalaron extensiones.
