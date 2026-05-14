# Plan de Obsolescencia de Admin Migration

- Este plan describe cuándo y cómo eliminar las herramientas de migración de admin/isAdmin tras confirmar un estado CLEAN estable en producción.
- Foco canónico: admin es el único campo de privilegios; isAdmin se elimina.
- Cronología propuesta:
 1. Verificar que verifyAdminMigration devuelva CLEAN por 7 días consecutivos en producción.
 2. Desactivar las funciones de migración ( Phase A ) mediante la bandera MIGRATION_DEPRECATION_ENABLED y, si procede, remover código de migración en Phase B14.
 3. Eliminar remanentes de isAdmin en repositorio de código y en las reglas si existen referencias.
 4. Documentar la eliminación y actualizar el plan de auditoría.

- Entregables:
  - Evidencia de estado CLEAN en producción durante 7 días.
  - Patch para deshabilitar migraciones (ya incorporado vía MIGRATION_DEPRECATION_ENABLED).
  - Informe de cambios y lecciones aprendidas.
