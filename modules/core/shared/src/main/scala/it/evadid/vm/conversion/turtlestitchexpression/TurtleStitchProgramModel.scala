package it.evadid.vm.conversion.turtlestitchexpression

/**
 * Scala.js data model mirroring the TurtleStitch/Snap! project XML structure.
 *
 * Important upstream references in TurtleStitch/Snap (for compatibility audits):
 * - `src/scenes.js`: `Project` / `Scene` constructors (lines ~65 and ~114).
 * - `src/store.js`: `Project.prototype.toXML` (line ~2021),
 *   `Scene.prototype.toXML` (line ~2048),
 *   `StageMorph.prototype.toXML` (line ~2116),
 *   `SpriteMorph.prototype.toXML` (line ~2224),
 *   `ScriptsMorph.prototype.toXML` (line ~2424),
 *   `BlockMorph.prototype.toScriptXML` / `toBlockXML` (line ~2436+).
 */
object TurtleStitchProgramModel {

  /**
   * Mirrors `<project ...>` and multi-scene support in Snap/TurtleStitch.
   */
  final case class Project(
      name: String = "Untitled",
      app: String = "",
      version: String = "2",
      notes: String = "",
      thumbnail: Option[String] = None,
      scenes: Vector[Scene] = Vector.empty,
      selectedScene: Int = 1,
      creator: Option[String] = None,
      origCreator: Option[String] = None,
      origName: Option[String] = None,
      /** Blocks the document contains outside scripts and block definitions; they have no meaning here. */
      unreadBlocks: Vector[String] = Vector.empty
  ) {
    /** Block definitions of every scene, stage and sprite. */
    def customDefinitions: Vector[CustomBlockDefinition] = scenes.flatMap(scene =>
      scene.customBlocks ++ scene.primitiveBlocks ++ scene.stage.blocks ++ scene.stage.sprites.flatMap(_.blocks))

    /** The scripts of every stage and sprite. */
    def scripts: Vector[Script] = scenes.flatMap(scene => scene.stage.scripts ++ scene.stage.sprites.flatMap(_.scripts))

    /** Every block in scripts and definition bodies, nested blocks included. */
    def allBlocks: Vector[BlockLike] = (scripts ++ customDefinitions.flatMap(_.body)).flatMap(_.blocks).flatMap(_.withNested)

    /** Variables of every scene, stage and sprite. */
    def variables: Vector[Variable] = scenes.flatMap(scene =>
      scene.variables ++ scene.stage.variables ++ scene.stage.sprites.flatMap(_.variables))
  }

  /**
   * Mirrors `<scene ...>`.
   */
  final case class Scene(
      name: String = "Untitled",
      notes: String = "",
      palette: Option[String] = None,
      categories: Option[Boolean] = None,
      buttons: Option[Boolean] = None,
      clickRun: Option[Boolean] = None,
      dragData: Option[Boolean] = None,
      colorModel: Option[String] = None,
      hiddenPrimitives: Vector[String] = Vector.empty,
      headers: Map[String, String] = Map.empty,
      codeMappings: Map[String, String] = Map.empty,
      customBlocks: Vector[CustomBlockDefinition] = Vector.empty,
      primitiveBlocks: Vector[CustomBlockDefinition] = Vector.empty,
      stage: Stage = Stage(),
      variables: Vector[Variable] = Vector.empty
  )

  /**
   * Mirrors `<stage ...>` attributes and child elements.
   */
  final case class Stage(
      name: String = "Stage",
      width: Int = 480,
      height: Int = 360,
      costume: Int = 0,
      color: Rgba = Rgba(255, 255, 255, 1.0),
      tempo: Double = 60.0,
      threadsafe: Boolean = false,
      penlog: Boolean = false,
      instrument: Option[Int] = None,
      volume: Double = 100.0,
      pan: Double = 0.0,
      lines: String = "round",
      ternary: Boolean = false,
      hyperops: Boolean = true,
      codify: Boolean = false,
      inheritance: Boolean = true,
      sublistIDs: Boolean = false,
      pentrails: Option[String] = None,
      wear: Option[Costume] = None,
      costumes: Vector[Costume] = Vector.empty,
      sounds: Vector[Sound] = Vector.empty,
      variables: Vector[Variable] = Vector.empty,
      blocks: Vector[CustomBlockDefinition] = Vector.empty,
      scripts: Vector[Script] = Vector.empty,
      sprites: Vector[Sprite] = Vector.empty,
      selectedSprite: Int = 1,
      id: Option[String] = None
  )

  /**
   * Mirrors `<sprite ...>` attributes and child elements.
   */
  final case class Sprite(
      name: String = "Sprite",
      idx: Int = 1,
      x: Double = 0.0,
      y: Double = 0.0,
      heading: Double = 90.0,
      scale: Double = 1.0,
      volume: Double = 100.0,
      pan: Double = 0.0,
      rotation: Int = 1,
      instrument: Option[Int] = None,
      draggable: Boolean = true,
      hidden: Boolean = false,
      costume: Int = 0,
      color: Rgba = Rgba(0, 0, 0, 1.0),
      pen: String = "tip",
      solution: Option[SpriteSolution] = None,
      inheritance: Option[SpriteInheritance] = None,
      nesting: Option[SpriteNesting] = None,
      wear: Option[Costume] = None,
      costumes: Vector[Costume] = Vector.empty,
      sounds: Vector[Sound] = Vector.empty,
      blocks: Vector[CustomBlockDefinition] = Vector.empty,
      variables: Vector[Variable] = Vector.empty,
      dispatches: Vector[Dispatch] = Vector.empty,
      scripts: Vector[Script] = Vector.empty,
      id: Option[String] = None
  )

  /**
   * `<script x="..." y="...">...</script>`.
   */
  final case class Script(
      x: Option[Double] = None,
      y: Option[Double] = None,
      blocks: Vector[BlockLike] = Vector.empty
  )

  sealed trait BlockLike {
    def inputs: Vector[InputValue]

    /** This block and every block in its slots, scripts and lists. */
    def withNested: Vector[BlockLike] = this +: inputs.flatMap(_.blocks)
  }

  /** `<block s="selector"> ... </block>` and `<block var="..."/>`. */
  final case class PrimitiveBlock(
      selector: Option[String] = None,
      variable: Option[String] = None,
      inputs: Vector[InputValue] = Vector.empty,
      comment: Option[Comment] = None
  ) extends BlockLike

  /** `<custom-block s="..." ...> ... </custom-block>`. */
  final case class CustomBlockCall(
      semanticSpec: String,
      scope: Option[String] = None,
      inputs: Vector[InputValue] = Vector.empty,
      variables: Vector[Variable] = Vector.empty,
      comment: Option[Comment] = None
  ) extends BlockLike

  sealed trait InputValue {
    def blocks: Vector[BlockLike] = this match {
      case NestedBlock(block) => block.withNested
      case NestedScript(script) => script.blocks.flatMap(_.withNested)
      case ListLiteral(items) => items.flatMap(_.blocks)
      case _ => Vector.empty
    }
  }
  final case class Literal(value: String) extends InputValue
  final case class BoolLiteral(value: Boolean) extends InputValue
  final case class ColorLiteral(value: Rgba) extends InputValue
  final case class ListLiteral(items: Vector[InputValue]) extends InputValue
  final case class NestedScript(value: Script) extends InputValue
  final case class NestedBlock(value: BlockLike) extends InputValue

  final case class CustomBlockDefinition(
      spec: String,
      blockType: it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapVocabulary.BlockType,
      category: it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapVocabulary.BlockCategory =
        it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapVocabulary.BlockCategory.Other,
      selector: Option[String] = None,
      primitive: Option[String] = None,
      helper: Option[String] = None,
      space: Option[String] = None,
      isGlobal: Option[Boolean] = None,
      isDisposable: Option[Boolean] = None,
      codeHeader: Option[String] = None,
      codeMapping: Option[String] = None,
      translations: Map[String, String] = Map.empty,
      inputs: Vector[BlockInputDefinition] = Vector.empty,
      variables: Vector[String] = Vector.empty,
      body: Vector[Script] = Vector.empty,
      comment: Option[Comment] = None
  )

  final case class BlockInputDefinition(
      inputType: String,
      name: String,
      defaultValue: Option[String] = None,
      options: Vector[String] = Vector.empty,
      readonly: Option[Boolean] = None
  )

  final case class Variable(
      name: String,
      value: Option[InputValue] = None,
      transient: Boolean = false,
      hidden: Boolean = false
  )

  final case class Costume(
      name: String,
      centerX: Double,
      centerY: Double,
      image: String,
      embed: Option[String] = None,
      id: Option[String] = None
  )

  final case class Sound(
      name: String,
      sound: String,
      id: Option[String] = None
  )

  final case class Dispatch(name: String)

  final case class Comment(
      text: String,
      x: Option[Double] = None,
      y: Option[Double] = None,
      width: Option[Double] = None,
      collapsed: Boolean = false
  )

  final case class SpriteSolution(scripts: Vector[Script])

  final case class SpriteInheritance(
      exemplar: String,
      inheritedAttributes: Vector[String] = Vector.empty
  )

  final case class SpriteNesting(
      anchor: String,
      synch: Boolean,
      scale: Option[Double] = None
  )

  final case class Rgba(r: Int, g: Int, b: Int, a: Double)
}
