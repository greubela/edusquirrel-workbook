package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.user.User
import it.evadid.homepage.control.model.*
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.elements.structureElements.{Workbook, WorkbookSection}

/** Digital reconstruction of Alina Verworn's 2025 Visual Novel workbook.
  *
  * The section and task order follows the print workbook. Open response fields
  * replace its ruled answer areas, while practical Scratch tasks retain their
  * original file names and links to the relevant source pages/figures.
  */
case class CreateVisualNovelWorkbook(override val fullInfo: FullInfo) extends WorkbookFactory {

  override val availableLanguages: List[HumanLanguage] = List(German, English)
  override val workbookId: String = "VisualNovelWorkbook"

  private def t(key: String): String = s"VisualNovelWorkbook/$key"
  private def answer: WorkbookElement = createTextInput()
  private def task(number: Int, elements: WorkbookElement*): WorkbookElement =
    container(t(s"task${number}Title"), elements.toList)
  private def prompt(number: Int): WorkbookElement = instructionHtml(t(s"task$number"))
  private def source(page: Int): WorkbookElement = instructionHtml(t(s"sourcePage$page"))

  override lazy val createWorkbook: Workbook = workbook(
    t("workbookTitle"),
    List(introduction, scratchBasics, firstScenes, variablesAndLists, dataDrivenNovel,
      reflection, twoCharacters, appendix),
    User.AlinaVerworn
  )

  private lazy val introduction: WorkbookSection = section("visual-novel", t("section0Title"), List(
    container(t("welcomeTitle"), List(instructionHtml(t("welcome")))),
    task(1, prompt(1), answer)
  ))

  private lazy val scratchBasics: WorkbookSection = section("scratch-basics", t("section1Title"), List(
    task(2, prompt(2), checklist(t("task2Done"))),
    task(3, prompt(3), checklist(t("task3Done")), source(3)),
    task(4, prompt(4), answer),
    task(5, prompt(5), answer),
    task(6, prompt(6), answer)
  ))

  private lazy val firstScenes: WorkbookSection = section("first-scenes", t("section2Title"), List(
    task(7, prompt(7), checklist(t("task7Done"))),
    task(8, prompt(8), checklist(t("task8Done"))),
    task(9, prompt(9), answer)
  ))

  private lazy val variablesAndLists: WorkbookSection = section("variables-lists", t("section3Title"), List(
    container(t("variablesIntroTitle"), List(instructionHtml(t("variablesIntro")))),
    task(10, prompt(10), answer), task(11, prompt(11), answer, source(6)),
    task(12, prompt(12), answer), task(13, prompt(13), answer), task(14, prompt(14), answer),
    task(15, prompt(15), answer, source(7)), task(16, prompt(16), checklist(t("task16Done"))),
    container(t("listsIntroTitle"), List(instructionHtml(t("listsIntro")))),
    task(17, prompt(17), answer), task(18, prompt(18), answer), task(19, prompt(19), answer),
    task(20, prompt(20), answer, source(9)), task(21, prompt(21), answer)
  ))

  private lazy val dataDrivenNovel: WorkbookSection = section("data-driven-novel", t("section4Title"), List(
    container(t("chapter2GoalTitle"), List(instructionHtml(t("chapter2Goal")))),
    task(22, prompt(22), checklist(t("task22Done"))), task(23, prompt(23), answer, source(10)),
    task(24, prompt(24), answer), task(25, prompt(25), answer), task(26, prompt(26), answer),
    task(27, prompt(27), checklist(t("task27Done"))),
    container(t("chapter3GoalTitle"), List(instructionHtml(t("chapter3Goal")))),
    task(28, prompt(28), checklist(t("task28Done"))), task(29, prompt(29), checklist(t("task29Done"))),
    task(30, prompt(30), answer), task(31, prompt(31), answer)
  ))

  private lazy val reflection: WorkbookSection = section("reflection", t("section5Title"), List(
    task(32, prompt(32), answer), task(33, prompt(33), answer),
    task(34, prompt(34), answer), task(35, prompt(35), checklist(t("task35Done")))
  ))

  private lazy val twoCharacters: WorkbookSection = section("two-characters", t("section6Title"), List(
    container(t("chapter4GoalTitle"), List(instructionHtml(t("chapter4Goal")))),
    task(36, prompt(36), checklist(t("task36Done")), source(15)), task(37, prompt(37), answer),
    task(38, prompt(38), checklist(t("task38Done"))), task(39, prompt(39), checklist(t("task39Done"))),
    task(40, prompt(40), checklist(t("task40Done"))), task(41, prompt(41), checklist(t("task41Done")))
  ))

  private lazy val appendix: WorkbookSection = section("appendix", t("section7Title"), List(
    container(t("appendixLanguageTitle"), List(instructionHtml(t("appendixLanguage")), source(17))),
    container(t("appendixCsvTitle"), List(instructionHtml(t("appendixCsv")), source(18))),
    container(t("appendixHintsTitle"), List(instructionHtml(t("appendixHints")), source(19)))
  ))
}
