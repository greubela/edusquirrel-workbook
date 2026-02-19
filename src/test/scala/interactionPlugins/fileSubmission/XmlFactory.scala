package interactionPlugins.fileSubmission

object XmlFactory {

  private def project(name: String, spriteAttrs: String, scriptBody: String, pentrails: Option[String] = None): String = {
    val pentrailsXml = pentrails.map(value => s"<pentrails>$value</pentrails>").getOrElse("")

    s"""<project name="$name" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2">
       |  <scenes select="1">
       |    <scene name="$name">
       |      <stage name="Stage" width="480" height="360" id="6">
       |        $pentrailsXml
       |        <sprites select="1">
       |          <sprite name="Sprite" idx="1" $spriteAttrs>
       |            <scripts>
       |              <script x="156" y="66">$scriptBody</script>
       |            </scripts>
       |          </sprite>
       |        </sprites>
       |      </stage>
       |    </scene>
       |  </scenes>
       |</project>""".stripMargin
  }

  val test1: String = project(
    name = "example-with-pentrails",
    spriteAttrs = """x="0" y="0" heading="90" scale="0.1" volume="100" pan="0" rotation="1""",
    scriptBody =
      """<block s="receiveGo"></block>
        |<block s="forward"><l>100</l></block>""".stripMargin,
    pentrails = Some("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO8B5hoAAAAASUVORK5CYII=")
  )

  val test2: String = project(
    name = "example-repeat",
    spriteAttrs = """x="0" y="0" heading="90" scale="0.1" volume="100" pan="0" rotation="1""",
    scriptBody =
      """<block s="receiveGo"></block>
        |<block s="doRepeat"><l>10</l><script><block s="forward"><l>10</l></block><block s="turn"><l>15</l></block></script></block>""".stripMargin
  )

  val test3: String = project(
    name = "example-mixed",
    spriteAttrs = """x="380.38825611104767" y="1532.9940431923285" heading="30" scale="0.1" volume="100" pan="0" rotation="1""",
    scriptBody =
      """<block s="receiveGo"></block>
        |<block s="doRepeat"><l>2</l><script><block s="forward"><l>10</l></block><block s="arcRight"><l>50</l><l>30</l></block><block s="arcLeft"><l>50</l><l>90</l></block><block s="turn"><l>15</l></block><block s="changeYPosition"><l>10</l></block><block s="setHeading"><l>30</l></block></script></block>""".stripMargin
  )

  val test4: String = project(
    name = "example-self-closing",
    spriteAttrs = """x="10" y="15" heading="30" scale="0.1" volume="100" pan="0" rotation="1""",
    scriptBody =
      """<block s="receiveGo"/>
        |<block s="gotoXY"><l>0</l><l>0</l></block>
        |<block s="clear"></block>
        |<block s="forward"><l>20</l></block>""".stripMargin
  )

  val test5: String = project(
    name = "example-invalid-args",
    spriteAttrs = """x="foo" y="bar" heading="baz" scale="1" volume="100" pan="0" rotation="1""",
    scriptBody =
      """<block s="receiveGo"></block>
        |<block s="forward"><l>abc</l></block>""".stripMargin
  )

  val test6: String = """<project name="complex" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes></notes><costumes><list struct="atomic" id="7"></list></costumes><sounds><list struct="atomic" id="8"></list></sounds><variables></variables><blocks></blocks><scripts></scripts><sprites select="1"><sprite name="Sprite" idx="1" x="110" y="0" heading="105" scale="0.1" volume="100" pan="0" rotation="1" draggable="true" hidden="true" costume="0" color="0,0,0,1" pen="tip" id="13"><costumes><list struct="atomic" id="14"></list></costumes><sounds><list struct="atomic" id="15"></list></sounds><blocks></blocks><variables></variables><scripts><script x="187" y="150"><block s="receiveGo"></block><block s="doRepeat"><l>10</l><script><block s="forward"><l>10</l></block><block s="doIf"><block s="reportTouchingObject"><l><option>mouse-pointer</option></l></block><script><block s="forward"><l>10</l></block></script><list><l><bool>true</bool></l><script><block s="turn"><l>15</l></block></script></list></block><block s="forward"><l>10</l></block></script></block><block s="forward"><l>10</l></block></script></scripts></sprite></sprites></stage><variables></variables></scene></scenes>
                        |<creator>anonymous</creator>
                        |<origCreator></origCreator>
                        |<origName></origName>
                        |</project>""".stripMargin

  val all: List[String] = List(test1, test2, test3, test4, test5, test6)
}
