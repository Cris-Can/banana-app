import { defineSecret } from "firebase-functions/params";

const geminiApiKey = defineSecret("GEMINI_API_KEY");
const groqApiKey = defineSecret("GROQ_API_KEY");

export { geminiApiKey, groqApiKey };

export interface AIExtractionResult {
  title: string | null;
  description: string | null;
  startAt: number | null;
  location: string | null;
  category: string | null;
  price: string | null;
  imageUrl: string | null;
}

function buildEnrichPrompt(html: string, currentEvent: Record<string, any>): string {
  const missingFields: string[] = [];
  if (!currentEvent.title) missingFields.push("title");
  if (!currentEvent.startAt) missingFields.push("startAt (fecha/hora de inicio)");
  if (!currentEvent.location) missingFields.push("location");
  if (!currentEvent.category || currentEvent.category === "OTRO") missingFields.push("category");
  if (!currentEvent.price) missingFields.push("price");

  // Truncar HTML para ahorrar tokens (~4000 chars)
  const cleaned = html
    .replace(/<script[\s\S]*?<\/script>/gi, "")
    .replace(/<style[\s\S]*?<\/style>/gi, "")
    .replace(/<nav[\s\S]*?<\/nav>/gi, "")
    .replace(/<footer[\s\S]*?<\/footer>/gi, "")
    .replace(/<header[\s\S]*?<\/header>/gi, "")
    .replace(/\s+/g, " ")
    .trim()
    .substring(0, 4000);

  return `Eres un extractor de datos de eventos. Dado el HTML de una página, extrae SOLO los campos que están faltando.

Campos a extraer: ${missingFields.join(", ")}

Reglas:
- startAt: devuelve timestamp en milisegundos (ej: 1750000000000). Si ves "15 de Junio de 2025 a las 19:00", calcula el timestamp.
- category: una de: CONCIERTO, FESTIVAL, CHARLA, TALLER, TEATRO, CINE, DEPORTE, FERIA, MUSICA, ARTE, GASTRONOMIA, OTRO
- price: incluye moneda si visible (ej: "$15.000", "Gratis")
- Si un campo no está visible en el HTML, devuelve null.
- Responde ÚNICAMENTE con JSON, sin texto adicional.

HTML:
${cleaned}`;
}

async function callGemini(prompt: string): Promise<string | null> {
  try {
    const key = geminiApiKey.value();
    if (!key) {
      console.warn("[AI] GEMINI_API_KEY not configured");
      return null;
    }
    const res = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${key}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          contents: [{ parts: [{ text: prompt }] }],
          generationConfig: { temperature: 0.1, maxOutputTokens: 512 },
        }),
      }
    );
    if (!res.ok) {
      console.warn(`[AI] Gemini API error: ${res.status}`);
      return null;
    }
    const data = await res.json() as any;
    return data?.candidates?.[0]?.content?.parts?.[0]?.text || null;
  } catch (e) {
    console.error("[AI] Gemini call failed:", e);
    return null;
  }
}

async function callGroq(prompt: string, model: string): Promise<string | null> {
  try {
    const key = groqApiKey.value();
    if (!key) {
      console.warn("[AI] GROQ_API_KEY not configured");
      return null;
    }
    const modelName = model === "groq-llama3-70b" ? "llama3-70b-8192" : "qwen-3-32b";
    const res = await fetch("https://api.groq.com/openai/v1/chat/completions", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${key}`,
      },
      body: JSON.stringify({
        model: modelName,
        messages: [{ role: "user", content: prompt }],
        temperature: 0.1,
        max_tokens: 512,
      }),
    });
    if (!res.ok) {
      console.warn(`[AI] Groq API error: ${res.status}`);
      return null;
    }
    const data = await res.json() as any;
    return data?.choices?.[0]?.message?.content || null;
  } catch (e) {
    console.error("[AI] Groq call failed:", e);
    return null;
  }
}

export async function callAI(prompt: string, model: string): Promise<string | null> {
  switch (model) {
    case "gemini-flash":
      return callGemini(prompt);
    case "groq-llama3-70b":
    case "groq-qwen3-32b":
      return callGroq(prompt, model);
    default:
      console.warn(`[AI] Unknown model: ${model}, falling back to Gemini`);
      return callGemini(prompt);
  }
}

function parseAIResponse(text: string | null): Partial<AIExtractionResult> {
  if (!text) return {};
  try {
    const jsonMatch = text.match(/\{[\s\S]*\}/);
    if (!jsonMatch) return {};
    return JSON.parse(jsonMatch[0]) as Partial<AIExtractionResult>;
  } catch {
    console.warn("[AI] Failed to parse AI response:", text.substring(0, 100));
    return {};
  }
}

export async function aiEnrichEvent(
  html: string,
  currentEvent: Record<string, any>,
  model: string
): Promise<Partial<AIExtractionResult>> {
  const prompt = buildEnrichPrompt(html, currentEvent);
  const raw = await callAI(prompt, model);
  if (!raw) return {};
  const parsed = parseAIResponse(raw);

  // Validar y convertir startAt si viene como string ISO
  if (parsed.startAt && typeof parsed.startAt === "string") {
    const ts = Date.parse(parsed.startAt as string);
    if (!isNaN(ts)) parsed.startAt = ts;
    else parsed.startAt = null;
  }

  console.log(`[AI] Enriched event "${currentEvent.title || "sin título"}":`, JSON.stringify(parsed));
  return parsed;
}
