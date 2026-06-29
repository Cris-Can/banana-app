# Informe de Auditoría - Banana App

> Fecha: 2026-06-17
> Proyecto: C:\Users\crist\AndroidStudioProjects\Banana - copia
> Herramientas: análisis manual de código, verificación de compilación

---

## Resumen Ejecutivo

Se auditaron ~180+ archivos Kotlin, 7 tests unitarios, 2 módulos core, 2 archivos de reglas Firebase. Se encontraron **11 issues** categorizados por severidad. Los issues críticos (Room faltante y Worker síncrono) bloquean la compilación y deben corregirse primero.

---

## Estado Inicial de Compilación

Según `build_error.txt`, la última compilación con `./gradlew assembleDevDebug` fue **exitosa** (BUILD SUCCESSFUL in 6s, 52 tareas, 2 ejecutadas). Sin embargo, el análisis revela dependencias faltantes de Room que **deberían** romper la compilación. Es posible que `build_error.txt` sea de una versión anterior del código.

---

## Issues Encontrados

### 🔴 CRÍTICOS (bloquean compilación)

#### 1. Faltan dependencias de Room

| Archivo | Línea | Problema |
|---------|-------|----------|
| `app/build.gradle.kts` | - | No incluye `room-runtime`, `room-ktx`, `room-compiler` |
| `data/local/AppDatabase.kt` | 1-13 | Usa `@Database`, `RoomDatabase` |
| `data/local/NotificationDao.kt` | 1-28 | Usa `@Dao`, `@Insert`, `@Query` |
| `data/local/NotificationEntity.kt` | 1-18 | Usa `@Entity`, `@PrimaryKey` |

**Fix requerido:**
1. En `gradle/libs.versions.toml`, agregar:
```toml
room = "2.6.1"
```
```toml
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
```

2. En `app/build.gradle.kts`, agregar:
```kotlin
implementation(libs.room.runtime)
implementation(libs.room.ktx)
ksp(libs.room.compiler)
```

---

### 🟡 ALTOS

#### 2. EventReminderWorker extiende Worker (síncrono)

| Archivo | Línea | Problema |
|---------|-------|----------|
| `workers/EventReminderWorker.kt` | 9-12 | `class EventReminderWorker : Worker(context, workerParams)` |

`Worker.doWork()` se ejecuta en un background thread de WorkManager, pero no es cancellable y no soporta corrutinas ni inyección Hilt.

**Fix:** Migrar a `@HiltWorker CoroutineWorker` como `LocationWorker`:

```kotlin
package com.eventos.banana.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eventos.banana.R
import com.eventos.banana.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.hilt.work.HiltWorker

@HiltWorker
class EventReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val eventTitle = inputData.getString("eventTitle")
            ?: applicationContext.getString(R.string.reminder_fallback_title)
        val eventId = inputData.getString("eventId")
            ?: return Result.failure()

        NotificationHelper.sendLocalNotification(
            context = applicationContext,
            channelId = NotificationHelper.CHANNEL_REMINDERS,
            notificationId = eventId.hashCode(),
            title = applicationContext.getString(R.string.reminder_title),
            body = applicationContext.getString(R.string.reminder_body, eventTitle)
        )

        return Result.success()
    }
}
```

---

#### 3. Constantes de canales de notificación duplicadas

| Archivo | Línea | Constante |
|---------|-------|-----------|
| `util/NotificationHelper.kt` | 18-20 | `CHANNEL_GENERAL = "banana_channel_01"`, `CHANNEL_REMINDERS = "banana_reminders"`, `CHANNEL_MESSAGES = "banana_messages"` |
| `util/AppConstants.kt` | 134-140 | `NOTIFICATION_CHANNEL_GENERAL = "banana_channel_01"`, `NOTIFICATION_CHANNEL_MESSAGES = "banana_messages"`, `NOTIFICATION_CHANNEL_REMINDERS = "banana_reminders"` |

Las constantes en `AppConstants` son **idénticas** a las de `NotificationHelper` y **nunca se usan** en el código fuente.

**Fix:** Eliminar las constantes duplicadas de `AppConstants.kt` (líneas 132-141) y reemplazar cualquier referencia futura con `NotificationHelper.CHANNEL_*`.

---

### 🟠 MEDIOS

#### 4. `println` debug leftover en test

| Archivo | Línea | Código |
|---------|-------|--------|
| `app/src/test/.../ui/rating/RatingViewModelTest.kt` | 85 | `println("TEST DEBUG STATE: $state")` |

**Fix:** Eliminar la línea `println("TEST DEBUG STATE: $state")`.

---

#### 5. AppFirebaseMessagingService usa NotificationManager directo

| Archivo | Línea | Código |
|---------|-------|--------|
| `notifications/AppFirebaseMessagingService.kt` | 83-84 | `val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager` y `notificationManager.notify(...)` |

Usa `NotificationManager` directamente. Debería usar `NotificationManagerCompat` (que maneja `SecurityException` correctamente) o mejor, delegar en `NotificationHelper.sendLocalNotification()`.

**Fix:** Reemplazar con `NotificationManagerCompat.from(this).notify(...)`.

---

#### 6. TODO pendiente en AdminRepository

| Archivo | Línea | Código |
|---------|-------|--------|
| `data/repository/AdminRepository.kt` | 25 | `// TODO: Implement actual pagination for large migration scripts` |

Sin implementar desde su creación. Decidir si implementar o eliminar el TODO.

---

#### 7. `eventId.hashCode()` como notification ID

| Archivo | Línea | Código |
|---------|-------|--------|
| `workers/EventReminderWorker.kt` | 23 | `notificationId = eventId.hashCode()` |

Riesgo de colisión de IDs de notificación. Baja probabilidad pero posible.

---

### 🟢 BAJOS

#### 8. Sin @Preview en ningún Composable

Ninguna de las 120+ funciones `@Composable` tiene `@Preview`. No afecta compilación pero dificulta el desarrollo visual.

#### 9. android.util.Log (~100+ llamadas) no migrado a Timber

El proyecto tiene Timber configurado pero muchos archivos usan `android.util.Log` directamente. Refactorizar progresivamente.

#### 10. Constantes de ads hardcodeadas

| Archivo | Línea | Código |
|---------|-------|--------|
| `util/AppConstants.kt` | 27-30 | `AD_UNIT_ID_INTERSTITIAL_TEST`, `AD_UNIT_ID_REWARDED_TEST` |

También definidas en `app/build.gradle.kts`. Limpiar para que solo existan en un lugar.

#### 11. Constantes NOTIFICATION_CHANNEL nunca usadas

| Archivo | Línea | Código |
|---------|-------|--------|
| `util/AppConstants.kt` | 132-141 | Bloque completo de constantes de notificación (muertas) |

Ver issue #3 combinado.

---

### ✅ VERIFICADO: Issues inexistentes

| Issue sospechado | Resultado |
|-----------------|-----------|
| `runBlocking` en AppFirebaseMessagingService | **FALSO** - No existe `runBlocking` en todo el proyecto |
| `searchRadiusKm` no resuelto | **FALSO** - Definido en `UserProfileDto.kt` y `UserProfile.kt` |
| `localizedName` no resuelto | **FALSO** - Definido en `LocalizedEnums.kt` |
| POST_NOTIFICATIONS no solicitado runtime | **FALSO** - Ya implementado en `MainActivity.kt:98-108` |

---

## Archivos de Reglas Firebase

### `firestore.rules` (158 líneas)
- Prácticas correctas: autenticación requerida, validación de campos protegidos, control de acceso por propietario
- Recomendación: **ningún cambio necesario**

### `storage.rules` (88 líneas)
- Prácticas correctas: límites de tamaño, restricción por tipo MIME, rutas con control de acceso
- Recomendación: **ningún cambio necesario**

---

## Orden de Corrección Recomendado

```
Paso 1: Agregar Room dependencies
Paso 2: Migrar EventReminderWorker
Paso 3: Unificar constantes de notificación
Paso 4: Eliminar println del test
Paso 5: Usar NotificationManagerCompat en MessagingService
Paso 6: Ejecutar lint y corregir warnings
Paso 7: Ejecutar tests
```

Cada paso debe ir seguido de `./gradlew assembleDevDebug` para verificar.

---

## Comandos de Verificación

```bash
# Compilar
./gradlew assembleDevDebug

# Lint
./gradlew lintDevDebug

# Tests unitarios
./gradlew testDevDebugUnitTest
```
