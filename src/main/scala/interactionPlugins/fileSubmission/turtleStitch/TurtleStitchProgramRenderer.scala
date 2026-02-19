package interactionPlugins.fileSubmission.turtleStitch

import interactionPlugins.fileSubmission.turtleLogic.TurtleXmlParser
import interactionPlugins.fileSubmission.turtleStitch.TurtleStitchProgramModel.*
import interactionPlugins.fileSubmission.turtleStitchRendering.TurtleStitchSnapBlockSvgRenderer

object TurtleStitchProgramRenderer {

  // Upstream references in TurtleStitch/Snap:
  // - Script image composition: src/blocks.js, ScriptsMorph.prototype.scriptsPicture (~9032).
  // - XML script serialization semantics: src/store.js, BlockMorph.prototype.toScriptXML (~2436+).
  // Current renderer intentionally produces a simplified SVG approximation for portability.

  def commandsFrom(project: Project): List[TurtleXmlParser.Command] = {
    scala.util.Try {
      val scene = selectedScene(project)
      val stageAndSpriteScripts = scene.toList.flatMap { current =>
        val stageCommands = commandsFromScripts(current.stage.scripts)
        val spriteCommands = selectedSprite(current.stage).toList.flatMap(commandsFromSprite)
        stageCommands ++ spriteCommands
      }

      if (stageAndSpriteScripts.nonEmpty) stageAndSpriteScripts else project.scenes.toList.flatMap { scene =>
        commandsFromScripts(scene.stage.scripts) ++ scene.stage.sprites.toList.flatMap(commandsFromSprite)
      }
    }.getOrElse(Nil)
  }

  def renderScriptsAsSvgDataUrl(project: Project): Option[String] =
    TurtleStitchSnapBlockSvgRenderer.renderScriptsAsSvgDataUrl(project)

  private def commandsFromScripts(scripts: Vector[Script]): List[TurtleXmlParser.Command] =
    scriptsToExecute(scripts).flatMap(script => script.blocks.toList.flatMap(blockToCommand))

  private def commandsFromSprite(sprite: Sprite): List[TurtleXmlParser.Command] = {
    val setup = List(
      TurtleXmlParser.GotoXY(sprite.x, sprite.y),
      TurtleXmlParser.SetHeading(sprite.heading)
    )
    setup ++ commandsFromScripts(sprite.scripts)
  }

  private def scriptsToExecute(scripts: Vector[Script]): List[Script] = {
    val list = scripts.toList
    val greenFlagScripts = list.filter(containsReceiveGo)
    if (greenFlagScripts.nonEmpty) greenFlagScripts else list
  }

  private def containsReceiveGo(script: Script): Boolean =
    script.blocks.exists {
      case PrimitiveBlock(Some("receiveGo"), _, _, _) => true
      case _ => false
    }

  private def blockToCommand(blockLike: BlockLike): Option[TurtleXmlParser.Command] = blockLike match {
    case PrimitiveBlock(Some(selector), _, inputs, _) =>
      val numbers = inputs.collect { case Literal(value) => scala.util.Try(value.toDouble).toOption }.flatten
      val number1 = numbers.headOption.getOrElse(0.0)
      val number2 = numbers.drop(1).headOption.getOrElse(0.0)
      selector match {
        case "forward" | "forward:" => Some(TurtleXmlParser.Forward(number1))
        case "turn" | "turn:" | "turnRight" | "turnRight:" | "turnLeftAndRight" => Some(TurtleXmlParser.TurnRight(number1))
        case "turnLeft" | "turnLeft:" => Some(TurtleXmlParser.TurnLeft(number1))
        case "arcRight" => Some(TurtleXmlParser.ArcRight(number1, number2))
        case "arcLeft" => Some(TurtleXmlParser.ArcLeft(number1, number2))
        case "gotoXY" | "gotoX:y:" => Some(TurtleXmlParser.GotoXY(number1, number2))
        case "setHeading" | "heading:" => Some(TurtleXmlParser.SetHeading(number1))
        case "changeYPosition" | "changeYposBy:" => Some(TurtleXmlParser.ChangeYPosition(number1))
        case "clear" | "clearPenTrails" => Some(TurtleXmlParser.Clear)
        case "receiveGo" => Some(TurtleXmlParser.ReceiveGo)
        case "up" | "penup" => Some(TurtleXmlParser.PenUp)
        case "down" | "pendown" => Some(TurtleXmlParser.PenDown)
        case "doRepeat" | "doRepeat:" =>
          val times = number1.round.toInt.max(0)
          val body = inputs.collect { case NestedScript(script) => script }.headOption.toList
            .flatMap(s => s.blocks.toList.flatMap(blockToCommand))
          Some(TurtleXmlParser.Repeat(times, body))
        case _ => None
      }
    case _ => None
  }

  private def scriptToLines(script: Script): List[(Int, String)] =
    script.blocks.toList.flatMap(block => blockToLines(block, 0))

  private def blockToLines(block: BlockLike, indent: Int): List[(Int, String)] = block match {
    case PrimitiveBlock(Some(selector), _, inputs, _) =>
      val args = inputs.collect { case Literal(value) => value }.toList
      val label = formatBlock(selector, args)
      val nested = inputs.collect { case NestedScript(script) => script }.toList.flatMap(s => scriptToLines(s).map { case (i, text) => (i + indent + 1, text) })
      (indent, label) :: nested
    case CustomBlockCall(spec, _, _, _, _) => List((indent, spec))
    case _ => List((indent, "block"))
  }

  private def formatBlock(selector: String, args: List[String]): String = selector match {
    case "receiveGo" => "when green flag clicked"
    case "gotoXY" if args.size >= 2 => s"go to x: ${args(0)} y: ${args(1)}"
    case "clear" => "clear"
    case "doRepeat" if args.nonEmpty => s"repeat ${args.head}"
    case "forward" if args.nonEmpty => s"move ${args.head} steps"
    case "arcRight" if args.size >= 2 => s"arc ↻ radius: ${args(0)} degrees: ${args(1)}"
    case "arcLeft" if args.size >= 2 => s"arc ↺ radius: ${args(0)} degrees: ${args(1)}"
    case "turn" if args.nonEmpty => s"turn ↻ ${args.head} degrees"
    case "turnLeft" if args.nonEmpty => s"turn ↺ ${args.head} degrees"
    case "changeYPosition" if args.nonEmpty => s"change y by ${args.head}"
    case "setHeading" if args.nonEmpty => s"point in direction ${args.head}"
    case other if args.nonEmpty => s"$other ${args.mkString(" ")}"
    case other => other
  }

  private def selectedScene(project: Project): Option[Scene] =
    project.scenes.lift(project.selectedScene - 1).orElse(project.scenes.headOption)

  private def selectedSprite(stage: Stage): Option[Sprite] =
    stage.sprites.lift(stage.selectedSprite - 1).orElse(stage.sprites.headOption)

  private def escape(value: String): String =
    value
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")
}
