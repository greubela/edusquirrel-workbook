package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.user.User
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.elements.interactionElements.emailSimulator.{MailInteraction, MailEditor}
import it.evadid.workbook.elements.interactionElements.table.*
import it.evadid.workbook.elements.interactionElements.text.*
import it.evadid.workbook.elements.structureElements.Workbook

/** Partial adaptation: mailbox, warning signs, Unicode/domain analysis and malware exercises. */
case class CreatePhishingWorkbook(fullInfo: FullInfo) extends WorkbookFactory {
  override def workbookId: String = "workbookPhishing"
  private def reflections(keys: String*) = keys.toList.flatMap(key => List(
    instructionPlaintext(s"digitalWorkbooks/$key"), createTextInput(s"phishing-$key")))
  override def createWorkbook: Workbook = workbook("emailSimulator/workbookTitle", List(
    section("phishing-mailbox", "emailSimulator/sectionTitle", List(
      container("emailSimulator/exerciseTitle", List(
        instructionPlaintext("digitalWorkbooks/phishingAdaptationScope"),
        instructionHtml("emailSimulator/instructions"),
        MailInteraction("phishing-original-mailbox", PhishingMailboxData.initialInbox)
      )),
      container("emailSimulator/practiceTitle", List(
        instructionPlaintext("emailSimulator/practiceInstructions"),
        MailEditor("phishing-local-practice")
      ))
    )),
    section("phishing-warning-signs", "digitalWorkbooks/phishingWarningTitle", List(container("digitalWorkbooks/phishingWarningTitle", List(
      instructionPlaintext("digitalWorkbooks/phishingWarningTask"), CreatePhishingWorkbook.warningEvidence
    ) ++ reflections("phishingLookFeel", "phishingSender", "phishingHiddenLink", "phishingIndependentCheck")))),
    section("phishing-domains-unicode", "digitalWorkbooks/phishingDomainTitle", List(container("digitalWorkbooks/phishingDomainTitle", List(
      instructionPlaintext("digitalWorkbooks/phishingDomainExplanation"), CreatePhishingWorkbook.domainParts,
      instructionPlaintext("digitalWorkbooks/phishingJudgmentTask"), CreatePhishingWorkbook.domainJudgments,
      CreatePhishingWorkbook.unicodeComparison,
      instructionPlaintext("digitalWorkbooks/phishingUnicodeTask"), CreatePhishingWorkbook.unicodeCodes
    ) ++ reflections("phishingUnicodeReason", "phishingUnicodeMitigation")))),
    section("phishing-attachments", "digitalWorkbooks/phishingAttachmentTitle", List(container("digitalWorkbooks/phishingAttachmentTitle", List(
      instructionPlaintext("digitalWorkbooks/phishingAttachmentExplanation"), CreatePhishingWorkbook.attachmentRisk,
      instructionPlaintext("digitalWorkbooks/phishingMalwareTask"), CreatePhishingWorkbook.malwareTypes
    ) ++ reflections("phishingMalwareDifference", "phishingAttachmentResponse")))),
    section("phishing-checklist", "digitalWorkbooks/phishingChecklistTitle", List(container("digitalWorkbooks/phishingChecklistTitle", List(
      instructionPlaintext("digitalWorkbooks/phishingChecklistTask"), CreatePhishingWorkbook.checklist
    ) ++ reflections("phishingUncertain", "phishingFinalReassessment"))))
  ), User("Marvin Kretschmer", "author-phishing-marvin-kretschmer", ""))
}
object CreatePhishingWorkbook {
  private def id(key: String) = LanguageMapContentId(s"digitalWorkbooks/$key")
  private def reflectionTable(elementId: String, title: String, rows: List[String], columns: List[String]) =
    AnswerTableInteraction(elementId, id(title), rows.map(id), columns.map(id), rows.map(_ => columns.map(_ => EditableTableCell())))
  val warningEvidence = reflectionTable("phishing-warning-evidence", "phishingWarningTable",
    List("phishingAppearance", "phishingDisplayName", "phishingLink", "phishingDomain"),
    List("phishingObservation", "phishingRequestedAction", "phishingCheck"))
  // These three source examples use simple suffixes; this is not a public-suffix resolver.
  val domainParts = AnswerTableInteraction("phishing-domain-parts", id("phishingDomainTable"),
    List("phishingPlanetarium", "phishingDfb", "phishingSparkasse").map(id),
    List(id("phishingHostname"), id("phishingRegisteredDomain")),
    List(("www.planetarium.berlin", "planetarium.berlin"), ("www.dfb.fanshop.io", "fanshop.io"),
      ("www.sparkasse.de.suport.ru", "suport.ru")).map((host, domain) =>
      List(EditableTableCell(Some(List(host))), EditableTableCell(Some(List(domain))))))
  val domainJudgments = reflectionTable("phishing-domain-judgments", "phishingJudgmentTable",
    List("phishingGoogleSpoof", "phishingApple", "phishingYoutubeSpoof", "phishingPaypal", "phishingInstagramSpoof"),
    List("phishingJudgment", "phishingEvidence"))
  val unicodeComparison = UnicodeComparisonInteraction("phishing-unicode", id("phishingUnicodeTitle"),
    UnicodeComparisonAnswer("paypal.com", "payраI.com")) // Cyrillic р/а and Latin capital I, as in T2.
  val unicodeCodes = AnswerTableInteraction("phishing-unicode-codes", id("phishingCodeTable"),
    List("phishingCodeA", "phishingCodeLowerA", "phishingCodeEszett", "phishingCodeNet", "phishingCodeEmoji").map(id),
    List(id("phishingCodeSequence")), List("65", "97", "223", "1053 1077 1090", "127757").map(code => List(EditableTableCell(Some(List(code))))))
  val attachmentRisk = reflectionTable("phishing-attachment-risk", "phishingAttachmentTable",
    List("phishingFileJpg", "phishingFileExe", "phishingFileZip", "phishingFileDocx", "phishingFileDocm", "phishingFilePdf"),
    List("phishingExtension", "phishingPossibleRisk", "phishingEvidence"))
  val malwareTypes = AnswerTableInteraction("phishing-malware-types", id("phishingMalwareTable"),
    List("phishingAdwareObservation", "phishingRansomObservation", "phishingSpyObservation").map(id),
    List(id("phishingMalwareType")), List("Adware", "Ransomware", "Spyware").map(kind =>
      List(EditableTableCell(Some(List(kind)), List("Adware", "Keylogger", "Spyware", "Ransomware")))))
  val checklist = reflectionTable("phishing-final-checklist", "phishingChecklistTable",
    (1 to 5).map(i => s"phishingMeasure$i").toList, List("phishingMeasure", "phishingDescription", "phishingReason"))
}
