# Workbook definitions and session exports

Definitions and saved answers have separate serialization contracts: a definition
needs its element graph; a history needs its `InteractionFormat`, not that graph.
This allows servers to exchange histories for workbooks they do not know.

| Class | Responsibility |
| --- | --- |
| `SerializedWorkbook` (`workbook.jsonFactory`) | Root ID and serialized element definitions; collects children and section prerequisites, rejecting conflicting IDs |
| `WorkbookElementFactory` | Encodes element definitions and resolves graph references when reconstructing elements |
| `SessionData` (`homepage.control.info`) | Save timestamp, user identity, public user configuration, workbook definition and `Map[SyncContext, InteractionHistoryJson]` |
| `WorkbookUserDataAnalyzer` / `HomepageUsageControl` | Export/download and login/import orchestration |

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
import it.evadid.homepage.control.info.SessionData
import it.evadid.homepage.control.model.HomepageInfo
import upickle.default.*

def exportSession(info: HomepageInfo): Option[String] =
  SessionData.createSessionData(info).map(data => write(data))
```

`createSessionData` returns `None` without a workbook or user. The UI imports through
`HomepageUsageControl.tryContinueWithSessionFile`; it resolves the user before calling
`Workbook.loadDataNoUserCheck`. Applying a history checks its expected format and
exercise-specific state constraints. Invalid file JSON or unknown format names fail
parsing; invalid individual history payloads are logged and excluded at the interaction
import boundary. Only the current saved schema is supported.

See [interaction formats](interaction-format.md) and [state ownership](state-and-serialization.md).

Round-trip coverage lives in `WorkbookSerializerSpec` (JSON, MessagePack, nested
structure, prerequisites, wrong-root rejection and definition/history separation)
and `WorkbookDefinitionsSpec` (every authored catalogue workbook plus the design
workbook). Shared tests run on both JVM and Scala.js:

```sh
sbt 'coreJVM/testOnly *WorkbookSerializerSpec' 'coreJS/testOnly *WorkbookSerializerSpec'
sbt 'client/testOnly *WorkbookDefinitionsSpec'
```
