import * as chrono from "chrono-node";

export function looksLikeEventUrl(href: string): boolean {
  const lower = href.toLowerCase();
  const patterns = [
    /event(?:o|s)?\//i,
    /\/(?:e|evt)\//i,
    /ticket/i,
    /calendario/i,
    /agenda/i,
    /actividad/i,
    /encuentro/i,
    /concierto/i,
    /taller/i,
    /charla/i,
    /curso/i,
    /feria/i,
    /torneo/i,
    /partido/i,
    /campeonato/i,
    /presentacion/i,
    /lanzamiento/i,
    /seminario/i,
    /workshop/i,
    /\d{5,}/,
    /\/e\/|\/event\b|\/events\b/,
    /\/detail\w*\//,
    /\/calendario\b|\/calendar\b|\/programacion\b|\/cartelera\b/,
    /\/show\b|\/shows\b|\/obra\b|\/exposicion\b|\/festival\b|\/conferencia\b/,
  ];
  return patterns.some((p) => p.test(lower));
}

export function normalizeUrl(url: string): string {
    try {
        const u = new URL(url);
        u.hash = "";
        u.search = "";
        return u.href.replace(/\/+$/, "").toLowerCase();
    } catch { return url.toLowerCase().replace(/\/+$/, ""); }
}

export function sanitizeHtml(text: string): string {
    return text.replace(/&amp;/g, "&").replace(/&ntilde;/g, "ñ")
        .replace(/&aacute;/g, "á").replace(/&eacute;/g, "é")
        .replace(/&iacute;/g, "í").replace(/&oacute;/g, "ó")
        .replace(/&uacute;/g, "ú").replace(/&Ntilde;/g, "Ñ")
        .replace(/&Aacute;/g, "Á").replace(/&Eacute;/g, "É")
        .replace(/&Iacute;/g, "Í").replace(/&Oacute;/g, "Ó")
        .replace(/&Uacute;/g, "Ú").replace(/&quot;/g, '"')
        .replace(/&#39;/g, "'").replace(/&nbsp;/g, " ")
        .replace(/<br\s*\/?>/gi, "\n").replace(/<[^>]*>/g, "");
}

export function extractCategoryFromMeta($: any): string | null {
  const metaCat = $('meta[property="article:section"]').attr("content")
    || $('meta[name="category"]').attr("content")
    || $('meta[property="og:type"]').attr("content");
  if (metaCat) {
    const known = ["CONCIERTO", "FESTIVAL", "CHARLA", "TALLER", "TEATRO", "CINE", "DEPORTE", "FERIA", "MUSICA", "ARTE", "GASTRONOMIA"];
    const upper = metaCat.toUpperCase().trim();
    if (known.includes(upper)) return upper;
  }
  return null;
}

export function extractCategoryFromText(text: string): string | null {
  const lower = text.toLowerCase();
  const keywords: Record<string, string[]> = {
    CONCIERTO: ["concierto", "recital", "show", "presentación en vivo", "banda", "música en vivo"],
    FESTIVAL: ["festival", "fest"],
    CHARLA: ["charla", "conferencia", "seminario", "webinar", "ponencia", "talk"],
    TALLER: ["taller", "workshop", "curso", "clase", "capacitación", "masterclass"],
    TEATRO: ["teatro", "obra", "obra de teatro", "función", "teatral"],
    CINE: ["cine", "película", "estreno", "documental", "cortometraje", "film"],
    DEPORTE: ["deporte", "partido", "torneo", "campeonato", "carrera", "maratón"],
    FERIA: ["feria", "expo", "exposición", "muestra"],
    MUSICA: ["música", "tocata", "presentación musical", "concierto"],
    ARTE: ["arte", "galería", "muestra artística", "instalación"],
    GASTRONOMIA: ["gastronomía", "food", "comida", "gastronómico", "cata", "degustación", "gourmet"],
  };
  for (const [cat, words] of Object.entries(keywords)) {
    if (words.some(w => lower.includes(w))) return cat;
  }
  return null;
}

export function tryParseDate(str: string): number | null {
  // Try native ISO parse first (fast path)
  const iso = Date.parse(str);
  if (!isNaN(iso)) return iso;
  // Fallback: chrono-node for Spanish natural language dates
  const chronoParsed = chrono.es.parseDate(str);
  return chronoParsed ? chronoParsed.getTime() : null;
}

/** @deprecated use tryParseDate */
export function tryParseIsoOrThrow(str: string): number | null {
  return tryParseDate(str);
}

export function extractDateFromText(text: string): number | null {
  const patterns = [
    // "15 de Junio de 2025" o "15 de junio del 2025"
    /(\d{1,2})\s+de\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)\s+de(?:l)?\s+(\d{4})/i,
    // "15-06-2025", "15/06/2025" con varios separadores
    /(\d{1,2})[\/\-\.](\d{1,2})[\/\-\.](\d{4})/,
    // "2025-06-15" ISO sin T
    /(\d{4})[\/\-](\d{1,2})[\/\-](\d{1,2})/,
  ];

  for (let i = 0; i < patterns.length; i++) {
    const pattern = patterns[i];
    const match = text.match(pattern);
    if (!match) continue;

    // Pattern 1: meses en español
    if (i === 0) {
      const meses: Record<string, number> = {
        enero: 0, febrero: 1, marzo: 2, abril: 3, mayo: 4, junio: 5,
        julio: 6, agosto: 7, septiembre: 8, octubre: 9, noviembre: 10, diciembre: 11,
      };
      const dia = parseInt(match[1]);
      const mes = meses[match[2].toLowerCase()];
      const anio = parseInt(match[3]);
      if (mes !== undefined) return new Date(anio, mes, dia).getTime();
    }

    // Pattern 2: dd/mm/yyyy o dd-mm-yyyy
    if (i === 1) {
      const dia = parseInt(match[1]);
      const mes = parseInt(match[2]) - 1;
      const anio = parseInt(match[3]);
      if (mes >= 0 && mes <= 11 && dia >= 1 && dia <= 31) return new Date(anio, mes, dia).getTime();
    }

    // Pattern 3: yyyy-mm-dd
    if (i === 2) {
      const anio = parseInt(match[1]);
      const mes = parseInt(match[2]) - 1;
      const dia = parseInt(match[3]);
      return new Date(anio, mes, dia).getTime();
    }
  }

  return null;
}

export function extractLocationFromText(text: string): string | null {
  const patterns = [
    /(?:direcci[oó]n|ubicaci[oó]n|lugar|d[oó]nde|en)\s*[:：]?\s*([^\n<,.!?]{10,120})/i,
    /(?:Av\.|Avda\.|Avenida|Calle|Pasaje|Pje\.|Camino)\s+[^\n<,.!?]{5,80}/i,
    /(?:Santiago|Providencia|Las Condes|Vitacura|Ñuñoa|La Florida|Maipú|Valparaíso|Viña del Mar|Concepción)\s*[,]?\s*[^\n<,.!?]{0,40}/i,
    /(?:Regi[oó]n|Comuna)\s*[:：]?\s*[^\n<,.!?]{5,50}/i,
  ];

  for (const pattern of patterns) {
    const match = text.match(pattern);
    if (match) {
      const result = match[1] || match[0];
      if (result.length > 5 && result.length < 150) return sanitizeHtml(result.trim());
    }
  }
  return null;
}

export function extractPriceFromText(text: string): string | null {
  const patterns = [
    /(?:precio|valor|entrada|desde|costo)\s*[:：]?\s*\$?\s*([\d.,]+)/i,
    /\$\s*([\d.,]+)/,
    /\b(gratis|gratuito|liberado|sin costo|entrada\s*libre)\b/i,
    /CLP\s*([\d.,]+)/i,
  ];

  for (const pattern of patterns) {
    const match = text.match(pattern);
    if (match) return sanitizeHtml(match[0].trim());
  }
  return null;
}
