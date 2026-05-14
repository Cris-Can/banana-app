# Phase B14 — Plan de Rollback y Mitigaciones

- Objetivo: Definir acciones para revertir cambios en caso de incidencias durante la migración de admin.
- Acciones recomendadas:
  1. Deshabilitar migraciones en ejecución (usando MIGRATION_DEPRECATION_ENABLED) y confirmar revertir cualquier cambio parcial mediante un script de reversión que vuelva a copiar admin desde isAdmin si existiese.
  2. Si se detecta pérdida de privilegios, ejecutar un rollback: copiar admin desde isAdmin (si existiera) y restaurar isAdmin en los registros afectados, asegurando coherencia de permisos.
  3. Desplegar un parche de lectura/archivo para reintroducir isAdmin como alias temporal si se requiere, con fecha de retirada definida.
- Verificación: validar en staging o producción que permisos de admin siguen funcionando y que no quedan isAdmin sin migrar.
- Cierre: documentar resultados y preparar Phase B15.
