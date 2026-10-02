import { getMinimumMarketCost, type MarketTotals } from "../logic/market";
import type { MarketConfig, MissionConfig, MissionVariant } from "../types";

type MarketMissionPanelProps = {
  activeMission: MissionConfig;
  activeVariant: MissionVariant;
  displayMissionNumber?: number;
  displayMissionTitle?: string;
  feedbackText: string;
  gardenProgressCopy?: string;
  isComplete: boolean;
  market: MarketConfig;
  missionEyebrow?: string;
  totals: MarketTotals;
};

const modeTitles = {
  coverage: "Genügend Saatgut auswählen",
  budget: "Fläche und Budget einhalten",
  optimization: "Den günstigsten Einkauf finden",
} as const;

export function MarketMissionPanel({
  activeMission,
  activeVariant,
  displayMissionNumber,
  displayMissionTitle,
  feedbackText,
  gardenProgressCopy,
  isComplete,
  market,
  missionEyebrow,
  totals,
}: MarketMissionPanelProps) {
  const coverageProgress = Math.min(
    70,
    Math.round((totals.coverage / market.requiredCoverage) * 70),
  );
  const progressPercent = Math.min(100, coverageProgress + (isComplete ? 30 : 0));
  const minimumCost =
    market.mode === "optimization" ? getMinimumMarketCost(market) : null;

  return (
    <aside className="mission-panel" aria-label="Aktuelle Mission">
      <section className="mission-card">
        <p className="eyebrow">{missionEyebrow ?? "Aktuelle Mission"}</p>
        <div className="mission-heading">
          <span className="mission-number-large">{displayMissionNumber ?? activeMission.id}</span>
          <div>
            <h2>{displayMissionTitle ?? activeMission.title}</h2>
            <span className="variant-label">{activeVariant.name}</span>
          </div>
        </div>
        <p className="mission-goal">{activeMission.goal}</p>
      </section>

      <section className="mission-card mission-card--progress" aria-label="Fortschritt der Mission">
        <div className="progress-title-row">
          <div>
            <p className="eyebrow">Einkaufsziel</p>
            <h3>{modeTitles[market.mode]}</h3>
          </div>
          <strong>{progressPercent}%</strong>
        </div>
        <div className="mission-progress-track" aria-hidden="true">
          <span className="mission-progress-fill" style={{ width: `${progressPercent}%` }} />
        </div>
        <div className="mission-stats">
          <span>
            <strong>{totals.coverage}/{market.requiredCoverage}</strong>
            m² Saatgut
          </span>
          <span>
            <strong>{totals.cost} €</strong>
            Einkauf
          </span>
          <span>
            <strong>{totals.itemCount}</strong>
            {totals.itemCount === 1 ? "Sack" : "Säcke"}
          </span>
        </div>
        <div
          className={
            isComplete
              ? "feedback-placeholder feedback-placeholder--success"
              : "feedback-placeholder"
          }
        >
          {feedbackText}
        </div>
      </section>

      <section className="mission-card">
        <p className="eyebrow">Gartenmarkt</p>
        <p className="garden-progress-copy">
          {gardenProgressCopy ?? "Vergleiche Reichweite und Preis der verfügbaren Säcke."}
        </p>
        <p className="mission-help-text">
          {market.mode === "coverage"
            ? `Der Einkauf muss für mindestens ${market.requiredCoverage} m² reichen.`
            : market.mode === "budget"
              ? `Der Einkauf muss für mindestens ${market.requiredCoverage} m² reichen und darf höchstens ${market.budget} € kosten.`
              : "Mehr Saatgut darf übrig bleiben, aber kein anderer ausreichender Einkauf darf günstiger sein."}
        </p>
        {isComplete ? (
          <p className="market-result-note">
            {market.mode === "optimization" && minimumCost !== null
              ? `Bestpreis gefunden: ${minimumCost} €.`
              : `Der Einkauf deckt ${totals.coverage} m² für ${totals.cost} € ab.`}
          </p>
        ) : null}
      </section>
    </aside>
  );
}
