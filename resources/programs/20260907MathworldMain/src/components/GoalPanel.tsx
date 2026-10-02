import type { MissionCoverage } from "../types";

type GoalPanelProps = {
  coverage: MissionCoverage;
  feedbackText: string;
  shapeCount: number;
  targetArea: number;
  totalShapeArea: number;
};

export function GoalPanel({ coverage, feedbackText, shapeCount, targetArea, totalShapeArea }: GoalPanelProps) {
  return (
    <section className="goal-panel">
      <p className="eyebrow">Ziel</p>
      <h2>{targetArea} m² aussähen</h2>
      <p>
        Ziehe die passenden Formen in die Zielfläche. Beim Loslassen snappen sie ins Raster. Neustarten erzeugt eine
        neue Variante dieses Levels.
      </p>
      <div className={coverage.isComplete ? "feedback-placeholder feedback-placeholder--success" : "feedback-placeholder"}>
        {feedbackText}
      </div>
      <dl className="measurement-list" aria-label="Live-Bemassung">
        <div>
          <dt>Erzeugte Fläche</dt>
          <dd>{totalShapeArea} m²</dd>
        </div>
        <div>
          <dt>Formen</dt>
          <dd>{shapeCount}</dd>
        </div>
      </dl>
    </section>
  );
}
