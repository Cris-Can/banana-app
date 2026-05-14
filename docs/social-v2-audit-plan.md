# Plan de Auditoría y Monitoreo: Sistema Social V2

Este plan asegura la salud a largo plazo del sistema social y previene la degradación de datos.

### 🔍 Checklist de Monitoreo (Semanal)
- [ ] **Cloud Logging:** Revisar errores con el prefijo `[SOCIAL_V2] ERROR`.
- [ ] **Latencia de Transacciones:** Verificar que el tiempo de ejecución de `acceptFriendRequestV2` sea < 2.0s.
- [ ] **Integridad de Contadores:** Ejecutar `verifySocialSystemConsistency` sobre una muestra del 5% de usuarios activos.

### 🛡️ Procedimientos de Saneamiento
En caso de detectar discrepancias (ej: un usuario ve a un amigo pero el contador dice 0):
1. **Identificación:** Usar `verifySocialSystemConsistency(userId)` para confirmar el desfase.
2. **Reparación:** Ejecutar `repairUserCounters(userId)`. Esta función reconstruirá el `friendCount` y el array legacy (si está activo) basándose en la realidad física de la colección `friendships`.
3. **Investigación:** Consultar `social_audit` con el `userId` para identificar qué operación falló o se ejecutó de forma concurrente.

### 📈 Métricas de Éxito
- **Tasa de Error:** < 0.1% de fallos en transacciones sociales.
- **Consistencia:** 100% de coincidencia entre `friendships.count()` y `user.friendCount`.
- **Limpieza UI:** Cero reportes de "solicitudes fantasma" (pendientes que no se pueden aceptar).

---
*BananaApp Maintenance Framework*
