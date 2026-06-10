import * as admin from "firebase-admin";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as cheerio from "cheerio";
import { geminiApiKey, groqApiKey, callAI } from "./ai-extractor";
import { encodeGeohash } from "./external-event-geohash";
import { sanitizeHtml, normalizeUrl } from "./external-event-extractors";
import { fetchWithTimeout, tryScrapeUrl } from "./external-event-scraper";
import { placesApiKey, geocodeLocation } from "./external-event-geocoder";
import { ensureBotUser, requireAdmin } from "./external-event-sources";

const db = admin.firestore();

const CATEGORY_TO_EVENT_TYPE: Record<string, string> = {
  "CONCIERTO": "CULTURAL",
  "FESTIVAL": "CULTURAL",
  "TEATRO": "CULTURAL",
  "CINE": "CULTURAL",
  "MUSICA": "CULTURAL",
  "ARTE": "CULTURAL",
  "CHARLA": "EDUCATIVO",
  "TALLER": "EDUCATIVO",
  "DEPORTE": "DEPORTES",
  "FERIA": "SOCIAL",
  "GASTRONOMIA": "GASTRONOMIA",
};

function mapCategoryToEventType(category: string | null | undefined): string {
  return (category && CATEGORY_TO_EVENT_TYPE[category]) || "OTRO";
}

interface ExternalEventInput {
  url?: string;
  title: string;
  description?: string;
  category?: string;
  eventType?: string;
  country?: string;
  region: string;
  commune: string;
  latitude: number;
  longitude: number;
  address?: string;
  startAt: number;
  endAt?: number;
  expiresAt?: number;
  imageUrl?: string;
  isPublic?: boolean;
  maxParticipants?: number;
  notificationRange?: "COMMUNE" | "REGION" | "NATIONAL";
  isAdultContent?: boolean;
}

export const listPendingExternalEvents = onCall(async (request) => {
  try {
    if (!request.auth) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
    await requireAdmin(request.auth.uid);

    const status = request.data?.status || "pending";
    const snap = await db.collection("pending_external_events")
      .where("status", "==", status)
      .limit(200)
      .get();

    const events = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
    return { success: true, events, count: events.length };
  } catch (error: any) {
    console.error("Error in listPendingExternalEvents:", error);
    return { success: false, error: String(error?.message || error), events: [], count: 0 };
  }
});

export const approveExternalEvent = onCall({ secrets: [placesApiKey] }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  await requireAdmin(request.auth.uid);

  const { pendingId, overrides } = request.data;
  if (!pendingId) throw new HttpsError("invalid-argument", "pendingId requerido.");

  const pendingRef = db.collection("pending_external_events").doc(pendingId);
  const pendingDoc = await pendingRef.get();

  if (!pendingDoc.exists) {
    throw new HttpsError("not-found", "Evento pendiente no encontrado.");
  }

  const pending = pendingDoc.data()!;
  if (pending.status !== "pending") {
    throw new HttpsError("failed-precondition", `El evento ya fue ${pending.status}.`);
  }

  if (!pending.startAt) {
    throw new HttpsError("failed-precondition",
      "El evento no tiene fecha. Debes proporcionarla en overrides.startAt.");
  }

  const merged = { ...pending, ...(overrides || {}) };

  // Si el admin no proveyó coordenadas pero hay location text, geocodificar
  if ((merged.latitude == null || merged.longitude == null) && merged.location) {
    const geo = await geocodeLocation(merged.location);
    merged.region = merged.region || geo.region;
    merged.commune = merged.commune || geo.commune;
    merged.latitude = merged.latitude ?? geo.latitude;
    merged.longitude = merged.longitude ?? geo.longitude;
    merged.address = merged.address || geo.address;
  }

  const startAt = merged.startAt;
  const endAt = merged.endAt || startAt + 7200000;

  if (!merged.region) {
    throw new HttpsError("invalid-argument",
      "La región es obligatoria. Pasa region en overrides.");
  }
  if (!merged.commune) {
    throw new HttpsError("invalid-argument",
      "La comuna es obligatoria. Pasa commune en overrides.");
  }
  if (merged.latitude == null || merged.longitude == null) {
    throw new HttpsError("invalid-argument",
      "Las coordenadas son obligatorias. Pasa latitude/longitude en overrides.");
  }

  await ensureBotUser();

  const eventRef = db.collection("events").doc();
  const eventId = eventRef.id;
  const geohash = encodeGeohash(merged.latitude, merged.longitude, 9);

  const eventData = {
    id: eventId,
    creatorId: request.auth.uid,
    imageUrl: merged.imageUrl || null,
    title: merged.title,
    description: merged.description || "",
    category: merged.category || "OTRO",
    eventType: merged.eventType || mapCategoryToEventType(merged.category),
    country: merged.country || "Chile",
    region: merged.region,
    commune: merged.commune,
    address: merged.address || merged.commune,
    latitude: merged.latitude,
    longitude: merged.longitude,
    exactLatitude: merged.latitude,
    exactLongitude: merged.longitude,
    exactAddress: merged.address || merged.commune,
    eventTimestamp: startAt,
    createdAt: Date.now(),
    startAt: startAt,
    endAt: endAt,
    expiresAt: merged.expiresAt || endAt,
    maxParticipants: merged.maxParticipants ?? 100,
    isPublic: merged.isPublic !== false,
    approvalRequired: merged.isPublic === false,
    joinQuestions: [],
    status: "OPEN",
    cancelledAt: null,
    cancelReason: null,
    approvedParticipants: [],
    pendingRequests: [],
    rejectedParticipants: [],
    minimumScore: null,
    ratingDeadline: null,
    canBeRated: false,
    isBoosted: false,
    boostExpiry: 0,
    geohash: geohash,
    notificationRange: merged.notificationRange || "COMMUNE",
    isAdultContent: merged.isAdultContent === true,
    fcmToken: null,
    notifyEventsByCommune: false,
    sourceUrl: merged.eventUrl || null,
  };

  await eventRef.set(eventData);

  await pendingRef.update({
    status: "approved",
    eventId: eventId,
    reviewedById: request.auth.uid,
    reviewedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  console.log(`[EXTERNAL] Approved -> Event ${eventId}: "${merged.title}"`);

  return {
    success: true,
    eventId,
    title: merged.title,
    message: `Evento "${merged.title}" publicado.`,
  };
});

export const rejectExternalEvent = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  await requireAdmin(request.auth.uid);

  const { pendingId, reason } = request.data;
  if (!pendingId) throw new HttpsError("invalid-argument", "pendingId requerido.");

  const pendingRef = db.collection("pending_external_events").doc(pendingId);
  const pendingDoc = await pendingRef.get();

  if (!pendingDoc.exists) {
    throw new HttpsError("not-found", "Evento pendiente no encontrado.");
  }

  const pending = pendingDoc.data()!;
  if (pending.status !== "pending") {
    throw new HttpsError("failed-precondition", `El evento ya fue ${pending.status}.`);
  }

  await pendingRef.update({
    status: "rejected",
    rejectionReason: reason || null,
    reviewedById: request.auth.uid,
    reviewedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  return { success: true, message: "Evento rechazado." };
});

export const publishExternalEvent = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  await requireAdmin(request.auth.uid);

  const input: ExternalEventInput = request.data;
  if (!input) throw new HttpsError("invalid-argument", "Datos requeridos.");

  let title = input.title;
  let description = input.description || "";
  let imageUrl = input.imageUrl;

  if (input.url) {
    const scraped = await tryScrapeUrl(input.url);
    if (!title && scraped.title) title = scraped.title;
    if (!description && scraped.description) description = scraped.description;
    if (!imageUrl && scraped.imageUrl) imageUrl = scraped.imageUrl;
  }

  if (!title) throw new HttpsError("invalid-argument", "Título requerido.");
  if (!input.region) throw new HttpsError("invalid-argument", "Región requerida.");
  if (!input.commune) throw new HttpsError("invalid-argument", "Comuna requerida.");
  if (input.latitude == null || input.longitude == null) throw new HttpsError("invalid-argument", "Coordenadas requeridas.");
  if (!input.startAt) throw new HttpsError("invalid-argument", "Fecha de inicio requerida.");

  await ensureBotUser();

  const eventRef = db.collection("events").doc();
  const eventId = eventRef.id;
  const endAt = input.endAt || input.startAt + 7200000;
  const geohash = encodeGeohash(input.latitude, input.longitude, 9);

  await eventRef.set({
    id: eventId,
    creatorId: request.auth.uid,
    imageUrl: imageUrl || null,
    title,
    description,
    category: input.category || "OTRO",
    eventType: input.eventType || mapCategoryToEventType(input.category),
    country: input.country || "Chile",
    region: input.region,
    commune: input.commune,
    address: input.address || input.commune,
    latitude: input.latitude,
    longitude: input.longitude,
    exactLatitude: input.latitude,
    exactLongitude: input.longitude,
    exactAddress: input.address || input.commune,
    eventTimestamp: input.startAt,
    createdAt: Date.now(),
    startAt: input.startAt,
    endAt: endAt,
    expiresAt: input.expiresAt || endAt,
    maxParticipants: input.maxParticipants ?? 100,
    isPublic: input.isPublic ?? true,
    approvalRequired: !(input.isPublic ?? true),
    joinQuestions: [],
    status: "OPEN",
    cancelledAt: null,
    cancelReason: null,
    approvedParticipants: [],
    pendingRequests: [],
    rejectedParticipants: [],
    minimumScore: null,
    ratingDeadline: null,
    canBeRated: false,
    isBoosted: false,
    boostExpiry: 0,
    geohash: geohash,
    notificationRange: input.notificationRange || "COMMUNE",
    isAdultContent: input.isAdultContent ?? false,
    fcmToken: null,
    notifyEventsByCommune: false,
    sourceUrl: input.url || null,
  });

  console.log(`[EXTERNAL] Manual event created: ${eventId} - "${title}"`);

  return { success: true, eventId, title, message: `Evento "${title}" publicado.` };
});

export const processInstagramUrl = onCall({ secrets: [geminiApiKey, groqApiKey] }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  await requireAdmin(request.auth.uid);

  const { url, model } = request.data;
  if (!url || typeof url !== "string" || url.trim() === "") {
    throw new HttpsError("invalid-argument", "URL de Instagram requerida.");
  }

  const selectedModel = model || "gemini-flash";
  console.log(`[INSTAGRAM] Processing URL: ${url}, model: ${selectedModel}`);

  const normalizedUrl = normalizeUrl(url);
  if (!normalizedUrl.includes("instagram.com")) {
    throw new HttpsError("invalid-argument", "La URL debe ser de instagram.com.");
  }

  // Check for duplicate
  const dupes = await db.collection("pending_external_events")
    .where("eventUrl", "==", normalizedUrl)
    .where("status", "in", ["pending", "approved"])
    .limit(1)
    .get();
  if (!dupes.empty) {
    throw new HttpsError("already-exists", "Este post de Instagram ya fue procesado.");
  }

  // Step 1: Try oEmbed API
  let caption = "";
  let thumbnailUrl = "";

  try {
    const token = process.env.INSTAGRAM_APP_TOKEN || "";
    const oembedUrl = token
      ? `https://graph.facebook.com/v21.0/instagram_oembed?url=${encodeURIComponent(normalizedUrl)}&access_token=${token}`
      : `https://api.instagram.com/oembed?url=${encodeURIComponent(normalizedUrl)}`;

    const res = await fetch(oembedUrl, { signal: AbortSignal.timeout(8000) });
    if (res.ok) {
      const data = await res.json() as any;
      caption = data.title || data.caption || "";
      thumbnailUrl = data.thumbnail_url || data.thumbnail || "";
      console.log(`[INSTAGRAM] oEmbed success: "${caption.substring(0, 80)}..."`);
    } else {
      // Fallback: fetch page directly
      const pageHtml = await fetchWithTimeout(normalizedUrl, 10000);
      if (pageHtml) {
        const $ = cheerio.load(pageHtml);
        caption = $('meta[property="og:description"]').attr("content")
          || $('meta[name="description"]').attr("content") || "";
        thumbnailUrl = $('meta[property="og:image"]').attr("content") || "";
        console.log(`[INSTAGRAM] Fallback fetch: "${caption.substring(0, 80)}..."`);
      }
    }
  } catch (e) {
    console.warn(`[INSTAGRAM] Fetch failed for ${normalizedUrl}:`, e);
  }

  if (!caption && !thumbnailUrl) {
    throw new HttpsError("unavailable", "No se pudo obtener información del post. Verifica que la URL sea pública.");
  }

  // Step 2: Call AI to extract event info
  const aiPrompt = `Eres un extractor de datos de eventos. Dado el texto de un post de Instagram, extrae la información del evento que anuncia (si es que anuncia un evento).
Si el post NO anuncia un evento, responde solo: {"isEvent": false}

Reglas:
- title: nombre del evento
- startAt: fecha y hora en timestamp millis. Si ves "15 de Junio a las 19:00", calcula el timestamp asumiendo el año actual.
- location: lugar del evento si se menciona
- description: descripción corta
- category: una de: CONCIERTO, FESTIVAL, CHARLA, TALLER, TEATRO, CINE, DEPORTE, FERIA, MUSICA, ARTE, GASTRONOMIA, OTRO
- price: precio si se menciona (ej: "$15.000", "Gratis")
- imageUrl: "${thumbnailUrl}"

Responde SOLO con JSON:

{
  "isEvent": boolean,
  "title": string | null,
  "startAt": number | null,
  "location": string | null,
  "description": string | null,
  "category": string | null,
  "price": string | null,
  "imageUrl": string | null
}

Texto del post:
${caption}`;

  const raw = await callAI(aiPrompt, selectedModel);
  if (!raw) {
    throw new HttpsError("internal", "La IA no respondió. Reintenta más tarde.");
  }

  // Parse JSON response
  const jsonMatch = raw.match(/\{[\s\S]*\}/);
  if (!jsonMatch) {
    throw new HttpsError("internal", "Respuesta de IA inválida.");
  }

  let parsed: any;
  try {
    parsed = JSON.parse(jsonMatch[0]);
  } catch {
    throw new HttpsError("internal", "Error al parsear respuesta de IA.");
  }

  if (parsed.isEvent === false) {
    return {
      success: true,
      isEvent: false,
      message: "El post no parece anunciar un evento.",
      caption: caption,
    };
  }

  const title = parsed.title || null;
  if (!title) {
    const docData = {
      sourceId: "instagram",
      sourceUrl: normalizedUrl,
      eventUrl: normalizedUrl,
      title: "Post de Instagram (sin título)",
      description: parsed.description || caption || "",
      imageUrl: thumbnailUrl || null,
      startAt: parsed.startAt ?? null,
      location: parsed.location || null,
      category: parsed.category || "OTRO",
      price: parsed.price || null,
      region: null,
      commune: null,
      latitude: null,
      longitude: null,
      address: null,
      isPublic: null,
      notificationRange: "COMMUNE",
      status: "pending",
      reviewedById: null,
      reviewedAt: null,
      rejectionReason: null,
      scrapedAt: admin.firestore.FieldValue.serverTimestamp(),
    };
    const ref = await db.collection("pending_external_events").add(docData);
    console.log(`[INSTAGRAM] Created pending event (no title): ${ref.id}`);
    return {
      success: true,
      isEvent: true,
      pendingId: ref.id,
      title: null,
      message: "Post procesado pero no se pudo extraer título. Revisa los pendientes.",
    };
  }

  // Save as pending event
  const docData = {
    sourceId: "instagram",
    sourceUrl: normalizedUrl,
    eventUrl: normalizedUrl,
    title: sanitizeHtml(title),
    description: sanitizeHtml(parsed.description || caption || ""),
    imageUrl: thumbnailUrl || null,
    startAt: parsed.startAt ?? null,
    location: parsed.location || null,
    category: parsed.category || "OTRO",
    price: parsed.price || null,
    region: null,
    commune: null,
    latitude: null,
    longitude: null,
    address: null,
    isPublic: null,
    notificationRange: "COMMUNE",
    status: "pending",
    reviewedById: null,
    reviewedAt: null,
    rejectionReason: null,
    scrapedAt: admin.firestore.FieldValue.serverTimestamp(),
  };

  const ref = await db.collection("pending_external_events").add(docData);
  console.log(`[INSTAGRAM] Created pending event: ${ref.id} - "${title}"`);

  return {
    success: true,
    isEvent: true,
    pendingId: ref.id,
    title,
    message: `Post procesado: "${title}". Revisa los pendientes para aprobar.`,
    caption: caption,
  };
});

export const listCreatedExternalEvents = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  await requireAdmin(request.auth.uid);

  const { limit: max = 100 } = request.data || {};

  try {
    const snap = await db.collection("events")
      .orderBy("createdAt", "desc")
      .limit(max * 2)
      .get();

    const events = snap.docs
      .map((d) => {
        const data = d.data();
        return {
          id: d.id,
          title: data.title || "Sin título",
          startAt: data.startAt || null,
          endAt: data.endAt || null,
          expiresAt: data.expiresAt || null,
          status: data.status || "UNKNOWN",
          createdAt: data.createdAt || null,
          sourceUrl: data.sourceUrl || null,
          creatorId: data.creatorId || null,
        };
      })
      .filter((e) => e.sourceUrl != null)
      .slice(0, max);

    return { success: true, events, count: events.length };
  } catch (error: any) {
    console.error("Error in listCreatedExternalEvents:", error);
    return { success: false, error: String(error?.message || error), events: [], count: 0 };
  }
});

export const deleteExternalEvent = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  await requireAdmin(request.auth.uid);

  const { eventId, hard } = request.data;
  if (!eventId) throw new HttpsError("invalid-argument", "eventId requerido.");

  const eventRef = db.collection("events").doc(eventId);
  const eventDoc = await eventRef.get();

  if (!eventDoc.exists) {
    throw new HttpsError("not-found", "Evento no encontrado.");
  }

  if (hard === true) {
    // Hard-delete: eliminar documento completamente
    await eventRef.delete();
    console.log(`[ADMIN] Hard-deleted event ${eventId}`);
    return { success: true, message: "Evento eliminado permanentemente." };
  }

  // Soft-delete: marcar como CANCELLED
  await eventRef.update({
    status: "CANCELLED",
    cancelledAt: Date.now(),
    cancelReason: "Eliminado por administrador",
  });

  // También marcar el pending asociado si existe
  const pendingSnap = await db.collection("pending_external_events")
    .where("eventId", "==", eventId)
    .limit(1)
    .get();

  if (!pendingSnap.empty) {
    await pendingSnap.docs[0].ref.update({
      status: "deleted",
      deletedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
  }

  console.log(`[ADMIN] Soft-deleted event ${eventId}`);
  return { success: true, message: "Evento cancelado." };
});
