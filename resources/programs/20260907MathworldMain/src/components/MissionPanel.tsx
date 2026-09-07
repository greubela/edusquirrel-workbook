import type { MissionConfig, MissionCoverage, MissionVariant } from "../types";

type FractionAnswerInput = {
  denominator: string;
  numerator: string;
};

type MissionPanelProps = {
  activeMission: MissionConfig;
  activeVariant: MissionVariant;
  completedGardenMissionCount: number;
  coverage: MissionCoverage;
  coveredArea: number;
  displayMissionNumber?: number;
  displayMissionTitle?: string;
  feedbackText: string;
  fractionAnswer: FractionAnswerInput;
  gardenProgressCopy?: string;
  isFractionAnswerCorrect: boolean;
  isFractionTaskComplete: boolean;
  missionEyebrow?: string;
  shapeCount: number;
  targetArea: number;
  totalGardenMissionCount: number;
  totalShapeArea: number;
};

export function MissionPanel({
  activeMission,
  activeVariant,
  completedGardenMissionCount,
  coverage,
  coveredArea,
  displayMissionNumber,
  displayMissionTitle,
  feedbackText,
  fractionAnswer,
  gardenProgressCopy,
  isFractionAnswerCorrect,
  isFractionTaskComplete,
  missionEyebrow,
  shapeCount,
  targetArea,
  totalGardenMissionCount,
  totalShapeArea,
}: MissionPanelProps) {
  const isFractionMission = activeMission.kind === "fraction-read";
  const fractionTarget = activeVariant.fractionRead ?? null;
  const supply = fractionTarget?.supply;
  const progressPercent = isFractionMission
    ? isFractionTaskComplete
      ? 100
      : isFractionAnswerCorrect && activeMission.requireSimplification
        ? 50
        : 0
    : targetArea > 0
      ? Math.round((coveredArea / targetArea) * 100)
      : 0;
  const isComplete = isFractionMission ? isFractionTaskComplete : coverage.isComplete;
  const displayedFractionAnswer =
    fractionAnswer.numerator || fractionAnswer.denominator
      ? `${fractionAnswer.numerator || "?"}/${fractionAnswer.denominator || "?"}`
      : "?";

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
            <h3>
              {isFractionMission && fractionTarget
                ? supply
                  ? "Sackverteilung pruefen"
                  : `${fractionTarget.denominator} Teile ablesen`
                : `${targetArea} m² aussähen`}
            </h3>
          </div>
          <strong>{progressPercent}%</strong>
        </div>
        <div className="mission-progress-track" aria-hidden="true">
          <span className="mission-progress-fill" style={{ width: `${Math.min(progressPercent, 100)}%` }} />
        </div>
        {isFractionMission && fractionTarget ? (
          <div className="mission-stats mission-stats--fraction">
            <span>
              <strong>{supply && !isFractionAnswerCorrect ? "?" : fractionTarget.numerator}</strong>
              {supply ? supply.filledBedLabel : "gefaerbt"}
            </span>
            <span>
              <strong>{fractionTarget.denominator}</strong>
              {supply ? "Beete insgesamt" : "insgesamt"}
            </span>
            <span>
              <strong>{displayedFractionAnswer}</strong>
              Antwort
            </span>
          </div>
        ) : (
          <div className="mission-stats">
            <span>
              <strong>{coveredArea}</strong>
              m² ausgesäht
            </span>
            <span>
              <strong>{totalShapeArea}</strong>
              m² eingekauft
            </span>
            <span>
              <strong>{shapeCount}</strong>
              Säcke
            </span>
          </div>
        )}
        <div className={isComplete ? "feedback-placeholder feedback-placeholder--success" : "feedback-placeholder"}>
          {feedbackText}
        </div>
      </section>

      <section className="mission-card">
        <p className="eyebrow">{supply ? "Vorrat" : isFractionMission ? "Brueche" : "Garten"}</p>
        <p className="garden-progress-copy">
          {supply
            ? "Ein Sack ist das Ganze. Jedes Beet bekommt gleich viel."
            : isFractionMission
              ? "Lies den gefaerbten Anteil des ganzen Quadrats ab."
              : gardenProgressCopy ??
                `${completedGardenMissionCount} von ${totalGardenMissionCount} Beeten sind bepflanzt.`}
        </p>
        <p className="mission-help-text">
          {supply
            ? "Nenner: alle Beete. Zaehler: die bereits versorgten Beete."
            : isFractionMission
              ? "Nenner: alle gleich grossen Teile. Zaehler: die gefaerbten Teile."
              : "Ziehe die bunten Samen-Säcke in das Beet. Beim Loslassen rasten sie auf dem Raster ein."}
        </p>
      </section>
    </aside>
  );
}
