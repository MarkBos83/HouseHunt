import type { Home, SiteData } from "./types";

const secondsPerHour = 3600;
const staleDataHours = 12;
const missingValue = "–";
const unknownValue = "?";

export const statusLabels: Record<string, string> = {
  AVAILABLE: "Available",
  UNDER_OFFER: "Under offer",
  SOLD_CONDITIONS_PENDING: "Sold, conditions pending",
};

export function formatPrice(price: number | null): string {
  return price === null ? missingValue : `€ ${price.toLocaleString("nl-NL")}`;
}

export function formatServiceCosts(amount: number | null): string {
  return amount === null ? missingValue : `€ ${amount.toLocaleString("nl-NL")}`;
}

export function formatArea(area: number | null): string {
  return area === null ? unknownValue : `${area} m²`;
}

export function formatBedrooms(bedrooms: number | null): string {
  return bedrooms === null ? unknownValue : String(bedrooms);
}

export function formatEnergyLabel(label: string | null): string {
  return label ?? unknownValue;
}

export function formatBike(meters: number | null, minutes: number | null): string {
  if (meters === null || minutes === null) return missingValue;
  const kilometers = (meters / 1000).toLocaleString("nl-NL", { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  return `${kilometers} km · ${minutes} min`;
}

export function isUnknownText(text: string): boolean {
  return text === missingValue || text === unknownValue;
}

export function isGone(home: Home): boolean {
  return home.goneSinceEpochSeconds !== null;
}

/** Decided in the browser, so the badge expires on time even between runs. */
export function isNewHome(home: Home, nowEpochSeconds: number, newBadgeHours: number): boolean {
  if (home.baseline || isGone(home)) return false;
  return nowEpochSeconds - home.firstSeenEpochSeconds < newBadgeHours * secondsPerHour;
}

export function sortCheapestFirst(homes: Home[]): Home[] {
  const priceOf = (home: Home) => home.price ?? Number.MAX_SAFE_INTEGER;
  return [...homes].sort((first, second) => priceOf(first) - priceOf(second));
}

export function formatLastUpdated(epochSeconds: number): string {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Europe/Amsterdam",
    weekday: "short",
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).formatToParts(new Date(epochSeconds * 1000));
  const part = (type: string) => parts.find((candidate) => candidate.type === type)?.value ?? "";
  return `${part("weekday")} ${part("day")} ${part("month")}, ${part("hour")}:${part("minute")}`;
}

export function findWarnings(data: SiteData, nowEpochSeconds: number): string[] {
  const sourceWarnings = data.sources
    .filter((source) => !source.ok || source.listingCount === 0)
    .map((source) => `${capitalize(source.name)}: ${source.error ?? "returned 0 listings"}`);
  const ageHours = (nowEpochSeconds - data.updatedAtEpochSeconds) / secondsPerHour;
  const staleWarning = ageHours > staleDataHours ? [`Data is ${Math.floor(ageHours)} hours old`] : [];
  return [...sourceWarnings, ...staleWarning];
}

function capitalize(text: string): string {
  return text.charAt(0) + text.slice(1).toLowerCase();
}
