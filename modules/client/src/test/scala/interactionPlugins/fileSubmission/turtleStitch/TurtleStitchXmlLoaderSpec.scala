package interactionPlugins.fileSubmission.turtleStitch

import it.evadid.homepage.workbook.legacy.interactionPlugins.fileSubmission.turtleStitch.TurtleStitchXmlLoader
import munit.FunSuite

class TurtleStitchXmlLoaderSpec extends FunSuite {

  test("load parses simple_forward project XML without crashing") {
    val xml =
      """<project name="simple_forward" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes></notes><scenes select="1"><scene name="simple_forward"><notes></notes><hidden></hidden><headers></headers><code></code><blocks></blocks><primitives></primitives><stage name="Bühne" width="480" height="360" costume="0" color="255,255,255,1" tempo="60" threadsafe="false" penlog="false" volume="100" pan="0" lines="round" ternary="false" hyperops="true" codify="false" inheritance="true" sublistIDs="false" id="6"><costumes><list struct="atomic" id="7"></list></costumes><sounds><list struct="atomic" id="8"></list></sounds><variables></variables><blocks></blocks><scripts></scripts><sprites select="1"><sprite name="Objekt" idx="1" x="0" y="0" heading="90" scale="0.1" volume="100" pan="0" rotation="1" draggable="true" hidden="true" costume="0" color="0,0,0,1" pen="tip" id="13"><costumes><list struct="atomic" id="14"></list></costumes><sounds><list struct="atomic" id="15"></list></sounds><blocks></blocks><variables></variables><scripts><script x="70" y="80"><block s="receiveGo"></block><block s="forward"><l>100</l></block></script></scripts></sprite></sprites></stage><variables></variables></scene></scenes>"""

    val project = TurtleStitchXmlLoader.load(xml)

    assertEquals(project.name, "simple_forward")
    assertEquals(project.scenes.length, 1)
    assertEquals(project.scenes.head.name, "simple_forward")
    assertEquals(project.scenes.head.stage.name, "Bühne")

    val sprite = project.scenes.head.stage.sprites.head
    assertEquals(sprite.name, "Objekt")
    assertEquals(sprite.scripts.length, 1)
    assertEquals(sprite.scripts.head.blocks.length, 2)
  }
}
