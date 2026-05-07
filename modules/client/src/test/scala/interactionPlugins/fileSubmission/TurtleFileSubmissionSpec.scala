package interactionPlugins.fileSubmission

import interactionPlugins.fileSubmission.turtleLogic.TurtleXmlParser
import interactionPlugins.fileSubmission.turtleStitch.TurtleStitchProgramModel.*
import interactionPlugins.blockEnvironment.programming.BeProgram
import munit.FunSuite

import java.nio.charset.StandardCharsets
import java.net.URLDecoder
import scala.util.Try

class TurtleFileSubmissionSpec extends FunSuite {

  private val tinyPngDataUrl =
    "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO8B5hoAAAAASUVORK5CYII="

  private val xmlWithPentrails =
    s"""<project name=\"test1\" app=\"TurtleStitch 2.11, http://www.turtlestitch.org\" version=\"2\">
       |  <scenes select=\"1\">
       |    <scene name=\"test1\">
       |      <stage name=\"Stage\" width=\"480\" height=\"360\" id=\"6\">
       |        <pentrails>$tinyPngDataUrl</pentrails>
       |        <sprites select=\"1\">
       |          <sprite name=\"Sprite\" id=\"13\">
       |            <scripts>
       |              <script x=\"214\" y=\"151\">
       |                <block s=\"receiveGo\"></block>
       |                <block s=\"forward\"><l>100</l></block>
       |              </script>
       |            </scripts>
       |          </sprite>
       |        </sprites>
       |      </stage>
       |    </scene>
       |  </scenes>
       |</project>""".stripMargin

  private val xmlWithRepeatNoPentrails =
    """<project name="test2" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes></notes><scenes select="1"><scene name="test2"><notes></notes><hidden></hidden><headers></headers><code></code><blocks></blocks><primitives></primitives><stage name="Stage" width="480" height="360" costume="0" color="255,255,255,1" tempo="60" threadsafe="false" penlog="false" volume="100" pan="0" lines="round" ternary="false" hyperops="true" codify="false" inheritance="true" sublistIDs="false" id="6"><costumes><list struct="atomic" id="7"></list></costumes><sounds><list struct="atomic" id="8"></list></sounds><variables></variables><blocks></blocks><scripts></scripts><sprites select="1"><sprite name="Sprite" idx="1" x="0" y="0" heading="90" scale="0.1" volume="100" pan="0" rotation="1" draggable="true" hidden="true" costume="0" color="0,0,0,1" pen="tip" id="13"><costumes><list struct="atomic" id="14"></list></costumes><sounds><list struct="atomic" id="15"></list></sounds><blocks></blocks><variables></variables><scripts><script x="156" y="66"><block s="receiveGo"></block><block s="doRepeat"><l>10</l><script><block s="forward"><l>10</l></block><block s="turn"><l>15</l></block></script></block></script></scripts></sprite></sprites></stage><variables></variables></scene></scenes><creator>anonymous</creator><origCreator></origCreator><origName></origName></project>"""

  private val xmlWithMixedCommandsNoPentrails =
    """<project name="test2" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes></notes><scenes select="1"><scene name="test2"><notes></notes><hidden></hidden><headers></headers><code></code><blocks></blocks><primitives></primitives><stage name="Stage" width="480" height="360" costume="0" color="255,255,255,1" tempo="60" threadsafe="false" penlog="false" volume="100" pan="0" lines="round" ternary="false" hyperops="true" codify="false" inheritance="true" sublistIDs="false" id="6"><costumes><list struct="atomic" id="7"></list></costumes><sounds><list struct="atomic" id="8"></list></sounds><variables></variables><blocks></blocks><scripts></scripts><sprites select="1"><sprite name="Sprite" idx="1" x="380.38825611104767" y="1532.9940431923285" heading="30" scale="0.1" volume="100" pan="0" rotation="1" draggable="true" hidden="true" costume="0" color="0,0,0,1" pen="tip" id="13"><costumes><list struct="atomic" id="14"></list></costumes><sounds><list struct="atomic" id="15"></list></sounds><blocks></blocks><variables></variables><scripts><script x="156" y="66"><block s="receiveGo"></block><block s="doRepeat"><l>10</l><script><block s="forward"><l>10</l></block><block s="arcRight"><l>50</l><l>30</l></block><block s="arcLeft"><l>50</l><l>90</l></block><block s="turn"><l>15</l></block><block s="forward"><l>50</l></block><block s="changeYPosition"><l>10</l></block><block s="forward"><l>10</l></block><block s="setHeading"><l>90</l></block><block s="setHeading"><l>30</l></block></script></block></script></scripts></sprite></sprites></stage><variables></variables></scene></scenes><creator>anonymous</creator><origCreator>anonymous</origCreator><origName></origName></project>"""


  test("renderFileAsTuple returns existing pentrails and simulated render for xml with pentrails") {
    val bytes = xmlWithPentrails.getBytes(StandardCharsets.UTF_8).map(_.toByte)
    val (existing, simulated) = TurtleFileSubmission.renderFileAsTuple(bytes)

    assertEquals(existing, tinyPngDataUrl)
    assert(simulated.startsWith("data:image/png;base64,"))
    assert(simulated.length > "data:image/png;base64,".length)
    assertEquals(TurtleFileSubmission.renderFile(bytes), tinyPngDataUrl)
  }

  test("renderFileAsTuple returns empty existing value and simulated render for xml without pentrails") {
    val bytes = xmlWithRepeatNoPentrails.getBytes(StandardCharsets.UTF_8).map(_.toByte)
    val (existing, simulated) = TurtleFileSubmission.renderFileAsTuple(bytes)

    assertEquals(existing, "")
    assert(simulated.startsWith("data:image/png;base64,"))
    assert(simulated.length > "data:image/png;base64,".length)
    assertEquals(TurtleFileSubmission.renderFile(bytes), simulated)
  }

  test("parser handles doRepeat chain in provided xml") {
    val commands = TurtleXmlParser.parse(xmlWithRepeatNoPentrails)
    assertEquals(
      commands,
      List(
        TurtleXmlParser.ReceiveGo,
        TurtleXmlParser.Repeat(
          10,
          List(TurtleXmlParser.Forward(10.0), TurtleXmlParser.TurnRight(15.0))
        )
      )
    )
  }


  test("renderFileAsTuple handles mixed unsupported/supported TurtleStitch commands") {
    val bytes = xmlWithMixedCommandsNoPentrails.getBytes(StandardCharsets.UTF_8).map(_.toByte)
    val (existing, simulated) = TurtleFileSubmission.renderFileAsTuple(bytes)

    assertEquals(existing, "")
    assert(simulated.startsWith("data:image/png;base64,"))
    assert(simulated.length > "data:image/png;base64,".length)
    assertEquals(TurtleFileSubmission.renderFile(bytes), simulated)
  }

  test("parser keeps supported commands from mixed TurtleStitch command sequence") {
    val commands = TurtleXmlParser.parse(xmlWithMixedCommandsNoPentrails)
    assertEquals(
      commands,
      List(
        TurtleXmlParser.ReceiveGo,
        TurtleXmlParser.Repeat(
          10,
          List(
            TurtleXmlParser.Forward(10.0),
            TurtleXmlParser.ArcRight(50.0, 30.0),
            TurtleXmlParser.ArcLeft(50.0, 90.0),
            TurtleXmlParser.TurnRight(15.0),
            TurtleXmlParser.Forward(50.0),
            TurtleXmlParser.ChangeYPosition(10.0),
            TurtleXmlParser.Forward(10.0),
            TurtleXmlParser.SetHeading(90.0),
            TurtleXmlParser.SetHeading(30.0)
          )
        )
      )
    )
  }


  test("loadProject parses scene/stage/sprite/script model from Turtle XML") {
    val project = TurtleFileSubmission.loadProject(xmlWithRepeatNoPentrails)

    assertEquals(project.name, "test2")
    assertEquals(project.scenes.length, 1)
    assertEquals(project.scenes.head.stage.sprites.length, 1)
    assert(project.scenes.head.stage.sprites.head.scripts.nonEmpty)
  }

  test("renderProgramAsSvg creates an SVG data URL for script view") {
    val svgDataUrl = TurtleFileSubmission.renderProgramAsSvg(xmlWithRepeatNoPentrails)
    assert(svgDataUrl.exists(_.startsWith("data:image/svg+xml;utf8,")))
  }

  test("malformed xml does not crash and returns safe defaults") {
    val malformedXml = "<project><scenes><scene><stage><sprites><sprite><scripts><script><block s=\"forward\"><l>10</l>"
    val (existing, simulated) = TurtleFileSubmission.renderXmlAsTuple(malformedXml)
    val project = TurtleFileSubmission.loadProject(malformedXml)

    assertEquals(existing, "")
    assert(simulated.startsWith("data:image/png;base64,"))
    assertEquals(project.name, "Untitled")
  }

  test("invalid numeric script args do not crash renderer") {
    val xml =
      """<project name="x"><scenes select="1"><scene name="s"><stage><sprites select="1"><sprite name="sp" idx="1" x="foo" y="bar" heading="baz" scale="1" volume="100" pan="0" rotation="1"><scripts><script><block s="receiveGo"></block><block s="forward"><l>abc</l></block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""
    val (existing, simulated) = TurtleFileSubmission.renderXmlAsTuple(xml)
    val svg = TurtleFileSubmission.renderProgramAsSvg(xml)

    assertEquals(existing, "")
    assert(simulated.startsWith("data:image/png;base64,"))
    assert(svg.exists(_.startsWith("data:image/svg+xml;utf8,")))
  }

  test("green flag script survives xml -> model -> xml round trip") {
    XmlFactory.all.foreach { xml =>
      val project = TurtleFileSubmission.loadProject(xml)
      val expression = interactionPlugins.fileSubmission.turtleStitch.TurtleStitchToBeExpressionParser.parseProject(project)
      val xmlRoundTripped = TurtleFileSubmission.serializeFromBeExpression(expression, "roundTrip1")

      val originalSelectors = greenFlagSelectors(xml)
      val newSelectors = greenFlagSelectors(xmlRoundTripped)

      assert(originalSelectors.nonEmpty)
      assert(newSelectors.nonEmpty)
      assertEquals(newSelectors.headOption, Some("receiveGo"))
    }
  }

  test("green flag script survives turtle -> BeExpression -> turtle round trip for both sample XML files") {
    val xmls = List(xmlWithRepeatNoPentrails, xmlWithMixedCommandsNoPentrails)

    xmls.foreach { xml =>
      val expression = TurtleFileSubmission.parseToBeExpression(xml)
      val xmlRoundTripped = TurtleFileSubmission.serializeFromBeExpression(expression, "roundTrip2")

      val originalSelectors = greenFlagSelectors(xml)
      val newSelectors = greenFlagSelectors(xmlRoundTripped)

      assert(originalSelectors.headOption.contains("receiveGo"))
      assert(newSelectors.headOption.contains("receiveGo"))
      assert(newSelectors.contains("receiveGo"))
    }
  }

  test("parseFileBytesToBeExpression converts UTF-8 XML bytes to a BeExpression") {
    val bytes = xmlWithRepeatNoPentrails.getBytes(StandardCharsets.UTF_8)
    val expression = TurtleFileSubmission.parseFileBytesToBeExpression(bytes)

    assert(expression != null)
  }

  test("parseFileBytesToBeProgram converts UTF-8 XML bytes to a BeProgram") {
    val bytes = xmlWithMixedCommandsNoPentrails.getBytes(StandardCharsets.UTF_8)
    val program = TurtleFileSubmission.parseFileBytesToBeProgram(bytes)

    assert(program.isInstanceOf[BeProgram])
    assert(program.fullProgram != null)
  }

  test("self-closing receiveGo blocks are preserved through parse and round trip") {
    val xml =
      """<project name="x" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><scenes select="1"><scene name="x"><stage name="Stage"><sprites select="1"><sprite name="Sprite" idx="1" x="0" y="0" heading="90" scale="1" volume="100" pan="0" rotation="1"><scripts><script x="10" y="10"><block s="receiveGo"/><block s="forward"><l>12</l></block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""

    val expression = TurtleFileSubmission.parseToBeExpression(xml)
    val roundTripped = TurtleFileSubmission.serializeFromBeExpression(expression)
    val selectors = greenFlagSelectors(roundTripped)

    assert(selectors.headOption.contains("receiveGo"))
    assert(selectors.contains("forward"))
  }

  test("XmlFactory examples can all be parsed and rendered as simulated output") {
    XmlFactory.all.zipWithIndex.foreach { case (xml, index) =>
      val (existing, simulated) = TurtleFileSubmission.renderXmlAsTuple(xml)
      val programSvg = TurtleFileSubmission.renderProgramAsSvg(xml)
      val commands = TurtleXmlParser.parse(xml)

      assert(simulated.startsWith("data:image/png;base64,"), clues(s"example=${index + 1}"))
      assert(simulated.length > "data:image/png;base64,".length, clues(s"example=${index + 1}"))
      assert(programSvg.exists(_.startsWith("data:image/svg+xml;utf8,")), clues(s"example=${index + 1}"))
      assert(commands.nonEmpty, clues(s"example=${index + 1}"))

      if index == 0 then assert(existing.startsWith("data:image/png;base64,"), clues(s"example=${index + 1}"))
      else assertEquals(existing, "", clues(s"example=${index + 1}"))
    }
  }

  test("XmlFactory examples keep all green-flag block labels in rendered SVG") {
    XmlFactory.all.zipWithIndex.foreach { case (xml, index) =>
      val project = TurtleFileSubmission.loadProject(xml)
      val expectedLabels = labelsFromGreenFlagScript(project)
      val svgDecoded = TurtleFileSubmission.renderProgramAsSvg(xml).map(decodeSvgDataUrl).getOrElse("")

      assert(expectedLabels.nonEmpty, clues(s"example=${index + 1}"))
      expectedLabels.foreach { label =>
        assert(
          svgDecoded.contains(xmlEscaped(label)),
          clues(s"example=${index + 1}, missingLabel=$label")
        )
      }
    }
  }

  test("XmlFactory examples survive turtle model -> BeExpression -> turtle model for most files") {
    val results = XmlFactory.all.zipWithIndex.map { case (xml, index) =>
      val roundTripTry = Try {
        val initialProject = TurtleFileSubmission.loadProject(xml)
        val expression = interactionPlugins.fileSubmission.turtleStitch.TurtleStitchToBeExpressionParser.parseProject(initialProject)
        val roundTrippedXml = TurtleFileSubmission.serializeFromBeExpression(expression, s"roundTrip-${index + 1}")
        val roundTrippedProject = TurtleFileSubmission.loadProject(roundTrippedXml)

        val beforeSelectors = labelsFromGreenFlagScript(initialProject)
        val afterSelectors = labelsFromGreenFlagScript(roundTrippedProject)

        assert(beforeSelectors.headOption.contains("when green flag clicked"), clues(s"example=${index + 1}"))
        assert(afterSelectors.headOption.contains("when green flag clicked"), clues(s"example=${index + 1}"))
      }

      index + 1 -> roundTripTry
    }

    val failures = results.collect { case (idx, scala.util.Failure(error)) => s"example=$idx, error=${error.getMessage}" }
    val successCount = results.count(_._2.isSuccess)

    assert(successCount >= XmlFactory.all.size - 1, clues(failures.mkString(" | ")))
  }

  private def greenFlagSelectors(xml: String): List[String] = {
    val scriptPattern = """(?s)<script[^>]*>(.*?)</script>""".r
    val blockPattern = """(?s)<block\s+[^>]*s=\"([^\"]+)\"[^>]*>.*?</block>|<block\s+[^>]*s=\"([^\"]+)\"\s*/>""".r

    scriptPattern.findAllMatchIn(xml).toList
      .map(_.group(1))
      .map { scriptBody =>
        blockPattern.findAllMatchIn(scriptBody).toList.map { m => Option(m.group(1)).getOrElse(m.group(2)) }
      }
      .find(_.headOption.contains("receiveGo"))
      .getOrElse(Nil)
  }

  private def labelsFromGreenFlagScript(project: Project): List[String] = {
    val scripts = project.scenes.toList.flatMap(_.stage.sprites.toList.flatMap(_.scripts.toList))
    scripts.find(_.blocks.headOption.exists {
      case PrimitiveBlock(Some("receiveGo"), _, _, _) => true
      case _ => false
    }).toList.flatMap(script => script.blocks.toList.flatMap(blockToLabels))
  }

  private def blockToLabels(blockLike: BlockLike): List[String] = blockLike match {
    case PrimitiveBlock(Some(selector), _, inputs, _) =>
      val args = inputs.collect { case Literal(value) => value }.toList
      val label = selector match {
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
      val nestedLabels = inputs.collect { case NestedScript(script) => script.blocks.toList.flatMap(blockToLabels) }.flatten.toList
      label :: nestedLabels
    case _ => Nil
  }

  private def decodeSvgDataUrl(dataUrl: String): String = {
    val prefix = "data:image/svg+xml;utf8,"
    if dataUrl.startsWith(prefix) then URLDecoder.decode(dataUrl.drop(prefix.length), StandardCharsets.UTF_8)
    else ""
  }

  private def xmlEscaped(value: String): String =
    value
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")

}
