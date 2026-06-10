package it.evadid.homepage.workbook.content


/*
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import content.WorkbookFactory
import todomove.webElementsOld.webElements.HtmlAppElement
import it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.exercise.{ProgrammingExerciseFactory, TurtleProgrammingInteraction}
import it.evadid.homepage.workbook.legacy.interactionPlugins.gpt.*
import it.evadid.homepage.workbook.legacy.interactionPlugins.programmingExercise.pythonExercise.turtle.TurtleExerciseDemo
import it.evadid.homepage.workbook.legacy.interactionPlugins.turtleStitchPlugin.TurtleStitchEditor
import it.evadid.workbook.model.interaction.WorkbookInteraction
import workbook.htmlElements.basic.*
import workbook.htmlElements.container.*
import it.evadid.homepage.workbook.legacy.model.*
import it.evadid.homepage.workbook.legacy.model.info.{FullInfo, HomepageInfo, WorkbookConfig}
import workbook.user.User

case class TestWorkbookFactory(override val fullInfo: FullInfo) extends WorkbookFactory {



  private def blockProgCont(workbookInfo: FullInfo): HtmlExerciseContainer = {
    val progElements = ProgrammingExerciseFactory.createTurtleProgrammingExercise(workbookInfo, "prog-007", "TestWorkbook/exerciseTitle", ProgrammingExerciseFactory.DefaultPentagonExpectedResult)
    HtmlExerciseContainer(workbookInfo, progElements)
  }

  private def visualNovelCont(workbookInfo: FullInfo): HtmlExerciseContainer = {
    ??? // todo
  }


  def createTestSection(workbookInfo: FullInfo): HtmlWorkbookSection = {

    val contList: List[HtmlExerciseContainer] = List(
      pythonTurtleDemo(workbookInfo),
      turtleEditorDemo(workbookInfo),
      //gptCont(workbookInfo),
      blockProgCont(workbookInfo)
    )


    HtmlWorkbookSection(workbookInfo, "TestWorkbook/section1Title", contList)
  }



  private val simple_turtle_xml: String = """<project name="simple_forward" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes></notes><scenes select="1"><scene name="simple_forward"><notes></notes><hidden></hidden><headers></headers><code></code><blocks></blocks><primitives></primitives><stage name="Bühne" width="480" height="360" costume="0" color="255,255,255,1" tempo="60" threadsafe="false" penlog="false" volume="100" pan="0" lines="round" ternary="false" hyperops="true" codify="false" inheritance="true" sublistIDs="false" id="6"><costumes><list struct="atomic" id="7"></list></costumes><sounds><list struct="atomic" id="8"></list></sounds><variables></variables><blocks></blocks><scripts></scripts><sprites select="1"><sprite name="Objekt" idx="1" x="0" y="0" heading="90" scale="0.1" volume="100" pan="0" rotation="1" draggable="true" hidden="true" costume="0" color="0,0,0,1" pen="tip" id="13"><costumes><list struct="atomic" id="14"></list></costumes><sounds><list struct="atomic" id="15"></list></sounds><blocks></blocks><variables></variables><scripts><script x="70" y="80"><block s="receiveGo"></block><block s="down"></block><block s="forward"><l>100</l></block></script></scripts></sprite></sprites></stage><variables></variables></scene></scenes>
                                            |<creator>anonymous</creator>
                                            |<origCreator>anonymous</origCreator>
                                            |<origName></origName>
                                            |</project>""".stripMargin

  /*private def gptCont(workbookInfo: FullInfo): HtmlExerciseContainer = {
    val gptElements = GptExerciseFactory.createGptExercise(
      workbookInfo,
      "text-007",
      "TestWorkbook/exerciseTitle",
      List("TestWorkbook/writeText1", "TestWorkbook/writeText2")
    )
    HtmlExerciseContainer(workbookInfo, gptElements)
  }*/


  private def pythonTurtleDemo(fullInfo: FullInfo): HtmlExerciseContainer = {
    val turtleDemo = TurtleExerciseDemo()

    HtmlExerciseContainer(fullInfo, List(
      instructionPlaintext("EmbroideryWorkbook/Ex1Instr2"),
      HtmlButtonElement.withTextLabel(fullInfo, "basic/demoButton", event => fullInfo.technical.makeFullscreen(turtleDemo))
    ))
  }


  private def turtleEditorDemo(fullInfo: FullInfo): HtmlExerciseContainer = {
    val turtleVar = Var(simple_turtle_xml)
    //turtleVar.signal.foreach(newVal => println("Turtle XML changed: " + newVal.size + "/" + newVal.take(60)))(unsafeWindowOwner)
    val turtleDemo = new TurtleStitchEditor(turtleVar)

    HtmlExerciseContainer(fullInfo, List(
      instructionPlaintext( "EmbroideryWorkbook/Ex1Instr3"),
      HtmlButtonElement.withTextLabel(fullInfo, "basic/demoButton", event => fullInfo.technical.makeFullscreen(turtleDemo))
    ))
  }
  
  override def createWorkbook: HtmlWorkbook = {
    val sec = createTestSection(fullInfo)
    HtmlWorkbook(fullInfo, "TestWorkbook/workbookTitle", List(sec))
  }
  
  
  
}
*/
