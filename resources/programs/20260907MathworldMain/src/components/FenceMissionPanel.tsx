import type { MissionConfig, MissionVariant } from "../types";

type FenceMissionPanelProps = {
  activeMission: MissionConfig;
  activeVariant: MissionVariant;
  answer: string;
  displayMissionNumber?: number;
  displayMissionTitle?: string;
  feedbackText: string;
  fencedEdgeCount: number;
  gardenProgressCopy?: string;
  isComplete: boolean;
  missionEyebrow?: string;
  perimeterLength: number;
  requiredEdgeCount: number;
};

export function FenceMissionPanel({
  activeMission,
  activeVariant,
  answer,
  displayMissionNumber,
  displayMissionTitle,
  feedbackText,
  fencedEdgeCount,
  gardenProgressCopy,
  isComplete,
  missionEyebrow,
  perimeterLength,
  requiredEdgeCount,
}: FenceMissionPanelProps) {
  const fenceProgress =
    requiredEdgeCount > 0 ? Math.round((fencedEdgeCount / requiredEdgeCount) * 70) : 0;
  const progressPercent = Math.min(100, fenceProgress + (isComplete ? 30 : 0));

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
            <h3>Den ganzen Rand einzäunen</h3>
          </div>
          <strong>{progressPercent}%</strong>
        </div>
        <div className="mission-progress-track" aria-hidden="true">
          <span className="mission-progress-fill" style={{ width: `${progressPercent}%` }} />
        </div>
        <div className="mission-stats">
          <span>
            <strong>{fencedEdgeCount}/{requiredEdgeCount}</strong>
            Randabschnitte
          </span>
          <span>
            <strong>{isComplete ? perimeterLength : "?"}</strong>
            m Zaun
          </span>
          <span>
            <strong>{answer || "?"}</strong>
            m Antwort
          </span>
        </div>
        <div className={isComplete ? "feedback-placeholder feedback-placeholder--success" : "feedback-placeholder"}>
          {feedbackText}
        </div>
      </section>

      <section className="mission-card">
        <p className="eyebrow">Zaunwerkstatt</p>
        <p className="garden-progress-copy">
          {gardenProgressCopy ?? "Der Zaun schützt den gesamten äußeren Beetrand."}
        </p>
        <p className="mission-help-text">
          Der Umfang ist die Länge des Randes. Addiere alle Zaunstücke in Metern, nicht die
          Fläche in Quadratmetern.
        </p>
      </section>
    </aside>
  );
}
