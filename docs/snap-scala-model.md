# Full Snap project model in shared core

`ProgrammingStateSnapScalaModel` represents the complete **saved Snap project**, independently of the smaller executable Turtle subset. It lives alongside `ProgrammingStateSnapXml` in [ProgrammingState.scala](../modules/core/shared/src/main/scala/it/evadid/workbook/elements/interactionElements/programming/state/ProgrammingState.scala). Existing workbooks can continue using the XML representation.

All project node types live in
[SnapScalaModel.scala](../modules/core/shared/src/main/scala/it/evadid/workbook/elements/interactionElements/programming/state/snap/SnapScalaModel.scala).
They model the bundled Snap/TurtleStitch serializer vocabulary as immutable data.
This is a persistence model: it does not create Morphic objects, execute scripts
or expand cyclic references.

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

XML → typed model → XML preserves project content, ordering, references, assets
and unknown metadata. Numeric and predefined XML entities expose decoded values
without recursively decoding escaped text. Opaque native data remains intact.

Formatting identity is **not a required contract**. The current reader also retains source spelling and whitespace, but callers should depend on preservation of content and metadata. Whitespace inside text, literals and CSV can carry meaning and must remain intact.

The model case classes and sealed node hierarchies derive uPickle `ReadWriter`. The typed programming-state variant also derives its codec and uses the standard interaction serializer. JSON and MessagePack retain the typed representation. The development schema has no historical-format readers; reset development databases when it changes.

The XML-only representation remains the default for existing workbooks. Eva and Snap editors can display typed states and retain their representation and opaque legacy floating data when publishing edits. Normal live-editor image cleanup still applies to native editing; **conversion itself never calls `removeBloatFromXml`**. Translation to Python/Java/`BeExpression` delegates to the existing checked subset converters and can fail for otherwise valid native Snap programs.

## Checks

```sh
sbt 'coreJVM/testOnly *ProgrammingStateSnapScalaModelSpec *SnapScalaWorkbookProjectsSpec'
sbt 'coreJS/testOnly *ProgrammingStateSnapScalaModelSpec'
sbt 'client/testOnly *EvaEditorSpec *SnapCodeEditorSyncSpec'
```

Shared tests exercise full projects, single-stage legacy projects, block libraries, separate media modules, immutable edits, malformed input and serialization on both JVM and Scala.js. The JVM fixture check reads all eight published embroidery projects, including their complete image payloads. Client checks cover typed state publication and preservation of legacy metadata.
