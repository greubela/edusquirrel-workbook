package content.TestWorkbook

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import content.WorkbookFactory
import contentmanagement.webElements.HtmlAppElement
import datastructures.core.language.{AppLanguage, HumanLanguage, LanguageMap}
import interactionPlugins.blockEnvironment.exercise.{ProgrammingExerciseFactory, TurtleProgrammingInteraction}
import interactionPlugins.gpt.GptExerciseFactory
import interactionPlugins.programmingExercise.pythonExercise.turtle.TurtleExerciseDemo
import interactionPlugins.turtleStitchPlugin.TurtleStitchEditor
import interactionPlugins.visualNovel.VisualNovelPanel
import workbook.htmlElements.basic.{HtmlButtonElement, HtmlUnsafeHtmlInstructionElement}
import workbook.model.*
import workbook.model.abstractions.WorkbookInteraction
import workbook.model.info.{AllWorkbookInfo, WorkbookConfig, WorkbookInfo}
import workbook.user.User
import workbook.htmlElements.container.*

case class TestWorkbookFactory(override val workbookInfo: AllWorkbookInfo) extends WorkbookFactory {


  override def createWorkbook: Workbook = {
    
    val sec = TestWorkbookFactory.createTestSection(workbookInfo)

    val title: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
      AppLanguage.English -> "Test Workbook",
      AppLanguage.German -> "Beispielheft",
      AppLanguage.French -> "Cahier de test",
      AppLanguage.Ukrainian -> "Тестовий зошит",
      AppLanguage.Russian -> "Тестовая тетрадь",
      AppLanguage.Turkish -> "Test Çalışma Kitabı",
      AppLanguage.Danish -> "Testarbejdsbog"
    ))

    Workbook(workbookInfo, title, List(sec))
  }
  
  
}

object TestWorkbookFactory {

  private val defaultTitle: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(
    AppLanguage.English -> "this is the title.",
    AppLanguage.German -> "Das ist der Titel.",
    AppLanguage.French -> "Ceci est le titre.",
    AppLanguage.Ukrainian -> "Це заголовок.",
    AppLanguage.Russian -> "Это заголовок.",
    AppLanguage.Turkish -> "Bu başlıktır.",
    AppLanguage.Danish -> "Dette er titlen."
  ))

  private val textInstruction1: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(
    AppLanguage.English -> "Write a text!",
    AppLanguage.German -> "Schreibe einen Text!",
    AppLanguage.French -> "Écris un texte !",
    AppLanguage.Ukrainian -> "Напиши текст!",
    AppLanguage.Russian -> "Напиши текст!",
    AppLanguage.Turkish -> "Bir metin yaz!",
    AppLanguage.Danish -> "Skriv en tekst!"
  ))

  private val textInstruction2: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(
    AppLanguage.English -> "Write another text!",
    AppLanguage.German -> "Schreibe noch einen Text!",
    AppLanguage.French -> "Écris encore un texte !",
    AppLanguage.Ukrainian -> "Напиши ще один текст!",
    AppLanguage.Russian -> "Напиши ещё один текст!",
    AppLanguage.Turkish -> "Bir metin daha yaz!",
    AppLanguage.Danish -> "Skriv endnu en tekst!"
  ))

  private def gptCont(workbookInfo: AllWorkbookInfo): HtmlExerciseContainer = {
    val gptElements = GptExerciseFactory.createGptExercise(
      workbookInfo,
      "text-007",
      defaultTitle,
      List(textInstruction1, textInstruction2)
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
    val progElements = ProgrammingExerciseFactory.createTurtleProgrammingExercise(workbookInfo, "prog-007", defaultTitle, ProgrammingExerciseFactory.DefaultPentagonExpectedResult)
    HtmlExerciseContainer(workbookInfo, progElements)
  }

  private def visualNovelCont(workbookInfo: AllWorkbookInfo): HtmlExerciseContainer = {
    ??? // todo
  }


  def createTestSection(workbookInfo: AllWorkbookInfo): WorkbookSection = {

    val contList: List[HtmlExerciseContainer] = List(
      pythonTurtleDemo(workbookInfo),
    //  turtleEditorDemo(workbookInfoVar),
      gptCont(workbookInfo),
      blockProgCont(workbookInfo)
    )


    val secTitle = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(
      AppLanguage.English -> "This is section 1.",
      AppLanguage.German -> "Das ist Abschnitt 1.",
      AppLanguage.French -> "Ceci est la section 1.",
      AppLanguage.Ukrainian -> "Це розділ 1.",
      AppLanguage.Russian -> "Это раздел 1.",
      AppLanguage.Turkish -> "Bu bölüm 1.",
      AppLanguage.Danish -> "Dette er afsnit 1."
    ))
    WorkbookSection(workbookInfo, secTitle, contList)
  }



  private val simple_turtle_xml: String = """<project name="simple_forward" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes></notes><scenes select="1"><scene name="simple_forward"><notes></notes><hidden></hidden><headers></headers><code></code><blocks></blocks><primitives></primitives><stage name="Bühne" width="480" height="360" costume="0" color="255,255,255,1" tempo="60" threadsafe="false" penlog="false" volume="100" pan="0" lines="round" ternary="false" hyperops="true" codify="false" inheritance="true" sublistIDs="false" id="6"><costumes><list struct="atomic" id="7"></list></costumes><sounds><list struct="atomic" id="8"></list></sounds><variables></variables><blocks></blocks><scripts></scripts><sprites select="1"><sprite name="Objekt" idx="1" x="0" y="0" heading="90" scale="0.1" volume="100" pan="0" rotation="1" draggable="true" hidden="true" costume="0" color="0,0,0,1" pen="tip" id="13"><costumes><list struct="atomic" id="14"></list></costumes><sounds><list struct="atomic" id="15"></list></sounds><blocks></blocks><variables></variables><scripts><script x="70" y="80"><block s="receiveGo"></block><block s="forward"><l>100</l></block></script></scripts></sprite></sprites></stage><variables></variables></scene></scenes>
                                            |<creator>anonymous</creator>
                                            |<origCreator>anonymous</origCreator>
                                            |<origName></origName>
                                            |</project>""".stripMargin

}
