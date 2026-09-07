import { getParkAreaTotal } from "../logic/parkArea";
import type { MissionConfig, MissionVariant, ParkAreaConfig } from "../types";

type ParkMissionPanelProps = {
  activeMission: MissionConfig;
  activeVariant: MissionVariant;
  answer: string;
  displayMissionNumber?: number;
  displayMissionTitle?: string;
  feedbackText: string;
  gardenProgressCopy?: string;
  isComplete: boolean;
  isPlanVisible: boolean;
  missionEyebrow?: string;
  parkArea: ParkAreaConfig;
};

const modeTitles = {
  remaining: "Die verbleibende Fläche bestimmen",
  triangle: "Die Dreiecksfläche bestimmen",
  composite: "Alle Teilflächen addieren",
} as const;

export function ParkMissionPanel({
  activeMission,
  activeVariant,
  answer,
  displayMissionNumber,
  displayMissionTitle,
  feedbackText,
  gardenProgressCopy,
  isComplete,
  isPlanVisible,
  missionEyebrow,
  parkArea,
}: ParkMissionPanelProps) {
  const expectedArea = getParkAreaTotal(parkArea);
  const progressPercent = (isPlanVisible ? 45 : 0) + (isComplete ? 55 : 0);

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
            <p className="eyebrow">Ziel</p>
            <h3>{modeTitles[parkArea.mode]}</h3>
          </div>
          <strong>{progressPercent}%</strong>
        </div>
        <div className="mission-progress-track" aria-hidden="true">
          <span className="mission-progress-fill" style={{ width: `${progressPercent}%` }} />
        </div>
        <div className="mission-stats">
          <span>
            <strong>{parkArea.parts.length}</strong>
            {parkArea.parts.length === 1 ? "Teilfläche" : "Teilflächen"}
          </span>
          <span>
            <strong>{isPlanVisible ? "Ja" : "–"}</strong>
            Hilfsrechteck
          </span>
          <span>
            <strong>{answer || "?"}</strong>
            m² Antwort
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
        <p className="eyebrow">Parkplanung</p>
        <p className="garden-progress-copy">
          {gardenProgressCopy ?? "Aus dem Plan entsteht Stück für Stück eine grüne Parkecke."}
        </p>
        <p className="mission-help-text">
          {parkArea.mode === "remaining"
            ? "Berechne zuerst die ganze Rechteckfläche und ziehe danach die ausgesparte Fläche ab."
            : parkArea.mode === "triangle"
              ? "Das Dreieck füllt genau die Hälfte des passenden Hilfsrechtecks."
              : "Berechne Rechteck und Dreieck getrennt und addiere anschließend beide Ergebnisse."}
        </p>
        {isComplete ? (
          <p className="park-result-note">
            {expectedArea} m² {parkArea.resultLabel.toLocaleLowerCase("de-DE")} sind eingeplant.
          </p>
        ) : null}
      </section>
    </aside>
  );
}
