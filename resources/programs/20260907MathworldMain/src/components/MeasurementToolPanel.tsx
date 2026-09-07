import { useEffect, useState, type KeyboardEvent } from "react";
import {
  getMeasurementEdgeKey,
  getRectangleAreaTotal,
} from "../logic/measurement";
import type { RectangleAreaConfig } from "../types";

type MeasurementToolPanelProps = {
  activeMissionId: number;
  answer: string;
  canAdvance: boolean;
  hasNextMission: boolean;
  isComplete: boolean;
  isMeasureToolActive: boolean;
  measuredEdges: Set<string>;
  measurement: RectangleAreaConfig;
  onAnswerChange: (answer: string) => void;
  onGoToNextMission: () => void;
  onGoToNextMissionInDevMode: () => void;
  onGoToPreviousMissionInDevMode: () => void;
  onRestartMission: () => void;
  onToggleMeasureTool: () => void;
  primaryActionLabel?: string;
  showDevControls: boolean;
  variantId: string;
};

function normalizeAreaInput(value: string) {
  return value.replace(/\D/g, "").slice(0, 3);
}

export function MeasurementToolPanel({
  activeMissionId,
  answer,
  canAdvance,
  hasNextMission,
  isComplete,
  isMeasureToolActive,
  measuredEdges,
  measurement,
  onAnswerChange,
  onGoToNextMission,
  onGoToNextMissionInDevMode,
  onGoToPreviousMissionInDevMode,
  onRestartMission,
  onToggleMeasureTool,
  primaryActionLabel = "Nächstes Level",
  showDevControls,
  variantId,
}: MeasurementToolPanelProps) {
  const [hasCheckedAnswer, setHasCheckedAnswer] = useState(false);
  const requiredEdgeCount = measurement.beds.length * 2;
  const hasAllMeasurements = measuredEdges.size === requiredEdgeCount;
  const expectedArea = getRectangleAreaTotal(measurement);
  const parsedAnswer = Number.parseInt(answer, 10);
  const isAnswerCorrect = parsedAnswer === expectedArea;

  useEffect(() => {
    setHasCheckedAnswer(false);
  }, [activeMissionId, variantId]);

  function updateAnswer(nextAnswer: string) {
    setHasCheckedAnswer(false);
    onAnswerChange(normalizeAreaInput(nextAnswer));
  }

  function checkAnswer() {
    setHasCheckedAnswer(true);
  }

  function handleAnswerKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key !== "Enter") {
      return;
    }

    event.preventDefault();

    if (isComplete && hasNextMission) {
      onGoToNextMission();
      return;
    }

    checkAnswer();
  }

  let answerFeedback = "";

  if (hasCheckedAnswer && !hasAllMeasurements) {
    answerFeedback = "Miss zuerst alle markierten Seiten.";
  } else if (hasCheckedAnswer && !answer) {
    answerFeedback = "Trage die berechnete Fläche ein.";
  } else if (hasCheckedAnswer && !isAnswerCorrect) {
    answerFeedback =
      measurement.beds.length > 1
        ? "Berechne beide Rechtecke und addiere die Teilflächen."
        : "Multipliziere die gemessene Länge mit der gemessenen Breite.";
  }

  return (
    <aside className="tool-panel" aria-label="Werkzeuge und Antwort">
      <section className="tool-panel__content">
        <p className="eyebrow">Werkzeug</p>
        <button
          aria-pressed={isMeasureToolActive}
          className={
            isMeasureToolActive
              ? "measurement-tool-button measurement-tool-button--active"
              : "measurement-tool-button"
          }
          onClick={onToggleMeasureTool}
          type="button"
        >
          <span aria-hidden="true" className="measurement-tape-icon measurement-tape-icon--large" />
          <span>
            <strong>Maßband</strong>
            <small>{isMeasureToolActive ? "ausgewählt" : "auswählen"}</small>
          </span>
        </button>

        <div className="measurement-formulas" aria-label="Gemessene Rechtecke">
          <p className="eyebrow">Messwerte</p>
          {measurement.beds.map((bed) => {
            const hasWidth = measuredEdges.has(getMeasurementEdgeKey(bed.id, "width"));
            const hasHeight = measuredEdges.has(getMeasurementEdgeKey(bed.id, "height"));

            return (
              <div className="measurement-formula" key={bed.id}>
                <span>{bed.label}</span>
                <strong>
                  {hasWidth ? bed.widthCells : "?"} m x {hasHeight ? bed.heightCells : "?"} m
                </strong>
              </div>
            );
          })}
        </div>

        <div className="measurement-answer-panel">
          <label htmlFor="measurement-area-answer">
            {measurement.beds.length > 1 ? "Gesamte Fläche" : "Fläche"}
          </label>
          <div className="measurement-answer-input">
            <input
              aria-describedby={answerFeedback ? "measurement-answer-feedback" : undefined}
              id="measurement-area-answer"
              inputMode="numeric"
              onChange={(event) => updateAnswer(event.target.value)}
              onKeyDown={handleAnswerKeyDown}
              type="text"
              value={answer}
            />
            <span>m²</span>
          </div>
          <button className="secondary-button" onClick={checkAnswer} type="button">
            Ergebnis prüfen
          </button>
          {answerFeedback ? (
            <p className="fraction-answer-error" id="measurement-answer-feedback">
              {answerFeedback}
            </p>
          ) : null}
          {isComplete ? (
            <p className="measurement-answer-success">Richtig: {expectedArea} m².</p>
          ) : null}
        </div>

        <div className="action-row">
          <button className="secondary-button" onClick={onRestartMission} type="button">
            Neustarten
          </button>
          <button
            className="primary-button"
            disabled={!canAdvance || !hasNextMission}
            onClick={onGoToNextMission}
            type="button"
          >
            {primaryActionLabel}
          </button>
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
