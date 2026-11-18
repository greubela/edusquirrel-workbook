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

The default `ReadWriter[WorkbookElement]` uses the self-contained graph representation.
Reference-only factory serialization requires the caller's registry. Constructor-style
syntax frames argument groups and delegates JSON validation to uJSON.

```scala
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.jsonFactory.SerializedWorkbook
import upickle.default.*

def copyDefinition(element: WorkbookElement): WorkbookElement = {
  val json = write(SerializedWorkbook.fromElement(element))
  read[SerializedWorkbook](json).toElement
}
```

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
