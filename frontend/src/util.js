export const SLOTS = [
  "FRAME", "FORK", "BARS", "STEM", "CRANKS", "BOTTOM_BRACKET", "SPROCKET", "CHAIN",
  "PEDALS", "FRONT_HUB", "REAR_HUB", "RIMS", "TIRES", "SEAT", "BRAKES",
];

export const STYLES = ["STREET", "PARK", "DIRT", "FLATLAND", "RACE"];

export const slotLabel = (slot) =>
  slot.charAt(0) + slot.slice(1).toLowerCase().replace(/_/g, " ");

export const money = (m) =>
  m ? `$${Number(m.amount).toFixed(2)} ${m.currency}` : "";

export const grams = (g) => `${Math.round(g).toLocaleString()} g`;

/** Jackson may send an Instant as an ISO string or as epoch seconds. */
export function when(instant) {
  if (instant === null || instant === undefined) return "";
  const d = typeof instant === "number" ? new Date(instant * 1000) : new Date(instant);
  return isNaN(d) ? String(instant) : d.toLocaleString();
}

// Small wrappers around localStorage (it can be unavailable, so never let it throw)
export function load(key, fallback) {
  try {
    const v = localStorage.getItem(key);
    return v === null ? fallback : JSON.parse(v);
  } catch {
    return fallback;
  }
}

export function save(key, value) {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    /* ignore */
  }
}
