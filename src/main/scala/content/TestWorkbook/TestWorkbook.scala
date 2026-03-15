package content.TestWorkbook

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import contentmanagement.model.file.*
import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}
import contentmanagement.webElements.HtmlAppElement
import interactionPlugins.blockEnvironment.exercise.{ProgrammingExerciseFactory, TurtleProgrammingInteraction}
import interactionPlugins.gpt.GptExerciseFactory
import interactionPlugins.visualNovel.VisualNovelPanel
import workbook.model.*
import workbook.model.info.{WorkbookConfig, WorkbookInfo}
import workbook.user.User
import workbook.workbookHtmlElements.abstractions.WorkbookInteraction
import workbook.workbookHtmlElements.container.*

class TestWorkbook(fullscreenElement: HtmlFullScreenElement) extends HtmlAppElement {


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

  private def blockProgCont(workbookInfoVar: Var[WorkbookInfo]): HtmlExerciseContainer = {
    val progElements = ProgrammingExerciseFactory.createTurtleProgrammingExercise(workbookInfoVar, "prog-007", defaultTitle, ProgrammingExerciseFactory.DefaultPentagonExpectedResult)
    HtmlExerciseContainer(workbookInfoVar, progElements)
  }

  private def visualNovelCont(workbookInfoVar: Var[WorkbookInfo]): HtmlExerciseContainer = {
    ??? // todo
  }
  
 
  def createTestSection(workbookInfoVar: Var[WorkbookInfo]): WorkbookSection = {
    val contList = List(
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

  def createTestWorkbook(fullscreenElement: HtmlFullScreenElement): Workbook = {
    val defaultInfo = WorkbookInfo(List[HumanLanguage](AppLanguage.English, AppLanguage.German, AppLanguage.Ukrainian, AppLanguage.Danish, AppLanguage.Turkish),fullscreenElement, WorkbookConfig(AppLanguage.German, None, User("TestUser", "dummy@test.de")), Map())
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


}
