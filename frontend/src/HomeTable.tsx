import {
  formatArea,
  formatBedrooms,
  formatBike,
  formatEnergyLabel,
  formatPrice,
  formatServiceCosts,
  isGone,
  isNewHome,
  isUnknownText,
  sortCheapestFirst,
  statusLabels,
} from "./formatting";
import type { Home } from "./types";

interface HomeTableProps {
  homes: Home[];
  nowEpochSeconds: number;
  newBadgeHours: number;
}

/** Grey text for values that are unknown (?) or missing (–). */
function Value({ text }: { text: string }) {
  return <span className={isUnknownText(text) ? "unknown" : undefined}>{text}</span>;
}

function StatusPill({ status }: { status: string | null }) {
  if (status === null) return <Value text="–" />;
  return <span className={`pill pill-${status.toLowerCase()}`}>{statusLabels[status] ?? status}</span>;
}

function HomeRow({ home, isNew }: { home: Home; isNew: boolean }) {
  const gone = isGone(home);
  return (
    <tr className={gone ? "gone" : undefined}>
      <td className="address" data-label="Address">
        <a href={home.url} target="_blank" rel="noopener noreferrer">
          {home.address}
        </a>
        {isNew && <span className="badge badge-new">New</span>}
        {gone && <span className="badge badge-gone">Gone</span>}
      </td>
      <td className="number price" data-label="Price"><Value text={formatPrice(home.price)} /></td>
      <td className="number" data-label="Service costs/month"><Value text={formatServiceCosts(home.serviceCostsPerMonth)} /></td>
      <td className="number" data-label="Area"><Value text={formatArea(home.areaSquareMeters)} /></td>
      <td className="number" data-label="Bedrooms"><Value text={formatBedrooms(home.bedrooms)} /></td>
      <td data-label="Energy label"><Value text={formatEnergyLabel(home.energyLabel)} /></td>
      <td data-label="Status"><StatusPill status={home.status} /></td>
      <td className="number" data-label="Bike to Databankweg">
        <Value text={formatBike(home.bikeToWorkMeters, home.bikeToWorkMinutes)} />
      </td>
      <td className="number" data-label="Bike to station">
        <Value text={formatBike(home.bikeToStationMeters, home.bikeToStationMinutes)} />
      </td>
    </tr>
  );
}

export function HomeTable({ homes, nowEpochSeconds, newBadgeHours }: HomeTableProps) {
  if (homes.length === 0) return <p className="empty">No homes match right now.</p>;
  return (
    <table className="home-table">
      <thead>
        <tr>
          <th>Address</th>
          <th className="number">Price</th>
          <th className="number">Service costs/month</th>
          <th className="number">Area</th>
          <th className="number">Bedrooms</th>
          <th>Energy label</th>
          <th>Status</th>
          <th className="number">Bike to Databankweg</th>
          <th className="number">Bike to station</th>
        </tr>
      </thead>
      <tbody>
        {sortCheapestFirst(homes).map((home) => (
          <HomeRow key={home.key} home={home} isNew={isNewHome(home, nowEpochSeconds, newBadgeHours)} />
        ))}
      </tbody>
    </table>
  );
}
