package it.evadid.workbook.interaction.sync

class SyncContextSpec extends munit.FunSuite {
  test("usage matching compares logical context strings including the user") {
    val context = SyncContext("program", "scenario", "student", "answer_history")
    assert(context.doesMatch(context.toUsageContext))
    assert(!context.doesMatch(context.toUsageContext.copy(userId = "other-student")))
    assert(!context.doesMatch(context.toUsageContext.copy(programId = "other-program")))
    assert(!context.doesMatch(context.toUsageContext.copy(scenarioId = "other-scenario")))
    assert(context.copy(keyForSerialisation = "another_answer").doesMatch(context.toUsageContext))
  }
}
