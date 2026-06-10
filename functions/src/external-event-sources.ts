import * as admin from "firebase-admin";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { fetchWithTimeout } from "./external-event-scraper";
import { sanitizeHtml } from "./external-event-extractors";
import { ScrapedEvent } from "./external-event-scraper";

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

const db = admin.firestore();
const BOT_UID = "external_event_bot";
const MAX_EVENT_LINKS_PER_SOURCE = 20;
const SCHEDULE_TZ = "America/Santiago";

async function ensureBotUser(): Promise<void> {
  const botRef = db.collection("users").doc(BOT_UID);
  const botDoc = await botRef.get();
  if (botDoc.exists) return;

  await botRef.set({
    uid: BOT_UID,
    nickname: "Eventos Externos",
    email: "bot@eventosexternos.app",
    isGold: true,
    isFounder: false,
    subscriptionType: "GOLD",
    subscriptionExpiry: 0,
    admin: false,
    profilePictureUrl: null,
    age: null,
    commune: "",
    region: "",
    country: "",
    latitude: null,
    longitude: null,
    interests: [],
    subscribedCategories: [],
    friends: [],
    friendRequestsSent: [],
    friendRequestsReceived: [],
    friendCount: 0,
    pendingRequestsSentCount: 0,
    pendingRequestsReceivedCount: 0,
    blockedUsers: [],
    eventsCreatedLifetime: 0,
    eventsCreatedInCycle: 0,
    joinRequestsInCycle: 0,
    currentCycleStartDate: 0,
    adEventsUnlocked: 0,
    adsWatchedProgress: 0,
    eventsRequestedCount: 0,
    averageScore: 5.0,
    totalRatings: 0,
    ratingCredits: 0,
    ratingCreditsExpiry: 0,
    isVerified: true,
    notifyByInterest: true,
    notifyEventsByCommune: true,
    notifyEventWall: true,
    searchRadiusKm: 50,
    identityVerified: true,
    createdAt: admin.firestore.Timestamp.fromMillis(0),
  });
  console.log(`[BOT] Created bot user: ${BOT_UID}`);
}

async function requireAdmin(uid: string): Promise<void> {
  const doc = await db.collection("users").doc(uid).get();
  if (!doc.exists || doc.data()?.admin !== true) {
    throw new HttpsError("permission-denied", "Solo administradores.");
  }
}

async function buildEventDocument(scraped: ScrapedEvent, sourceId: string, sourceUrl: string): Promise<any> {
  return {
    sourceId,
    sourceUrl,
    eventUrl: scraped.eventUrl,
    title: sanitizeHtml(scraped.title || "Sin título"),
    description: sanitizeHtml(scraped.description || ""),
    imageUrl: scraped.imageUrl || null,
    startAt: scraped.startAt ?? null,
    location: scraped.location || null,
    category: scraped.category || "OTRO",
    price: scraped.price || null,
    eventType: CATEGORY_TO_EVENT_TYPE[scraped.category || ""] || null,
    country: null,
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
}

export const setupExternalEventBot = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  await requireAdmin(request.auth.uid);
  await ensureBotUser();
  return {
    success: true,
    message: `Bot user ${BOT_UID} creado/verificado.`,
    botUid: BOT_UID,
  };
});

export const addExternalSource = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  await requireAdmin(request.auth.uid);

  const { url, name } = request.data;
  if (!url || typeof url !== "string") {
    throw new HttpsError("invalid-argument", "URL requerida.");
  }

  const existing = await db.collection("external_sources")
    .where("url", "==", url)
    .limit(1)
    .get();

  if (!existing.empty) {
    throw new HttpsError("already-exists", "Esta fuente ya está registrada.");
  }

  // Verificar que la URL sea alcanzable antes de guardarla
  const testHtml = await fetchWithTimeout(url, 8000);
  if (!testHtml) {
    throw new HttpsError("unavailable", "La URL no responde. Verifica que sea accesible.");
  }

  const ref = await db.collection("external_sources").add({
    url,
    name: name || url,
    isActive: true,
    lastCheckedAt: null,
    createdBy: request.auth.uid,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  return { success: true, id: ref.id, message: `Fuente "${name || url}" agregada.` };
});

export const removeExternalSource = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  await requireAdmin(request.auth.uid);

  const { id } = request.data;
  if (!id) throw new HttpsError("invalid-argument", "ID requerido.");

  await db.collection("external_sources").doc(id).delete();
  return { success: true, message: "Fuente eliminada." };
});

export const listExternalSources = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  await requireAdmin(request.auth.uid);

  const snap = await db.collection("external_sources")
    .orderBy("createdAt", "desc")
    .limit(200)
    .get();

  const sources = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
  return { success: true, sources };
});

export { BOT_UID, MAX_EVENT_LINKS_PER_SOURCE, SCHEDULE_TZ };
export { ensureBotUser, requireAdmin, buildEventDocument };
