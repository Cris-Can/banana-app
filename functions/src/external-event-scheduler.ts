import * as admin from "firebase-admin";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { geminiApiKey, groqApiKey, aiEnrichEvent } from "./ai-extractor";
import { extractEventsFromPage, fetchWithTimeout } from "./external-event-scraper";
import { normalizeUrl } from "./external-event-extractors";
import { buildEventDocument, requireAdmin, SCHEDULE_TZ } from "./external-event-sources";

const db = admin.firestore();

export const scheduledCheckExternalSources = onSchedule(
  { schedule: "every 6 hours", timeZone: SCHEDULE_TZ, secrets: [geminiApiKey, groqApiKey] },
  async () => {
    console.log("[SCHEDULER] Starting external source check...");

    // Leer modelo desde config de Firestore (o default)
    let model = "gemini-flash";
    try {
      const configDoc = await db.collection("config").doc("ai").get();
      if (configDoc.exists) {
        model = configDoc.data()?.defaultModel || "gemini-flash";
      }
    } catch { /* usar default */ }
    console.log(`[SCHEDULER] Using AI model: ${model}`);

    const sourcesSnap = await db.collection("external_sources")
      .where("isActive", "==", true)
      .get();

    if (sourcesSnap.empty) {
      console.log("[SCHEDULER] No active sources.");
      return;
    }

    let processedCount = 0;
    let newEventsCount = 0;
    let aiCount = 0;

    for (const sourceDoc of sourcesSnap.docs) {
      const source = sourceDoc.data() as { url: string; name?: string };
      console.log(`[SCHEDULER] Checking source: ${source.name || source.url}`);

      try {
        const scrapedEvents = await extractEventsFromPage(source.url);
        let newCount = 0;

        for (const scraped of scrapedEvents) {
          const needsEnrichment = !scraped.title || !scraped.startAt || !scraped.location || !scraped.category || scraped.category === "OTRO";
          if (needsEnrichment) {
            const html = await fetchWithTimeout(source.url, 15000);
            if (html) {
              const enriched = await aiEnrichEvent(html, scraped as any, model);
              if (enriched.title) scraped.title = enriched.title;
              if (enriched.startAt != null) scraped.startAt = enriched.startAt;
              if (enriched.location) scraped.location = enriched.location;
              if (enriched.category) scraped.category = enriched.category;
              if (enriched.price) (scraped as any).price = enriched.price;
              if (enriched.imageUrl) scraped.imageUrl = enriched.imageUrl;
              aiCount++;
            }
          }

          scraped.eventUrl = normalizeUrl(scraped.eventUrl);
          const dupes = await db.collection("pending_external_events")
            .where("eventUrl", "==", scraped.eventUrl)
            .limit(1)
            .get();

          if (!dupes.empty) continue;

          const docData = await buildEventDocument(scraped, sourceDoc.id, source.url);
          await db.collection("pending_external_events").add(docData);
          newCount++;
        }

        await sourceDoc.ref.update({
          lastCheckedAt: admin.firestore.FieldValue.serverTimestamp(),
        });

        newEventsCount += newCount;
        processedCount++;
        console.log(`[SCHEDULER] ${newCount} new events from ${source.name || source.url}`);
        console.log(`[SCHEDULER] Progress: ${processedCount}/${sourcesSnap.size} sources, ${newEventsCount} new events, ${aiCount} AI enrichments`);
      } catch (error) {
        processedCount++;
        console.error(`[SCHEDULER] Error processing source ${sourceDoc.id}:`, error);
        console.log(`[SCHEDULER] Progress: ${processedCount}/${sourcesSnap.size} sources, ${newEventsCount} new events, ${aiCount} AI enrichments`);
      }
    }

    console.log(`[SCHEDULER] Finished. ${newEventsCount} new events, ${aiCount} AI enrichments`);
  }
);

export const runSchedulerNow = onCall({ secrets: [geminiApiKey, groqApiKey] }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  await requireAdmin(request.auth.uid);

  const model = request.data?.model || "gemini-flash";
  console.log(`[RUN_SCHEDULER_NOW] Manual trigger by admin: ${request.auth.uid}, model: ${model}`);

  const sourcesSnap = await db.collection("external_sources")
    .where("isActive", "==", true)
    .get();

  if (sourcesSnap.empty) {
    console.log("[RUN_SCHEDULER_NOW] No active sources.");
    return { success: true, checkedCount: 0, newEventsCount: 0 };
  }

  let newEventsCount = 0;
  let aiCount = 0;
  let processedCount = 0;

  for (const sourceDoc of sourcesSnap.docs) {
    const source = sourceDoc.data() as { url: string; name?: string };
    console.log(`[RUN_SCHEDULER_NOW] Checking source: ${source.name || source.url}`);

    try {
      const scrapedEvents = await extractEventsFromPage(source.url);
      let newCount = 0;

      for (const scraped of scrapedEvents) {
        // Nivel 4: IA enrichment for missing critical fields
        const needsEnrichment = !scraped.title || !scraped.startAt || !scraped.location;
        if (needsEnrichment) {
          const html = await fetchWithTimeout(source.url, 15000);
          if (html) {
            const enriched = await aiEnrichEvent(html, scraped as any, model);
            if (enriched.title) scraped.title = enriched.title;
            if (enriched.startAt != null) scraped.startAt = enriched.startAt;
            if (enriched.location) scraped.location = enriched.location;
            if (enriched.category) scraped.category = enriched.category;
            if (enriched.price) (scraped as any).price = enriched.price;
            if (enriched.imageUrl) scraped.imageUrl = enriched.imageUrl;
            aiCount++;
          }
        }

        scraped.eventUrl = normalizeUrl(scraped.eventUrl);
        const dupes = await db.collection("pending_external_events")
          .where("eventUrl", "==", scraped.eventUrl)
          .limit(1)
          .get();

        if (!dupes.empty) continue;

        const docData = await buildEventDocument(scraped, sourceDoc.id, source.url);
        await db.collection("pending_external_events").add(docData);
        newCount++;
      }

      await sourceDoc.ref.update({
        lastCheckedAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      newEventsCount += newCount;
      processedCount++;
      console.log(`[RUN_SCHEDULER_NOW] ${newCount} new events from ${source.name || source.url}`);
    } catch (error) {
      processedCount++;
      console.error(`[RUN_SCHEDULER_NOW] Error processing source ${sourceDoc.id}:`, error);
    }
  }

  console.log(`[RUN_SCHEDULER_NOW] Finished. Sources: ${sourcesSnap.size}, new events: ${newEventsCount}, AI enrichments: ${aiCount}`);
  return { success: true, checkedCount: sourcesSnap.size, newEventsCount, aiEnrichments: aiCount };
});
