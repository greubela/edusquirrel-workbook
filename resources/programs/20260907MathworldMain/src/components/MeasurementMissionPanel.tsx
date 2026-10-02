import { getRectangleAreaTotal } from "../logic/measurement";
import type { MissionConfig, MissionVariant, RectangleAreaConfig } from "../types";

type MeasurementMissionPanelProps = {
  activeMission: MissionConfig;
  activeVariant: MissionVariant;
  answer: string;
  displayMissionNumber?: number;
  displayMissionTitle?: string;
  feedbackText: string;
  gardenProgressCopy?: string;
  isComplete: boolean;
  measuredEdgeCount: number;
  measurement: RectangleAreaConfig;
  missionEyebrow?: string;
  requiredEdgeCount: number;
};

export function MeasurementMissionPanel({
  activeMission,
  activeVariant,
  answer,
  displayMissionNumber,
  displayMissionTitle,
  feedbackText,
  gardenProgressCopy,
  isComplete,
  measuredEdgeCount,
  measurement,
  missionEyebrow,
  requiredEdgeCount,
}: MeasurementMissionPanelProps) {
  const expectedArea = getRectangleAreaTotal(measurement);
  const measurementProgress =
    requiredEdgeCount > 0 ? Math.round((measuredEdgeCount / requiredEdgeCount) * 70) : 0;
  const progressPercent = Math.min(100, measurementProgress + (isComplete ? 30 : 0));

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
            <h3>{measurement.beds.length > 1 ? "Gesamtfläche berechnen" : "Beetfläche berechnen"}</h3>
          </div>
          <strong>{progressPercent}%</strong>
        </div>
        <div className="mission-progress-track" aria-hidden="true">
          <span className="mission-progress-fill" style={{ width: `${progressPercent}%` }} />
        </div>
        <div className="mission-stats">
          <span>
            <strong>{measuredEdgeCount}/{requiredEdgeCount}</strong>
            Seiten gemessen
          </span>
          <span>
            <strong>{measurement.beds.length}</strong>
            {measurement.beds.length === 1 ? "Beet" : "Teilbeete"}
          </span>
          <span>
            <strong>{answer || "?"}</strong>
            m² Antwort
          </span>
        </div>
        <div className={isComplete ? "feedback-placeholder feedback-placeholder--success" : "feedback-placeholder"}>
          {feedbackText}
        </div>
      </section>

      <section className="mission-card">
        <p className="eyebrow">Gewächshaus</p>
        <p className="garden-progress-copy">
          {gardenProgressCopy ?? "Miss die Beete, bevor die Jungpflanzen einziehen."}
        </p>
        <p className="mission-help-text">
          Ein Rechteck hat die Fläche Länge mal Breite. Bei zwei Beeten addierst du beide
          Teilflächen.
        </p>
        {isComplete ? (
          <p className="measurement-result-note">
            {expectedArea} m² Anzuchtfläche sind vorbereitet.
          </p>
        ) : null}
      </section>
    </aside>
  );
}
