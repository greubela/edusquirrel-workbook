package interactionPlugins.fileSubmission.turtleStitch

import interactionPlugins.fileSubmission.turtleStitch.TurtleStitchProgramModel.*
import org.scalajs.dom
import scala.scalajs.js

object TurtleStitchXmlLoader {

  // Upstream reference points in TurtleStitch:
  // - src/store.js: SnapSerializer.loadProjectModel (~335), loadScene (~368),
  //   loadScripts (~1358), loadScript (~1432), loadBlock (~1476), loadInput (~1596).
  // - src/scenes.js: Project/Scene constructors (~65/~114).
  // We intentionally keep tolerant parsing (DOM + string fallback) for workbook robustness.

  private val EmptyProject = Project()

  def load(xml: String): Project = {
    scala.util.Try {
      val hasDomParser = scala.util.Try(js.Dynamic.global.selectDynamic("DOMParser")).toOption
        .exists(parser => !(js.isUndefined(parser) || parser == null))
      val hasDocument = scala.util.Try(js.Dynamic.global.selectDynamic("document")).toOption
        .exists(document => !(js.isUndefined(document) || document == null))

      if (!hasDomParser || !hasDocument) {
        warnFallback("DOM parser is unavailable in this runtime")
        loadWithStringFallback(xml)
      } else {
        val parser = new dom.DOMParser()
        val document = parser.parseFromString(xml, "text/xml".asInstanceOf[dom.MIMEType])
        val projectNode = Option(document.getElementsByTagName("project").item(0)).getOrElse(document.documentElement)

        val scenesNode = firstChild(projectNode, "scenes")
        val sceneNodes = childrenNamed(scenesNode, "scene")
        val scenes = sceneNodes.map(parseScene).toVector

        Project(
          name = attr(projectNode, "name").getOrElse("Untitled"),
          app = attr(projectNode, "app").getOrElse(""),
          version = attr(projectNode, "version").getOrElse("2"),
          notes = textChild(projectNode, "notes").getOrElse(""),
          thumbnail = textChild(projectNode, "thumbnail").filter(_.nonEmpty),
          scenes = scenes,
          selectedScene = attrIntOpt(scenesNode, "select").getOrElse(1),
          creator = textChild(projectNode, "creator").filter(_.nonEmpty),
          origCreator = textChild(projectNode, "origCreator").filter(_.nonEmpty),
          origName = textChild(projectNode, "origName").filter(_.nonEmpty)
        )
      }
    }.recover { case error =>
      warnFallback(s"DOM parsing failed (${error.getClass.getSimpleName}); using string parser")
      loadWithStringFallback(xml)
    }.getOrElse(EmptyProject)
  }

  private def warnFallback(reason: String): Unit = {
    scala.util.Try {
      val globalConsole = js.Dynamic.global.selectDynamic("console")
      if (!(js.isUndefined(globalConsole) || globalConsole == null)) {
        globalConsole.selectDynamic("warn")(s"[WARN] TurtleStitchXmlLoader fallback: $reason")
      }
    }
    ()
  }


  private def loadWithStringFallback(xml: String): Project = {
    scala.util.Try {
      val pentrails = """(?s)<pentrails>\s*(.*?)\s*</pentrails>""".r.findFirstMatchIn(xml).map(_.group(1).trim)
      val scripts = parseScriptsFromString(xml)

      Project(
        name = attrFromString(xml, "project", "name").getOrElse("Untitled"),
        app = attrFromString(xml, "project", "app").getOrElse(""),
        version = attrFromString(xml, "project", "version").getOrElse("2"),
        notes = textTag(xml, "notes").getOrElse(""),
        selectedScene = attrFromString(xml, "scenes", "select").flatMap(v => scala.util.Try(v.toInt).toOption).getOrElse(1),
        scenes = Vector(
          Scene(
            name = attrFromString(xml, "scene", "name").getOrElse("Untitled"),
            stage = Stage(
              name = attrFromString(xml, "stage", "name").getOrElse("Stage"),
              pentrails = pentrails,
              scripts = scripts,
              selectedSprite = attrFromString(xml, "sprites", "select").flatMap(v => scala.util.Try(v.toInt).toOption).getOrElse(1),
              sprites = Vector(Sprite(
                name = attrFromString(xml, "sprite", "name").getOrElse("Sprite"),
                idx = attrFromString(xml, "sprite", "idx").flatMap(v => scala.util.Try(v.toInt).toOption).getOrElse(1),
                x = attrFromString(xml, "sprite", "x").flatMap(v => scala.util.Try(v.toDouble).toOption).getOrElse(0.0),
                y = attrFromString(xml, "sprite", "y").flatMap(v => scala.util.Try(v.toDouble).toOption).getOrElse(0.0),
                heading = attrFromString(xml, "sprite", "heading").flatMap(v => scala.util.Try(v.toDouble).toOption).getOrElse(90.0),
                scale = attrFromString(xml, "sprite", "scale").flatMap(v => scala.util.Try(v.toDouble).toOption).getOrElse(1.0),
                volume = attrFromString(xml, "sprite", "volume").flatMap(v => scala.util.Try(v.toDouble).toOption).getOrElse(100.0),
                pan = attrFromString(xml, "sprite", "pan").flatMap(v => scala.util.Try(v.toDouble).toOption).getOrElse(0.0),
                rotation = attrFromString(xml, "sprite", "rotation").flatMap(v => scala.util.Try(v.toInt).toOption).getOrElse(1),
                scripts = scripts
              ))
            )
          )
        )
      )
    }.getOrElse(EmptyProject)
  }

  private def parseScriptsFromString(xml: String): Vector[Script] = {
    val scriptPattern = """(?s)<script[^>]*>(.*?)</script>""".r
    val scripts = scriptPattern.findAllMatchIn(xml).toList.map { m =>
      Script(blocks = parseBlocksFromString(m.group(1)).toVector)
    }
    if (scripts.nonEmpty) scripts.toVector else Vector.empty
  }

  private def parseBlocksFromString(xml: String): List[BlockLike] = {
    val normalizedXml = """<block\s+([^>]*)/>""".r.replaceAllIn(xml, m => s"<block ${m.group(1)}></block>")
    val blockPattern = """(?s)<block\s+([^>]*)>(.*?)</block>""".r

    blockPattern.findAllMatchIn(normalizedXml).toList.map { m =>
      val attrs = m.group(1)
      val body = m.group(2)
      val selector = attrFromAttrText(attrs, "s")
      val literals = """(?s)<l>\s*(.*?)\s*</l>""".r.findAllMatchIn(body).toList.map(mm => Literal(mm.group(1).trim))
      val nestedScript = """(?s)<script>(.*?)</script>""".r.findFirstMatchIn(body).map(mm => NestedScript(Script(blocks = parseBlocksFromString(mm.group(1)).toVector))).toList
      PrimitiveBlock(selector = selector, inputs = (literals ++ nestedScript).toVector)
    }
  }

  private def attrFromString(xml: String, tag: String, key: String): Option[String] = {
    val pattern = ("""(?s)<""" + tag + """\b([^>]*)>""").r
    pattern.findFirstMatchIn(xml).flatMap(m => attrFromAttrText(m.group(1), key))
  }

  private def attrFromAttrText(attrs: String, key: String): Option[String] = {
    val pattern = (key + "=\"([^\"]+)\"").r
    pattern.findFirstMatchIn(attrs).map(_.group(1))
  }

  private def textTag(xml: String, tag: String): Option[String] =
    ("(?s)<" + tag + ">\\s*(.*?)\\s*</" + tag + ">" ).r.findFirstMatchIn(xml).map(_.group(1))

  private def parseScene(node: dom.Node): Scene = {
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
  }

  private def parseStage(node: dom.Node): Stage = {
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

  private def parseSprite(node: dom.Node): Sprite = {
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

  private def parseScripts(scriptsNode: Option[dom.Node]): Vector[Script] =
    childrenNamed(scriptsNode, "script").map(parseScript).toVector

  private def parseScript(node: dom.Node): Script =
    Script(
      x = attrDouble(node, "x"),
      y = attrDouble(node, "y"),
      blocks = children(node).filter(isBlockNode).map(parseBlockLike).toVector
    )

  private def parseBlockLike(node: dom.Node): BlockLike =
    node.nodeName match {
      case "block" => parsePrimitiveBlock(node)
      case "custom-block" => parseCustomBlockCall(node)
      case _ => PrimitiveBlock(selector = Option(node.nodeName))
    }

  private def parsePrimitiveBlock(node: dom.Node): PrimitiveBlock =
    PrimitiveBlock(
      selector = attr(node, "s"),
      variable = attr(node, "var"),
      inputs = children(node).flatMap(parseInput).toVector,
      comment = firstChild(node, "comment").map(parseComment)
    )

  private def parseCustomBlockCall(node: dom.Node): CustomBlockCall =
    CustomBlockCall(
      semanticSpec = attr(node, "s").getOrElse(""),
      scope = attr(node, "scope"),
      inputs = children(node).flatMap(parseInput).toVector,
      variables = parseVariables(firstChild(node, "variables")),
      comment = firstChild(node, "comment").map(parseComment)
    )

  private def parseInput(node: dom.Node): Option[InputValue] = node.nodeName match {
    case "l" => Some(Literal(node.textContent.trim))
    case "bool" => Some(BoolLiteral(node.textContent.trim.equalsIgnoreCase("true")))
    case "color" => Some(ColorLiteral(parseRgba(node.textContent.trim, Rgba(0, 0, 0, 1.0))))
    case "script" => Some(NestedScript(parseScript(node)))
    case "block" | "custom-block" => Some(NestedBlock(parseBlockLike(node)))
    case "list" => Some(ListLiteral(children(node).flatMap(parseInput).toVector))
    case "comment" | "variables" => None
    case _ => Some(Literal(node.textContent.trim))
  }

  private def parseCustomBlockContainer(blocksNode: Option[dom.Node]): Vector[CustomBlockDefinition] =
    childrenNamed(blocksNode, "block-definition").map(parseCustomBlockDefinition).toVector

  private def parseCustomBlockDefinition(node: dom.Node): CustomBlockDefinition =
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

  private def parseBlockInputDefinition(node: dom.Node): BlockInputDefinition =
    BlockInputDefinition(
      inputType = attr(node, "type").getOrElse("%s"),
      name = attr(node, "name").getOrElse(""),
      defaultValue = attr(node, "default"),
      options = childrenNamed(Some(node), "option").map(_.textContent.trim).toVector,
      readonly = attrBool(node, "readonly")
    )

  private def parseVariables(node: Option[dom.Node]): Vector[Variable] =
    childrenNamed(node, "variable").map { variableNode =>
      Variable(
        name = attr(variableNode, "name").getOrElse(""),
        value = children(variableNode).headOption.flatMap(parseInput),
        transient = attrBool(variableNode, "transient").getOrElse(false),
        hidden = attrBool(variableNode, "hidden").getOrElse(false)
      )
    }.toVector

  private def parseCostumes(node: Option[dom.Node]): Vector[Costume] =
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

  private def parseWearCostume(node: dom.Node): Option[Costume] =
    children(node).find(_.nodeName == "costume").map { costumeNode =>
      Costume(
        name = attr(costumeNode, "name").getOrElse(""),
        centerX = attrDouble(costumeNode, "center-x").getOrElse(0.0),
        centerY = attrDouble(costumeNode, "center-y").getOrElse(0.0),
        image = attr(costumeNode, "image").getOrElse(""),
        embed = attr(costumeNode, "embed"),
        id = attr(costumeNode, "id")
      )
    }

  private def parseSounds(node: Option[dom.Node]): Vector[Sound] =
    childrenNamed(node, "sound").map { soundNode =>
      Sound(
        name = attr(soundNode, "name").getOrElse(""),
        sound = attr(soundNode, "sound").getOrElse(""),
        id = attr(soundNode, "id")
      )
    }.toVector

  private def parseDispatches(node: Option[dom.Node]): Vector[Dispatch] =
    children(node).map(n => Dispatch(attr(n, "name").getOrElse(n.textContent.trim))).filter(_.name.nonEmpty).toVector

  private def parseComment(node: dom.Node): Comment =
    Comment(
      text = node.textContent,
      x = attrDouble(node, "x"),
      y = attrDouble(node, "y"),
      width = attrDouble(node, "w"),
      collapsed = attrBool(node, "collapsed").getOrElse(false)
    )

  private def parseTagMap(node: Option[dom.Node]): Map[String, String] =
    children(node).map(child => child.nodeName -> child.textContent).toMap

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

  private def childrenNamed(parent: Option[dom.Node], name: String): List[dom.Node] =
    parent.toList.flatMap(children).filter(_.nodeName == name)

  private def firstChild(parent: dom.Node, name: String): Option[dom.Node] =
    children(Some(parent)).find(_.nodeName == name)

  private def firstChild(parent: Option[dom.Node], name: String): Option[dom.Node] =
    parent.flatMap(firstChild(_, name))

  private def textChild(parent: dom.Node, name: String): Option[String] =
    firstChild(parent, name).map(_.textContent.trim)

  private def attr(node: dom.Node, name: String): Option[String] = {
    val element = node.asInstanceOf[dom.Element]
    val value = Option(element.getAttribute(name)).map(_.trim).getOrElse("")
    if (value.nonEmpty) Some(value) else None
  }

  private def attrBool(node: dom.Node, name: String): Option[Boolean] =
    attr(node, name).map(_.equalsIgnoreCase("true"))

  private def attrInt(node: dom.Node, name: String): Option[Int] =
    attr(node, name).flatMap(v => scala.util.Try(v.toInt).toOption)

  private def attrIntOpt(node: Option[dom.Node], name: String): Option[Int] =
    node.flatMap(attrInt(_, name))

  private def attrDouble(node: dom.Node, name: String): Option[Double] =
    attr(node, name).flatMap(v => scala.util.Try(v.toDouble).toOption)

  private def children(parent: Option[dom.Node]): List[dom.Node] =
    parent.toList.flatMap(children)

  private def children(parent: dom.Node): List[dom.Node] =
    (0 until parent.childNodes.length).toList
      .flatMap(index => Option(parent.childNodes.item(index)))
      .filter(isElementNode)

  private val ElementNodeType = 1

  private def isElementNode(node: dom.Node): Boolean = {
    val nodeTypeValue = scala.util.Try {
      node.asInstanceOf[js.Dynamic].selectDynamic("nodeType")
    }.toOption

    nodeTypeValue.exists { rawValue =>
      if (js.isUndefined(rawValue) || rawValue == null) false
      else {
        scala.util.Try(rawValue.toString.toDouble.toInt).toOption
          .contains(ElementNodeType)
      }
    }
  }

  private def isBlockNode(node: dom.Node): Boolean = node.nodeName == "block" || node.nodeName == "custom-block"
}
