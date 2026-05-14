# Arquitectura Social V2: Fuente Única de Verdad (Singularity)

## 🏛️ Estándar Técnico
El sistema social de BananaApp ha migrado a una arquitectura basada en **documentos de relación independientes**, eliminando la dependencia de arrays dentro del perfil de usuario.

### 1. Estructura de Datos
- **Colección `friendships`:** Contiene los documentos de relación bidireccional.
  - ID: `{userId}_{canonicalId}`
  - Campos: `ownerId`, `friendId`, `createdAt`, `metadata`.
- **Colección `friend_requests`:** Almacena solicitudes pendientes.
  - Al ser aceptada o rechazada, el documento se ELIMINA físicamente para mantener la UI limpia.
- **Colección `social_audit`:** Log inmutable de todas las transacciones sociales.

### 2. Reglas de Consistencia (Singularity)
- **Verdad Canónica:** La existencia de un documento en la colección `friendships` define la amistad.
- **Contadores:** Los campos `friendCount`, `pendingRequestsSentCount` y `pendingRequestsReceivedCount` en el perfil de usuario son **vistas materializadas** que deben ser actualizadas únicamente mediante transacciones atómicas.
- **Sugerencias:** Se generan reactivamente excluyendo a los usuarios presentes en `friendships` o `friend_requests`.

### 3. Flujos de Operación
- **Aceptación:** [Transaction] -> Delete Request + Set 2 Friendships + Increment Counters.
- **Eliminación:** [Transaction] -> Check Friendship Existence -> Delete 2 Friendships + Decrement Counters (solo si existían).

## 🚀 Plan Phase D: Eliminación de Legacy (Arrays)
Una vez confirmada la estabilidad de la V2 en el 100% de los clientes Android, se procederá a:
1. **Desactivación:** Cambiar `ENABLE_LEGACY_COMPATIBILITY: false` en Cloud Functions.
2. **Limpieza de Código:** Eliminar bloques de código `FieldValue.arrayUnion/Remove` en todas las funciones sociales.
3. **Purgado de Datos:** Ejecutar un script de mantenimiento para eliminar los campos `friends`, `friendRequestsSent` y `friendRequestsReceived` de todos los documentos de la colección `users`.

---
*Última actualización: 2026-05-04*
