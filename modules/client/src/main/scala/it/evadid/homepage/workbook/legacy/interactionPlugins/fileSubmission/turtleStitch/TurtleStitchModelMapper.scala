package it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleStitch

import TurtleStitchProgramModel.*

object TurtleStitchModelMapper {

  val EmptyProject: Project = Project()

  def toProject(document: TurtleStitchXmlParser.XmlDocument): Project = {
    val projectNode = Option.when(document.root.name == "project")(document.root)
    val scenes = projectNode.toList.flatMap { root =>
      val scenesNode = firstChild(root, "scenes")
      childrenNamed(scenesNode, "scene").map(parseScene)
    }.toVector

    projectNode.filter(_ => scenes.nonEmpty).map { root =>
      val scenesNode = firstChild(root, "scenes")
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
    }.getOrElse(EmptyProject)
  }

  private def parseScene(node: TurtleStitchXmlParser.XmlElement): Scene =
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

  private def parseStage(node: TurtleStitchXmlParser.XmlElement): Stage = {
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

  private def parseSprite(node: TurtleStitchXmlParser.XmlElement): Sprite = {
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

  private def parseScripts(scriptsNode: Option[TurtleStitchXmlParser.XmlElement]): Vector[Script] =
    childrenNamed(scriptsNode, "script").map(parseScript).toVector

  private def parseScript(node: TurtleStitchXmlParser.XmlElement): Script =
    Script(
      x = attrDouble(node, "x"),
      y = attrDouble(node, "y"),
      blocks = children(node).filter(isBlockNode).map(parseBlockLike).toVector
    )

  private def parseBlockLike(node: TurtleStitchXmlParser.XmlElement): BlockLike =
    node.name match {
      case "block" => parsePrimitiveBlock(node)
      case "custom-block" => parseCustomBlockCall(node)
      case _ => PrimitiveBlock(selector = Option(node.name))
    }

  private def parsePrimitiveBlock(node: TurtleStitchXmlParser.XmlElement): PrimitiveBlock =
    PrimitiveBlock(
      selector = attr(node, "s"),
      variable = attr(node, "var"),
      inputs = children(node).flatMap(parseInput).toVector,
      comment = firstChild(node, "comment").map(parseComment)
    )

  private def parseCustomBlockCall(node: TurtleStitchXmlParser.XmlElement): CustomBlockCall =
    CustomBlockCall(
      semanticSpec = attr(node, "s").getOrElse(""),
      scope = attr(node, "scope"),
      inputs = children(node).flatMap(parseInput).toVector,
      variables = parseVariables(firstChild(node, "variables")),
      comment = firstChild(node, "comment").map(parseComment)
    )

  private def parseInput(node: TurtleStitchXmlParser.XmlElement): Option[InputValue] = node.name match {
    case "l" => Some(Literal(node.text.trim))
    case "bool" => Some(BoolLiteral(node.text.trim.equalsIgnoreCase("true")))
    case "color" => Some(ColorLiteral(parseRgba(node.text.trim, Rgba(0, 0, 0, 1.0))))
    case "script" => Some(NestedScript(parseScript(node)))
    case "block" | "custom-block" => Some(NestedBlock(parseBlockLike(node)))
    case "list" => Some(ListLiteral(children(node).flatMap(parseInput).toVector))
    case "comment" | "variables" => None
    case _ => Some(Literal(node.text.trim))
  }

  private def parseCustomBlockContainer(blocksNode: Option[TurtleStitchXmlParser.XmlElement]): Vector[CustomBlockDefinition] =
    childrenNamed(blocksNode, "block-definition").map(parseCustomBlockDefinition).toVector

  private def parseCustomBlockDefinition(node: TurtleStitchXmlParser.XmlElement): CustomBlockDefinition =
    CustomBlockDefinition(
      spec = attr(node, "s").getOrElse(""),
      blockType = attr(node, "type").getOrElse("command"),
      category = attr(node, "category").getOrElse("other"),
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
      body = parseScripts(firstChild(node, "scripts")),
      comment = firstChild(node, "comment").map(parseComment)
    )

  private def parseBlockInputDefinition(node: TurtleStitchXmlParser.XmlElement): BlockInputDefinition =
    BlockInputDefinition(
      inputType = attr(node, "type").getOrElse("%s"),
      name = attr(node, "name").getOrElse(""),
      defaultValue = attr(node, "default"),
      options = childrenNamed(Some(node), "option").map(_.text.trim).toVector,
      readonly = attrBool(node, "readonly")
    )

  private def parseVariables(node: Option[TurtleStitchXmlParser.XmlElement]): Vector[Variable] =
    childrenNamed(node, "variable").map { variableNode =>
      Variable(
        name = attr(variableNode, "name").getOrElse(""),
        value = children(variableNode).headOption.flatMap(parseInput),
        transient = attrBool(variableNode, "transient").getOrElse(false),
        hidden = attrBool(variableNode, "hidden").getOrElse(false)
      )
    }.toVector

  private def parseCostumes(node: Option[TurtleStitchXmlParser.XmlElement]): Vector[Costume] =
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

  private def parseWearCostume(node: TurtleStitchXmlParser.XmlElement): Option[Costume] =
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

  private def parseSounds(node: Option[TurtleStitchXmlParser.XmlElement]): Vector[Sound] =
    childrenNamed(node, "sound").map { soundNode =>
      Sound(
        name = attr(soundNode, "name").getOrElse(""),
        sound = attr(soundNode, "sound").getOrElse(""),
        id = attr(soundNode, "id")
      )
    }.toVector

  private def parseDispatches(node: Option[TurtleStitchXmlParser.XmlElement]): Vector[Dispatch] =
    children(node).map(n => Dispatch(attr(n, "name").getOrElse(n.text.trim))).filter(_.name.nonEmpty).toVector

  private def parseComment(node: TurtleStitchXmlParser.XmlElement): Comment =
    Comment(
      text = node.text,
      x = attrDouble(node, "x"),
      y = attrDouble(node, "y"),
      width = attrDouble(node, "w"),
      collapsed = attrBool(node, "collapsed").getOrElse(false)
    )

  private def parseTagMap(node: Option[TurtleStitchXmlParser.XmlElement]): Map[String, String] =
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

  private def childrenNamed(parent: Option[TurtleStitchXmlParser.XmlElement], name: String): List[TurtleStitchXmlParser.XmlElement] =
    parent.toList.flatMap(children).filter(_.name == name)

  private def firstChild(parent: TurtleStitchXmlParser.XmlElement, name: String): Option[TurtleStitchXmlParser.XmlElement] =
    children(Some(parent)).find(_.name == name)

  private def firstChild(parent: Option[TurtleStitchXmlParser.XmlElement], name: String): Option[TurtleStitchXmlParser.XmlElement] =
    parent.flatMap(firstChild(_, name))

  private def textChild(parent: TurtleStitchXmlParser.XmlElement, name: String): Option[String] =
    firstChild(parent, name).map(_.text.trim)

  private def attr(node: TurtleStitchXmlParser.XmlElement, name: String): Option[String] =
    node.attrs.get(name).map(_.trim).filter(_.nonEmpty)

  private def attrBool(node: TurtleStitchXmlParser.XmlElement, name: String): Option[Boolean] =
    attr(node, name).map(_.equalsIgnoreCase("true"))

  private def attrInt(node: TurtleStitchXmlParser.XmlElement, name: String): Option[Int] =
    attr(node, name).flatMap(v => scala.util.Try(v.toInt).toOption)

  private def attrIntOpt(node: Option[TurtleStitchXmlParser.XmlElement], name: String): Option[Int] =
    node.flatMap(attrInt(_, name))

  private def attrDouble(node: TurtleStitchXmlParser.XmlElement, name: String): Option[Double] =
    attr(node, name).flatMap(v => scala.util.Try(v.toDouble).toOption)

  private def children(parent: Option[TurtleStitchXmlParser.XmlElement]): List[TurtleStitchXmlParser.XmlElement] =
    parent.toList.flatMap(children)

  private def children(parent: TurtleStitchXmlParser.XmlElement): List[TurtleStitchXmlParser.XmlElement] =
    parent.children

  private def isBlockNode(node: TurtleStitchXmlParser.XmlElement): Boolean = node.name == "block" || node.name == "custom-block"
}
