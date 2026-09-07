import { useEffect, useState, type KeyboardEvent } from "react";
import { getBoundaryEdgeId, getPerimeterLength } from "../logic/perimeter";
import type { BoundaryEdge } from "../types";

type FenceToolPanelProps = {
  activeMissionId: number;
  answer: string;
  boundaryEdges: BoundaryEdge[];
  canAdvance: boolean;
  fencedEdgeIds: Set<string>;
  hasNextMission: boolean;
  isComplete: boolean;
  isFenceToolActive: boolean;
  onAnswerChange: (answer: string) => void;
  onGoToNextMission: () => void;
  onGoToNextMissionInDevMode: () => void;
  onGoToPreviousMissionInDevMode: () => void;
  onRestartMission: () => void;
  onToggleFenceTool: () => void;
  primaryActionLabel?: string;
  showDevControls: boolean;
  variantId: string;
};

function normalizePerimeterInput(value: string) {
  return value.replace(/\D/g, "").slice(0, 3);
}

export function FenceToolPanel({
  activeMissionId,
  answer,
  boundaryEdges,
  canAdvance,
  fencedEdgeIds,
  hasNextMission,
  isComplete,
  isFenceToolActive,
  onAnswerChange,
  onGoToNextMission,
  onGoToNextMissionInDevMode,
  onGoToPreviousMissionInDevMode,
  onRestartMission,
  onToggleFenceTool,
  primaryActionLabel = "Nächstes Level",
  showDevControls,
  variantId,
}: FenceToolPanelProps) {
  const [hasCheckedAnswer, setHasCheckedAnswer] = useState(false);
  const perimeterLength = getPerimeterLength(boundaryEdges);
  const hasAllFenceEdges =
    boundaryEdges.length > 0 && fencedEdgeIds.size === boundaryEdges.length;
  const isAnswerCorrect = Number.parseInt(answer, 10) === perimeterLength;
  const placedLengths = boundaryEdges
    .filter((edge) => fencedEdgeIds.has(getBoundaryEdgeId(edge)))
    .map((edge) => edge.end - edge.start);

  useEffect(() => {
    setHasCheckedAnswer(false);
  }, [activeMissionId, variantId]);

  function updateAnswer(nextAnswer: string) {
    setHasCheckedAnswer(false);
    onAnswerChange(normalizePerimeterInput(nextAnswer));
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

  if (hasCheckedAnswer && !hasAllFenceEdges) {
    answerFeedback = "Am Beetrand fehlen noch Zaunstücke.";
  } else if (hasCheckedAnswer && !answer) {
    answerFeedback = "Trage die gesamte Zaunlänge ein.";
  } else if (hasCheckedAnswer && !isAnswerCorrect) {
    answerFeedback = "Addiere die Längen aller verlegten Randabschnitte noch einmal.";
  }

  return (
    <aside className="tool-panel" aria-label="Zaunrolle und Antwort">
      <section className="tool-panel__content">
        <p className="eyebrow">Werkzeug</p>
        <button
          aria-pressed={isFenceToolActive}
          className={
            isFenceToolActive
              ? "fence-tool-button fence-tool-button--active"
              : "fence-tool-button"
          }
          onClick={onToggleFenceTool}
          type="button"
        >
          <span aria-hidden="true" className="fence-roll-icon fence-roll-icon--large" />
          <span>
            <strong>Zaunrolle</strong>
            <small>{isFenceToolActive ? "ausgewählt" : "auswählen"}</small>
          </span>
        </button>

        <div className="fence-material-panel">
          <p className="eyebrow">Material</p>
          <div className="fence-material-equation">
            {placedLengths.length > 0 ? placedLengths.join(" m + ") : "Noch kein Zaun"}
            {placedLengths.length > 0 ? " m" : ""}
            {!hasAllFenceEdges && placedLengths.length > 0 ? " + ..." : ""}
          </div>
          <span>
            {fencedEdgeIds.size} von {boundaryEdges.length} Randabschnitten verlegt
          </span>
        </div>

        <div className="measurement-answer-panel">
          <label htmlFor="fence-perimeter-answer">Gesamter Umfang</label>
          <div className="measurement-answer-input">
            <input
              aria-describedby={answerFeedback ? "fence-answer-feedback" : undefined}
              id="fence-perimeter-answer"
              inputMode="numeric"
              onChange={(event) => updateAnswer(event.target.value)}
              onKeyDown={handleAnswerKeyDown}
              type="text"
              value={answer}
            />
            <span>m</span>
          </div>
          <button className="secondary-button" onClick={checkAnswer} type="button">
            Ergebnis prüfen
          </button>
          {answerFeedback ? (
            <p className="fraction-answer-error" id="fence-answer-feedback">
              {answerFeedback}
            </p>
          ) : null}
          {isComplete ? (
            <p className="measurement-answer-success">Richtig: {perimeterLength} m Zaun.</p>
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
