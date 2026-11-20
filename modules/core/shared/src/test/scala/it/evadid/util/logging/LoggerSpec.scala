package it.evadid.util.logging

import it.evadid.core.datastructures.state.State
import it.evadid.util.logging.derived.SyncLogger
import munit.FunSuite

class LoggerSpec extends FunSuite {
  private final class Recording(out: String = "", err: String = "") extends Logger {
    val entries = State(Vector.empty[(String, LoggingLevel)])
    override def log(msg: String, level: LoggingLevel): Unit = entries.update(_ :+ (msg -> level))
    override def getOut(): String = out
    override def getErr(): String = err
  }

  test("external output is wrapped once and blank external output is ignored") {
    val logger = new Recording
    logger.logFromExternalInfo("  ")
    logger.logFromExternalError("")
    assertEquals(logger.entries.now(), Vector.empty)
    logger.logFromExternalInfo(" hello ")
    logger.logFromExternalError("broken")
    val Vector((info, LoggingLevel.INFO), (error, LoggingLevel.ERROR)) = logger.entries.now(): @unchecked
    assert(info.contains("Inserted Messages from external Logger") && info.contains("hello\n ~~~[End"), info)
    assert(error.contains("broken"), error)
  }

  test("output and errors of another logger are copied with their levels") {
    val target = new Recording
    target.logAllFrom(new Recording(out = "printed", err = "failed"))
    assertEquals(target.entries.now().map(_._2), Vector(LoggingLevel.INFO, LoggingLevel.ERROR))
    assertEquals(target.entries.now()(1)._1, "failed")
    target.logAllFrom(new Recording)
    assertEquals(target.entries.now().size, 2)
  }

  test("exceptions are logged with their message and the recovery reason") {
    val logger = new Recording
    logger.logException(IllegalStateException("boom"))
    logger.logExceptionWarn("used default", IllegalStateException("bad value"))
    logger.logExceptionInfo("skipped", "expected", IllegalStateException("gone"))
    val Vector((plain, LoggingLevel.ERROR), (warn, LoggingLevel.WARN), (info, LoggingLevel.INFO)) = logger.entries.now(): @unchecked
    assert(plain.contains("Logging Exception: boom"), plain)
    assert(warn.contains("Recovered from exception by: used default") && warn.contains("bad value"), warn)
    assert(info.contains("skipped") && info.contains("gone"), info)
  }

  test("derived loggers transform messages and expose the underlying output") {
    val underlying = BasicLogger()
    val derived = Logger.deriveFrom(underlying, (msg, level) => s"[$level] $msg")
    derived.logWarn("careful")
    assertEquals(derived.getOut(), "[WARN] careful\n")
    assertEquals(derived.getErr(), "")
    assertEquals(derived.underlyingLogger, underlying)
    assertEquals(Logger.formatPerformance("cache", 1, 4, "hits", "requests"),
      "cache performance (hits / requests): 1 / 4 (= requests succeeded so far (=0.25%)")
  }

  test("sync loggers mark direction, level and destination") {
    val underlying = new Recording
    val logger = SyncLogger(underlying).forSyncDest("backend")
    logger.log("sent", LoggingLevel.INFO, Some(true))
    logger.log("received", LoggingLevel.INFO, Some(false))
    logger.log("slow", LoggingLevel.WARN, Some(true))
    logger.log("late", LoggingLevel.WARN, Some(false))
    logger.log("rejected", LoggingLevel.ERROR, Some(true))
    logger.log("refused", LoggingLevel.ERROR, Some(false))
    logger.logInfo("plain")
    logger.logWarn("warned")
    logger.logError("failed")
    logger.logException("ignored", IllegalStateException("bad"), Some(true), LoggingLevel.ERROR)
    val arrows = underlying.entries.now().map(_._1).map(_.split(" ").find(_.matches("[-<>?|]{5}")).getOrElse(""))
    assertEquals(arrows, Vector("--->>", "<<---", "-?->>", "<<-?-", "-|->>", "<<-|-", "-----", "--?--", "--|--", "-|->>"))
    assert(underlying.entries.now().forall(_._1.contains("Sync to 'backend''")))
    assert(underlying.entries.now().last._1.contains("Recovered from exception by: ignored"))
  }
}
