package interactionPlugins.fileSubmission.turtleLogic

object TurtleXmlTestSamples {

  val ifDemoXml: String =
    """<project name="if-demo"><scenes select="1"><scene name="s"><stage><sprites select="1"><sprite name="Sprite"><scripts><script><block s="receiveGo"></block><block s="doIf"><block s="reportTrue"></block><script><block s="forward"><l>12</l></block><block s="turn"><l>30</l></block></script></block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""

  val whileDemoXml: String =
    """<project name="while-demo"><scenes select="1"><scene name="s"><stage><sprites select="1"><sprite name="Sprite"><scripts><script><block s="receiveGo"></block><block s="doUntil"><block s="reportTouchingObject"><l><option>mouse-pointer</option></l></block><script><block s="forward"><l>5</l></block><block s="turnLeft"><l>10</l></block></script></block></script></scripts></sprite></sprites></stage></scene></scenes></project>"""

  val asdfProjectXml: String =
    """<project name="asdf" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2">
      |<notes/>
      |<scenes select="1">
      |<scene name="asdf">
      |<notes/>
      |<hidden/>
      |<headers/>
      |<code/>
      |<blocks/>
      |<primitives/>
      |<stage name="Stage" width="480" height="360" costume="0" color="255,255,255,1" tempo="60" threadsafe="false" penlog="false" volume="100" pan="0" lines="round" ternary="false" hyperops="true" codify="false" inheritance="true" sublistIDs="false" id="6">
      |<costumes>
      |<list struct="atomic" id="7"/>
      |</costumes>
      |<sounds>
      |<list struct="atomic" id="8"/>
      |</sounds>
      |<variables/>
      |<blocks/>
      |<scripts/>
      |<sprites select="1">
      |<sprite name="Sprite" idx="1" x="50" y="0" heading="90" scale="0.1" volume="100" pan="0" rotation="1" draggable="true" hidden="true" costume="0" color="0,0,0,1" pen="tip" id="13">
      |<costumes>
      |<list struct="atomic" id="14"/>
      |</costumes>
      |<sounds>
      |<list struct="atomic" id="15"/>
      |</sounds>
      |<blocks/>
      |<variables/>
      |<scripts>
      |<script x="90" y="91">
      |<block s="receiveGo"/>
      |<block s="gotoXY">
      |<l>0</l>
      |<l>0</l>
      |</block>
      |<block s="setHeading">
      |<l>90</l>
      |</block>
      |<block s="clear"/>
      |<block s="down"/>
      |<block s="doRepeat">
      |<l>36</l>
      |<script>
      |<block s="forward">
      |<l>20</l>
      |</block>
      |<block s="turn">
      |<l>10</l>
      |</block>
      |</script>
      |</block>
      |<block s="doRepeat">
      |<l>18</l>
      |<script>
      |<block s="gotoXY">
      |<l>0</l>
      |<l>0</l>
      |</block>
      |<block s="turn">
      |<l>20</l>
      |</block>
      |<block s="forward">
      |<l>50</l>
      |</block>
      |</script>
      |</block>
      |</script>
      |</scripts>
      |</sprite>
      |</sprites>
      |</stage>
      |<variables/>
      |</scene>
      |</scenes>
      |<creator>anonymous</creator>
      |<origCreator/>
      |<origName/>
      |</project>""".stripMargin

  val moreProjectXml: String =
    """<project name="more" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes></notes><scenes select="1"><scene name="more"><notes></notes><hidden></hidden><headers></headers><code></code><blocks></blocks><primitives></primitives><stage name="Stage" width="480" height="360" costume="0" color="255,255,255,1" tempo="60" threadsafe="false" penlog="false" volume="100" pan="0" lines="round" ternary="false" hyperops="true" codify="false" inheritance="true" sublistIDs="false" id="6"><costumes><list struct="atomic" id="7"></list></costumes><sounds><list struct="atomic" id="8"></list></sounds><variables></variables><blocks></blocks><scripts></scripts><sprites select="1"><sprite name="Sprite" idx="1" x="196.58510357835848" y="25.88087003221233" heading="90" scale="0.1" volume="100" pan="0" rotation="1" draggable="true" hidden="true" costume="0" color="0,0,0,1" pen="tip" id="13"><costumes><list struct="atomic" id="14"></list></costumes><sounds><list struct="atomic" id="15"></list></sounds><blocks></blocks><variables></variables><scripts><script x="69" y="56"><block s="receiveGo"></block><block s="gotoXY"><l>0</l><l>0</l></block><block s="setHeading"><l>90</l></block><block s="clear"></block><block s="down"></block><block s="tatamiStitch"><l>5</l><l>40</l><l><bool>true</bool></l></block><block s="doSetVar"><l>a</l><l>100</l></block><block s="turnLeft"><l>15</l></block><block s="forward"><l>100</l></block><block s="turn"><l>15</l></block><block s="doIf"><block s="reportVariadicGreaterThan"><list><block s="reportDifference"><block var="a"/><l>30</l></block><block s="reportPower"><l>3</l><l></l></block></list></block><script><block s="doRepeat"><l>10</l><script><block s="forward"><l>10</l></block></script></block></script><list></list></block></script></scripts></sprite><watcher var="a" style="normal" x="10" y="10" color="243,118,29"/></sprites></stage><variables><variable name="a"><l>100</l></variable></variables></scene></scenes>
      |<creator>anonymous</creator>
      |<origCreator></origCreator>
      |<origName></origName>
      |</project>""".stripMargin
}
