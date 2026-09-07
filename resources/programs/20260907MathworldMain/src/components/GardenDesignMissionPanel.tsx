import type {
  GardenDesignConfig,
  GardenMissionStage,
  MissionConfig,
  MissionVariant,
} from "../types";
import type { GardenDesignStatus } from "../logic/gardenDesign";

type GardenDesignMissionPanelProps = {
  activeMission: MissionConfig;
  activeVariant: MissionVariant;
  design: GardenDesignConfig;
  displayMissionNumber?: number;
  displayMissionTitle?: string;
  dugCellCount: number;
  feedbackText: string;
  gardenMissionStage: GardenMissionStage;
  gardenProgressCopy?: string;
  isComplete: boolean;
  missionEyebrow?: string;
  sownCellCount: number;
  status: GardenDesignStatus;
  totalCellCount: number;
};

function getProgressRatio(currentValue: number, requiredValue: number) {
  return requiredValue > 0 ? Math.min(currentValue / requiredValue, 1) : 1;
}

export function GardenDesignMissionPanel({
  activeMission,
  activeVariant,
  design,
  displayMissionNumber,
  displayMissionTitle,
  dugCellCount,
  feedbackText,
  gardenMissionStage,
  gardenProgressCopy,
  isComplete,
  missionEyebrow,
  sownCellCount,
  status,
  totalCellCount,
}: GardenDesignMissionPanelProps) {
  const progressParts = [
    getProgressRatio(status.bedCount, design.minimumBedCount),
    getProgressRatio(status.distinctSizeCount, design.minimumDistinctSizes),
    getProgressRatio(status.totalArea, design.minimumTotalArea),
    ...(design.minimumOuterCornerCount
      ? [
          getProgressRatio(
            status.outerCornerCount,
            design.minimumOuterCornerCount,
          ),
        ]
      : []),
    ...(design.requireConnectedLayout
      ? [status.isConnectedLayout ? 1 : 0]
      : []),
  ];
  const stakingProgress =
    progressParts.reduce(
      (progressSum, progressValue) => progressSum + progressValue,
      0,
    ) / progressParts.length;
  const progressPercent = isComplete
    ? 100
    : gardenMissionStage === "stake"
      ? Math.round(stakingProgress * 33)
      : gardenMissionStage === "dig"
        ? Math.round(
            33 +
              (totalCellCount > 0 ? dugCellCount / totalCellCount : 0) * 34,
          )
        : Math.round(
            67 +
              (totalCellCount > 0 ? sownCellCount / totalCellCount : 0) * 33,
          );

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
            <p className="eyebrow">
              {gardenMissionStage === "stake"
                ? "Dein Beetplan"
                : gardenMissionStage === "dig"
                  ? "Arbeitsschritt 2"
                  : "Arbeitsschritt 3"}
            </p>
            <h3>
              {gardenMissionStage === "stake"
                ? design.requireConnectedLayout
                  ? "Rechteckteile verbinden"
                  : "Eigene Rechtecke abstecken"
                : gardenMissionStage === "dig"
                  ? "Beetfläche umgraben"
                  : "Beetfläche aussäen"}
            </h3>
          </div>
          <strong>{progressPercent}%</strong>
        </div>
        <div className="mission-progress-track" aria-hidden="true">
          <span
            className="mission-progress-fill"
            style={{ width: `${Math.min(progressPercent, 100)}%` }}
          />
        </div>
        {gardenMissionStage === "stake" ? (
          <>
            <div className="mission-stats">
              <span>
                <strong>
                  {status.bedCount}/{design.minimumBedCount}
                </strong>
                {design.requireConnectedLayout ? "Rechteckteile" : "Beete"}
              </span>
              <span>
                <strong>
                  {status.distinctSizeCount}/{design.minimumDistinctSizes}
                </strong>
                verschiedene Maße
              </span>
              <span>
                <strong>
                  {status.totalArea}/{design.minimumTotalArea}
                </strong>
                m² Fläche
              </span>
            </div>
            <div className="garden-design-thread-summary">
              <span>
                Faden an der Außenkante
                {design.minimumOuterCornerCount
                  ? ` · ${status.outerCornerCount}/${design.minimumOuterCornerCount} Ecken`
                  : ""}
              </span>
              <strong>
                {status.threadLengthUsed}/{design.threadLength} m
              </strong>
            </div>
          </>
        ) : (
          <div className="mission-stats">
            <span>
              <strong>
                {gardenMissionStage === "dig"
                  ? dugCellCount
                  : sownCellCount}
                /{totalCellCount} m²
              </strong>
              {gardenMissionStage === "dig" ? "umgegraben" : "ausgesät"}
            </span>
            <span>
              <strong>
                {Math.max(
                  totalCellCount -
                    (gardenMissionStage === "dig"
                      ? dugCellCount
                      : sownCellCount),
                  0,
                )}{" "}
                m²
              </strong>
              noch zu bearbeiten
            </span>
            <span>
              <strong>{gardenMissionStage === "dig" ? "2/3" : "3/3"}</strong>
              Arbeitsschritt
            </span>
          </div>
        )}
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
        <p className="eyebrow">Planungswiese</p>
        <p className="garden-progress-copy">
          {gardenProgressCopy ??
            (gardenMissionStage === "stake"
              ? design.requireConnectedLayout
                ? "Setze Rechteckteile Kante an Kante zu einem komplexen Beet zusammen."
                : "Entwirf deinen Beetplan aus selbst gewählten Rechtecken."
              : gardenMissionStage === "dig"
                ? "Die Absteckung bleibt grün, bis du jeden Quadratmeter mit dem Spaten bearbeitest."
                : "Bestimme passende Saat-Sack-Größen und bedecke damit die vorbereitete Beetfläche.")}
        </p>
        <p className="mission-help-text">
          {gardenMissionStage === "stake"
            ? "Setze drei Eckpunkte. Der Faden schließt das Rechteck automatisch im rechten Winkel."
            : gardenMissionStage === "dig"
              ? "Jeder Spatenklick verwandelt genau 1 m² grüne Wiese in braune Erde."
              : "Wähle Saatgut, bestimme Breite und Höhe des Sacks und ziehe ihn vollständig auf freie braune Erde."}
        </p>
      </section>
    </aside>
  );
}
