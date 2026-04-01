package content

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import content.WorkbookFactory
import contentmanagement.webElements.HtmlAppElement
import interactionPlugins.blockEnvironment.exercise.{ProgrammingExerciseFactory, TurtleProgrammingInteraction}
import interactionPlugins.gpt.GptExerciseFactory
import interactionPlugins.programmingExercise.pythonExercise.turtle.TurtleExerciseDemo
import interactionPlugins.turtleStitchPlugin.TurtleStitchEditor
import interactionPlugins.visualNovel.VisualNovelPanel
import workbook.htmlElements.basic.{HtmlButtonElement, HtmlUnsafeHtmlInstructionElement}
import workbook.htmlElements.container.*
import workbook.model.*
import workbook.model.abstractions.WorkbookInteraction
import workbook.model.info.{AllWorkbookInfo, WorkbookConfig, WorkbookInfo}
import workbook.user.User

case class TestWorkbookFactory(override val workbookInfo: AllWorkbookInfo) extends WorkbookFactory {


  override def createWorkbook: Workbook = {
    
    val sec = TestWorkbookFactory.createTestSection(workbookInfo)

    Workbook(workbookInfo, "TestWorkbook/workbookTitle", List(sec))
  }
  
  
}

object TestWorkbookFactory {

  private def gptCont(workbookInfo: AllWorkbookInfo): HtmlExerciseContainer = {
    val gptElements = GptExerciseFactory.createGptExercise(
      workbookInfo,
      "text-007",
      "TestWorkbook/exerciseTitle",
      List("TestWorkbook/writeText1", "TestWorkbook/writeText2")
    )
    HtmlExerciseContainer(workbookInfo, gptElements)
  }

  private def pythonTurtleDemo(workbookInfo: AllWorkbookInfo): HtmlExerciseContainer = {
    val turtleDemo = TurtleExerciseDemo()

    HtmlExerciseContainer(workbookInfo, List(
      HtmlUnsafeHtmlInstructionElement(workbookInfo, "EmbroideryWorkbook/Ex1Instr2"),
      HtmlButtonElement(workbookInfo, "turtle-demo-button", event => workbookInfo.technicalElements.fullScreenContainer.setElementFullscreen(turtleDemo.getDomElement()))
    ))
  }

  private def turtleEditorDemo(workbookInfo: AllWorkbookInfo): HtmlExerciseContainer = {
    val turtleVar = Var(simple_turtle_xml)
    turtleVar.signal.foreach(newVal => println("Turtle XML changed: " + newVal.size + "/" + newVal.take(60)))(unsafeWindowOwner)
    val turtleDemo = new TurtleStitchEditor(turtleVar)

    HtmlExerciseContainer(workbookInfo, List(
      HtmlUnsafeHtmlInstructionElement(workbookInfo, "EmbroideryWorkbook/Ex1Instr3"),
      HtmlButtonElement(workbookInfo, "turtle-demo-button", event => workbookInfo.technicalElements.fullScreenContainer.setElementFullscreen(turtleDemo.getDomElement()))
    ))
  }

  private def blockProgCont(workbookInfo: AllWorkbookInfo): HtmlExerciseContainer = {
    val progElements = ProgrammingExerciseFactory.createTurtleProgrammingExercise(workbookInfo, "prog-007", "TestWorkbook/exerciseTitle", ProgrammingExerciseFactory.DefaultPentagonExpectedResult)
    HtmlExerciseContainer(workbookInfo, progElements)
  }

  private def visualNovelCont(workbookInfo: AllWorkbookInfo): HtmlExerciseContainer = {
    ??? // todo
  }


  def createTestSection(workbookInfo: AllWorkbookInfo): WorkbookSection = {

    val contList: List[HtmlExerciseContainer] = List(
      pythonTurtleDemo(workbookInfo),
      turtleEditorDemo(workbookInfo),
      gptCont(workbookInfo),
      blockProgCont(workbookInfo)
    )


    WorkbookSection(workbookInfo, "TestWorkbook/section1Title", contList)
  }



  private val simple_turtle_xml: String = """<project name="simple_forward" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes></notes><scenes select="1"><scene name="simple_forward"><notes></notes><hidden></hidden><headers></headers><code></code><blocks></blocks><primitives></primitives><stage name="Bühne" width="480" height="360" costume="0" color="255,255,255,1" tempo="60" threadsafe="false" penlog="false" volume="100" pan="0" lines="round" ternary="false" hyperops="true" codify="false" inheritance="true" sublistIDs="false" id="6"><costumes><list struct="atomic" id="7"></list></costumes><sounds><list struct="atomic" id="8"></list></sounds><variables></variables><blocks></blocks><scripts></scripts><sprites select="1"><sprite name="Objekt" idx="1" x="0" y="0" heading="90" scale="0.1" volume="100" pan="0" rotation="1" draggable="true" hidden="true" costume="0" color="0,0,0,1" pen="tip" id="13"><costumes><list struct="atomic" id="14"></list></costumes><sounds><list struct="atomic" id="15"></list></sounds><blocks></blocks><variables></variables><scripts><script x="70" y="80"><block s="receiveGo"></block><block s="forward"><l>100</l></block></script></scripts></sprite></sprites></stage><variables></variables></scene></scenes>
                                            |<creator>anonymous</creator>
                                            |<origCreator>anonymous</origCreator>
                                            |<origName></origName>
                                            |</project>""".stripMargin

}
