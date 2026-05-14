# Checklist de Despliegue y Monitoreo (Cierre Phase B)

Este checklist debe seguirse rigurosamente para el despliegue final de la Phase B15 en producción.

### 📦 Fase de Despliegue
- [ ] **Firestore Rules:** Desplegar primero las reglas para asegurar que el campo `admin` esté protegido antes de que las nuevas funciones entren en acción.
- [ ] **Cloud Functions:** Desplegar con `firebase deploy --only functions`. Verificar que las funciones de migración antiguas (ej: `migrateAdminField`) han sido eliminadas de la consola.
- [ ] **Android App:** Publicar la versión que utiliza la propiedad `admin`.

### 🔍 Fase de Monitoreo (Primeras 24-72h)
- [ ] **Cloud Logging:** Filtrar por errores `403` o `permission-denied`. Verificar que los administradores legítimos no están siendo bloqueados.
- [ ] **Firestore Audit:** Realizar una consulta aleatoria en la colección `users` para confirmar que los nuevos perfiles se crean/editan usando el campo `admin`.
- [ ] **Crashlytics:** Monitorear posibles crashes relacionados con la serialización de `UserProfileDto`.

### 🛡️ Fase de Mantenimiento CI
- [ ] **Ejecución de Script:** Correr `./scripts/check-legacy-fields.sh` localmente antes de cada commit.
- [ ] **Revisión de Código:** No permitir el uso de `isAdmin` en ninguna nueva funcionalidad de administración.

---
*Estado: Phase B15 - Cierre de Proyecto*
