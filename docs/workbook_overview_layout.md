# Workbook Overview Visualization Redesign

## 1. Survey of Sugiyama-Style Graph Layout Frameworks

Several widely used graph drawing frameworks implement Sugiyama-style layered layouts. They share a common pipeline—layer assignment, crossing minimisation, coordinate assignment—but differ in optimisation heuristics and configuration surfaces.

### Graphviz / DOT
* **Layering:** Defaults to longest-path layering with options for min-rank / max-rank constraints. Nodes can be kept compact with `rank=same` and `ranksep`/`nodesep` parameters.
* **Crossing minimisation:** Uses a barycentric heuristic with randomised restarts. Supports ordering constraints (e.g. `ordering="out"`).
* **Positioning:** Horizontal coordinates derived from network simplex optimisation to balance edge lengths. Offers spline edges with configurable curvature.

### Dagre (and dagre-d3)
* **Layering:** Implements Sugiyama with longest-path + network simplex optimisation. Supports user-defined rank separation.
* **Crossing minimisation:** Multi-pass barycentric ordering with median heuristics. Edge label / port handling reduces crossings by aligning edge attachment points.
* **Positioning:** Uses iterative relaxation to compute x/y coordinates. Output emphasises straight edges with minimal bend count, but supports spline curves.

### Eclipse Layout Kernel (ELK) – Layered Algorithm
* **Layering:** Advanced constraint system (priorities, minimum/maximum length, layering strategies like longest path, network simplex, or interactive).
* **Crossing minimisation:** Sophisticated heuristics including greedy switch, median, and sifting with configurable iterations and constraints.
* **Positioning:** Dedicated segment for node placement including compaction steps, balancers, and edge routing with orthogonal or spline options.

### Common Takeaways
1. **Layer-first approach:** Compute a strict left-to-right order via layering based on required dependencies. Recommended edges can be relaxed but should not violate topological order.
2. **Crossing reduction via barycentric/median ordering:** Iterate between layers while anchoring already-ordered layers to minimise edge crossings without exact optimisation.
3. **Compaction step:** After ordering, solve for coordinates that keep nodes compact, using per-layer alignment and spacing constraints.
4. **Dedicated edge routing:** Curved edges (splines or Bezier) improve readability when horizontal spacing is tight; dashed styling differentiates edge types.

These principles inform the redesign: maintain the layered dependency ordering, but ensure sections occupy minimal vertical footprint and exercises follow a clear horizontal flow.

## 2. Goals for the New Workbook Overview
* Present the workbook as a left-to-right progression where horizontal position encodes ordering.
* Use compact section capsules showing only the title and an inline sequence of exercises (tasks) arranged horizontally.
* Provide a separate exercise lane with consistent vertical alignment so the progression of tasks is easy to scan.
* Draw dependencies as smooth curves with solid strokes for required relationships and dashed strokes for recommended ones.
* Preserve readability for sections with different exercise counts by automatically sizing capsules and distributing exercises evenly.

## 3. Proposed Layout Changes

### 3.1 Data & Layout Model
* Extend the layout model to explicitly represent exercises with absolute coordinates (`ExerciseNodeLayout`) instead of only relative bubble offsets.
* Compute a layered order of sections using existing dependency logic to keep Sugiyama semantics (required edges dictate rank).
* Within each layer, compact sections horizontally by using maximum exercise capsule width as the layer width. Maintain vertical alignment (single row) to deliver the requested "lane" appearance.

### 3.2 Section Geometry
* Represent sections as rounded capsules with small height (e.g. title strip above exercise lane).
* Measure exercise capsules based on estimated duration, clamped between configurable min/max widths; place them in a single horizontal row with uniform gap.
* Compute section width as sum of exercise capsule widths plus padding. Section height becomes padding + title band + exercise lane height.

### 3.3 Exercise Lane Alignment
* Align the exercise lane baseline across all sections to create a visually continuous horizontal band. Keep section titles immediately above.
* Store the exercise lane Y-offset in the layout so renderer can draw exercises outside the main capsule border if needed.

### 3.4 Edge Routing
* Route edges between the trailing edge of source section (midpoint of last exercise) and the leading edge of target section (first exercise midpoint).
* Use cubic Bezier curves with pronounced curvature (control points offset vertically) to emphasise flow. Dashed pattern applied to recommended edges.
* Introduce slight vertical offsets when multiple edges share the same source to avoid overlap.

## 4. Rendering Adjustments
* Update the section renderer to draw compact capsules with rounded rectangles, title text, and horizontal exercise pills (rounded rectangles).
* Replace "No exercises" placeholder with a minimal pill labelled accordingly, ensuring consistent height.
* Update edge renderer to use new anchor points from exercises and to enforce curved routing. Arrowheads positioned tangentially to the curve endpoint.

## 5. Configuration Parameters
Add or update `VisualizationConfig` fields:
* `sectionCornerRadius`, `exerciseCornerRadius` for rounded styling.
* `sectionHeight`, `exerciseHeight`, `exerciseGap`, `titleFontSize`, `exerciseFontSize` for sizing.
* `laneBaselineOffset` to align exercise lanes.
* `edgeCurveStrength`, `edgeLaneOffset` to tune curvature and multi-edge spacing.

## 6. Implementation Plan
1. **Model Update:** Extend `SectionNode` to compute exercise layouts with absolute positions, storing anchor points for edges.
2. **Layout Algorithm:** Modify `assignCoordinates` to position sections in a single row per layer with compact spacing. Compute exercise absolute coordinates during geometry step using new configuration values.
3. **Rendering:** Update `SectionRenderer` and `EdgeRenderer` to draw rounded capsules and use the new exercise coordinates and anchor points.
4. **Styling Fine-Tuning:** Adjust colors, stroke widths, and fonts to match the provided visual inspiration.
5. **Verification:** Render sample workbook to verify horizontal progression, arrow curvature, and dashed styling.

## 7. Testing Strategy
* Run existing test suite (`sbt test`) to ensure no regressions.
* Manually load a representative workbook to visually confirm layout changes (if automated UI tests are unavailable).

## 8. Follow-Up Evaluation & Improvement Plan

A brief audit of the current implementation against layered-graph best practices surfaced several opportunities for further refinement:

1. **Row-wise compaction for uniform lanes.** Sugiyama frameworks such as Dagre and ELK emphasise a dedicated compaction stage so that aligned ranks occupy minimal vertical space and preserve consistent anchor lanes for edge routing. The current `assignCoordinates` method still stacks sections sequentially inside a rank, which creates drifting exercise lanes and wastes vertical space. *Plan:* introduce row baselines shared across ranks—derive per-order rows, compute their heights, and place every section so that its exercise lane sits on a global centre line. This keeps the workbook legible as a left-to-right conveyor of tasks while adhering to the compaction recommendation.【F:docs/workbook_overview_layout.md†L5-L68】

2. **Legible exercise labelling inside capsules.** Best-practice gallery diagrams keep node purpose evident; for layered task flows, frameworks typically render node labels directly within the node shapes. Our pills currently omit the exercise titles, forcing users to cross-reference elsewhere. *Plan:* render each exercise title (with truncation where necessary) centred within its pill, ensuring the lane communicates the primary content of the workbook at a glance.【F:docs/workbook_overview_layout.md†L47-L64】

3. **Semantic styling for recommended vs. required edges.** Reference implementations combine stroke style and colour to differentiate semantic edge types. Although we already dash recommended edges, they share the same hue as required edges, diluting the distinction. *Plan:* adjust the configuration so recommended edges use a softer accent colour (while keeping dashes), reinforcing the hierarchy without overwhelming the palette.【F:docs/workbook_overview_layout.md†L47-L64】

These changes will make the overview denser, clearer, and more consistent with established layered layout guidance before we iterate on further niceties.
