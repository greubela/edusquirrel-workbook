package interactionPlugins.fileSubmission.turtleLogic

import org.scalajs.dom
import scala.scalajs.js

object TurtleXmlParser {

  sealed trait Command
  case class Forward(distance: Double) extends Command
  case class TurnLeft(degrees: Double) extends Command
  case class TurnRight(degrees: Double) extends Command
  case class ArcRight(radius: Double, degrees: Double) extends Command
  case class ArcLeft(radius: Double, degrees: Double) extends Command
  case class GotoXY(x: Double, y: Double) extends Command
  case class SetHeading(degrees: Double) extends Command
  case class ChangeYPosition(delta: Double) extends Command
  case object Clear extends Command
  case object ReceiveGo extends Command
  case object PenUp extends Command
  case object PenDown extends Command
  case class Repeat(times: Int, body: List[Command]) extends Command

  def parse(xml: String): List[Command] = {
    val hasDomParser = {
      scala.util.Try(js.Dynamic.global.selectDynamic("DOMParser")).toOption
        .exists(p => !(js.isUndefined(p) || p == null))
    }

    if (hasDomParser) parseWithDomParser(xml)
    else parseWithStringFallback(xml)
  }

  private def parseWithDomParser(xml: String): List[Command] = {
    val parser = new dom.DOMParser()
    val document = parser.parseFromString(xml, "text/xml".asInstanceOf[dom.MIMEType])

    val scripts = document.getElementsByTagName("script")
    val topLevelScripts = (0 until scripts.length).toList
      .map(i => scripts.item(i))
      .filter(node => Option(node.parentNode).exists(_.nodeName == "scripts"))

    val allScriptCommands = topLevelScripts.flatMap(parseScript)

    if (allScriptCommands.nonEmpty) allScriptCommands
    else {
      val blocks = document.getElementsByTagName("block")
      (0 until blocks.length).toList
        .flatMap(i => parseBlock(blocks.item(i)).toList)
    }
  }

  private def parseWithStringFallback(xml: String): List[Command] = {
    val repeatPattern = """(?s)<block\s+s=\"doRepeat\"[^>]*>\s*<l>([-0-9.]+)</l>\s*<script>(.*?)</script>\s*</block>""".r
    val repeats = repeatPattern.findAllMatchIn(xml).toList.map { m =>
      val times = scala.util.Try(m.group(1).toDouble).getOrElse(0.0).round.toInt.max(0)
      val bodyXml = m.group(2)
      val body = parseLinearBlocks(bodyXml)
      Repeat(times, body)
    }

    val withoutRepeats = repeatPattern.replaceAllIn(xml, "")
    repeats ++ parseLinearBlocks(withoutRepeats)
  }

  private def parseLinearBlocks(xml: String): List[Command] = {
    val blockPattern = """(?s)<block\s+s=\"([^\"]+)\"[^>]*>(.*?)</block>""".r

    blockPattern.findAllMatchIn(xml).toList.flatMap { m =>
      val selector = m.group(1)
      val body = m.group(2)
      val numbers = """(?s)<l>\s*([-0-9.]+)\s*</l>""".r.findAllMatchIn(body).toList
        .flatMap(mm => scala.util.Try(mm.group(1).toDouble).toOption)

      val number = numbers.headOption.getOrElse(0.0)
      val number2 = numbers.drop(1).headOption.getOrElse(0.0)

      selector match {
        case "forward" | "forward:" => Some(Forward(number))
        case "turn" | "turn:" => Some(TurnRight(number))
        case "turnLeft" | "turnLeft:" => Some(TurnLeft(number))
        case "turnLeftAndRight" | "turnRight" | "turnRight:" => Some(TurnRight(number))
        case "arcRight" => Some(ArcRight(number, number2))
        case "arcLeft" => Some(ArcLeft(number, number2))
        case "gotoXY" | "gotoX:y:" => Some(GotoXY(number, number2))
        case "setHeading" | "heading:" => Some(SetHeading(number))
        case "changeYPosition" | "changeYposBy:" => Some(ChangeYPosition(number))
        case "clear" | "clearPenTrails" => Some(Clear)
        case "receiveGo" => Some(ReceiveGo)
        case "up" | "penup" => Some(PenUp)
        case "down" | "pendown" => Some(PenDown)
        case _ => None
      }
    }
  }

  private def parseScript(scriptNode: dom.Node): List[Command] = {
    firstBlockChild(scriptNode)
      .map(parseBlockChain)
      .getOrElse(Nil)
  }

  private def parseBlock(blockNode: dom.Node): Option[Command] = {
    val element = blockNode.asInstanceOf[dom.Element]
    val selector = element.getAttribute("s")
    parseSingleBlock(selector, element)
  }

  private def parseBlockChain(startBlock: dom.Node): List[Command] =
    iterateBlocks(startBlock).flatMap(parseBlock)

  private def parseSingleBlock(selector: String, blockElement: dom.Element): Option[Command] = {
    selector match {
      case "forward" | "forward:" =>
        Some(Forward(readNumberArg(blockElement, index = 0, default = 0.0)))
      case "turn" | "turn:" =>
        Some(TurnRight(readNumberArg(blockElement, index = 0, default = 0.0)))
      case "turnLeft" | "turnLeft:" =>
        Some(TurnLeft(readNumberArg(blockElement, index = 0, default = 0.0)))
      case "turnLeftAndRight" | "turnRight" | "turnRight:" =>
        Some(TurnRight(readNumberArg(blockElement, index = 0, default = 0.0)))
      case "arcRight" =>
        Some(ArcRight(
          readNumberArg(blockElement, index = 0, default = 0.0),
          readNumberArg(blockElement, index = 1, default = 0.0)
        ))
      case "arcLeft" =>
        Some(ArcLeft(
          readNumberArg(blockElement, index = 0, default = 0.0),
          readNumberArg(blockElement, index = 1, default = 0.0)
        ))
      case "gotoXY" | "gotoX:y:" =>
        Some(GotoXY(
          readNumberArg(blockElement, index = 0, default = 0.0),
          readNumberArg(blockElement, index = 1, default = 0.0)
        ))
      case "setHeading" | "heading:" =>
        Some(SetHeading(readNumberArg(blockElement, index = 0, default = 0.0)))
      case "changeYPosition" | "changeYposBy:" =>
        Some(ChangeYPosition(readNumberArg(blockElement, index = 0, default = 0.0)))
      case "clear" | "clearPenTrails" =>
        Some(Clear)
      case "receiveGo" =>
        Some(ReceiveGo)
      case "up" | "penup" => Some(PenUp)
      case "down" | "pendown" => Some(PenDown)
      case "doRepeat" | "doRepeat:" =>
        val times = readNumberArg(blockElement, index = 0, default = 0.0).round.toInt.max(0)
        val body = blockElement.childNodesAsList
          .find(_.nodeName == "script")
          .map(parseScript)
          .getOrElse(Nil)
        Some(Repeat(times, body))
      case _ => None
    }
  }

  private def readNumberArg(blockElement: dom.Element, index: Int, default: Double): Double = {
    val literal = blockElement.childNodesAsList
      .filter(_.nodeName == "l")
      .lift(index)
      .map(_.textContent.trim)
      .filter(_.nonEmpty)

    literal.flatMap(value => scala.util.Try(value.toDouble).toOption).getOrElse(default)
  }

  extension (node: dom.Node)
    private def childNodesAsList: List[dom.Node] =
      (0 until node.childNodes.length).toList.map(node.childNodes.item)

  private def firstBlockChild(node: dom.Node): Option[dom.Node] =
    node.childNodesAsList.find(_.nodeName == "block")

  private def iterateBlocks(start: dom.Node): List[dom.Node] = {
    @annotation.tailrec
    def loop(current: Option[dom.Node], acc: List[dom.Node]): List[dom.Node] =
      current match {
        case None => acc.reverse
        case Some(node) =>
          val nextBlock = findNextBlockSibling(node)
          loop(nextBlock, node :: acc)
      }

    loop(Some(start), Nil)
  }

  private def findNextBlockSibling(node: dom.Node): Option[dom.Node] = {
    @annotation.tailrec
    def next(n: dom.Node): Option[dom.Node] =
      Option(n.nextSibling) match {
        case None => None
        case Some(sibling) if sibling.nodeName == "block" => Some(sibling)
        case Some(sibling) => next(sibling)
      }

    next(node)
  }
}
