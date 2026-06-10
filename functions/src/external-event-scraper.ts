import * as cheerio from "cheerio";
import * as chrono from "chrono-node";
import {
  sanitizeHtml,
  extractCategoryFromMeta,
  extractCategoryFromText,
  extractDateFromText,
  extractLocationFromText,
  extractPriceFromText,
  looksLikeEventUrl,
  normalizeUrl,
  tryParseDate,
  tryParseIsoOrThrow,
} from "./external-event-extractors";

const MAX_EVENT_LINKS_PER_SOURCE = 20;

export interface ScrapedEvent {
  eventUrl: string;
  title?: string;
  description?: string;
  imageUrl?: string;
  startAt?: number;
  location?: string;
  category?: string;
  price?: string;
}

export async function fetchWithTimeout(url: string, timeoutMs = 10000): Promise<string | null> {
  try {
    const res = await fetch(url, {
      headers: { "User-Agent": "Mozilla/5.0 (compatible; PanoramasBot/1.0)" },
      signal: AbortSignal.timeout(timeoutMs),
    });
    if (!res.ok) return null;
    return await res.text();
  } catch {
    return null;
  }
}

export function scrapeSinglePage(html: string, url: string): ScrapedEvent {
  const $ = cheerio.load(html);

  const ogTitle = $('meta[property="og:title"]').attr("content");
  const ogDesc = $('meta[property="og:description"]').attr("content");
  const ogImage = $('meta[property="og:image"]').attr("content");
  const htmlTitle = $("title").first().text().trim();

  let startAt: number | undefined;

  const ogStart = $('meta[property="event:start_time"]').attr("content");

  if (ogStart) {
    const ts = tryParseIsoOrThrow(ogStart);
    if (ts) startAt = ts;
  }

  if (!startAt) {
    const jsonldScripts = $('script[type="application/ld+json"]').toArray();
    for (const el of jsonldScripts) {
      try {
        const parsed = JSON.parse($(el).text());
        const items = parsed["@graph"] || [parsed];
        for (const item of items) {
          if (item["@type"] === "Event" || item["@type"] === "Evento") {
            if (!startAt && item.startDate) {
              const ts = tryParseIsoOrThrow(item.startDate);
              if (ts) startAt = ts;
            }
          }
        }
      } catch { /* skip invalid JSON-LD */ }
    }
  }

  let location: string | undefined;
  const ogLoc = $('meta[property="event:location"]').attr("content")
    || $('meta[property="place:location"]').attr("content");
  if (ogLoc) location = ogLoc;

  return {
    eventUrl: url,
    title: ogTitle || htmlTitle || undefined,
    description: ogDesc || undefined,
    imageUrl: ogImage || undefined,
    startAt,
    location,
  };
}

function extractJsonLdCategory(type: any, item: any): string | undefined {
  const explicitCat = typeof item.category === "string" ? item.category : (typeof item.eventType === "string" ? item.eventType : null);
  if (explicitCat) {
    const upper = explicitCat.toUpperCase();
    if (["CONCIERTO", "FESTIVAL", "CHARLA", "TALLER", "TEATRO", "CINE", "DEPORTE", "FERIA", "MUSICA", "ARTE", "GASTRONOMIA", "OTRO"].includes(upper)) {
      return upper;
    }
  }
  const types = Array.isArray(type) ? type : [type];
  for (const t of types) {
    if (typeof t !== "string") continue;
    const lower = t.toLowerCase();
    if (lower.includes("musicfestival") || lower.includes("musicevent")) return "MUSICA";
    if (lower.includes("festival")) return "FESTIVAL";
    if (lower.includes("theater") || lower.includes("comedy")) return "TEATRO";
    if (lower.includes("sports") || lower.includes("game")) return "DEPORTE";
    if (lower.includes("exhibition") || lower.includes("visualarts")) return "ARTE";
    if (lower.includes("food") || lower.includes("restaurant")) return "GASTRONOMIA";
    if (lower.includes("business") || lower.includes("education") || lower.includes("conference")) return "CHARLA";
    if (lower.includes("workshop") || lower.includes("course")) return "TALLER";
    if (lower.includes("dance") || lower.includes("party")) return "CONCIERTO";
    if (lower.includes("movie") || lower.includes("film")) return "CINE";
  }
  return undefined;
}

export async function extractEventsFromPage(sourceUrl: string): Promise<ScrapedEvent[]> {
  const html = await fetchWithTimeout(sourceUrl, 15000);
  if (!html) {
    console.warn(`[SCHEDULER] Could not fetch source: ${sourceUrl}`);
    return [];
  }

  const $ = cheerio.load(html);

  // ──────────────────────────────────────────────────
  // STEP 1: JSON-LD multi-event (most reliable)
  // ──────────────────────────────────────────────────
  const jsonldResults: ScrapedEvent[] = [];
  const jsonldScripts = $('script[type="application/ld+json"]').toArray();
  for (const el of jsonldScripts) {
    try {
      const parsed = JSON.parse($(el).text());
      const items: any[] = Array.isArray(parsed["@graph"])
        ? parsed["@graph"]
        : [parsed];

      for (const item of items) {
        const type = item["@type"];
        const isEvent = type === "Event"
          || type === "Evento"
          || (Array.isArray(type) && type.some((t: string) =>
            t.toLowerCase().includes("event")));
        if (!isEvent) continue;

        const title: string | undefined = item.name || undefined;
        const description: string | undefined = item.description || undefined;
        const imageUrl: string | undefined =
          (typeof item.image === "string" ? item.image : item.image?.url) || undefined;
        const location: string | undefined =
          item.location?.name || item.location?.address?.streetAddress || undefined;

        let startAt: number | undefined;
        if (item.startDate) {
          const ts = tryParseDate(item.startDate);
          if (ts !== null) startAt = ts;
        }

        let eventUrl = sourceUrl;
        if (item.url) {
          try { eventUrl = new URL(item.url, sourceUrl).href; } catch { /* keep sourceUrl */ }
        }

        if (title) {
          const category = extractJsonLdCategory(type, item);
          jsonldResults.push({ eventUrl, title, description, imageUrl, startAt, location, category });
        }
      }
    } catch { /* skip invalid JSON-LD */ }
  }

  // Also scan itemListElement (ItemList) if @graph found nothing
  if (jsonldResults.length === 0) {
    for (const el of jsonldScripts) {
      try {
        const parsed = JSON.parse($(el).text());
        const itemListEl: any[] = Array.isArray(parsed["itemListElement"])
          ? parsed["itemListElement"]
          : [];
        for (const listItem of itemListEl) {
          const inner = listItem.item || listItem;
          const type = inner["@type"];
          const isEvent = type === "Event" || type === "Evento"
            || (Array.isArray(type) && type.some((t: string) => t.toLowerCase().includes("event")));
          if (!isEvent) continue;
          const title: string | undefined = inner.name || undefined;
          if (!title) continue;
          let startAt: number | undefined;
          if (inner.startDate) {
            const ts = tryParseDate(inner.startDate);
            if (ts !== null) startAt = ts;
          }
          let eventUrl = sourceUrl;
          if (inner.url) {
            try { eventUrl = new URL(inner.url, sourceUrl).href; } catch { /* keep */ }
          }
          jsonldResults.push({
            eventUrl,
            title,
            description: inner.description || undefined,
            imageUrl: (typeof inner.image === "string" ? inner.image : inner.image?.url) || undefined,
            startAt,
            location: inner.location?.name || inner.location?.address?.streetAddress || undefined,
            category: extractJsonLdCategory(type, inner),
          });
        }
      } catch { /* skip */ }
    }
  }

  if (jsonldResults.length > 0) {
    console.log(`[EXTRACTOR] Step 1 (JSON-LD): found ${jsonldResults.length} events in ${sourceUrl}`);
    return jsonldResults;
  }
  console.log(`[EXTRACTOR] Step 1 (JSON-LD): 0 events, trying Step 2...`);

  // ──────────────────────────────────────────────────
  // STEP 2: Microdata events
  // ──────────────────────────────────────────────────
  const microdataResults: ScrapedEvent[] = [];
  $("[itemscope][itemtype]").each((_, el) => {
    const itemtype = $(el).attr("itemtype") || "";
    if (!itemtype.toLowerCase().includes("event")) return;

    const title = $(el).find('[itemprop="name"]').first().text().trim() || undefined;
    const description = $(el).find('[itemprop="description"]').first().text().trim() || undefined;
    const imageUrl = $(el).find('[itemprop="image"]').first().attr("src")
      || $(el).find('[itemprop="image"]').first().attr("content") || undefined;
    const location = $(el).find('[itemprop="location"] [itemprop="name"]').first().text().trim()
      || $(el).find('[itemprop="location"]').first().text().trim() || undefined;

    let startAt: number | undefined;
    const startDateEl = $(el).find('[itemprop="startDate"]').first();
    const startDateStr = startDateEl.attr("datetime") || startDateEl.attr("content") || startDateEl.text().trim();
    if (startDateStr) {
      const ts = tryParseDate(startDateStr);
      if (ts !== null) startAt = ts;
    }

    let eventUrl = sourceUrl;
    const linkEl = $(el).find("a[href]").first();
    if (linkEl.length) {
      try { eventUrl = new URL(linkEl.attr("href")!, sourceUrl).href; } catch { /* keep sourceUrl */ }
    }

    if (title) {
      microdataResults.push({ eventUrl, title, description, imageUrl, startAt, location });
    }
  });

  if (microdataResults.length > 0) {
    console.log(`[EXTRACTOR] Step 2 (Microdata): found ${microdataResults.length} events in ${sourceUrl}`);
    return microdataResults;
  }
  console.log(`[EXTRACTOR] Step 2 (Microdata): 0 events, trying Step 3...`);

  // ──────────────────────────────────────────────────
  // STEP 3: Event card CSS heuristics
  // ──────────────────────────────────────────────────
  const cardSelector = [
    // English selectors
    "[class*='event-card']",
    "[class*='event-item']",
    "[class*='calendar-event']",
    "[class*='event-entry']",
    "[class*='event_card']",
    "[class*='eventCard']",
    "[class*='eventItem']",
    // Spanish selectors
    "[class*='evento']",
    "[class*='card-evento']",
    "[class*='item-actividad']",
    "[class*='tarjeta-evento']",
    "[class*='panel-evento']",
    "[class*='contenido-evento']",
    "[class*='entry-evento']",
    // Bootstrap .card inside agenda/calendar containers
    "[class*='agenda'] .card",
    "[class*='calendario'] .card",
    "[class*='programacion'] .card",
    "[class*='eventos'] .card",
    "[id*='agenda'] .card",
    "[id*='calendario'] .card",
    "[id*='programacion'] .card",
    "[id*='eventos'] .card",
  ].join(", ");

  const cardResults: ScrapedEvent[] = [];
  const cardEls = $(cardSelector).toArray().slice(0, 30);

  for (const el of cardEls) {
    const title = $(el).find("h2, h3, h4").first().text().trim() || undefined;
    if (!title) continue; // must have title

    const description = $(el).find("p").first().text().trim() || undefined;
    const imageUrl = $(el).find("img").first().attr("src") || undefined;

    let startAt: number | undefined;
    const timeEl = $(el).find("time").first();
    const datetimeStr = timeEl.attr("datetime") || timeEl.text().trim();
    if (datetimeStr) {
      const ts = tryParseDate(datetimeStr);
      if (ts !== null) startAt = ts;
    }
    // If no <time> element, try any text that looks like a date inside the card
    if (startAt === undefined) {
      const cardText = $(el).text();
      const chronoParsed = chrono.es.parseDate(cardText, new Date(), { forwardDate: true });
      if (chronoParsed) startAt = chronoParsed.getTime();
    }

    let eventUrl = sourceUrl;
    const linkEl = $(el).find("a[href]").first();
    if (linkEl.length) {
      try { eventUrl = new URL(linkEl.attr("href")!, sourceUrl).href; } catch { /* keep sourceUrl */ }
    }

    // must have a title AND either a date or a link
    const hasDateOrLink = startAt !== undefined || eventUrl !== sourceUrl;
    if (!hasDateOrLink) continue;

    cardResults.push({ eventUrl, title, description, imageUrl, startAt, location: undefined });
  }

  if (cardResults.length > 0) {
    console.log(`[EXTRACTOR] Step 3 (CSS heuristics): found ${cardResults.length} events in ${sourceUrl}`);
    return cardResults;
  }
  console.log(`[EXTRACTOR] Step 3 (CSS heuristics): 0 events, trying Step 4 (link fallback)...`);

  // ──────────────────────────────────────────────────
  // STEP 4: Link-based fallback (original logic)
  // ──────────────────────────────────────────────────
  const ogTitle = $('meta[property="og:title"]').attr("content");

  const linkSet = new Set<string>();
  $("a[href]").each((_, el) => {
    const href = $(el).attr("href");
    if (!href) return;
    try {
      const fullUrl = new URL(href, sourceUrl).href;
      if (looksLikeEventUrl(fullUrl) && !linkSet.has(fullUrl)) {
        linkSet.add(fullUrl);
      }
    } catch { /* skip invalid URLs */ }
  });

  const eventLinks = Array.from(linkSet).slice(0, MAX_EVENT_LINKS_PER_SOURCE);
  console.log(`[EXTRACTOR] Step 4 (link fallback): found ${eventLinks.length} potential event links in ${sourceUrl}`);

  if (eventLinks.length === 0 && ogTitle) {
    // Single-event page: has OG tags but no child event links
    const single = scrapeSinglePage(html, sourceUrl);
    const found = single.title ? [single] : [];
    console.log(`[EXTRACTOR] Step 4 (single-event OG): ${found.length} event(s) from ${sourceUrl}`);
    return found;
  }

  const results: ScrapedEvent[] = [];
  for (const link of eventLinks) {
    try {
      const pageHtml = await fetchWithTimeout(link, 10000);
      if (!pageHtml) continue;
      const scraped = scrapeSinglePage(pageHtml, link);
      if (scraped.title) results.push(scraped);
    } catch (e) {
      console.warn(`[EXTRACTOR] Failed to scrape ${link}:`, e);
    }
  }

  console.log(`[EXTRACTOR] Step 4 (link fallback): scraped ${results.length} events from ${sourceUrl}`);

  for (const event of results) {
    if (event.startAt === undefined) {
      const extractedDate = extractDateFromText(html);
      if (extractedDate !== null) {
        event.startAt = extractedDate;
      }
    }
    if (event.location === undefined) {
      const extractedLoc = extractLocationFromText(html);
      if (extractedLoc !== null) {
        event.location = extractedLoc;
      }
    }
    const extractedPrice = extractPriceFromText(html);
    if (extractedPrice !== null) {
      event.price = extractedPrice;
    }
  }

  // Categoría: intentar extraer si no vino en JSON-LD/microdata
  for (const event of results) {
    if (!event.category || event.category === "OTRO") {
      event.category = extractCategoryFromMeta($) || extractCategoryFromText($("body").text()) || undefined;
    }
  }

  return results;
}

export async function tryScrapeUrl(url: string): Promise<{ title?: string; description?: string; imageUrl?: string }> {
  const html = await fetchWithTimeout(url, 8000);
  if (!html) return {};
  const $ = cheerio.load(html);
  return {
    title: $('meta[property="og:title"]').attr("content") || $("title").first().text().trim() || undefined,
    description: $('meta[property="og:description"]').attr("content") || undefined,
    imageUrl: $('meta[property="og:image"]').attr("content") || undefined,
  };
}

export { sanitizeHtml, normalizeUrl };
