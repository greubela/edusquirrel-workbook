import { MAX_INPUT_DIMENSION } from "../config/workspace";
import type { GardenDesignStatus } from "../logic/gardenDesign";
import {
  isGardenSeedSackShape,
} from "../logic/gardenDesign";
import {
  getShapeArea,
  normalizeDimensionInput,
} from "../logic/geometry";
import { getRectangleThreadLength } from "../logic/stakingThread";
import type {
  GardenDesignConfig,
  GardenMissionStage,
  RewardKind,
  WorkspaceShape,
} from "../types";
import { PlantPicker } from "./PlantPicker";
import { GardenSpadeIcon } from "./GardenSpade";
import { ParametricSack } from "./ParametricSack";

type GardenDesignToolPanelProps = {
  allowedPlantKinds: readonly RewardKind[];
  canAdvance: boolean;
  canCompleteDigging: boolean;
  canSpawnCustomShape: boolean;
  customHeightCells: string;
  customWidthCells: string;
  design: GardenDesignConfig;
  dugCellCount: number;
  gardenMissionStage: GardenMissionStage;
  hasNextMission: boolean;
  primaryActionLabel?: string;
  parsedCustomHeightCells: number | null;
  parsedCustomWidthCells: number | null;
  selectedPlantKind: RewardKind;
  selectedShape: WorkspaceShape | null;
  showDevControls: boolean;
  showPlantPicker: boolean;
  sownCellCount: number;
  stakingPointCount: number;
  status: GardenDesignStatus;
  totalCellCount: number;
  onAddSeedSack: (widthCells: number, heightCells: number) => void;
  onCancelStaking: () => void;
  onCompleteDigging: () => void;
  onCompleteStaking: () => void;
  onDeleteSelectedShape: () => void;
  onGoToNextMission: () => void;
  onGoToNextMissionInDevMode: () => void;
  onGoToPreviousMissionInDevMode: () => void;
  onRestartMission: () => void;
  onSelectPlant?: (rewardKind: RewardKind) => void;
  onCustomHeightChange: (value: string) => void;
  onCustomWidthChange: (value: string) => void;
};

const STAKING_STEPS = ["Startpunkt", "Erste Kante", "Zweite Kante"];

export function GardenDesignToolPanel({
  allowedPlantKinds,
  canAdvance,
  canCompleteDigging,
  canSpawnCustomShape,
  customHeightCells,
  customWidthCells,
  design,
  dugCellCount,
  gardenMissionStage,
  hasNextMission,
  onCancelStaking,
  onAddSeedSack,
  onCompleteDigging,
  onCompleteStaking,
  onDeleteSelectedShape,
  onGoToNextMission,
  onGoToNextMissionInDevMode,
  onGoToPreviousMissionInDevMode,
  onRestartMission,
  onSelectPlant,
  onCustomHeightChange,
  onCustomWidthChange,
  parsedCustomHeightCells,
  parsedCustomWidthCells,
  primaryActionLabel = "Nächster Beetplan",
  selectedPlantKind,
  selectedShape,
  showDevControls,
  showPlantPicker,
  sownCellCount,
  stakingPointCount,
  status,
  totalCellCount,
}: GardenDesignToolPanelProps) {
  const usedThreadPercent = Math.min(
    (status.threadLengthUsed / design.threadLength) * 100,
    100,
  );
  const emptySelectionCopy =
    stakingPointCount === 1
      ? "Setze den zweiten Punkt für die erste Kante."
      : stakingPointCount === 2
        ? "Setze den dritten Punkt für die zweite Kante."
        : "Setze den ersten Eckpunkt auf einen Rasterpunkt.";
  const stageIndex =
    gardenMissionStage === "stake"
      ? 0
      : gardenMissionStage === "dig"
        ? 1
        : 2;
  const remainingDigArea = Math.max(totalCellCount - dugCellCount, 0);
  const remainingSowArea = Math.max(totalCellCount - sownCellCount, 0);
  const selectedGardenSeedSack =
    selectedShape && isGardenSeedSackShape(selectedShape)
      ? selectedShape
      : null;
  const previewWidthCells = parsedCustomWidthCells ?? 1;
  const previewHeightCells = parsedCustomHeightCells ?? 1;
  const previewScale = Math.min(
    132 / (previewWidthCells * 32),
    92 / (previewHeightCells * 32),
    1,
  );
  const previewWidth = Math.max(
    40,
    previewWidthCells * 32 * previewScale,
  );
  const previewHeight = Math.max(
    48,
    previewHeightCells * 32 * previewScale,
  );
  const stakingRequirements = [
    {
      isMet: status.bedCount >= design.minimumBedCount,
      label: `${
        design.requireConnectedLayout ? "Rechteckteile" : "Beete"
      }: ${status.bedCount}/${design.minimumBedCount}`,
    },
    {
      isMet: status.distinctSizeCount >= design.minimumDistinctSizes,
      label: `Maße: ${status.distinctSizeCount}/${design.minimumDistinctSizes}`,
    },
    {
      isMet: status.totalArea >= design.minimumTotalArea,
      label: `Fläche: ${status.totalArea}/${design.minimumTotalArea} m²`,
    },
    ...(design.requireConnectedLayout
      ? [
          {
            isMet: status.isConnectedLayout,
            label: "Alle Teile verbunden",
          },
        ]
      : []),
    ...(design.minimumOuterCornerCount
      ? [
          {
            isMet:
              status.outerCornerCount >= design.minimumOuterCornerCount,
            label: `Ecken: ${status.outerCornerCount}/${design.minimumOuterCornerCount}`,
          },
        ]
      : []),
    {
      isMet: !status.hasOverlap,
      label: status.hasOverlap
        ? "Beete überlappen sich"
        : "Keine Überlappung",
    },
    {
      isMet: !status.isOverThreadLimit,
      label: status.isOverThreadLimit
        ? `${status.threadLengthUsed - design.threadLength} m zu lang`
        : "Faden im Limit",
    },
  ];

  return (
    <aside className="tool-panel" aria-label="Werkzeuge für den Beetplan">
      <section className="tool-panel__content">
        <div className="garden-work-phases" aria-label="Arbeitsschritte">
          {["Abstecken", "Umgraben", "Aussäen"].map((label, index) => (
            <span
              className={[
                "garden-work-phase",
                index < stageIndex ? "garden-work-phase--complete" : "",
                index === stageIndex ? "garden-work-phase--active" : "",
              ]
                .filter(Boolean)
                .join(" ")}
              key={label}
            >
              <i>{index + 1}</i>
              {label}
            </span>
          ))}
        </div>

        {gardenMissionStage === "sow" && showPlantPicker && onSelectPlant ? (
          <PlantPicker
            allowedPlantKinds={allowedPlantKinds}
            label="Saatgut für den nächsten Sack"
            onSelectPlant={onSelectPlant}
            selectedPlantKind={selectedPlantKind}
          />
        ) : null}

        {gardenMissionStage === "stake" ? (
          <div className="staking-tool-section">
          <p className="eyebrow">Werkzeug</p>
          <div className="staking-thread-tool">
            <span aria-hidden="true" className="staking-thread-icon">
              <span />
            </span>
            <span>
              <strong>Absteckfaden</strong>
              <small>{design.threadLength} m Faden auf der Rolle</small>
            </span>
          </div>

          <div className="staking-thread-budget">
            <div>
              <span>Fadenvorrat</span>
              <strong>
                {status.isOverThreadLimit
                  ? `${status.threadLengthUsed - design.threadLength} m zu lang`
                  : `${status.remainingThreadLength} m übrig`}
              </strong>
            </div>
            <div
              className={[
                "staking-thread-budget__track",
                status.isOverThreadLimit
                  ? "staking-thread-budget__track--over"
                  : "",
              ]
                .filter(Boolean)
                .join(" ")}
              aria-hidden="true"
            >
              <span style={{ width: `${usedThreadPercent}%` }} />
            </div>
            <small>
              {status.threadLengthUsed} von {design.threadLength} m verwendet
            </small>
            <p className="staking-thread-rule">
              Der Faden ist die Obergrenze und muss nicht vollständig verbraucht
              werden.
            </p>
          </div>

          <div
            className="garden-staking-checklist"
            aria-label="Bedingungen zum Abschließen"
          >
            {stakingRequirements.map((requirement) => (
              <span
                className={
                  requirement.isMet
                    ? "garden-staking-check garden-staking-check--complete"
                    : "garden-staking-check"
                }
                key={requirement.label}
              >
                <i aria-hidden="true">
                  {requirement.isMet ? "✓" : "·"}
                </i>
                {requirement.label}
              </span>
            ))}
          </div>

          <div className="staking-steps" aria-label="Ecken des neuen Beetes">
            {STAKING_STEPS.map((step, stepIndex) => {
              const isComplete = stepIndex < stakingPointCount;
              const isCurrent =
                stepIndex === stakingPointCount && stakingPointCount < 3;

              return (
                <span
                  className={[
                    "staking-step",
                    isComplete ? "staking-step--complete" : "",
                    isCurrent ? "staking-step--current" : "",
                  ]
                    .filter(Boolean)
                    .join(" ")}
                  key={step}
                >
                  <i>{stepIndex + 1}</i>
                  {step}
                </span>
              );
            })}
          </div>

          {stakingPointCount > 0 ? (
            <button
              className="secondary-button"
              onClick={onCancelStaking}
              type="button"
            >
              Abstecken abbrechen
            </button>
          ) : null}
          </div>
        ) : null}

        {gardenMissionStage === "dig" ? (
          <div className="garden-stage-tool-section">
            <p className="eyebrow">Werkzeug</p>
            <div className="garden-stage-tool">
              <GardenSpadeIcon className="garden-spade-icon" />
              <span>
                <strong>Spaten</strong>
                <small>Ein Klick gräbt 1 m² um</small>
              </span>
            </div>
            <div className="garden-work-counter">
              <span>Noch umgraben</span>
              <strong>{remainingDigArea} m²</strong>
            </div>
          </div>
        ) : null}

        {gardenMissionStage === "sow" ? (
          <div className="garden-stage-tool-section">
            <p className="eyebrow">Werkzeug</p>
            <div className="garden-stage-tool garden-stage-tool--seeds">
              <ParametricSack
                ariaHidden
                className="garden-stage-sack-icon"
                height={62}
                kind="seed"
                plantKind={selectedPlantKind}
                title="Saat-Sack"
                width={58}
              />
              <span>
                <strong>Saat-Säcke</strong>
                <small>Größe bestimmen und aufs Beet ziehen</small>
              </span>
            </div>
            <div
              className="garden-seed-sack-builder"
              aria-label="Größe des Saat-Sacks"
            >
              <div className="garden-seed-sack-builder__inputs">
                <label>
                  Breite
                  <span>
                    <input
                      max={MAX_INPUT_DIMENSION}
                      min="1"
                      onChange={(event) =>
                        onCustomWidthChange(
                          normalizeDimensionInput(event.target.value),
                        )
                      }
                      type="number"
                      value={customWidthCells}
                    />
                    m
                  </span>
                </label>
                <label>
                  Höhe
                  <span>
                    <input
                      max={MAX_INPUT_DIMENSION}
                      min="1"
                      onChange={(event) =>
                        onCustomHeightChange(
                          normalizeDimensionInput(event.target.value),
                        )
                      }
                      type="number"
                      value={customHeightCells}
                    />
                    m
                  </span>
                </label>
              </div>
              <button
                className="garden-seed-sack-create"
                disabled={!canSpawnCustomShape}
                onClick={() => {
                  if (parsedCustomWidthCells && parsedCustomHeightCells) {
                    onAddSeedSack(
                      parsedCustomWidthCells,
                      parsedCustomHeightCells,
                    );
                  }
                }}
                type="button"
              >
                <span className="garden-seed-sack-create__preview">
                  <ParametricSack
                    ariaHidden
                    height={previewHeight}
                    kind="seed"
                    plantKind={selectedPlantKind}
                    title="Eigener Saat-Sack"
                    width={previewWidth}
                  />
                </span>
                <span>
                  <strong>
                    {canSpawnCustomShape
                      ? `${parsedCustomWidthCells} × ${parsedCustomHeightCells} m`
                      : "Maße eingeben"}
                  </strong>
                  <small>Saat-Sack bereitstellen</small>
                </span>
              </button>
            </div>
            <div className="garden-work-counter">
              <span>Noch aussäen</span>
              <strong>{remainingSowArea} m²</strong>
            </div>
          </div>
        ) : null}

        {gardenMissionStage === "stake" ? (
          <div
            className="selection-panel"
            aria-label={
              design.requireConnectedLayout
                ? "Ausgewähltes Rechteckteil"
                : "Ausgewähltes Beet"
            }
          >
          <p className="eyebrow">Auswahl</p>
          {selectedShape ? (
            <>
              <div className="selection-summary">
                <strong>
                  {selectedShape.widthCells} × {selectedShape.heightCells}
                </strong>
                <span>{getShapeArea(selectedShape)} m²</span>
              </div>
              {!design.requireConnectedLayout ? (
                <div className="selected-bed-thread-length">
                  <span>Umrandung</span>
                  <strong>
                    {getRectangleThreadLength(
                      selectedShape.widthCells,
                      selectedShape.heightCells,
                    )}{" "}
                    m Faden
                  </strong>
                </div>
              ) : null}
              <button
                className="danger-button"
                onClick={onDeleteSelectedShape}
                type="button"
              >
                {design.requireConnectedLayout
                  ? "Rechteckteil entfernen"
                  : "Beet entfernen"}
              </button>
            </>
          ) : (
            <p className="selection-empty">{emptySelectionCopy}</p>
          )}
          </div>
        ) : null}

        {gardenMissionStage === "sow" ? (
          <div
            className="selection-panel"
            aria-label="Ausgewählter Saat-Sack"
          >
            <p className="eyebrow">Auswahl</p>
            {selectedGardenSeedSack ? (
              <>
                <div className="selection-summary">
                  <strong>
                    {selectedGardenSeedSack.widthCells} ×{" "}
                    {selectedGardenSeedSack.heightCells} m
                  </strong>
                  <span>{getShapeArea(selectedGardenSeedSack)} m²</span>
                </div>
                <button
                  className="danger-button"
                  onClick={onDeleteSelectedShape}
                  type="button"
                >
                  Saat-Sack entfernen
                </button>
              </>
            ) : (
              <p className="selection-empty">
                Stelle einen Saat-Sack bereit und ziehe ihn auf die braune Erde.
              </p>
            )}
          </div>
        ) : null}

        <div className="action-row">
          <button
            className="secondary-button"
            onClick={onRestartMission}
            type="button"
          >
            Neustarten
          </button>
          {gardenMissionStage === "stake" ? (
            <button
              className="primary-button"
              disabled={!status.isComplete}
              onClick={onCompleteStaking}
              type="button"
            >
              Abstecken fertig
            </button>
          ) : gardenMissionStage === "dig" ? (
            <button
              className="primary-button"
              disabled={!canCompleteDigging}
              onClick={onCompleteDigging}
              type="button"
            >
              Aussäen beginnen
            </button>
          ) : (
            <button
              className="primary-button"
              disabled={!canAdvance || !hasNextMission}
              onClick={onGoToNextMission}
              type="button"
            >
              {primaryActionLabel}
            </button>
          )}
        </div>
        {showDevControls ? (
          <div className="dev-navigation">
            <button
              className="dev-button dev-button--previous"
              onClick={onGoToPreviousMissionInDevMode}
              type="button"
            >
              Dev: Voriges Level
            </button>
            <button
              className="dev-button dev-button--next"
              onClick={onGoToNextMissionInDevMode}
              type="button"
            >
              Dev: Nächstes Level
            </button>
          </div>
        ) : null}
      </section>
    </aside>
  );
}
