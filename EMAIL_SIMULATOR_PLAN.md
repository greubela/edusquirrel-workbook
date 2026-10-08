# Email Simulator

The compilation checkpoint is commit `c9336b32`, based on main `5c77c7c3`. It repairs shared/browser imports, persisted-state serializers, Laminar bindings and invalid tests without adding dependencies.

## Completed implementation

- `MailInteraction` provides the original phishing sorting exercise. Compose, reply and forward are disabled, matching the Python original.
- `MailEditor` uses the same fullscreen mailbox and enables local compose, reply and forward practice. Sending only appends a plain-text message to the local Sent folder; there is no mail transport.
- Both interactions have workbook factories and HTML renderer registrations, a preview line, an Open Editor button and bound persistent state.
- The fullscreen editor contains the toolbar, five folders, independently scrolling message list and message pane, and a link/status footer. Styles live in `homepage/css/workbook/email-simulator.css`, using shared color and dimension tokens. Duplicate Scala CSS definitions and the unused generic editor config were removed.
- `homepage/phishingWorkbook/index.html` and the landing page link expose the digital mailbox exercise and separate local writing practice. English/German labels and instructions are registered through the existing language-map loader. The original messages remain German teaching examples.
- The test workbook also has a Mail Simulator section with the original 15 messages and composing enabled, showcasing sorting, reading, simulated links/attachments and local writing in one interaction.

## State and grading

`Mail.realFolder` is the learner's current placement, while `expectedFolder` stores the original exercise answer. Marked is an exclusive folder for genuine messages requiring action, rather than an overlapping star label. Original messages start in Inbox; sent practice messages have no expected answer.

`InboxState` maintains stable message ordering and exclusive folder membership. Movement is idempotent; unknown IDs and folders do nothing. Read/unread, archive, mark, trash and restoration update the same bound state. The per-message placement is authoritative; normalization rebuilds stale indexes. Duplicate message IDs and invalid folders are rejected when constructing a normalized inbox.

Grading counts final placements of messages with expected answers, so repeated movements cannot inflate the result. The exercise is complete when every original has left Inbox and passes when every original is in its expected folder. Messages can be restored and corrected. Empty/practice mailboxes do not pass the sorting exercise.

The editor controller has no DOM, fullscreen or synchronization dependency, so its selection, navigation, action gating and send/cancel behavior are unit tested under Node. Stored messages, folder placement and read status survive closing/reopening and browser reloads. Unsent drafts are discarded on closing the fullscreen editor.

## Original assets and simulated content

`tools/dev/import-phishing-mails.py` imports the 15 original `Email(...)` records from the tracked Python workbook using `ast.literal_eval`, without executing its module or requiring Python packages. It generates `PhishingMailboxData.scala`; rerun the script after intentionally changing the teaching examples. Local image assets are reused from the original resource directory without copying them.

Mail HTML is parsed into an inert template and rebuilt from an allowlist. Supplied styles, event handlers and active content never enter the document. Links become buttons showing their exact destinations on hover/focus; clicking only displays a simulation notice. Only validated `pics/<filename>` raster image paths can load from the local workbook assets. Remote images, frames and executable content are excluded. Attachment names (including deceptive executable extensions) are displayed as simulated buttons; nothing downloads or executes. Original presentation hints are mapped to external CSS classes.

Compose validates recipient addresses, subject and body. Reply/forward preserve context, prevent repeated prefixes, and produce plain-text local drafts; only forwarding retains a simulated attachment filename. Invalid drafts stay visible and never overwrite mailbox state.

Late-created fullscreen labels now initialize from the already loaded language map, avoiding stuck loading placeholders in the folder header and status footer.

## Verification

No dependencies were added. The simulator page loads only the app bundle and shared workbook CSS, without CodeMirror, TurtleStitch, JSXGraph, CDN scripts or the missing legacy dropdown stylesheet. Unit tests use existing munit/upickle/Airstream infrastructure; browser verification uses the already installed Playwright development dependency and Chromium.

- Compilation checkpoint: compile main/test sources for core JVM, core JS, client, server and worker; four repaired serialization tests.
- `InboxStateSpec` and `MailSimulatorSpec`: transitions between every pair of folders, membership/index consistency, idempotence, stable ordering, read state, invalid inputs, final-placement grading, composing, reply/forward, serialization and legacy defaults.
- `MailEditorStateSpec` and renderer serialization suites: selected message/navigation, stale selection after synchronization, persistent state, compose gating, validation/cancellation/sending, original data, image paths and workbook serialization.
- `tools/dev/mail-simulator.test.mjs`: actual fullscreen interaction, disabled original compose controls, all original message bodies, simulated link/attachment clicks, local compose/reply/forward, browser reload persistence, grading, desktop/mobile layout and hostile restored HTML. Run after `sbt buildJS` with `node --test tools/dev/mail-simulator.test.mjs`; requests are served locally or aborted, so no backend/account is required.
- Full regression suites: `coreJVM/test`, `coreJS/test`, `client/test`, `server/test`, `worker/test`.

The digital page implements the mailbox exercise and local writing practice. It does not yet migrate all explanatory chapters of the original PDF workbook.
