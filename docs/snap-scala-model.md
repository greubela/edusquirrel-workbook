# Full Snap project model in shared core

`ProgrammingStateSnapScalaModel` represents the complete **saved Snap project**, independently of the smaller executable Turtle subset. It lives alongside `ProgrammingStateSnapXml` in [ProgrammingState.scala](../modules/core/shared/src/main/scala/it/evadid/workbook/elements/interactionElements/programming/state/ProgrammingState.scala). Existing XML states and historical serialization tags remain readable.

[SnapScalaModel.scala](../modules/core/shared/src/main/scala/it/evadid/workbook/elements/interactionElements/programming/state/snap/SnapScalaModel.scala) contains immutable, serializable classes for projects, scenes, stages, sprites, scripts, primitive/custom blocks and definitions, variables, literals, lists and references, contexts, costumes, sounds, watchers, inheritance, nesting, comments and the standard serializer containers. These classes model the bundled Snap and TurtleStitch `store.js` vocabulary. This is a persistence model; it does not instantiate Morphic objects, execute scripts or expand cyclic references.

Each Snap node has one authoritative `Element` containing ordered attributes and child nodes. Its typed accessors expose numbers, booleans, geometry, scripts and other domain values. Optional accessors distinguish absent values from explicit values; they do not invent defaults or overwrite unrecognized attribute values. `Extension` retains unknown/version-specific elements and dynamic code-mapping tags. New native Snap fields therefore survive before Scala-specific accessors are added.

## Conversion and editing

```scala
import it.evadid.workbook.elements.interactionElements.programming.state.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapScalaModel.*

val xmlState = ProgrammingStateSnapXml(
  "<project name='Example'><scenes><scene><stage><sprites>" +
    "<sprite name='Turtle' x='0'><scripts/></sprite>" +
    "</sprites></stage></scene></scenes></project>"
)
val typed = xmlState.toSnapScalaModel.fold(
  message => throw IllegalArgumentException(message), identity
)
val sprites: Vector[Sprite] = typed.model.project.get.scenes.head.stage.get.sprites
val x: Option[Double] = sprites.head.x

val changed = typed.copy(model = typed.model.transform {
  case sprite: Sprite if sprite.name.contains("Turtle") =>
    sprite.withAttribute("x", "12.5")
  case node => node
})
val savedXml: ProgrammingStateSnapXml = changed.toSnapXml
```

`transform` visits nodes from children to parents. Copies preserve the other nodes, assets, identities and extension content. To author a tree directly, construct typed nodes around `Element`, for example `Project(Element("project", selfClosing = true))` and `Document(Vector(project))`. Attribute edits accept decoded values and escape them for XML.

`toSnapScalaModel` returns an error for malformed input instead of silently returning an incomplete project. The reader is shared Scala, with no browser DOM or XML-library dependency. Input limits are 64 MiB, one million nodes and 256 levels of nesting. DOCTYPE declarations and unknown DTD entities remain opaque: the reader never fetches external resources or expands entities.

## Round-trip contract

XML → typed model → XML preserves scripts and arguments, scene/sprite ordering and selection, custom definition settings, watcher and canvas positions, variables and list structures, reference IDs, contexts, authored and generated images, sounds, metadata, comments, mixed text/CDATA, unknown tags/attributes and legacy floating data. Numeric and predefined XML entities expose decoded values without recursively decoding escaped text. Atomic-list CSV remains content rather than being interpreted or rewritten.

Formatting identity is **not a required contract**. The current reader also retains source spelling and whitespace, but callers should depend on preservation of content and metadata. Whitespace inside text, literals and CSV can carry meaning and must remain intact.

All model case classes and sealed node hierarchies derive uPickle `ReadWriter`. The programming-state codec uses an explicit stable tag for the new variant, matching the existing state codec convention. JSON, MessagePack and `ProgrammingExercise.StateSerializer` retain the typed representation; the workbook format uses `PROGRAMMING_STATE_V2` with `SNAP_SCALA_MODEL`. Older applications do not understand this new discriminator, so deploy readers before persisting the new representation for their users.

The XML-only representation remains the default for existing workbooks. Eva and Snap editors can display typed states and retain their representation and opaque legacy floating data when publishing edits. Normal live-editor image cleanup still applies to native editing; **conversion itself never calls `removeBloatFromXml`**. Translation to Python/Java/`BeExpression` delegates to the existing checked subset converters and can fail for otherwise valid native Snap programs.

## Checks

```sh
sbt 'coreJVM/testOnly *ProgrammingStateSnapScalaModelSpec *SnapScalaWorkbookProjectsSpec'
sbt 'coreJS/testOnly *ProgrammingStateSnapScalaModelSpec'
sbt 'client/testOnly *EvaEditorSpec *SnapCodeEditorSyncSpec'
```

Shared tests exercise full projects, single-stage legacy projects, block libraries, separate media modules, immutable edits, malformed input and serialization on both JVM and Scala.js. The JVM fixture check reads all eight published embroidery projects, including their complete image payloads. Client checks cover typed state publication and preservation of legacy metadata.
