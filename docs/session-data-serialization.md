# Workbook definitions and session exports

Definitions and saved answers have separate serialization contracts: a definition
needs its element graph; a history needs its `InteractionFormat`, not that graph.
This allows servers to exchange histories for workbooks they do not know.

| Class | Responsibility |
| --- | --- |
| `SerializedWorkbook` (`workbook.jsonFactory`) | Root ID and serialized element definitions; collects children and section prerequisites, rejecting conflicting IDs |
| `WorkbookElementFactory` | Encodes element definitions and resolves graph references when reconstructing elements |
| `SessionData` (`workbook.session`, shared) | Save timestamp, user identity, public user configuration, workbook definition (`AllWorkbookInfo`) and `Map[SyncContext, InteractionHistoryJson]` |
| `WorkbookUserDataAnalyzer` / `UserSessionControl` | Export/download and login/import orchestration |

`Workbook` and `WorkbookSection` supply explicit companion `given ReadWriter`
instances; they do not need `derives ReadWriter`. These delegate to the
`WorkbookElement` graph codec backed by `SerializedWorkbook`, so nested elements
and section prerequisites travel with the root. The concrete reader checks the
root kind.

```scala
import it.evadid.workbook.elements.structureElements.Workbook
import upickle.default.*

def workbookToString(workbook: Workbook): String = write[Workbook](workbook)
def workbookFromString(json: String): Workbook = read[Workbook](json)
```

For a generic root, use `write[WorkbookElement](element)` and
`read[WorkbookElement](json)`. `SerializedWorkbook.fromElement(element)` exposes
the graph DTO when needed. Reference-only factory serialization requires a registry;
constructor-style syntax frames argument groups and delegates JSON validation to uJSON.

This serializes the **workbook definition**, not live learner answers or feedback.
Export `SessionData` to include those histories.

Session export includes each interaction's format-coded history and strips credentials:

```scala
import it.evadid.homepage.control.model.HomepageInfo
import upickle.default.*

def exportSession(info: HomepageInfo): Option[String] =
  info.toSessionData.map(data => write(data))
```

`HomepageInfo.toSessionData` returns `None` without a workbook or user. The UI imports through
`UserSessionControl.tryContinueWithSessionFile`, which parses the file and calls
`continueWithSession`: it signs in as the session's user if needed, opens the session's
workbook (the open one when it has the same ID, else the definition stored in the file)
and calls `Workbook.restoreHistories`. A workbook restores only histories whose context
names its own ID, so element IDs reused across workbooks never mix. Each history passes
`InteractionVariable.restoreHistory`, which discards and logs a record with another format,
an unreadable payload or a state the exercise rejects. Invalid file JSON fails the import.
Only the current saved schema is supported.

See [interaction formats](interaction-format.md) and [state ownership](state-and-serialization.md).

Round-trip coverage lives in `WorkbookSerializerSpec` (JSON, MessagePack, nested
structure, prerequisites, wrong-root rejection and definition/history separation)
and `WorkbookDefinitionsSpec` (every authored catalogue workbook plus the design
workbook). `SessionFileJourneySpec` downloads a session file and continues it in a fresh page
with nobody signed in. Shared tests run on both JVM and Scala.js:

```sh
sbt 'coreJVM/testOnly *WorkbookSerializerSpec' 'coreJS/testOnly *WorkbookSerializerSpec'
sbt 'client/testOnly *WorkbookDefinitionsSpec *SessionFileJourneySpec'
```

## Client ownership

`FullInfo.userSessionControl` handles authentication and user transitions;
`UserSessionStorage` owns browser persistence. `workbookNavigationControl` handles
workbook/section changes, and `displayControl` handles display and language changes.
Call these owners directly. User transitions still invalidate feedback and reset
interaction bindings when the person changes; browser storage keys and schemas
are unchanged by this separation.

## Session submission

A `WorkbookSubmissionElement` renders [HtmlSubmissionForm](../modules/client/src/main/scala/it/evadid/homepage/workbook/htmlRenderer/controlElements/HtmlSubmissionForm.scala):
name and address fields in the login style, prefilled only for online accounts. Its button
sends `HomepageInfo.toSubmissionBy(submitter)` for a new, never-registered user built from
those fields: every current history under its bound context and a local-only configuration.
It does not sign in, switch users or change bindings, and sends no backend token, so
submissions stay out of the account and history tables.

`SubmissionCommands.submitSessionCommand` (`submit-session-event`) carries
`SubmitSessionEvent(sessionData)` and needs no account. The backend logs it in
`submissionLog` ([database guide](database/README.md#logging-and-proxy-addresses)), then
[SubmitSessionCommand](../modules/server/src/main/scala/it/evadid/server/SubmitSessionCommand.scala):

1. derives a [SimpleSubmission](../modules/server/src/main/scala/it/evadid/server/SimpleSubmission.scala):
   the claimed user and request address, plus the latest answer and combined authored-grader
   status of each answered interaction of the session's own workbook, in workbook order;
   unreadable histories are logged and left out;
2. mails the full credential-free session (`.eva`, importable via session continuation) and
   the summary to the archive address; failure fails the command;
3. mails the summary to the session's address, only if it is one plain address. The response
   reports the number of summarized interactions and whether this confirmation was sent.

Coverage: `HtmlSubmissionFormSpec`, `SubmitSessionCommandSpec`, `BackendCommandHttpSpec` and `UserAccountMysqlSpec`.
