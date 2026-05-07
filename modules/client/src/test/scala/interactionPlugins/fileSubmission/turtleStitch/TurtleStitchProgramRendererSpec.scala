package interactionPlugins.fileSubmission.turtleStitch

import interactionPlugins.fileSubmission.turtleLogic.TurtleXmlParser
import interactionPlugins.fileSubmission.{TurtleFileSubmission, XmlFactory}
import interactionPlugins.fileSubmission.turtleStitch.TurtleStitchProgramModel.*
import munit.FunSuite

class TurtleStitchProgramRendererSpec extends FunSuite {

  test("read establishedXMLFiles"){
    val results = XmlFactory.all.map(TurtleFileSubmission.renderXmlAsTuple)
    assert(results.forall(_._2.nonEmpty))
    assert(results.exists(_._1.nonEmpty))

  }


  test("commandsFrom uses receiveGo script when present") {
    val scriptA = Script(blocks = Vector(
      PrimitiveBlock(selector = Some("forward"), inputs = Vector(Literal("100")))
    ))
    val scriptB = Script(blocks = Vector(
      PrimitiveBlock(selector = Some("receiveGo")),
      PrimitiveBlock(selector = Some("forward"), inputs = Vector(Literal("20")))
    ))

    val project = Project(scenes = Vector(
      Scene(stage = Stage(sprites = Vector(
        Sprite(
          name = "Sprite",
          idx = 1,
          x = 10,
          y = 15,
          heading = 30,
          scale = 1,
          volume = 100,
          pan = 0,
          rotation = 1,
          scripts = Vector(scriptA, scriptB)
        )
      )))
    ))

    val commands = TurtleStitchProgramRenderer.commandsFrom(project)
    assertEquals(
      commands,
      List(
        TurtleXmlParser.GotoXY(10, 15),
        TurtleXmlParser.SetHeading(30),
        TurtleXmlParser.ReceiveGo,
        TurtleXmlParser.Forward(20)
      )
    )
  }

  test("renderScriptsAsSvgDataUrl returns image for available scripts") {
    val project = Project(scenes = Vector(
      Scene(stage = Stage(sprites = Vector(
        Sprite(
          name = "Sprite",
          idx = 1,
          x = 0,
          y = 0,
          heading = 90,
          scale = 1,
          volume = 100,
          pan = 0,
          rotation = 1,
          scripts = Vector(
            Script(blocks = Vector(
              PrimitiveBlock(selector = Some("receiveGo")),
              PrimitiveBlock(selector = Some("forward"), inputs = Vector(Literal("10")))
            ))
          )
        )
      )))
    ))

    val image = TurtleStitchProgramRenderer.renderScriptsAsSvgDataUrl(project)
    assert(image.exists(_.startsWith("data:image/svg+xml;utf8,")))
  }
}
