package interactionPlugins.fileSubmission.turtleStitch

import contentmanagement.model.vm.code.controlStructures.BeSequence
import contentmanagement.model.vm.code.defining.BeDefineFunction
import contentmanagement.model.vm.code.others.BeStartProgram
import contentmanagement.model.vm.code.usage.BeFunctionCall
import interactionPlugins.fileSubmission.TurtleFileSubmission
import munit.FunSuite

class TurtleStitchToBeExpressionParserSpec extends FunSuite {

  private val xmlWithRepeatNoPentrails =
    """<project name="test2" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes></notes><scenes select="1"><scene name="test2"><notes></notes><hidden></hidden><headers></headers><code></code><blocks></blocks><primitives></primitives><stage name="Stage" width="480" height="360" costume="0" color="255,255,255,1" tempo="60" threadsafe="false" penlog="false" volume="100" pan="0" lines="round" ternary="false" hyperops="true" codify="false" inheritance="true" sublistIDs="false" id="6"><costumes><list struct="atomic" id="7"></list></costumes><sounds><list struct="atomic" id="8"></list></sounds><variables></variables><blocks></blocks><scripts></scripts><sprites select="1"><sprite name="Sprite" idx="1" x="0" y="0" heading="90" scale="0.1" volume="100" pan="0" rotation="1" draggable="true" hidden="true" costume="0" color="0,0,0,1" pen="tip" id="13"><costumes><list struct="atomic" id="14"></list></costumes><sounds><list struct="atomic" id="15"></list></sounds><blocks></blocks><variables></variables><scripts><script x="156" y="66"><block s="receiveGo"></block><block s="doRepeat"><l>10</l><script><block s="forward"><l>10</l></block><block s="turn"><l>15</l></block></script></block></script></scripts></sprite></sprites></stage><variables></variables></scene></scenes><creator>anonymous</creator><origCreator></origCreator><origName></origName></project>"""

  test("parser builds two-phase output with definitions first and calls afterwards") {
    val expression = TurtleFileSubmission.parseToBeExpression(xmlWithRepeatNoPentrails)
    val start = expression.asInstanceOf[BeStartProgram]
    val body = start.startSequence.getOrElse(BeSequence.optionalBody(Nil)).body

    val firstCallIndex = body.indexWhere(_.isInstanceOf[BeFunctionCall])
    assert(firstCallIndex > 0)
    assert(body.take(firstCallIndex).forall(_.isInstanceOf[BeDefineFunction]))
    assert(body.drop(firstCallIndex).exists(_.isInstanceOf[BeFunctionCall]))
  }

  test("malformed XML is handled safely in BeExpression parser") {
    val malformedXml = "<project><scenes><scene><stage><sprites><sprite><scripts><script><block s=\"forward\"><l>10</l>"
    val expression = TurtleFileSubmission.parseToBeExpression(malformedXml)
    assert(expression.isInstanceOf[BeStartProgram])
  }
}
