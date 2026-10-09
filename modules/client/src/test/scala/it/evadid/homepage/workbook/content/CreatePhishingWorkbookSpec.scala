package it.evadid.homepage.workbook.content

import it.evadid.workbook.elements.interactionElements.emailSimulator.{MailInteraction, MailEditor}
import it.evadid.workbook.elements.interactionElements.table.*
import it.evadid.workbook.model.text.UnicodeText
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import munit.FunSuite

class CreatePhishingWorkbookSpec extends FunSuite {
  test("expanded phishing workbook retains mailbox and practice IDs in both definition formats") {
    val workbook = CreatePhishingWorkbook(null).createWorkbook
    for serializer <- List(WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike) do
      assertEquals(serializer.deserialize(serializer.serialize(workbook)), workbook)
    val elements = workbook.allChildrenFullSubtree
    assertEquals(elements.map(_.elementId).distinct.size, elements.size)
    assertEquals(elements.collect { case m: MailInteraction => m.elementId }, List("phishing-original-mailbox"))
    assertEquals(elements.collect { case m: MailEditor => m.elementId }, List("phishing-local-practice"))
    assertEquals(workbook.sections.size, 5)
  }
  test("source hostname exercise checks host separately from registrable domain") {
    val e = CreatePhishingWorkbook.domainParts
    val answer = TableAnswer(List("www.planetarium.berlin", "planetarium.berlin", "www.dfb.fanshop.io", "fanshop.io",
      "www.sparkasse.de.suport.ru", "suport.ru"))
    assertEquals(e.grade(answer), Some(TableGrade(6, 6)))
    assertEquals(e.grade(answer.copy(values = answer.values.updated(5, "sparkasse.de"))), Some(TableGrade(5, 6)))
  }
  test("Unicode examples distinguish code points, preserve scripts and count emoji once") {
    val factory = CreatePhishingWorkbook
    assertEquals(UnicodeText.characters(factory.unicodeComparison.initial.second).slice(3, 6).map(_.codePoint), List(0x440, 0x430, 0x49))
    val answer = TableAnswer(List("65", "97", "223", "1053 1077 1090", "127757"))
    assertEquals(factory.unicodeCodes.grade(answer), Some(TableGrade(5, 5)))
    assertEquals(factory.unicodeCodes.grade(answer.copy(values = answer.values.updated(4, "55356 57101"))), Some(TableGrade(4, 5)))
  }
  test("textbook malware categories accept the expected choices and reject a different category") {
    val e = CreatePhishingWorkbook.malwareTypes
    assertEquals(e.grade(TableAnswer(List("Adware", "Ransomware", "Spyware"))), Some(TableGrade(3, 3)))
    assertEquals(e.grade(TableAnswer(List("Keylogger", "Ransomware", "Spyware"))), Some(TableGrade(2, 3)))
  }
  test("trust, risk, evidence and checklist are saved human judgments without invented grades") {
    val factory = CreatePhishingWorkbook
    val tables = List(factory.warningEvidence, factory.domainJudgments, factory.attachmentRisk, factory.checklist)
    assertEquals(tables.map(_.editableCells.size), List(12, 10, 18, 15))
    tables.foreach { e =>
      val answer = TableAnswer(List.fill(e.editableCells.size)("Independent evidence"))
      assert(e.isAnswered(answer))
      assertEquals(e.grade(answer), None)
      assertEquals(e.serializerInteractionContent.deserialize(e.serializerInteractionContent.serialize(answer)), answer)
    }
  }
}
