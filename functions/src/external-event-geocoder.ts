import { defineSecret } from "firebase-functions/params";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { requireAdmin } from "./external-event-sources";

const placesApiKey = defineSecret("PLACES_API_KEY");

interface GeocodingResult {
  region: string | null;
  commune: string | null;
  latitude: number | null;
  longitude: number | null;
  address: string | null;
}

export async function geocodeLocation(locationText: string): Promise<GeocodingResult> {
  try {
    const url = `https://maps.googleapis.com/maps/api/geocode/json?address=${encodeURIComponent(locationText)}&key=${placesApiKey.value()}&region=cl&language=es`;
    const res = await fetch(url);
    const data = await res.json() as any;
    
    if (data.status !== "OK" || !data.results?.length) {
      console.warn(`[GEOCODE] No results for: ${locationText}`);
      return { region: null, commune: null, latitude: null, longitude: null, address: null };
    }

    const result = data.results[0];
    const lat = result.geometry?.location?.lat ?? null;
    const lng = result.geometry?.location?.lng ?? null;
    let region: string | null = null;
    let commune: string | null = null;

    for (const comp of result.address_components || []) {
      const types = comp.types || [];
      if (types.includes("administrative_area_level_1")) region = comp.long_name;
      if (types.includes("locality")) commune = comp.long_name;
      if (!commune && types.includes("sublocality")) commune = comp.long_name;
      if (!commune && types.includes("administrative_area_level_2")) commune = comp.long_name;
    }

    return {
      region,
      commune,
      latitude: lat,
      longitude: lng,
      address: result.formatted_address || null,
    };
  } catch (error) {
    console.error(`[GEOCODE] Error geocoding "${locationText}":`, error);
    return { region: null, commune: null, latitude: null, longitude: null, address: null };
  }
}

export const geocodeExternalLocation = onCall({ secrets: [placesApiKey] }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Debes iniciar sesión.");
  await requireAdmin(request.auth.uid);

  const { location } = request.data;
  if (!location || typeof location !== "string" || location.trim() === "") {
    throw new HttpsError("invalid-argument", "El campo 'location' es requerido y debe ser un texto no vacío.");
  }

  const geo = await geocodeLocation(location.trim());
  return {
    success: true,
    region: geo.region,
    commune: geo.commune,
    latitude: geo.latitude,
    longitude: geo.longitude,
    address: geo.address,
  };
});

export { placesApiKey };
