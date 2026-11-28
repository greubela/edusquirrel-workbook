package it.evadid.workbook.interactions.programming


import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap}
import it.evadid.util.logging.LoggingLevel
import it.evadid.core.util.io.serializer.ConstructorLikeSerializer.VariableDisplayConfig
import it.evadid.vm.BeProgram
import it.evadid.workbook.abstractions.WorkbookStructuringType
import it.evadid.workbook.interactions.basic.LabeledNumberInteraction.{NumberType, NumberInteractionConfig}
import it.evadid.workbook.abstractions.grading.GradingStatus
import it.evadid.workbook.interactions.emailSimulator.MailDraft
import munit.FunSuite
import upickle.default.*

class PracticeValueCodecSpec extends FunSuite {
  private def roundTrip[T: ReadWriter](value: T): T = {
    val restored = read[T](write(value))
    assertEquals(restored, value)
    assertEquals(readBinary[T](writeBinary(value)), value)
    restored
  }
  test("grading status codecs preserve each refactored status variant") {
    import GradingStatus.*
    import it.evadid.distribution.command.SerializedException
    val values: List[GradingStatus] = List(Correct, PartiallyCorrect, Incorrect, NotRequested,
      TestError(SerializedException(new IllegalArgumentException("fixture"))))
    values.foreach { value =>
      for (decoded <- List(read[GradingStatus](write(value)), readBinary[GradingStatus](writeBinary(value)))) {
        assertEquals(decoded.getClass, value.getClass)
        assertEquals(writeJs(decoded), writeJs(value))
      }
    }
    roundTrip(Correct)
    roundTrip(Incorrect)
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
}
