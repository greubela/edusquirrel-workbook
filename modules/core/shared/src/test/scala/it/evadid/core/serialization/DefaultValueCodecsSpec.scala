package it.evadid.core.serialization

import it.evadid.core.datastructures.geometry.*
import it.evadid.core.datastructures.matrix.*
import it.evadid.workbook.sync.*
import munit.FunSuite
import upickle.default.*

import it.evadid.workbook.abstractions.InteractionFormat.*

class DefaultValueCodecsSpec extends FunSuite {
  private def roundTrip[T: ReadWriter](value: T): Unit = assertEquals(read[T](write(value)), value)
  test("all built-in event sync strategy variants have default codecs") {
    List[EventSyncStrategy](EventSyncStrategy.SYNC_EVERYTHING, EventSyncStrategy.KEEP_LAST_MAJOR, EventSyncStrategy.KeepImportance(Set(UpdateImportance.MAJOR))).foreach(roundTrip(_))
  }
  test("sync contexts and caches expose default codecs without private imports") {
    val usage = UsageContext("program", "scenario", "user")
    val sync = usage.toSyncContext("variable")
    roundTrip(usage)
    roundTrip(sync)
    val history = write(it.evadid.workbook.history.InteractionVariableHistory.empty[TextInteractionFormat.type])
  }
  test("registry descriptors have a default codec") {
    val descriptor = it.evadid.workbook.serialization.WorkbookElementSerializable("id", "TextInteraction", Map("value" -> ujson.Str("x")))
    roundTrip(descriptor)
  }

  test("chat value models and every sender role have default codecs") {
    import it.evadid.core.datastructures.chat.*
    SenderRole.allRoles.foreach(roundTrip(_))
    val basic = ChatParticipant.BasicPerson("Ada", "id", SenderRole.USER, Some("AD"))
    val serializable = ChatParticipant.BasicPerson("Agent", "agent", SenderRole.AGENT, Some("AI"))
    roundTrip(basic)
    roundTrip(serializable)
    assertEquals(basic.toSerializable.abbreviation, basic.abbreviation)
    assertEquals(serializable.toBasic.abbreviation, Some("AI"))
  }
  test("numeric constraint snapshots and Turtle project state have default codecs") {
    import it.evadid.core.datastructures.numbers.NumberConstraintImpl
    roundTrip(NumberConstraintImpl(Some(BigDecimal("1.5")), Some(BigDecimal("9.25"))))
    roundTrip(NumberConstraintImpl[Double](None, None))
    import it.evadid.workbook.interactions.programming.programmingExerciseTurtle.TurtleStitchProjectState
    roundTrip(TurtleStitchProjectState.empty())
    roundTrip(TurtleStitchProjectState.parseFromStringOrEmpty("<project>Grüße</project>"))
  }

}
