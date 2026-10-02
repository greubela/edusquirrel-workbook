import { MAX_INPUT_DIMENSION } from "../config/workspace";
import { getShapeArea, normalizeDimensionInput } from "../logic/geometry";
import type { FractionReadConfig, MissionConfig, RewardKind, ToolConfig, WorkspaceShape } from "../types";
import { useEffect, useState, type KeyboardEvent } from "react";
import { ParametricSack } from "./ParametricSack";
import { PlantPicker } from "./PlantPicker";

type ResizeLimits = {
  maxHeightCells: number;
  maxWidthCells: number;
};

type FractionAnswerInput = {
  denominator: string;
  numerator: string;
};

type SimplificationAnswer = "yes" | "no";

type ToolPanelProps = {
  activeMission: MissionConfig;
  allowedPlantKinds: readonly RewardKind[];
  canAdvance: boolean;
  canResizeSelectedShape: boolean;
  canSpawnCustomShape: boolean;
  customHeightCells: string;
  customWidthCells: string;
  canSimplifyFraction: boolean;
  fractionAnswer: FractionAnswerInput;
  fractionTipIndex: number | null;
  fractionTarget: FractionReadConfig | null;
  hasNextMission: boolean;
  parsedCustomHeightCells: number | null;
  parsedCustomWidthCells: number | null;
  primaryActionLabel?: string;
  selectedResizeLimits: ResizeLimits | null;
  selectedPlantKind: RewardKind;
  selectedShape: WorkspaceShape | null;
  shouldShowSimplificationPrompt: boolean;
  showDevControls: boolean;
  showPlantPicker: boolean;
  simplifiedFractionAnswer: FractionAnswerInput;
  simplifiedFraction: {
    denominator: number;
    numerator: number;
  } | null;
  simplificationAnswer: SimplificationAnswer | null;
  onAddCustomShape: (widthCells: number, heightCells: number) => void;
  onAddShape: (tool: ToolConfig) => void;
  onCustomHeightChange: (value: string) => void;
  onCustomWidthChange: (value: string) => void;
  onDeleteSelectedShape: () => void;
  onFractionAnswerChange: (answer: FractionAnswerInput) => void;
  onFractionTipIndexChange: (tipIndex: number | null | ((currentTipIndex: number | null) => number | null)) => void;
  onGoToNextMission: () => void;
  onGoToNextMissionInDevMode: () => void;
  onGoToPreviousMissionInDevMode: () => void;
  onRestartMission: () => void;
  onSelectPlant?: (rewardKind: RewardKind) => void;
  onSimplifiedFractionAnswerChange: (answer: FractionAnswerInput) => void;
  onSimplificationAnswerChange: (answer: SimplificationAnswer) => void;
  onUpdateSelectedShapeDimension: (dimension: "widthCells" | "heightCells", delta: number) => void;
};

type ToolSeedSackPreviewProps = {
  heightCells: number;
  plantKind: RewardKind;
  widthCells: number;
};

function ToolSeedSackPreview({ heightCells, plantKind, widthCells }: ToolSeedSackPreviewProps) {
  const previewWidth = Math.max(30, Math.min(66, 24 + widthCells * 10));
  const previewHeight = Math.max(32, Math.min(62, 26 + heightCells * 9));

  return (
    <ParametricSack
      ariaHidden
      className="shape-button__sack"
      height={previewHeight}
      kind="seed"
      plantKind={plantKind}
      style={{
        height: `${previewHeight}px`,
        width: `${previewWidth}px`,
      }}
      title={`${widthCells} mal ${heightCells} Saatgutsack`}
      width={previewWidth}
    />
  );
}

function normalizeFractionInput(value: string) {
  return value.replace(/\D/g, "").slice(0, 2);
}

const FRACTION_TIPS = [
  ["Nenner = alle Felder.", "Zaehler = die eingefaerbten Felder."],
  ["Kuerzen bedeutet:", "den Bruch mit kleineren Zahlen aufzuschreiben."],
  ["Nicht jeden Bruch", "kann man kuerzen."],
  ["Du darfst dir die eingefaerbten Felder", "gedanklich umsortiert vorstellen."],
  ["Kuerzen:", "Zaehler und Nenner", "durch dieselbe Zahl teilen."],
];

const SUPPLY_FRACTION_TIPS = [
  ["Nenner = alle Beete.", "Zaehler = die versorgten Beete."],
  ["Ein Sack ist das Ganze.", "Jedes Beet bekommt gleich viel."],
  ["Nicht jeden Bruch", "kann man kuerzen."],
  ["Die Lage der versorgten Beete", "aendert den Bruch nicht."],
  ["Kuerzen:", "Zaehler und Nenner", "durch dieselbe Zahl teilen."],
];

type FractionInputStackProps = {
  answer: FractionAnswerInput;
  ariaLabel: string;
  onAnswerChange: (answer: FractionAnswerInput) => void;
  onBlur?: () => void;
  onSubmit: () => void;
};

function FractionInputStack({ answer, ariaLabel, onAnswerChange, onBlur, onSubmit }: FractionInputStackProps) {
  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key !== "Enter") {
      return;
    }

    event.preventDefault();
    onSubmit();
  }

  return (
    <div className="fraction-input-stack" aria-label={ariaLabel}>
      <span className="fraction-numerator-label">Zaehler</span>
      <input
        aria-label="Zaehler"
        inputMode="numeric"
        onChange={(event) =>
          onAnswerChange({
            ...answer,
            numerator: normalizeFractionInput(event.target.value),
          })
        }
        onBlur={onBlur}
        onKeyDown={handleKeyDown}
        type="text"
        value={answer.numerator}
      />
      <span className="fraction-divider" aria-hidden="true" />
      <input
        aria-label="Nenner"
        inputMode="numeric"
        onChange={(event) =>
          onAnswerChange({
            ...answer,
            denominator: normalizeFractionInput(event.target.value),
          })
        }
        onBlur={onBlur}
        onKeyDown={handleKeyDown}
        type="text"
        value={answer.denominator}
      />
      <span className="fraction-denominator-label">Nenner</span>
    </div>
  );
}

export function ToolPanel({
  activeMission,
  allowedPlantKinds,
  canAdvance,
  canResizeSelectedShape,
  canSpawnCustomShape,
  canSimplifyFraction,
  customHeightCells,
  customWidthCells,
  fractionAnswer,
  fractionTipIndex,
  fractionTarget,
  hasNextMission,
  onAddCustomShape,
  onAddShape,
  onCustomHeightChange,
  onCustomWidthChange,
  onDeleteSelectedShape,
  onFractionAnswerChange,
  onFractionTipIndexChange,
  onGoToNextMission,
  onGoToNextMissionInDevMode,
  onGoToPreviousMissionInDevMode,
  onRestartMission,
  onSelectPlant,
  onUpdateSelectedShapeDimension,
  parsedCustomHeightCells,
  parsedCustomWidthCells,
  primaryActionLabel = "Naechstes Level",
  selectedResizeLimits,
  selectedPlantKind,
  selectedShape,
  shouldShowSimplificationPrompt,
  showDevControls,
  showPlantPicker,
  simplifiedFractionAnswer,
  simplifiedFraction,
  simplificationAnswer,
  onSimplifiedFractionAnswerChange,
  onSimplificationAnswerChange,
}: ToolPanelProps) {
  const isFractionMission = activeMission.kind === "fraction-read";
  const supply = fractionTarget?.supply;
  const [hasFractionAnswerError, setHasFractionAnswerError] = useState(false);
  const [hasSimplifiedFractionAnswerError, setHasSimplifiedFractionAnswerError] = useState(false);
  const fractionTips = supply ? SUPPLY_FRACTION_TIPS : FRACTION_TIPS;
  const activeFractionTip = fractionTipIndex === null ? null : fractionTips[fractionTipIndex];
  const parsedFractionAnswer = {
    numerator: Number.parseInt(fractionAnswer.numerator, 10),
    denominator: Number.parseInt(fractionAnswer.denominator, 10),
  };
  const hasCompleteFractionAnswer =
    Number.isInteger(parsedFractionAnswer.numerator) &&
    Number.isInteger(parsedFractionAnswer.denominator) &&
    parsedFractionAnswer.denominator > 0;
  const isFractionAnswerCorrect =
    Boolean(fractionTarget) &&
    parsedFractionAnswer.numerator === fractionTarget?.numerator &&
    parsedFractionAnswer.denominator === fractionTarget?.denominator;
  const parsedSimplifiedFractionAnswer = {
    numerator: Number.parseInt(simplifiedFractionAnswer.numerator, 10),
    denominator: Number.parseInt(simplifiedFractionAnswer.denominator, 10),
  };
  const hasCompleteSimplifiedFractionAnswer =
    Number.isInteger(parsedSimplifiedFractionAnswer.numerator) &&
    Number.isInteger(parsedSimplifiedFractionAnswer.denominator) &&
    parsedSimplifiedFractionAnswer.denominator > 0;
  const isSimplifiedFractionAnswerCorrect =
    Boolean(simplifiedFraction) &&
    parsedSimplifiedFractionAnswer.numerator === simplifiedFraction?.numerator &&
    parsedSimplifiedFractionAnswer.denominator === simplifiedFraction?.denominator;
  const canSubmitWithEnter = canAdvance && hasNextMission;
  const submitAnswer = () => {
    if (canSubmitWithEnter) {
      onGoToNextMission();
    }
  };

  useEffect(() => {
    onFractionTipIndexChange(null);
    setHasFractionAnswerError(false);
    setHasSimplifiedFractionAnswerError(false);
  }, [activeMission.id, fractionTarget?.numerator, fractionTarget?.denominator, onFractionTipIndexChange]);

  useEffect(() => {
    setHasSimplifiedFractionAnswerError(false);
  }, [simplificationAnswer]);

  function showNextFractionTip() {
    onFractionTipIndexChange((currentTipIndex) =>
      currentTipIndex === null ? 0 : (currentTipIndex + 1) % fractionTips.length,
    );
  }

  function showFractionAnswerErrorIfNeeded() {
    if (!hasCompleteFractionAnswer || isFractionAnswerCorrect) {
      return;
    }

    setHasFractionAnswerError(true);
    onFractionTipIndexChange((currentTipIndex) => currentTipIndex ?? 0);
  }

  function submitMainFractionAnswer() {
    if (isFractionAnswerCorrect) {
      submitAnswer();
      return;
    }

    showFractionAnswerErrorIfNeeded();
  }

  function updateMainFractionAnswer(answer: FractionAnswerInput) {
    setHasFractionAnswerError(false);
    setHasSimplifiedFractionAnswerError(false);
    onFractionAnswerChange(answer);
  }

  function showSimplifiedFractionAnswerErrorIfNeeded() {
    if (!hasCompleteSimplifiedFractionAnswer || isSimplifiedFractionAnswerCorrect) {
      return;
    }

    setHasSimplifiedFractionAnswerError(true);
    onFractionTipIndexChange((currentTipIndex) => currentTipIndex ?? 1);
  }

  function submitSimplifiedFractionAnswer() {
    if (isSimplifiedFractionAnswerCorrect) {
      submitAnswer();
      return;
    }

    showSimplifiedFractionAnswerErrorIfNeeded();
  }

  function updateSimplifiedFractionAnswer(answer: FractionAnswerInput) {
    setHasSimplifiedFractionAnswerError(false);
    onSimplifiedFractionAnswerChange(answer);
  }

  return (
    <aside className="tool-panel" aria-label="Werkzeuge und Ziel">
      <section className="tool-panel__content">
        <p className="eyebrow">{isFractionMission ? "Antwort" : "Werkzeuge"}</p>
        {showPlantPicker && onSelectPlant ? (
          <PlantPicker
            allowedPlantKinds={allowedPlantKinds}
            className="plant-picker--mission"
            label="Saatgut fuer den naechsten Sack"
            onSelectPlant={onSelectPlant}
            selectedPlantKind={selectedPlantKind}
          />
        ) : null}
        {isFractionMission && fractionTarget ? (
          <div className="fraction-answer-panel" aria-label="Bruch eingeben">
            <p className="fraction-answer-copy">
              {supply ? "Welcher Bruchteil des ganzen Sacks wurde bereits verteilt?" : "Welcher Bruchteil ist gefaerbt?"}
            </p>
            <div className="fraction-equation-row">
              <FractionInputStack
                answer={fractionAnswer}
                ariaLabel="Abgelesenen Bruch eingeben"
                onAnswerChange={updateMainFractionAnswer}
                onBlur={showFractionAnswerErrorIfNeeded}
                onSubmit={submitMainFractionAnswer}
              />
              {simplificationAnswer === "yes" ? (
                <>
                  <span className="fraction-equals" aria-hidden="true">
                    =
                  </span>
                  <FractionInputStack
                    answer={simplifiedFractionAnswer}
                    ariaLabel="Gekuerzten Bruch eingeben"
                    onAnswerChange={updateSimplifiedFractionAnswer}
                    onBlur={showSimplifiedFractionAnswerErrorIfNeeded}
                    onSubmit={submitSimplifiedFractionAnswer}
                  />
                </>
              ) : null}
            </div>
            {hasFractionAnswerError ? (
              <p className="fraction-answer-error">
                {supply
                  ? "Das stimmt noch nicht. Zaehle die versorgten Beete und alle Beete noch einmal."
                  : "Das stimmt noch nicht. Zaehle noch einmal die Felder."}
              </p>
            ) : null}
            {hasSimplifiedFractionAnswerError ? (
              <p className="fraction-answer-error">
                Das ist noch nicht die gekuerzte Form. Teile Zaehler und Nenner durch dieselbe Zahl.
              </p>
            ) : null}
            {shouldShowSimplificationPrompt ? (
              <div className="fraction-simplification-panel" aria-label="Bruch kuerzen">
                <p className="fraction-answer-copy">Kannst du den Bruch kuerzen?</p>
                <div className="simplification-choice-row">
                  <button
                    className={simplificationAnswer === "yes" ? "simplification-choice simplification-choice--selected" : "simplification-choice"}
                    onClick={() => onSimplificationAnswerChange("yes")}
                    type="button"
                  >
                    Ja
                  </button>
                  <button
                    className={simplificationAnswer === "no" ? "simplification-choice simplification-choice--selected" : "simplification-choice"}
                    onClick={() => onSimplificationAnswerChange("no")}
                    type="button"
                  >
                    Nein
                  </button>
                </div>
                {simplificationAnswer === "yes" && simplifiedFraction ? (
                  <p className="simplification-feedback">
                    {isSimplifiedFractionAnswerCorrect
                      ? "Richtig, jetzt ist der Bruch vollstaendig gekuerzt."
                      : "Gut gesehen. Trage den gekuerzten Bruch rechts ein."}
                  </p>
                ) : null}
                {simplificationAnswer === "no" && canSimplifyFraction ? (
                  <p className="simplification-feedback">
                    Doch, hier geht noch etwas. Die feinen Linien werden als Hilfe heller.
                  </p>
                ) : null}
                {simplificationAnswer === "yes" && !canSimplifyFraction ? (
                  <p className="simplification-feedback">
                    Dieser eingetragene Bruch ist schon gekuerzt.
                  </p>
                ) : null}
                {simplificationAnswer === "no" && !canSimplifyFraction ? (
                  <p className="simplification-feedback">Richtig, dieser eingetragene Bruch ist schon gekuerzt.</p>
                ) : null}
              </div>
            ) : null}
            <div className="fraction-tip-slot">
              <button className="tip-button" onClick={showNextFractionTip} type="button">
                Tipp
              </button>
              {activeFractionTip ? (
                <p className="selection-empty fraction-tip">
                  {activeFractionTip.map((tipLine) => (
                    <span key={tipLine}>{tipLine}</span>
                  ))}
                </p>
              ) : null}
            </div>
          </div>
        ) : null}

        {!isFractionMission && activeMission.tools.length > 0 ? (
          <div className="shape-tray" aria-label="Vordefinierte Samen-Saecke">
            {activeMission.tools.map((tool) => (
              <button
                className="shape-button"
                disabled={tool.disabled}
                key={tool.id}
                onClick={() => onAddShape(tool)}
                type="button"
              >
                <ToolSeedSackPreview
                  heightCells={tool.heightCells}
                  plantKind={selectedPlantKind}
                  widthCells={tool.widthCells}
                />
                <span className="shape-button__label">{tool.label}</span>
              </button>
            ))}
          </div>
        ) : null}

        {!isFractionMission && activeMission.allowFreeDrawing ? (
          <div className="free-shape-builder">
            <label>
              Seite a
              <input
                max={MAX_INPUT_DIMENSION}
                min="1"
                onChange={(event) => onCustomWidthChange(normalizeDimensionInput(event.target.value))}
                type="number"
                value={customWidthCells}
              />
            </label>
            <label>
              Seite b
              <input
                max={MAX_INPUT_DIMENSION}
                min="1"
                onChange={(event) => onCustomHeightChange(normalizeDimensionInput(event.target.value))}
                type="number"
                value={customHeightCells}
              />
            </label>
            <button
              className="custom-shape-preview"
              disabled={!canSpawnCustomShape}
              onClick={() => {
                if (parsedCustomWidthCells && parsedCustomHeightCells) {
                  onAddCustomShape(parsedCustomWidthCells, parsedCustomHeightCells);
                }
              }}
              type="button"
            >
              <span
                className="custom-shape-swatch"
                style={{
                  height: `${(parsedCustomHeightCells ?? 1) * 16}px`,
                  width: `${(parsedCustomWidthCells ?? 1) * 16}px`,
                }}
              />
              <span>{canSpawnCustomShape ? `${parsedCustomWidthCells} x ${parsedCustomHeightCells}` : "Masse eingeben"}</span>
            </button>
          </div>
        ) : null}

        {!isFractionMission ? (
          <div className="selection-panel" aria-label="Ausgewaehlter Samen-Sack">
            <p className="eyebrow">Auswahl</p>
            {selectedShape && selectedResizeLimits ? (
              <>
                <div className="selection-summary">
                  <strong>
                    {selectedShape.widthCells} x {selectedShape.heightCells}
                  </strong>
                  <span>{getShapeArea(selectedShape)} m²</span>
                </div>
                {selectedShape.kind === "rectangle" && canResizeSelectedShape ? (
                  <div className="dimension-controls" aria-label="Samen-Sack-Groesse bearbeiten">
                    <div className="dimension-stepper">
                      <span>Seite a</span>
                      <div className="stepper-controls">
                        <button
                          aria-label="Seite a verringern"
                          disabled={selectedShape.widthCells <= 1}
                          onClick={() => onUpdateSelectedShapeDimension("widthCells", -1)}
                          type="button"
                        >
                          -
                        </button>
                        <output>{selectedShape.widthCells} m</output>
                        <button
                          aria-label="Seite a vergroessern"
                          disabled={selectedShape.widthCells >= selectedResizeLimits.maxWidthCells}
                          onClick={() => onUpdateSelectedShapeDimension("widthCells", 1)}
                          type="button"
                        >
                          +
                        </button>
                      </div>
                    </div>
                    <div className="dimension-stepper">
                      <span>Seite b</span>
                      <div className="stepper-controls">
                        <button
                          aria-label="Seite b verringern"
                          disabled={selectedShape.heightCells <= 1}
                          onClick={() => onUpdateSelectedShapeDimension("heightCells", -1)}
                          type="button"
                        >
                          -
                        </button>
                        <output>{selectedShape.heightCells} m</output>
                        <button
                          aria-label="Seite b vergroessern"
                          disabled={selectedShape.heightCells >= selectedResizeLimits.maxHeightCells}
                          onClick={() => onUpdateSelectedShapeDimension("heightCells", 1)}
                          type="button"
                        >
                          +
                        </button>
                      </div>
                    </div>
                  </div>
                ) : null}
                {selectedShape.kind !== "rectangle" ? (
                  <p className="selection-empty">Dieser Samen-Sack kann noch nicht bearbeitet werden.</p>
                ) : null}
                <button className="danger-button" onClick={onDeleteSelectedShape} type="button">
                  Samen-Sack loeschen
                </button>
              </>
            ) : (
              <p className="selection-empty">Klicke einen Samen-Sack auf der Arbeitsflaeche an.</p>
            )}
          </div>
        ) : null}

        <div className="action-row">
          <button className="secondary-button" onClick={onRestartMission} type="button">
            Neustarten
          </button>
          <button className="primary-button" disabled={!canAdvance || !hasNextMission} onClick={onGoToNextMission} type="button">
            {primaryActionLabel}
          </button>
        </div>
        {showDevControls ? (
          <div className="dev-navigation">
            <button className="dev-button dev-button--previous" onClick={onGoToPreviousMissionInDevMode} type="button">
              Dev: Voriges Level
            </button>
            <button className="dev-button dev-button--next" onClick={onGoToNextMissionInDevMode} type="button">
              Dev: Naechstes Level
            </button>
          </div>
        ) : null}
      </section>
    </aside>
  );
}
