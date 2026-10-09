package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.user.User
import it.evadid.homepage.control.model.*
import it.evadid.workbook.elements.structureElements.{Workbook, WorkbookSection}
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.elements.interactionElements.compression.CompressionExperimentInteraction
import it.evadid.workbook.elements.interactionElements.pixel.BinaryPixelInteraction
import it.evadid.workbook.model.pixel.BinaryPixelImage
import it.evadid.workbook.model.compression.*

case class CreateCompressionWorkbook(override val fullInfo: FullInfo) extends WorkbookFactory {

  override val availableLanguages: List[HumanLanguage] = List(German)

  override val workbookId: String = "CompressionWorkbook"

  private def t(key: String): String = s"CompressionWorkbook/$key"

  /** Consume the replaced display's auto ID so existing written answers retain their IDs. */
  private def experiment(key: String, initial: CompressionExperiment): WorkbookElement = {
    nextId()
    CompressionExperimentInteraction(s"compression-$key", LanguageMapContentId(t(key)), initial)
  }

  private def efficiencyExercise: WorkbookElement = {
    nextId()
    sortingReasonExercise("compression-efficiency", List(t("efficiencyRle"), t("efficiencyDictionary"), t("efficiencyNone")), List(
      (t("efficiencyScan"), 0, t("efficiencyScanFeedback"), t("efficiencyReason")),
      (t("efficiencyReport"), 1, t("efficiencyReportFeedback"), t("efficiencyReason")),
      (t("efficiencyEncrypted"), 2, t("efficiencyEncryptedFeedback"), t("efficiencyReason")),
      (t("efficiencyAddresses"), 1, t("efficiencyAddressesFeedback"), t("efficiencyReason"))))
  }

  override lazy val createWorkbook: Workbook = workbook(
    t("workbookTitle"),
    List(
      introSection,
      losslessSection,
      lossySection,
      filetypesSection,
      sortingDemoSection,
      finalSection
    ),
    User.YanneckDimitrov
  )

  private lazy val introSection: WorkbookSection = section(
    "section0",
    t("section0Title"),
    List(
      container(t("introScenarioTitle"), List(
        instructionHtml(t("introScenario")),
      )),
      container(t("introAnswerTitle"), List(
        instructionHtml(t("introAnswerTask")),
        createTextInput(),
      )),
      container(t("introSourceTitle"), List(
        instructionHtml(t("introSnowdenIntro")),
        instructionHtml(t("introArticle")),
        instructionHtml(t("introQuote")),
      )),
      container(t("introVideoTitle"), List(
        instructionHtml(t("introTaskText")),
        instructionHtml(t("introRatesInfo")),
        experiment("introWidgetPlaceholder", VideoBudget()),
        instructionHtml(t("introReflectionTask")),
        instructionHtml(t("introReflectionHint")),
        createTextInput(),
      )),
    )
  )

  private lazy val losslessSection: WorkbookSection = section(
    "section1",
    t("section1Title"),
    List(
      container(t("s1Task1Title"), List(
        instructionHtml(t("s1Task1Title")),
        instructionHtml(t("s1Task1Intro")),
        experiment("s1Task1Widget", TextBits()),
        BinaryPixelInteraction("compression-bit-image", LanguageMapContentId(t("bitImageTitle")),
          BinaryPixelImage(8, 8, List.tabulate(64)(i => { val x = i % 8; val y = i / 8;
            (x == 1 || x == 6) && y >= 2 && y <= 5 || (y == 1 || y == 6) && x >= 2 && x <= 5 }))),
        instructionHtml(t("s1Task1A")),
        createTextInput(),
        instructionHtml(t("s1Task1B")),
        createTextInput(),
      )),
      container(t("s1Task2Title"), List(
        instructionHtml(t("s1IntroP1")),
        instructionHtml(t("s1Transition")),
        instructionHtml(t("s1Task2Title")),
        instructionHtml(t("s1Task2Intro")),
        experiment("s1Task2Widget", RunLengthText()),
        instructionHtml(t("s1Task2A")),
        createTextInput(),
        instructionHtml(t("s1Task2B")),
        createTextInput(),
        instructionHtml(t("s1Task2C")),
        createTextInput(),
      )),
      container(t("s1Task3Title"), List(
        instructionHtml(t("s1Task3Title")),
        experiment("s1Task3RleWidget", RunLengthText(CreateCompressionWorkbook.sampleText)),
        instructionHtml(t("s1Task3A")),
        createTextInput(),
        instructionHtml(t("s1Task3B")),
        createTextInput(),
        instructionHtml(t("s1Task3C")),
        createTextInput(),
        experiment("s1Task3CWidget", DictionaryText(CreateCompressionWorkbook.sampleText)),
        instructionHtml(t("s1Task3CHint")),
        instructionHtml(t("s1Task3D")),
        createTextInput(),
      )),
      container(t("s1Task4Title"), List(
        instructionHtml(t("s1Task4Title")),
        instructionHtml(t("s1Task4QuotePlain")),
        instructionHtml(t("s1Task4A")),
        instructionHtml(t("s1Task4QuoteEncrypted")),
        createTextInput(),
        instructionHtml(t("s1Task4B")),
        createTextInput(),
      )),
      container(t("s1ClosingTitle"), List(
        instructionHtml(t("s1ClosingTitle")),
        instructionHtml(t("s1ClosingIntro")),
        instructionHtml(t("s1ClosingA")),
        createTextInput(),
        instructionHtml(t("s1ClosingB")),
        efficiencyExercise,
      )),
    )
  )

  private lazy val lossySection: WorkbookSection = section(
    "section2",
    t("section2Title"),
    List(
      container(t("s2Task1Title"), List(
        instructionHtml(t("s2IntroP1")),
        instructionHtml(t("s2IntroP2")),
        instructionHtml(t("s2Task1Title")),
        instructionHtml(t("s2Task1Intro")),
        experiment("s2Task1Widget", ImageBlocks(CreateCompressionWorkbook.imageResource)),
        instructionHtml(t("s2Task1A")),
        createTextInput(),
        instructionHtml(t("s2Task1B")),
        createTextInput(),
        instructionHtml(t("s2Task1C")),
        createTextInput(),
      )),
      container(t("s2ExplanationTitle"), List(
        instructionHtml(t("s2ExplanationP1")),
        instructionHtml(t("s2ExplanationP2")),
      )),
      container(t("s2Task2Title"), List(
        instructionHtml(t("s2Task2Title")),
        experiment("s2Task2Widget", ImageBlocks(CreateCompressionWorkbook.imageResource, separateChannels = false)),
        instructionHtml(t("s2Task2A")),
        createTextInput(),
        instructionHtml(t("s2Task2B")),
        createTextInput(),
      )),
      container(t("s2Task3Title"), List(
        instructionHtml(t("s2Task3Title")),
        instructionHtml(t("s2Task3A")),
        createTextInput(),
        instructionHtml(t("s2Task3B")),
        createTextInput(),
        instructionHtml(t("s2Task3C")),
        createTextInput(),
      )),
      container(t("s2ClosingTitle"), List(
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
    "section3",
    t("section3Title"),
    List(
      container(t("s3Task1Title"), List(
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
      container(t("s3Task2Title"), List(
        instructionHtml(t("s3Task2Title")),
        instructionHtml(t("s3Task2Intro")),
        instructionHtml(t("s3Task2A")),
        createTextInput(),
        instructionHtml(t("s3Task2B")),
        createTextInput(),
      )),
      container(t("s3Task3Title"), List(
        instructionHtml(t("s3Task3Title")),
        instructionHtml(t("s3Task3Intro")),
        instructionHtml(t("s3Task3A")),
        createTextInput(),
        instructionHtml(t("s3Task3B")),
        createTextInput(),
      )),
      container(t("s3Task4Title"), List(
        instructionHtml(t("s3Task4Title")),
        instructionHtml(t("s3Task4Intro")),
        experiment("s3Task4Widget", ArchiveBudget()),
        instructionHtml(t("s3Task4A")),
        createTextInput(),
        instructionHtml(t("s3Task4B")),
        createTextInput(),
        instructionHtml(t("s3Task4C")),
        createTextInput(),
      )),
      container(t("s3ClosingTitle"), List(
        instructionHtml(t("s3ClosingTitle")),
        instructionHtml(t("s3ClosingA")),
        createTextInput(),
        instructionHtml(t("s3ClosingB")),
        createTextInput(),
      )),
    )
  )

  private lazy val sortingDemoSection: WorkbookSection = section(
    "sectionSortingDemo",
    t("sortingDemoSectionTitle"),
    List(
      container(t("sortingDemoContainerTitle"), List(
        instructionHtml(t("sortingDemoIntro")),
        sortingExercise(
          "compression-sorting-demo",
          List(
            t("sortingDemoFieldLossless"),
            t("sortingDemoFieldLossy")
          ),
          List(
            (t("sortingDemoItemRle"), 0, t("sortingDemoErrorRle")),
            (t("sortingDemoItemJpeg"), 1, t("sortingDemoErrorJpeg")),
            (t("sortingDemoItemPng"), 0, t("sortingDemoErrorPng")),
            (t("sortingDemoItemMp3"), 1, t("sortingDemoErrorMp3"))
          )
        )
      )),
      container(t("sortingReasonDemoContainerTitle"), List(
        instructionHtml(t("sortingReasonDemoIntro")),
        sortingReasonExercise(
          "compression-sorting-reason-demo",
          List(
            t("sortingDemoFieldLossless"),
            t("sortingDemoFieldLossy")
          ),
          List(
            (t("sortingDemoItemRle"), 0, t("sortingDemoErrorRle"), t("sortingReasonDemoItemRlePrompt")),
            (t("sortingDemoItemJpeg"), 1, t("sortingDemoErrorJpeg"), t("sortingReasonDemoItemJpegPrompt")),
            (t("sortingDemoItemPng"), 0, t("sortingDemoErrorPng"), t("sortingReasonDemoItemPngPrompt")),
            (t("sortingDemoItemMp3"), 1, t("sortingDemoErrorMp3"), t("sortingReasonDemoItemMp3Prompt"))
          )
        )
      ))
    )
  )

  private lazy val finalSection: WorkbookSection = section(
    "section4",
    t("section4Title"),
    List(
      container(t("s4Task1Title"), List(
        instructionHtml(t("s4IntroP1")),
        instructionHtml(t("s4IntroP2")),
        instructionHtml(t("s4IntroP3")),
        instructionHtml(t("s4Task1Title")),
        instructionHtml(t("s4Task1Note")),
        experiment("s4Task1Widget", CreateCompressionWorkbook.storageStudy),
        instructionHtml(t("s4Task1A")),
        createTextInput(),
        instructionHtml(t("s4Task1B")),
        createTextInput(),
      )),
      container(t("s4ClosingTitle"), List(
        instructionHtml(t("s4ClosingTitle")),
        instructionHtml(t("s4ClosingA")),
        createTextInput(),
        instructionHtml(t("s4ClosingB")),
        instructionHtml(t("s4ClosingBHint")),
        createTextInput(),
        instructionHtml(t("s4ClosingC")),
        createTextInput(),
      )),
      container(t("s4PlenumTitle"), List(
        instructionHtml(t("s4PlenumTitle")),
        instructionHtml(t("s4PlenumNote")),
        instructionHtml(t("s4PlenumQuestions")),
      )),
    )
  )

}

object CreateCompressionWorkbook {
  val sampleText = "Die Daten bleiben geheim. Die Daten bleiben wichtig. Die Daten bleiben erhalten."
  val imageResource = "programs/20260907Datenkompression/img/katze.jpg"
  private def file(name: String, size: Int, information: String): StorageFile =
    StorageFile(name, size, LanguageMapContentId(s"CompressionWorkbook/$information"))
  private def pack(label: String, files: List[StorageFile]): StoragePackage =
    StoragePackage(LanguageMapContentId(s"CompressionWorkbook/$label"), files)
  // Authored decimal-MB teaching packages. Sizes/rates are assumptions, not measured compression results.
  val storageStudy = StorageStudy(List(
    pack("packageVideos", List(
      file("Aufnahmen.mp4", 30000, "fileVideoInfo"), file("Berichte.txt", 8000, "fileTextInfo"))),
    pack("packageLogs", List(
      file("Protokolle.txt", 33600, "fileTextInfo"), file("Fotos.raw", 8000, "fileRawInfo"),
      file("Berichte.docx", 1200, "fileDocumentInfo"))),
    pack("packageMixed", List(
      file("Aufnahmen.mp4", 18000, "fileVideoInfo"), file("Scans.tiff", 6000, "fileScanInfo"),
      file("Berichte.docx", 5000, "fileDocumentInfo"), file("Archiv.zip", 9000, "fileArchiveInfo"),
      file("Verschluesselt.bin", 4000, "fileEncryptedInfo")))))
}
