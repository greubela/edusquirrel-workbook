# HtmlWorkbookOverview Visualization Plan

## Image Description

The reference image shows four rectangular section boxes laid out horizontally from left to right. Each section is a rounded rectangle with a light blue background and a dark teal border/fill for content. Inside the rectangles, circular or pill-shaped "bubbles" represent exercises. The width of each bubble scales with the exercise's estimated duration, so longer exercises appear as elongated pills while short ones appear nearly circular. Sections are connected with arrows: solid arrows denote mandatory prerequisites, and dashed arrows denote recommended (non-mandatory) prerequisites. The arrows follow a layered left-to-right ordering without crossing, emphasizing dependency flow.

## Visualization Goals

* Place sections in horizontal layers (columns) according to dependency depth, so that prerequisite sections appear to the left of dependent sections.
* Within each section rectangle, stack exercise bubbles vertically with consistent spacing, and scale bubble width relative to the maximum exercise duration across the whole workbook (respecting minimum/maximum widths for readability).
* Draw curved arrows between section rectangles to visualize `sectionsRequiredBefore` (solid line + arrowhead) and `sectionsRecommendedBefore` (dashed line + arrowhead).
* Render everything on an `SvgCanvas` via the existing `AppCanvas` abstraction.

## Sugiyama-Style Algorithm Plan

1. **Input Graph**
   * Vertices: `ExerciseSection`
   * Edges: Directed edges for both required and recommended dependencies.
   * Graph is acyclic (given), but we still need to detect and handle invalid inputs gracefully (e.g., empty section list, disconnected subgraphs).

2. **Layer Assignment**
   * Compute topological ordering based on required dependencies first. Recommended edges do not constrain topological depth but should not violate layer order if they point backward. We will:
     * Treat required edges as precedence constraints when computing depths.
     * Allow recommended edges to span multiple layers but ensure they never point to the left by adjusting node depth to `max(currentDepth, recommendedSourceDepth + 1)` when necessary.
   * If multiple components are disconnected (no dependencies), place them in the earliest possible layer but maintain given ordering for determinism.

3. **Node Ordering Within Layers**
   * Use a heuristic to minimize edge crossings:
     * Start with the order inherited from the input list for the first layer.
     * For subsequent layers, order sections by the average position of their predecessors (barycenter method). Apply two-phase sweep (left-to-right, then right-to-left) until ordering stabilizes or after a small number of iterations.

4. **Coordinate Assignment**
   * Choose fixed spacing constants:
     * Horizontal layer spacing (e.g., 300 px between section centers).
     * Vertical spacing between nodes within a layer.
     * Section rectangle size determined by exercise content:
       * Width: base width + total bubble width + padding.
       * Height: number of exercises × bubble height + vertical padding.
   * After ordering layers, compute each section's `(x, y)` position relative to top-left canvas origin. Align sections vertically per layer with equal spacing.
   * Canvas size derived from bounding boxes plus margin.

5. **Exercise Bubble Layout**
   * Determine max estimated time across all exercises. For each bubble width: `bubbleWidth = minWidth + scaleFactor * estimatedTime` with clamp at a max width (not exceeding section width minus padding).
   * Stack bubbles inside section with consistent vertical spacing and align them left within the section rectangle.
   * Consider sections without exercises: display placeholder or skip bubbles? Proposed behavior: show empty section rectangle with text "No exercises" or minimal height.

6. **Arrow Routing**
   * Draw Bezier or polyline edges between section centers with slight vertical offsets based on edge ordering to avoid overlap.
   * Solid arrows for required edges, dashed stroke for recommended edges.
   * Arrowheads drawn at the target section boundary. Use `AppCanvas` capabilities to draw lines/paths; if needed, extend helper to support arrowheads (triangle filled at target).
   * When multiple edges originate from the same source/target pair with different types, draw separate arrows with slight vertical displacement.

7. **Edge Cases & Expected Behavior**
   * **Single Section**: Render a solitary section centered in the canvas without arrows.
   * **Disconnected Graph**: Components placed in independent layers respecting input order.
   * **Multiple Prerequisites**: Section depth equals max depth of required/recommended predecessors plus one, ensuring it appears to the right of all prerequisites.
   * **Example (A→B→C, D depends on B required and A recommended)**:
     * Layer 0: A
     * Layer 1: B
     * Layer 2: C and D (ordered by average predecessor positions; D may align near B/A). Arrow from A to D is dashed, from B to D is solid.
   * **No Exercises in Section**: Render minimal height rectangle with text label only.
   * **Large Duration Spread**: Clamp bubble widths to maintain readability.
   * **Duplicate Dependencies**: Ignore duplicates during graph construction.

## Helper Components to Implement

* `SectionNode`: holds section data, computed layer, order, size, and drawing coordinates.
* `Edge`: encapsulates source/target nodes and dependency type (`Required`, `Recommended`).
* `LayeredLayout`: algorithm class performing steps 2-4 and returning positioned `SectionNode`s and `Edge`s.
* `SectionRenderer`: helper to draw section rectangles and exercise bubbles on the canvas.
* `EdgeRenderer`: helper to draw arrows between sections with appropriate styling.

## HtmlWorkbookOverview Responsibilities

* Accept sections list, build graph, run layout, and produce `HtmlElement` (SVG) containing visualization.
* Provide configuration constants (spacing, colors, fonts) with sensible defaults.
* Ensure the resulting SVG fits within a 60vw × 10vh box when embedded via `MainApp` sample.

