package it.evadid.core.serialization

import it.evadid.core.datastructures.geometry.*
import it.evadid.core.datastructures.matrix.*
import it.evadid.workbook.interaction.sync.*
import munit.FunSuite
import upickle.default.*

class DefaultValueCodecsSpec extends FunSuite {
  private def roundTrip[T: ReadWriter](value: T): Unit = assertEquals(read[T](write(value)), value)
  test("all matrix value classes have default codecs") {
    val dim = MatrixDimension(2, 2, wrapAround = true)
    roundTrip(dim)
    roundTrip(MatrixPosition(-1, 2))
    roundTrip(PositionInMatrix(MatrixPosition(0, 1), dim))
    roundTrip(Direction.BOTTOM_RIGHT)
    roundTrip(Neighbourhood.knight)
    roundTrip(Matrix(dim, List(1, 2, 3, 4)))
  }
  test("geometry codecs reconstruct their numeric context and derived behavior") {
    val dim = Dimension(4.0, 2.0)
    val bounds = Bounds(Point(1.0, 2.0), dim)
    val relative = RelativeBounds(Point(3.0, 4.0), dim)
    roundTrip(dim)
    roundTrip(bounds)
    roundTrip(relative)
    roundTrip(AspectRatio(2.0))
    assertEquals(read[Bounds[Double]](write(bounds)).centerPoint, Point(3.0, 3.0))
    assertEquals(read[RelativeBounds[Double]](write(relative)).toAbsoluteBounds(Point(1.0, 2.0)).startPoint, Point(4.0, 6.0))
    val decimal = Dimension(BigDecimal("1.25"), BigDecimal("2.5"))
    roundTrip(decimal)
  }
  test("all built-in sync strategy singleton variants have default codecs") {
    List[SyncStrategy](SyncStrategy.SYNC_EVERYTHING, SyncStrategy.SYNC_LAST_AND_MAJOR, SyncStrategy.SYNC_LAST).foreach(roundTrip(_))
  }
  test("sync contexts and caches expose default codecs without private imports") {
    val usage = UsageContext("program", "scenario", "user")
    val sync = usage.toSyncContext("variable")
    roundTrip(usage)
    roundTrip(sync)
    val history = it.evadid.workbook.interaction.variable.InteractionVariableHistorySerialized.empty
    roundTrip(SyncCache(java.time.LocalDateTime.of(2025, 1, 1, 12, 0), usage, Map(sync -> history)))
  }
  test("registry descriptors have a default codec") {
    val descriptor = it.evadid.workbook.jsonFactory.WorkbookElementSerializable("id", "TextInteraction", Map("value" -> ujson.Str("x")))
    roundTrip(descriptor)
  }

  test("normalized Python models preserve nested statements and indentation") {
    import it.evadid.vm.parsing.python.normalization.PythonNormalizationModel.*
    roundTrip(RawLine(4, "print('x')"))
    roundTrip(Line(1, "print('x')"))
    val statements: List[Statement] = List(SimpleStatement("x = 1"),
      CompoundStatement("while running:", List(SimpleStatement("break"))),
      IfStatement("enabled", List(SimpleStatement("forward(1)")), Some(List(SimpleStatement("pass")))))
    statements.foreach(roundTrip(_))
    roundTrip(ParsedStatementTree(statements, 4))
    roundTrip(ParsedStatementTree(Nil, 2))
  }
  test("chat value models and every sender role have default codecs") {
    import it.evadid.core.datastructures.chat.*
    SenderRole.allRoles.foreach(roundTrip(_))
    val basic = Person.BasicPerson("Ada", "id", SenderRole.USER, Some("AD"))
    val serializable = Person.SerializablePerson("Agent", "agent", SenderRole.AGENT, "AI")
    roundTrip(basic)
    roundTrip(serializable)
    assertEquals(basic.toSerializable.abbreviation, basic.abbreviation)
    assertEquals(serializable.toBasic.abbreviation, Some("AI"))
  }
  test("execution timing models preserve timestamps and fixed request times") {
    import it.evadid.distribution.command.*
    val time = java.time.LocalDateTime.of(2025, 1, 1, 12, 0)
    roundTrip(ExecutionDuration(time, time.plusSeconds(1)))
    val history = ExecutionHistory(time, time.plusSeconds(1), time.plusSeconds(2), time.plusSeconds(3))
    roundTrip(history)
    assertEquals(history.withFixedTime(time.minusSeconds(2), time.minusSeconds(1)).timestampExecutionFinished, time.plusSeconds(3))
  }
  test("numeric constraint snapshots and Turtle project state have default codecs") {
    import it.evadid.core.datastructures.numbers.NumberConstraintImpl
    roundTrip(NumberConstraintImpl(Some(BigDecimal("1.5")), Some(BigDecimal("9.25"))))
    roundTrip(NumberConstraintImpl[Double](None, None))
    import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleStitchProjectState
    roundTrip(TurtleStitchProjectState.empty())
    roundTrip(TurtleStitchProjectState.parseFromStringOrEmpty("<project>Grüße</project>"))
  }
  test("sealed code-check results preserve success and missing hints") {
    import it.evadid.workbook.elements.interactionElements.codeTaskToggle.AdvancedCodeCheckResult.*
    import it.evadid.workbook.elements.interactionElements.codeTaskToggle.AdvancedCodeCheckResult
    import it.evadid.core.datastructures.language.LanguageMapContentId
    roundTrip[AdvancedCodeCheckResult](Success)
    roundTrip[AdvancedCodeCheckResult](Incomplete(List(LanguageMapContentId("hint/missing"))))
  }

}
