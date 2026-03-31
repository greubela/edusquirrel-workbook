package content.TestWorkbook

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import contentmanagement.model.file.*
import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}
import contentmanagement.webElements.HtmlAppElement
import interactionPlugins.blockEnvironment.exercise.{ProgrammingExerciseFactory, TurtleProgrammingInteraction}
import interactionPlugins.gpt.GptExerciseFactory
import interactionPlugins.programmingExercise.pythonExercise.turtle.TurtleExerciseDemo
import interactionPlugins.turtleStitchPlugin.TurtleStitchEditor
import interactionPlugins.visualNovel.VisualNovelPanel
import workbook.htmlElements.basic.{HtmlButtonElement, HtmlUnsafeHtmlInstructionElement}
import workbook.model.*
import workbook.model.abstractions.WorkbookInteraction
import workbook.model.info.{WorkbookConfig, WorkbookInfo}
import workbook.user.User
import workbook.htmlElements.container.*

class TestWorkbook(fullscreenElement: HtmlFullScreenContainerElement) extends HtmlAppElement {


  private val domElement: Element = div()

  override def getDomElement(): L.Element = domElement
}

object TestWorkbook {

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

  private def gptCont(workbookInfoVar: Var[WorkbookInfo]): HtmlExerciseContainer = {
    val gptElements = GptExerciseFactory.createGptExercise(
      workbookInfoVar,
      "text-007",
      defaultTitle,
      List(textInstruction1, textInstruction2)
    )
    HtmlExerciseContainer(workbookInfoVar, gptElements)
  }

  private def pythonTurtleDemo(workbookInfoVar: Var[WorkbookInfo]): HtmlExerciseContainer = {
    val turtleDemo = TurtleExerciseDemo()

    HtmlExerciseContainer(workbookInfoVar, List(
      HtmlUnsafeHtmlInstructionElement(workbookInfoVar, "EmbroideryWorkbook/Ex1Instr2"),
      HtmlButtonElement(workbookInfoVar, "turtle-demo-button", event => workbookInfoVar.now().fullscreenElement.setElementFullscreen(turtleDemo.getDomElement()))
    ))
  }

  private def turtleEditorDemo(workbookInfoVar: Var[WorkbookInfo]): HtmlExerciseContainer = {
    val turtleVar = Var(simple_turtle_xml)
    turtleVar.signal.foreach(newVal => println("Turtle XML changed: " + newVal.size + "/" + newVal.take(60)))(unsafeWindowOwner)
    val turtleDemo = TurtleStitchEditor(turtleVar)

    HtmlExerciseContainer(workbookInfoVar, List(
      HtmlUnsafeHtmlInstructionElement(workbookInfoVar, "EmbroideryWorkbook/Ex1Instr3"),
      HtmlButtonElement(workbookInfoVar, "turtle-demo-button", event => workbookInfoVar.now().fullscreenElement.setElementFullscreen(turtleDemo.getDomElement()))
    ))
  }

  private def blockProgCont(workbookInfoVar: Var[WorkbookInfo]): HtmlExerciseContainer = {
    val progElements = ProgrammingExerciseFactory.createTurtleProgrammingExercise(workbookInfoVar, "prog-007", defaultTitle, ProgrammingExerciseFactory.DefaultPentagonExpectedResult)
    HtmlExerciseContainer(workbookInfoVar, progElements)
  }

  private def visualNovelCont(workbookInfoVar: Var[WorkbookInfo]): HtmlExerciseContainer = {
    ??? // todo
  }


  def createTestSection(workbookInfoVar: Var[WorkbookInfo]): WorkbookSection = {

    val contList: List[HtmlExerciseContainer] = List(
      pythonTurtleDemo(workbookInfoVar),
    //  turtleEditorDemo(workbookInfoVar),
      gptCont(workbookInfoVar),
      blockProgCont(workbookInfoVar)
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
    WorkbookSection(workbookInfoVar, secTitle, contList)
  }

  def createTestWorkbook(fullscreenElement: HtmlFullScreenContainerElement): Workbook = {
    val defaultInfo = WorkbookInfo(List[HumanLanguage](AppLanguage.English, AppLanguage.German, AppLanguage.Ukrainian, AppLanguage.Danish, AppLanguage.Turkish), fullscreenElement, WorkbookConfig(AppLanguage.German, None, User("TestUser", "dummy@test.de")), Map())
    val workbookInfoVar = Var(defaultInfo)

    val sec = createTestSection(workbookInfoVar)

    val title: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
      AppLanguage.English -> "Test Workbook",
      AppLanguage.German -> "Beispielheft",
      AppLanguage.French -> "Cahier de test",
      AppLanguage.Ukrainian -> "Тестовий зошит",
      AppLanguage.Russian -> "Тестовая тетрадь",
      AppLanguage.Turkish -> "Test Çalışma Kitabı",
      AppLanguage.Danish -> "Testarbejdsbog"
    ))

    Workbook(workbookInfoVar, title, List(sec))
  }


  private val simple_turtle_xml: String = """<project name="simple_forward" app="TurtleStitch 2.11, http://www.turtlestitch.org" version="2"><notes></notes><scenes select="1"><scene name="simple_forward"><notes></notes><hidden></hidden><headers></headers><code></code><blocks></blocks><primitives></primitives><stage name="Bühne" width="480" height="360" costume="0" color="255,255,255,1" tempo="60" threadsafe="false" penlog="false" volume="100" pan="0" lines="round" ternary="false" hyperops="true" codify="false" inheritance="true" sublistIDs="false" id="6"><costumes><list struct="atomic" id="7"></list></costumes><sounds><list struct="atomic" id="8"></list></sounds><variables></variables><blocks></blocks><scripts></scripts><sprites select="1"><sprite name="Objekt" idx="1" x="0" y="0" heading="90" scale="0.1" volume="100" pan="0" rotation="1" draggable="true" hidden="true" costume="0" color="0,0,0,1" pen="tip" id="13"><costumes><list struct="atomic" id="14"></list></costumes><sounds><list struct="atomic" id="15"></list></sounds><blocks></blocks><variables></variables><scripts><script x="70" y="80"><block s="receiveGo"></block><block s="forward"><l>100</l></block></script></scripts></sprite></sprites></stage><variables></variables></scene></scenes>
                                            |<creator>anonymous</creator>
                                            |<origCreator>anonymous</origCreator>
                                            |<origName></origName>
                                            |</project>""".stripMargin

}
