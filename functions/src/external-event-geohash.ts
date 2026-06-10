const BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";

export function encodeGeohash(lat: number, lng: number, precision: number = 9): string {
  let minLat = -90, maxLat = 90;
  let minLng = -180, maxLng = 180;
  let hash = "";
  let isEven = true;
  let bit = 0;
  let ch = 0;
  while (hash.length < precision) {
    if (isEven) {
      const mid = (minLng + maxLng) / 2;
      if (lng >= mid) { ch |= (1 << (4 - bit)); minLng = mid; }
      else { maxLng = mid; }
    } else {
      const mid = (minLat + maxLat) / 2;
      if (lat >= mid) { ch |= (1 << (4 - bit)); minLat = mid; }
      else { maxLat = mid; }
    }
    isEven = !isEven;
    if (bit < 4) { bit++; }
    else { hash += BASE32[ch]; bit = 0; ch = 0; }
  }
  return hash;
}

export { BASE32 };
