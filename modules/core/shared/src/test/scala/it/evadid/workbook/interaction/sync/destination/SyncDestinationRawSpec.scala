package it.evadid.workbook.interaction.sync.destination

import it.evadid.core.util.io.ConstructorLikeParserWithJsonElements.ConstructorLikeReadResult
import it.evadid.core.util.io.Serializer
import it.evadid.util.logging.BasicLogger
import it.evadid.util.logging.derived.SyncLogger
import munit.FunSuite
import scala.concurrent.{ExecutionContext, Future}

class SyncDestinationRawSpec extends FunSuite {
  test("a typed cache write serializes its value exactly once") {
    var serialized = 0
    var stored = Option.empty[(String, String)]
    val codec = new Serializer[String] {
      def serialize(value: String): String = { serialized += 1; value }
      def deserialize(value: String): String = value
    }
    val raw = new SyncDestinationRaw {
      def readAllRaw(): Future[Map[String, String]] = Future.successful(Map.empty)
      def storeToRaw(key: String, value: String): Future[Boolean] = {
        stored = Some(key -> value)
        Future.successful(true)
      }
      def deserializeFromConstructorLikeString(value: ConstructorLikeReadResult): Option[SyncDestination] = None
      def serializeToConstructorLikeString(): ConstructorLikeReadResult = ConstructorLikeReadResult("Test", Nil)
    }
    raw.getSyncDestinationForType(SyncLogger(BasicLogger()), "cache", codec).storeElement("data")
    assertEquals(serialized, 1)
    assertEquals(stored, Some("cache" -> "data"))
  }
}
