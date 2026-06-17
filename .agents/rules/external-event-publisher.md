---
description: Publica eventos externos en Banana desde links. Usa cuando el usuario pase links de eventos (Eventbrite, Meetup, redes sociales) para crear eventos en Firestore con la cuenta bot y activar notificaciones por zona.
---

# External Event Publisher Agent

Publicas eventos externos en la app Banana. El sistema tiene dos modos:

1. **Automático (scheduler)**: cada 6h revisa las fuentes registradas y extrae eventos nuevos → quedan en `pending_external_events` para que el admin apruebe
2. **Manual**: el usuario pasa un link directo y lo publicas al instante via `publishExternalEvent`

En ambos casos, al aprobar/crear el evento, el trigger `onEventCreatedNotifyZone` notifica automáticamente a usuarios por intereses y ubicación.

## Setup inicial (una vez)

```ts
// 1. Crear cuenta bot
await firebase.functions().httpsCallable('setupExternalEventBot')()

// 2. Agregar fuentes a monitorear
await firebase.functions().httpsCallable('addExternalSource')({
  url: 'https://site-ejemplo.cl/eventos',
  name: 'Eventos de Ejemplo'
})
```

## Flujo automático

1. Admin agrega fuentes vía `addExternalSource`
2. `scheduledCheckExternalSources` corre cada 6h y extrae eventos nuevos
3. Admin revisa pendientes con `listPendingExternalEvents`
4. Admin aprueba (`approveExternalEvent`) con overrides de ubicación/fecha
5. Al aprobar → evento en Firestore → notificaciones automáticas

## Funciones disponibles

| Función | Propósito |
|---|---|
| `setupExternalEventBot` | Crear cuenta bot (ejecutar 1 vez) |
| `addExternalSource` | Agregar URL de sitio a monitorear |
| `removeExternalSource` | Eliminar fuente |
| `listExternalSources` | Listar fuentes registradas |
| `listPendingExternalEvents` | Ver eventos pendientes |
| `approveExternalEvent` | Aprobar pendiente → crear evento |
| `rejectExternalEvent` | Rechazar pendiente |
| `publishExternalEvent` | Publicar evento manual directo |

## Manual: publishExternalEvent

Usar cuando el usuario pase un link directo de un evento.

| Campo | Obligatorio | Notas |
|---|---|---|
| `title` | Sí | Título |
| `region` | Sí | Región |
| `commune` | Sí | Comuna |
| `latitude` | Sí | Latitud |
| `longitude` | Sí | Longitud |
| `startAt` | Sí | Timestamp ms inicio |
| `url` | No | Link original |
| `description` | No | Descripción |
| `category` | No | Categoría |
| `eventType` | No | DEPORTES, SOCIAL, CULTURAL, EDUCATIVO, JUEGOS, GASTRONOMIA, AIRE_LIBRE, OTRO |
| `endAt` | No | Default: startAt + 2h |
| `notificationRange` | No | COMMUNE, REGION, NATIONAL |

```ts
await firebase.functions().httpsCallable('publishExternalEvent')({
  url: 'https://ejemplo.com/evento',
  title: 'Torneo de Fútbol',
  region: 'Metropolitana',
  commune: 'Santiago',
  latitude: -33.4489,
  longitude: -70.6693,
  startAt: Date.parse('2026-06-15T18:00:00-04:00'),
})
```

## Automático: aprobar pendiente

```ts
// Ver pendientes
const { events } = await firebase.functions().httpsCallable('listPendingExternalEvents')()

// Aprobar con datos faltantes (ubicación obligatoria)
await firebase.functions().httpsCallable('approveExternalEvent')({
  pendingId: events[0].id,
  overrides: {
    region: 'Metropolitana',
    commune: 'Santiago',
    latitude: -33.4489,
    longitude: -70.6693,
    startAt: Date.parse('2026-06-15T18:00:00-04:00'),
    category: 'Música',
  }
})

// Rechazar
await firebase.functions().httpsCallable('rejectExternalEvent')({
  pendingId: events[0].id,
  reason: 'No corresponde a un evento real',
})
```

## Notas importantes

- El scraper usa cheerio y Open Graph tags; no todos los sitios tienen datos estructurados
- `latitude`/`longitude` son obligatorias al aprobar (el scraper no las detecta)
- Las notificaciones se disparan automáticamente al crear el evento en Firestore
