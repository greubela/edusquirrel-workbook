package content

import it.evadid.core.datastructures.language.AppLanguage.*
import workbook.htmlElements.basic.*
import workbook.model.*
import workbook.model.info.FullInfo

case class CreateCompressionWorkbook(override val fullInfo: FullInfo) extends WorkbookFactory {

  override val availableLanguages: List[HumanLanguage] = List(German)

  private def t(key: String): String = s"IuBWorkbook/$key"

  override lazy val createWorkbook: Workbook = workbook(
    t("workbookTitle"),
    List(
      introSection,
      losslessSection,
      lossySection,
      filetypesSection,
      finalSection
    )
  )

  private lazy val introSection: WorkbookSection = section(
    t("section0Title"),
    List(
      container(List(
        instructionHtml(t("introScenario")),
      )),
      container(List(
        instructionHtml(t("introAnswerTask")),
        createTextInput(),
      )),
      container(List(
        instructionHtml(t("introSnowdenIntro")),
        instructionHtml(t("introArticle")),
        instructionHtml(t("introQuote")),
      )),
      container(List(
        instructionHtml(t("introTaskText")),
        instructionHtml(t("introRatesInfo")),
        instructionHtml(t("introWidgetPlaceholder")),
        instructionHtml(t("introReflectionTask")),
        instructionHtml(t("introReflectionHint")),
        createTextInput(),
      )),
    )
  )

  private lazy val losslessSection: WorkbookSection = section(
    t("section1Title"),
    List(
      container(List(
        instructionHtml(t("s1Task1Title")),
        instructionHtml(t("s1Task1Intro")),
        instructionHtml(t("s1Task1Widget")),
        instructionHtml(t("s1Task1A")),
        createTextInput(),
        instructionHtml(t("s1Task1B")),
        createTextInput(),
      )),
      container(List(
        instructionHtml(t("s1IntroP1")),
        instructionHtml(t("s1Transition")),
        instructionHtml(t("s1Task2Title")),
        instructionHtml(t("s1Task2Intro")),
        instructionHtml(t("s1Task2Widget")),
        instructionHtml(t("s1Task2A")),
        createTextInput(),
        instructionHtml(t("s1Task2B")),
        createTextInput(),
        instructionHtml(t("s1Task2C")),
        createTextInput(),
      )),
      container(List(
        instructionHtml(t("s1Task3Title")),
        instructionHtml(t("s1Task3RleWidget")),
        instructionHtml(t("s1Task3A")),
        createTextInput(),
        instructionHtml(t("s1Task3B")),
        createTextInput(),
        instructionHtml(t("s1Task3C")),
        createTextInput(),
        instructionHtml(t("s1Task3CWidget")),
        instructionHtml(t("s1Task3CHint")),
        instructionHtml(t("s1Task3D")),
        createTextInput(),
      )),
      container(List(
        instructionHtml(t("s1Task4Title")),
        instructionHtml(t("s1Task4QuotePlain")),
        instructionHtml(t("s1Task4A")),
        instructionHtml(t("s1Task4QuoteEncrypted")),
        createTextInput(),
        instructionHtml(t("s1Task4B")),
        createTextInput(),
      )),
      container(List(
        instructionHtml(t("s1ClosingTitle")),
        instructionHtml(t("s1ClosingIntro")),
        instructionHtml(t("s1ClosingA")),
        createTextInput(),
        instructionHtml(t("s1ClosingB")),
        instructionHtml(t("s1EfficiencyWidget")),
      )),
    )
  )

  private lazy val lossySection: WorkbookSection = section(
    t("section2Title"),
    List(
      container(List(
        instructionHtml(t("s2IntroP1")),
        instructionHtml(t("s2IntroP2")),
        instructionHtml(t("s2Task1Title")),
        instructionHtml(t("s2Task1Intro")),
        instructionHtml(t("s2Task1Widget")),
        instructionHtml(t("s2Task1A")),
        createTextInput(),
        instructionHtml(t("s2Task1B")),
        createTextInput(),
        instructionHtml(t("s2Task1C")),
        createTextInput(),
      )),
      container(List(
        instructionHtml(t("s2ExplanationP1")),
        instructionHtml(t("s2ExplanationP2")),
      )),
      container(List(
        instructionHtml(t("s2Task2Title")),
        instructionHtml(t("s2Task2Widget")),
        instructionHtml(t("s2Task2A")),
        createTextInput(),
        instructionHtml(t("s2Task2B")),
        createTextInput(),
      )),
      container(List(
        instructionHtml(t("s2Task3Title")),
        instructionHtml(t("s2Task3A")),
        createTextInput(),
        instructionHtml(t("s2Task3B")),
        createTextInput(),
        instructionHtml(t("s2Task3C")),
        createTextInput(),
      )),
      container(List(
        instructionHtml(t("s2ClosingTitle")),
        instructionHtml(t("s2ClosingIntro")),
        instructionHtml(t("s2ClosingA")),
        createTextInput(),
        instructionHtml(t("s2ClosingB")),
        createTextInput(),
      )),
    )
  )

  private lazy val filetypesSection: WorkbookSection = section(
    t("section3Title"),
    List(
      container(List(
        instructionHtml(t("s3IntroP1")),
        instructionHtml(t("s3IntroP2")),
        instructionHtml(t("s3Task1Title")),
        instructionHtml(t("s3Task1Intro")),
        instructionHtml(t("s3Task1Widget")),
        instructionHtml(t("s3Task1A")),
        createTextInput(),
        instructionHtml(t("s3Task1B")),
        createTextInput(),
      )),
      container(List(
        instructionHtml(t("s3Task2Title")),
        instructionHtml(t("s3Task2Intro")),
        instructionHtml(t("s3Task2A")),
        createTextInput(),
        instructionHtml(t("s3Task2B")),
        createTextInput(),
      )),
      container(List(
        instructionHtml(t("s3Task3Title")),
        instructionHtml(t("s3Task3Intro")),
        instructionHtml(t("s3Task3A")),
        createTextInput(),
        instructionHtml(t("s3Task3B")),
        createTextInput(),
      )),
      container(List(
        instructionHtml(t("s3Task4Title")),
        instructionHtml(t("s3Task4Intro")),
        instructionHtml(t("s3Task4Widget")),
        instructionHtml(t("s3Task4A")),
        createTextInput(),
        instructionHtml(t("s3Task4B")),
        createTextInput(),
        instructionHtml(t("s3Task4C")),
        createTextInput(),
      )),
      container(List(
        instructionHtml(t("s3ClosingTitle")),
        instructionHtml(t("s3ClosingA")),
        createTextInput(),
        instructionHtml(t("s3ClosingB")),
        createTextInput(),
      )),
    )
  )

  private lazy val finalSection: WorkbookSection = section(
    t("section4Title"),
    List(
      container(List(
        instructionHtml(t("s4IntroP1")),
        instructionHtml(t("s4IntroP2")),
        instructionHtml(t("s4IntroP3")),
        instructionHtml(t("s4Task1Title")),
        instructionHtml(t("s4Task1Note")),
        instructionHtml(t("s4Task1Widget")),
        instructionHtml(t("s4Task1A")),
        createTextInput(),
        instructionHtml(t("s4Task1B")),
        createTextInput(),
      )),
      container(List(
        instructionHtml(t("s4ClosingTitle")),
        instructionHtml(t("s4ClosingA")),
        createTextInput(),
        instructionHtml(t("s4ClosingB")),
        instructionHtml(t("s4ClosingBHint")),
        createTextInput(),
        instructionHtml(t("s4ClosingC")),
        createTextInput(),
      )),
      container(List(
        instructionHtml(t("s4PlenumTitle")),
        instructionHtml(t("s4PlenumNote")),
        instructionHtml(t("s4PlenumQuestions")),
      )),
    )
  )

}
