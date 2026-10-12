package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap}
import it.evadid.util.logging.LoggingLevel
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig
import it.evadid.vm.BeProgram
import it.evadid.workbook.abstractions.WorkbookStructuringType
import it.evadid.workbook.elements.interactionElements.basic.LabeledNumberInteraction.{NumberType, NumberInteractionConfig}
import it.evadid.workbook.abstractions.grading.GradingStatus
import it.evadid.workbook.elements.interactionElements.emailSimulator.MailDraft
import munit.FunSuite
import upickle.default.*

class PracticeValueCodecSpec extends FunSuite {
  private def roundTrip[T: ReadWriter](value: T): T = {
    val restored = read[T](write(value))
    assertEquals(restored, value)
    assertEquals(readBinary[T](writeBinary(value)), value)
    restored
  }
  test("blank drafts and absent attachments round trip") {
    assertEquals(roundTrip(MailDraft()).validationError, Some("invalidRecipient"))
    assertEquals(read[MailDraft]("{}"), MailDraft())
  }
  test("draft codecs preserve recipients, Unicode, multiline text and attachments") {
    val draft = MailDraft("a@example.com; b@example.org", "学校", "line one\n\"quoted\" \\ text", Some("résumé.pdf"))
    assertEquals(roundTrip(draft).recipientList, List("a@example.com", "b@example.org"))
    assertEquals(roundTrip(draft).validationError, None)
  }
  test("logging levels round trip with their existing names") {
    LoggingLevel.values.foreach { level =>
      roundTrip(level)
      assert(write(level).contains(level.toString))
    }
  }
  test("grading status codecs preserve each refactored status variant") {
    import GradingStatus.*
    import it.evadid.distribution.command.SerializedException
    val values: List[GradingStatus] = List(Correct(), PartiallyCorrect(), Incorrect(), NotExecuted(),
      TestError(SerializedException(new IllegalArgumentException("fixture"))))
    values.foreach { value =>
      for (decoded <- List(read[GradingStatus](write(value)), readBinary[GradingStatus](writeBinary(value)))) {
        assertEquals(decoded.getClass, value.getClass)
        assertEquals(writeJs(decoded), writeJs(value))
      }
    }
    roundTrip(Correct())
    roundTrip(Incorrect())
  }

  test("program containers preserve their expression tree and display") {
    val program = BeProgram.empty
    assertEquals(roundTrip(program).toString, program.toString)
  }
  test("workbook structure kinds round trip") {
    WorkbookStructuringType.values.foreach(roundTrip(_))
  }
  test("number interaction configurations preserve every numeric policy") {
    NumberType.values.foreach { kind =>
      roundTrip(kind)
      roundTrip(NumberInteractionConfig(kind))
    }
  }
  test("constructor field display metadata preserves positional and named choices") {
    roundTrip(VariableDisplayConfig("学校", true))
    roundTrip(VariableDisplayConfig("quoted\"field", false))
  }
}
