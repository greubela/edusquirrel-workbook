# Printable Export Design

## Goal
Provide educators and learners with high-fidelity PDF or print-friendly exports of workbook pages that preserve interactive content as static snapshots, ensuring exercises can be reviewed offline or archived for compliance.

## Use Cases
- Teachers distribute printable packets mirroring the digital experience.
- Learners submit PDF evidence of work for accreditation bodies.
- Administrators archive course materials with recorded learner responses.

## Requirements
### Functional
- Generate printable view (HTML/CSS) of any workbook page or selected exercises.
- Capture current state of interactive plugins (inputs, selections, rendered graphs) as static images or annotations.
- Support batch export of multiple pages into a single PDF.
- Provide configuration for watermarking, answer masking (hide/show solutions), and metadata headers.
- Allow both user-triggered exports (client) and automated exports (server-side job).

### Non-Functional
- Output must match on-screen layout closely while ensuring printer-friendly typography and margins.
- Efficient rendering for large workbooks; incremental snapshotting to avoid freezing the UI.
- Accessibility: maintain alt-text and semantic structure where possible.
- Localization of headers/footers.

## Architecture Options
1. **Client-side export**
   - Use `print()` with dedicated CSS for print media.
   - Leverage canvas/svg serialization for plugin states.
   - Optional use of browser-based PDF libraries (e.g., `pdf-lib`, `html2pdf`).
   - Pros: no backend dependency; quick iteration.
   - Cons: inconsistent output across browsers; limited for large docs.

2. **Server-side rendering**
   - Render printable HTML via headless browser (Puppeteer/Playwright) or server templating.
   - Requires API to submit export jobs and retrieve finished PDFs.
   - Pros: consistent output, scalable for batch jobs.
   - Cons: infrastructure overhead; need secure handling of learner data.

## Data Capture Strategy
- Extend each interaction plugin with `serializeForPrint(): Promise<RenderableSnapshot>` returning:
  - `type`: `image`, `svg`, `text`, etc.
  - `content`: base64 image data or serialized markup.
  - `dimensions`: intended size for layout.
  - `metadata`: alt-text, timestamp of snapshot.
- Compose page export by replacing live components with snapshot placeholders.
- Store snapshots alongside Interaction History/Persistence state for reuse without recomputation if unchanged.

## Export Workflow
1. User triggers "Export" from workbook UI.
2. Client assembles printable DOM:
   - Clone page structure.
   - For each interactive element, call `serializeForPrint` (using cached state when offline).
   - Insert static representations and metadata.
3. Client either:
   - Invokes browser print/PDF dialog directly (client-side), or
   - Sends printable HTML + assets to export service.
4. Export service renders PDF, stores temporary file, and returns download URL.
5. Notify user when export is ready; provide status indicators for long-running jobs.

## Styling Considerations
- Introduce `print.css` with typography, spacing, and page-break utilities.
- Provide configuration panel to toggle answer visibility, hints, timestamps.
- Include header/footer templates (course name, learner name, date, page numbers).

## Integration
- Reuse persistence state snapshots for offline exports.
- Provide analytics hooks to track export usage.
- Align with LMS submission workflows (auto-upload exported PDF).

## Open Questions
- Should exports include embedded media (videos) as thumbnail references?
- How to handle very large interactive canvases (e.g., GeoGebra) without quality loss?
- Need for differential styling per institution branding?

## Milestones
1. Define plugin serialization contract and build reference implementations for core plugins.
2. Implement client-side printable view with print styles.
3. Evaluate need for server-side rendering; prototype with headless browser.
4. Add batch export & LMS submission integration.
5. Conduct usability testing and refine layout for educators' needs.
