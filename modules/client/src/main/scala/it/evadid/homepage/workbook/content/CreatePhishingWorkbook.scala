package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.user.User
import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.elements.interactionElements.emailSimulator.{MailInteraction, MailEditor}
import it.evadid.workbook.elements.structureElements.Workbook

/** Digital version of the original mailbox exercise; messages and files are simulated. */
case class CreatePhishingWorkbook(fullInfo: FullInfo) extends WorkbookFactory {
  override def workbookId: String = "workbookPhishing"
  override def createWorkbook: Workbook = workbook("emailSimulator/workbookTitle", List(
    section("phishing-mailbox", "emailSimulator/sectionTitle", List(
      container("emailSimulator/exerciseTitle", List(
        instructionHtml("emailSimulator/instructions"),
        MailInteraction("phishing-original-mailbox", PhishingMailboxData.initialInbox)
      )),
      container("emailSimulator/practiceTitle", List(
        instructionPlaintext("emailSimulator/practiceInstructions"),
        MailEditor("phishing-local-practice")
      ))
    ))
  ), User("Marvin Kretschmer", "author-phishing-marvin-kretschmer", ""))
}
