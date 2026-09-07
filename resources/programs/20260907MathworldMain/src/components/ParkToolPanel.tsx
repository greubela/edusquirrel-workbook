import { useEffect, useState, type KeyboardEvent } from "react";
import {
  getParkAreaFormula,
  getParkAreaTotal,
  getParkPartFormula,
} from "../logic/parkArea";
import type { ParkAreaConfig } from "../types";

type ParkToolPanelProps = {
  activeMissionId: number;
  answer: string;
  canAdvance: boolean;
  hasNextMission: boolean;
  isComplete: boolean;
  isPlanVisible: boolean;
  onAnswerChange: (answer: string) => void;
  onGoToNextMission: () => void;
  onGoToNextMissionInDevMode: () => void;
  onGoToPreviousMissionInDevMode: () => void;
  onRestartMission: () => void;
  onTogglePlan: () => void;
  parkArea: ParkAreaConfig;
  primaryActionLabel?: string;
  showDevControls: boolean;
  variantId: string;
};

function normalizeAreaInput(value: string) {
  return value.replace(/\D/g, "").slice(0, 3);
}

export function ParkToolPanel({
  activeMissionId,
  answer,
  canAdvance,
  hasNextMission,
  isComplete,
  isPlanVisible,
  onAnswerChange,
  onGoToNextMission,
  onGoToNextMissionInDevMode,
  onGoToPreviousMissionInDevMode,
  onRestartMission,
  onTogglePlan,
  parkArea,
  primaryActionLabel = "Nächstes Level",
  showDevControls,
  variantId,
}: ParkToolPanelProps) {
  const [hasCheckedAnswer, setHasCheckedAnswer] = useState(false);
  const expectedArea = getParkAreaTotal(parkArea);
  const isAnswerCorrect = Number.parseInt(answer, 10) === expectedArea;

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

  if (hasCheckedAnswer && !isPlanVisible) {
    answerFeedback = "Lege zuerst das Hilfsrechteck über den Parkplan.";
  } else if (hasCheckedAnswer && !answer) {
    answerFeedback = `Trage die ${parkArea.resultLabel.toLocaleLowerCase("de-DE")} ein.`;
  } else if (hasCheckedAnswer && !isAnswerCorrect) {
    answerFeedback =
      parkArea.mode === "remaining"
        ? "Ziehe die Fläche der Aussparung von der ganzen Rechteckfläche ab."
        : parkArea.mode === "triangle"
          ? "Berechne Länge mal Breite und halbiere das Ergebnis."
          : "Addiere die berechneten Flächen von Rechteck und Dreieck.";
  }

  return (
    <aside className="tool-panel" aria-label="Parkplan und Antwort">
      <section className="tool-panel__content">
        <p className="eyebrow">Werkzeug</p>
        <button
          aria-pressed={isPlanVisible}
          className={
            isPlanVisible
              ? "park-tool-button park-tool-button--active"
              : "park-tool-button"
          }
          onClick={onTogglePlan}
          type="button"
        >
          <span aria-hidden="true" className="park-plan-icon park-plan-icon--large" />
          <span>
            <strong>Hilfsrechteck</strong>
            <small>{isPlanVisible ? "im Plan sichtbar" : "in den Plan legen"}</small>
          </span>
        </button>

        <div className="park-formulas" aria-label="Teilflächen im Parkplan">
          <p className="eyebrow">Flächenplan</p>
          {parkArea.parts.map((part, partIndex) => (
            <div className="park-formula" key={part.id}>
              <span>
                {partIndex > 0 ? (part.operation === "subtract" ? "Abziehen: " : "Addieren: ") : ""}
                {part.label}
              </span>
              <strong>{isPlanVisible ? getParkPartFormula(part) : "? × ?"}</strong>
            </div>
          ))}
          <div className="park-equation">
            {isPlanVisible ? getParkAreaFormula(parkArea) : "Hilfsrechteck einblenden"}
          </div>
        </div>

        <div className="measurement-answer-panel">
          <label htmlFor="park-area-answer">{parkArea.resultLabel}</label>
          <div className="measurement-answer-input">
            <input
              aria-describedby={answerFeedback ? "park-answer-feedback" : undefined}
              id="park-area-answer"
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
            <p className="fraction-answer-error" id="park-answer-feedback">
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
