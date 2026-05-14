# Arquitectura Canónica de Privilegios: admin

## Estado Final: **NORMALIZADO**

La unificación del campo `admin` se ha completado satisfactoriamente. Este documento establece el estándar técnico único para la gestión de privilegios en el proyecto BananaApp.

### 🏛️ Estándar de Implementación
- **Firestore:** El campo físico en la colección `/users` es `admin` (Boolean).
- **Kotlin:** La propiedad en los modelos `UserProfile` y `UserProfileDto` es `admin`.
- **Backend:** La lógica de autorización en Cloud Functions consulta exclusivamente `doc.data().admin`.
- **Seguridad:** El campo está protegido contra escritura del cliente en `firestore.rules` mediante la función `protectedFields()`.

### ✅ Hitos del Cierre (Phase B15)
1. **Eliminación Física:** Se han borrado todas las funciones de migración y la suite de auditoría legacy del backend.
2. **Normalización Total:** No existen referencias a `isAdmin` en ningún archivo del repositorio (Android, Backend o Reglas).
3. **Seguridad Endurecida:** El campo `admin` es inmutable desde el cliente, eliminando riesgos de escalada de privilegios.

### 🛠️ Mantenimiento y CI
Se ha establecido una política de **"Zero Legacy"**. Cualquier intento de reintroducir campos con el prefijo `is...` para roles administrativos será bloqueado en el proceso de revisión de código.

*Última actualización: 2026-05-04*
