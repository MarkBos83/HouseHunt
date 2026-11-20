export interface EncryptedPayload {
  version: number;
  iterations: number;
  salt: string;
  iv: string;
  data: string;
}

export type SourceName = "FUNDA" | "PARARIUS" | "HUISLIJN" | "BUURTJE";

export interface Home {
  key: string;
  source: SourceName;
  address: string;
  url: string;
  googleMapsUrl: string;
  price: number | null;
  serviceCostsPerMonth: number | null;
  areaSquareMeters: number | null;
  bedrooms: number | null;
  energyLabel: string | null;
  status: string | null;
  bikeToWorkMeters: number | null;
  bikeToWorkMinutes: number | null;
  bikeToStationMeters: number | null;
  bikeToStationMinutes: number | null;
  firstSeenEpochSeconds: number;
  baseline: boolean;
  goneSinceEpochSeconds: number | null;
}

export interface SourceStatus {
  name: string;
  ok: boolean;
  listingCount: number;
  error: string | null;
}

export interface SiteData {
  updatedAtEpochSeconds: number;
  newBadgeHours: number;
  sources: SourceStatus[];
  homes: Home[];
}
