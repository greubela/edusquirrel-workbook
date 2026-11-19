package it.evadid.vm.conversion.turtlestitchexpression

import it.evadid.vm.code.errors.ProgrammingConversionException
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapScalaModel
import it.evadid.vm.conversion.turtlestitchexpression.TurtleStitchProgramModel.{BlockInputDefinition, BlockLike, BoolLiteral, ColorLiteral, Comment, Costume, CustomBlockCall, CustomBlockDefinition, Dispatch, InputValue, ListLiteral, Literal, NestedBlock, NestedScript, PrimitiveBlock, Project, Rgba, Scene, Script, Sound, Sprite, Stage, Variable}
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapVocabulary.{BlockCategory, BlockType}


/** Maps the lossless Snap document ([[SnapScalaModel]]) to the program model the expression reader uses.
  * A document is a project (with scenes; without them, its one stage or its own scripts and sprites),
  * or a fragment: blocks, a script, scripts, a sprite or a block library.
  */
object TurtleStitchModelMapper {
  private type Node = SnapScalaModel.Element
  extension (node: Node) private def name: String = node.tag

  val EmptyProject: Project = Project()

  def toProject(document: SnapScalaModel.Document): Project = {
    val project = document.project match {
      case Some(root) => fromProject(root.element)
      case None => fragment(document.root.element)
    }
    project.copy(unreadBlocks = unread(document.root.element, project))
  }

  /** Every block element in the document is read, apart from scratch scripts in a block editor. */
  private def unread(root: Node, project: Project): Vector[String] = {
    def label(node: Node) = node.attribute("s").orElse(node.attribute("var")).getOrElse(node.tag)
    def blocks(node: Node): Vector[String] = node.tag match {
      case "block" | "custom-block" => label(node) +: node.elements.map(_.element).flatMap(blocks)
      case "block-definition" => node.elements.map(_.element).filterNot(_.tag == "scripts").flatMap(blocks)
      case _ => node.elements.map(_.element).flatMap(blocks)
    }
    def modelLabel(block: BlockLike) = block match {
      case PrimitiveBlock(selector, variable, _, _) => selector.orElse(variable).getOrElse("block")
      case call: CustomBlockCall => call.semanticSpec
    }
    blocks(root).diff(project.allBlocks.map(modelLabel)).distinct
  }

  private def fromProject(root: Node): Project = {
    val scenesNode = firstChild(root, "scenes")
    // Without scenes, the older format has one stage; a minimal project holds its scripts itself.
    val scenes = childrenNamed(scenesNode, "scene").map(parseScene).toVector match {
      case Vector() => Vector(Scene(name = attr(root, "name").getOrElse("Untitled"),
        stage = parseStage(firstChild(root, "stage").getOrElse(root))))
      case found => found
    }
    Project(
      name = attr(root, "name").getOrElse("Untitled"),
      app = attr(root, "app").getOrElse(""),
      version = attr(root, "version").getOrElse("2"),
      notes = textChild(root, "notes").getOrElse(""),
      thumbnail = textChild(root, "thumbnail").filter(_.nonEmpty),
      scenes = scenes,
      selectedScene = attrIntOpt(scenesNode, "select").getOrElse(1),
      creator = textChild(root, "creator").filter(_.nonEmpty),
      origCreator = textChild(root, "origCreator").filter(_.nonEmpty),
      origName = textChild(root, "origName").filter(_.nonEmpty)
    )
  }

  private def fragment(root: Node): Project = {
    def withSprite(sprite: Sprite) = Project(scenes = Vector(Scene(stage = Stage(sprites = Vector(sprite)))))
    root.name match {
      case "script" => withSprite(Sprite(scripts = Vector(parseScript(root))))
      case "scripts" => withSprite(Sprite(scripts = parseScripts(Some(root))))
      case "block" | "custom-block" => withSprite(Sprite(scripts = Vector(Script(blocks = Vector(parseBlockLike(root))))))
      case "sprite" => withSprite(parseSprite(root))
      case "blocks" => Project(scenes = Vector(Scene(customBlocks = parseCustomBlockContainer(Some(root)))))
      case other => throw ProgrammingConversionException.unparsable(s"<$other> is not a Snap project, script or block.")
    }
  }

  private def parseScene(node: Node): Scene =
    Scene(
      name = attr(node, "name").getOrElse("Untitled"),
      notes = textChild(node, "notes").getOrElse(""),
      palette = attr(node, "palette"),
      categories = attrBool(node, "categories"),
      buttons = attrBool(node, "buttons"),
      clickRun = attrBool(node, "clickrun"),
      dragData = attrBool(node, "dragdata"),
      colorModel = attr(node, "colormodel"),
      hiddenPrimitives = textChild(node, "hidden").toList.flatMap(_.split("\\s+")).filter(_.nonEmpty).toVector,
      headers = parseTagMap(firstChild(node, "headers")),
      codeMappings = parseTagMap(firstChild(node, "code")),
      customBlocks = parseCustomBlockContainer(firstChild(node, "blocks")),
      primitiveBlocks = parseCustomBlockContainer(firstChild(node, "primitives")),
      stage = firstChild(node, "stage").map(parseStage).getOrElse(Stage()),
      variables = parseVariables(firstChild(node, "variables"))
    )

  private def parseStage(node: Node): Stage = {
    val spritesNode = firstChild(node, "sprites")
    Stage(
      name = attr(node, "name").getOrElse("Stage"),
      width = attrInt(node, "width").getOrElse(480),
      height = attrInt(node, "height").getOrElse(360),
      costume = attrInt(node, "costume").getOrElse(0),
      color = parseRgba(attr(node, "color").getOrElse("255,255,255,1"), Rgba(255, 255, 255, 1.0)),
      tempo = attrDouble(node, "tempo").getOrElse(60.0),
      threadsafe = attrBool(node, "threadsafe").getOrElse(false),
      penlog = attrBool(node, "penlog").getOrElse(false),
      instrument = attrInt(node, "instrument"),
      volume = attrDouble(node, "volume").getOrElse(100.0),
      pan = attrDouble(node, "pan").getOrElse(0.0),
      lines = attr(node, "lines").getOrElse("round"),
      ternary = attrBool(node, "ternary").getOrElse(false),
      hyperops = attrBool(node, "hyperops").getOrElse(true),
      codify = attrBool(node, "codify").getOrElse(false),
      inheritance = attrBool(node, "inheritance").getOrElse(true),
      sublistIDs = attrBool(node, "sublistIDs").getOrElse(false),
      pentrails = textChild(node, "pentrails").filter(_.nonEmpty),
      wear = firstChild(node, "wear").flatMap(parseWearCostume),
      costumes = parseCostumes(firstChild(node, "costumes")),
      sounds = parseSounds(firstChild(node, "sounds")),
      variables = parseVariables(firstChild(node, "variables")),
      blocks = parseCustomBlockContainer(firstChild(node, "blocks")),
      scripts = parseScripts(firstChild(node, "scripts")),
      sprites = childrenNamed(spritesNode, "sprite").map(parseSprite).toVector,
      selectedSprite = attrIntOpt(spritesNode, "select").getOrElse(1),
      id = attr(node, "id")
    )
  }

  private def parseSprite(node: Node): Sprite = {
    Sprite(
      name = attr(node, "name").getOrElse("Sprite"),
      idx = attrInt(node, "idx").getOrElse(1),
      x = attrDouble(node, "x").getOrElse(0.0),
      y = attrDouble(node, "y").getOrElse(0.0),
      heading = attrDouble(node, "heading").getOrElse(90.0),
      scale = attrDouble(node, "scale").getOrElse(1.0),
      volume = attrDouble(node, "volume").getOrElse(100.0),
      pan = attrDouble(node, "pan").getOrElse(0.0),
      rotation = attrInt(node, "rotation").getOrElse(1),
      instrument = attrInt(node, "instrument"),
      draggable = attrBool(node, "draggable").getOrElse(true),
      hidden = attrBool(node, "hidden").getOrElse(false),
      costume = attrInt(node, "costume").getOrElse(0),
      color = parseRgba(attr(node, "color").getOrElse("0,0,0,1"), Rgba(0, 0, 0, 1.0)),
      pen = attr(node, "pen").getOrElse("tip"),
      wear = firstChild(node, "wear").flatMap(parseWearCostume),
      costumes = parseCostumes(firstChild(node, "costumes")),
      sounds = parseSounds(firstChild(node, "sounds")),
      blocks = parseCustomBlockContainer(firstChild(node, "blocks")),
      variables = parseVariables(firstChild(node, "variables")),
      dispatches = parseDispatches(firstChild(node, "dispatches")),
      scripts = parseScripts(firstChild(node, "scripts")),
      id = attr(node, "id")
    )
  }

  private def parseScripts(scriptsNode: Option[Node]): Vector[Script] =
    childrenNamed(scriptsNode, "script").map(parseScript).toVector

  private def parseScript(node: Node): Script =
    Script(
      x = attrDouble(node, "x"),
      y = attrDouble(node, "y"),
      blocks = children(node).filter(isBlockNode).map(parseBlockLike).toVector
    )

  private def parseBlockLike(node: Node): BlockLike =
    node.name match {
      case "block" => parsePrimitiveBlock(node)
      case "custom-block" => parseCustomBlockCall(node)
      case _ => PrimitiveBlock(selector = Option(node.name))
    }

  private def parsePrimitiveBlock(node: Node): PrimitiveBlock =
    PrimitiveBlock(
      selector = attr(node, "s"),
      variable = attr(node, "var"),
      inputs = children(node).flatMap(parseInput).toVector,
      comment = firstChild(node, "comment").map(parseComment)
    )

  private def parseCustomBlockCall(node: Node): CustomBlockCall =
    CustomBlockCall(
      semanticSpec = attr(node, "s").getOrElse(""),
      scope = attr(node, "scope"),
      inputs = children(node).flatMap(parseInput).toVector,
      variables = parseVariables(firstChild(node, "variables")),
      comment = firstChild(node, "comment").map(parseComment)
    )

  private def parseInput(node: Node): Option[InputValue] = node.name match {
    // A slot holds either text or one nested value: a boolean or a menu option.
    case "l" => children(node) match {
      case List(value) if value.name == "bool" => parseInput(value)
      case List(value) if value.name == "option" => Some(Literal(value.text.trim))
      case _ => Some(Literal(node.text.trim))
    }
    case "bool" => Some(BoolLiteral(node.text.trim.equalsIgnoreCase("true")))
    case "color" => Some(ColorLiteral(parseRgba(node.text.trim, Rgba(0, 0, 0, 1.0))))
    case "script" => Some(NestedScript(parseScript(node)))
    case "block" | "custom-block" => Some(NestedBlock(parseBlockLike(node)))
    case "list" => Some(ListLiteral(children(node).flatMap(parseInput).toVector))
    case "comment" | "variables" => None
    case _ => Some(Literal(node.text.trim))
  }

  private def parseCustomBlockContainer(blocksNode: Option[Node]): Vector[CustomBlockDefinition] =
    childrenNamed(blocksNode, "block-definition").map(parseCustomBlockDefinition).toVector

  private def parseCustomBlockDefinition(node: Node): CustomBlockDefinition =
    CustomBlockDefinition(
      spec = attr(node, "s").getOrElse(""),
      blockType = attr(node, "type").flatMap(BlockType.fromXml).getOrElse(BlockType.Command),
      category = attr(node, "category").map(BlockCategory.fromXml).getOrElse(BlockCategory.Other),
      selector = attr(node, "selector"),
      primitive = attr(node, "primitive"),
      helper = attr(node, "helper"),
      space = attr(node, "space"),
      isGlobal = attrBool(node, "isGlobal"),
      isDisposable = attrBool(node, "isDisposable"),
      codeHeader = textChild(node, "header").filter(_.nonEmpty),
      codeMapping = textChild(node, "code").filter(_.nonEmpty),
      translations = parseTagMap(firstChild(node, "translations")),
      inputs = childrenNamed(firstChild(node, "inputs"), "input").map(parseBlockInputDefinition).toVector,
      variables = childrenNamed(firstChild(node, "variables"), "variable").flatMap(attr(_, "name")).toVector,
      body = parseCustomBlockBody(node),
      comment = firstChild(node, "comment").map(parseComment)
    )

  /** Snap stores the executed body as a direct `<script>` child; `<scripts>` holds editor orphans. */
  private def parseCustomBlockBody(node: Node): Vector[Script] = {
    val direct = firstChild(node, "script").map(parseScript).toVector
    if direct.nonEmpty then direct
    else parseScripts(firstChild(node, "scripts"))
  }

  private def parseBlockInputDefinition(node: Node): BlockInputDefinition =
    BlockInputDefinition(
      inputType = attr(node, "type").getOrElse("%s"),
      name = attr(node, "name").getOrElse(""),
      defaultValue = attr(node, "default"),
      options = childrenNamed(Some(node), "option").map(_.text.trim).toVector,
      readonly = attrBool(node, "readonly")
    )

  private def parseVariables(node: Option[Node]): Vector[Variable] =
    childrenNamed(node, "variable").map { variableNode =>
      Variable(
        name = attr(variableNode, "name").getOrElse(""),
        value = children(variableNode).headOption.flatMap(parseInput),
        transient = attrBool(variableNode, "transient").getOrElse(false),
        hidden = attrBool(variableNode, "hidden").getOrElse(false)
      )
    }.toVector

  private def parseCostumes(node: Option[Node]): Vector[Costume] =
    childrenNamed(node, "costume").map { costumeNode =>
      Costume(
        name = attr(costumeNode, "name").getOrElse(""),
        centerX = attrDouble(costumeNode, "center-x").getOrElse(0.0),
        centerY = attrDouble(costumeNode, "center-y").getOrElse(0.0),
        image = attr(costumeNode, "image").getOrElse(""),
        embed = attr(costumeNode, "embed"),
        id = attr(costumeNode, "id")
      )
    }.toVector

  private def parseWearCostume(node: Node): Option[Costume] =
    children(node).find(_.name == "costume").map { costumeNode =>
      Costume(
        name = attr(costumeNode, "name").getOrElse(""),
        centerX = attrDouble(costumeNode, "center-x").getOrElse(0.0),
        centerY = attrDouble(costumeNode, "center-y").getOrElse(0.0),
        image = attr(costumeNode, "image").getOrElse(""),
        embed = attr(costumeNode, "embed"),
        id = attr(costumeNode, "id")
      )
    }

  private def parseSounds(node: Option[Node]): Vector[Sound] =
    childrenNamed(node, "sound").map { soundNode =>
      Sound(
        name = attr(soundNode, "name").getOrElse(""),
        sound = attr(soundNode, "sound").getOrElse(""),
        id = attr(soundNode, "id")
      )
    }.toVector

  private def parseDispatches(node: Option[Node]): Vector[Dispatch] =
    children(node).map(n => Dispatch(attr(n, "name").getOrElse(n.text.trim))).filter(_.name.nonEmpty).toVector

  private def parseComment(node: Node): Comment =
    Comment(
      text = node.text,
      x = attrDouble(node, "x"),
      y = attrDouble(node, "y"),
      width = attrDouble(node, "w"),
      collapsed = attrBool(node, "collapsed").getOrElse(false)
    )

  private def parseTagMap(node: Option[Node]): Map[String, String] =
    children(node).map(child => child.name -> child.text).toMap

  private def parseRgba(value: String, default: Rgba): Rgba = {
    val parts = value.split(",").map(_.trim)
    if (parts.length < 4) default
    else {
      val maybe = for {
        r <- scala.util.Try(parts(0).toInt).toOption
        g <- scala.util.Try(parts(1).toInt).toOption
        b <- scala.util.Try(parts(2).toInt).toOption
        a <- scala.util.Try(parts(3).toDouble).toOption
      } yield Rgba(r, g, b, a)
      maybe.getOrElse(default)
    }
  }

  private def childrenNamed(parent: Option[Node], name: String): List[Node] =
    parent.toList.flatMap(children).filter(_.name == name)

  private def firstChild(parent: Node, name: String): Option[Node] =
    children(Some(parent)).find(_.name == name)

  private def firstChild(parent: Option[Node], name: String): Option[Node] =
    parent.flatMap(firstChild(_, name))

  private def textChild(parent: Node, name: String): Option[String] =
    firstChild(parent, name).map(_.text.trim)

  private def attr(node: Node, name: String): Option[String] =
    node.attribute(name).map(_.trim).filter(_.nonEmpty)

  private def attrBool(node: Node, name: String): Option[Boolean] =
    attr(node, name).map(_.equalsIgnoreCase("true"))

  private def attrInt(node: Node, name: String): Option[Int] =
    attr(node, name).flatMap(v => scala.util.Try(v.toInt).toOption)

  private def attrIntOpt(node: Option[Node], name: String): Option[Int] =
    node.flatMap(attrInt(_, name))

  private def attrDouble(node: Node, name: String): Option[Double] =
    attr(node, name).flatMap(v => scala.util.Try(v.toDouble).toOption)

  private def children(parent: Option[Node]): List[Node] =
    parent.toList.flatMap(children)

  private def children(parent: Node): List[Node] =
    parent.elements.map(_.element).toList

  private def isBlockNode(node: Node): Boolean = node.name == "block" || node.name == "custom-block"
}
