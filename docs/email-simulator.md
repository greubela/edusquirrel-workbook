# Email simulator

The mailbox is available at `homepage/phishingWorkbook/`. The test workbook at
`homepage/workbookDesign/` showcases the same messages with composing enabled.
It follows the shared [interaction architecture](workbook-interaction-architecture.md).

## Models and state

`MailInteraction` configures sorting practice; `MailEditor` configures local
writing practice using the same fullscreen UI. Model implementations live under
`modules/core/shared/src/main/scala/it/evadid/workbook/elements/interactionElements/emailSimulator/`;
renderers live under the client `workbook/htmlRenderer/interactionRenderer/emailSimulator/`
package. Styles belong in `homepage/css/workbook/email-simulator.css`.

One bound inbox value owns message placement, ordering and read status. Folders
are exclusive; Marked means a genuine message requiring action. Expected folders
belong to the exercise definition. Grading evaluates final placements rather
than counting movements, so repeated actions cannot inflate the result. Practice
messages have no expected answer, and an empty practice mailbox does not pass a
sorting exercise.

Saved messages survive close/reopen and reload. Selection and unsent compose
drafts are transient; closing discards unsent work. Sending only adds a plain-text
message to the local Sent folder. There is no mail transport.

## Teaching content boundary

`tools/dev/import-phishing-mails.py` imports the original Python message records
without executing the source module. Rerun it when intentionally updating the
examples. Original messages and local images remain teaching assets in German;
interface labels follow the selected workbook language.

Mail HTML is rebuilt from an allowlist. Active content and remote resources are
excluded. Links expose their exact destination but only show simulation notices;
attachments are inert labels and never download or execute. Local raster images
must resolve within the validated teaching-asset directory. Restored messages go
through the same rendering boundary.

Compose validation keeps invalid drafts out of saved state. Reply/forward retain
context while producing plain-text local messages.

## Verification

Shared JVM/JS tests cover inbox transitions, grading and serialization; client
tests cover drafts, selection and action gating. Browser checks cover actual mail
content, inert links/attachments, hostile restored HTML and persistence.

```sh
sbt buildClientDev buildWorkerDev
npm run test:workbooks -- mail-simulator
```

For the wider Phishing edition and remaining source-content gaps, see the
[migration inventory](digital-workbook-migration.md#phishing-warning-signs-domains-and-attachments).
