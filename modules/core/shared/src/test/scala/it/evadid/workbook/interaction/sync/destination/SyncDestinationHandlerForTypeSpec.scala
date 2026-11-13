package it.evadid.workbook.interaction.sync.destination

import it.evadid.core.util.io.Serializer
import it.evadid.util.logging.BasicLogger
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.SyncSuccess
import munit.FunSuite
import scala.concurrent.{ExecutionContext, Future, Promise}

class SyncDestinationHandlerForTypeSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val integer = Serializer.fromUpickleJson(upickle.default.readwriter[Int])

  private class Destination(records: Map[String, String]) extends SyncDestinationHandlerBasic {
    val logger = BasicLogger()
    val stored = Promise[Map[String, String]]()
    override val syncLogger = SyncLogger(logger)
    override val underlyingDefinition = SyncDestinationDefinition.LocalStorageSyncDefinition(10000)
    override def readAll(implicit ec: ExecutionContext): Future[Map[String, String]] = Future.successful(records)
    override def readElement(key: String)(implicit ec: ExecutionContext): Future[String] = Future.fromTry(scala.util.Try(records(key)))
    override def storeElement(key: String, value: String)(implicit ec: ExecutionContext): Future[SyncSuccess] = storeBulk(Map(key -> value))
    override def storeBulk(values: Map[String, String])(implicit ec: ExecutionContext): Future[SyncSuccess] = {
      stored.trySuccess(values)
      Future.successful(SyncSuccess.emptyNow())
    }
    override def clearValue(key: String)(implicit ec: ExecutionContext): Future[SyncSuccess] = Future.successful(SyncSuccess.emptyNow())
  }

  test("fully readable stores do not emit conversion warnings even with more than two records") {
    val destination = new Destination(Map("1" -> "10", "2" -> "20", "3" -> "30"))
    destination.forSpecificType(integer, integer).readFullStore.map { values =>
      assertEquals(values, Map(1 -> 10, 2 -> 20, 3 -> 30))
      assertEquals(destination.logger.getOut(), "")
    }
  }

  test("one unreadable record is reported without its key, contents or a compatibility conversion") {
    val destination = new Destination(Map("private-key" -> "private conversation"))
    destination.forSpecificType(integer, integer).readFullStore.map { values =>
      assertEquals(values, Map.empty[Int, Int])
      val warning = destination.logger.getOut()
      assert(warning.contains("Ignoring 1 unreadable records out of 1"))
      assert(!warning.contains("private"))
    }
  }

  test("mixed stores retain valid records and bound warnings independently of rejected payload size") {
    val destination = new Destination(Map("1" -> "10", "2" -> ("private conversation" * 50000), "private-key" -> "30"))
    destination.forSpecificType(integer, integer).readFullStore.map { values =>
      assertEquals(values, Map(1 -> 10))
      val warning = destination.logger.getOut()
      assert(warning.contains("Ignoring 2 unreadable records out of 3"))
      assert(warning.length < 400)
      assert(!warning.contains("private"))
    }
  }

  test("serialization failures are counted without dumping rejected values and valid writes are retained") {
    val positive = new Serializer[Int] {
      def serialize(value: Int): String = {
        require(value > 0, "private serialization detail")
        integer.serialize(value)
      }
      def deserialize(value: String): Int = integer.deserialize(value)
    }
    val destination = new Destination(Map.empty)
    destination.forSpecificType(integer, positive).storeAll(Map(1 -> 10, 2 -> -987654321)).flatMap { _ =>
      destination.stored.future.map { values =>
        assertEquals(values, Map("1" -> "10"))
        val warning = destination.logger.getOut()
        assert(warning.contains("Could not serialize 1 records out of 2"))
        assert(!warning.contains("private"))
        assert(!warning.contains("-987654321"))
      }
    }
  }
}
