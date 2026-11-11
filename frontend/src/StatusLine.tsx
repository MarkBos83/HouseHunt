import { findWarnings, formatLastUpdated } from "./formatting";
import type { SiteData } from "./types";

interface StatusLineProps {
  data: SiteData;
  nowEpochSeconds: number;
}

export function StatusLine({ data, nowEpochSeconds }: StatusLineProps) {
  const warnings = findWarnings(data, nowEpochSeconds);
  return (
    <header className="status-line">
      <span>Last updated: {formatLastUpdated(data.updatedAtEpochSeconds)}</span>
      {warnings.map((warning) => (
        <span key={warning} className="warning" role="status">
          ⚠ {warning}
        </span>
      ))}
    </header>
  );
}
