package it.evadid.workbook.interaction.sync.destination

import it.evadid.core.datastructures.state.State
import it.evadid.core.util.io.Serializer
import it.evadid.distribution.command.SerializedException
import it.evadid.util.logging.BasicLogger
import it.evadid.util.logging.derived.SyncLogger
import it.evadid.workbook.interaction.sync.SyncSuccess
import munit.FunSuite

import scala.concurrent.{ExecutionContext, Future}

class SyncDestinationKeyedAccessSpec extends FunSuite {
  private given ExecutionContext = ExecutionContext.global
  private val integer = Serializer.fromUpickleJson(upickle.default.readwriter[Int])
  private val positive = new Serializer[Int] {
    def serialize(value: Int): String = { require(value > 0, "not positive"); value.toString }
    def deserialize(value: String): Int = value.toInt
  }

  private final class Memory(initial: Map[String, String]) extends SyncDestinationHandlerBasic {
    val records = State(initial)
    override val syncLogger = SyncLogger(BasicLogger())
    override val underlyingDefinition = SyncDestinationDefinition.LocalStorageSyncDefinition(10000)
    override def readAll(implicit ec: ExecutionContext): Future[Map[String, String]] = Future.successful(records.now())
    override def updateElement(key: String, update: Option[String] => String)(using ec: ExecutionContext): Future[SyncSuccess] =
      storeElement(key, update(records.now().get(key)))
    override def readElement(key: String)(implicit ec: ExecutionContext): Future[String] = Future.fromTry(scala.util.Try(records.now()(key)))
    override def storeElement(key: String, value: String)(implicit ec: ExecutionContext): Future[SyncSuccess] = storeBulk(Map(key -> value))
    override def storeBulk(values: Map[String, String])(implicit ec: ExecutionContext): Future[SyncSuccess] = {
      records.update(_ ++ values)
      Future.successful(SyncSuccess.emptyNow())
    }
    override def clearAll()(implicit ec: ExecutionContext): Future[SyncSuccess] = { records.set(Map.empty); Future.successful(SyncSuccess.emptyNow()) }
    override def clearValue(key: String)(implicit ec: ExecutionContext): Future[SyncSuccess] = { records.update(_ - key); Future.successful(SyncSuccess.emptyNow()) }
  }

  test("key-bound access stores, reads and clears one typed record") {
    val memory = new Memory(Map.empty)
    val typed = memory.forSpecificType(integer, integer)
    val slot = typed.forSpecificKey(7)
    assertEquals(slot.forKey, 7)
    for {
      _ <- slot.storeValue(42)
      read <- slot.readValue
      stored = memory.records.now()
      _ <- typed.clearValue(7)
    } yield {
      assertEquals(read, 42)
      assertEquals(stored, Map("7" -> "42"))
      assertEquals(memory.records.now(), Map.empty[String, String])
    }
  }

  test("serialization failures of keys or values and unreadable values fail the typed request") {
    val memory = new Memory(Map("1" -> "not a number"))
    val typed = memory.forSpecificType(positive, integer)
    for {
      badKeyStore <- typed.storeElementTyped(-1, 1).failed
      badKeyRead <- typed.readElementTyped(-1).failed
      badKeyClear <- typed.clearValue(-1).failed
      badValue <- typed.readElementTyped(1).failed
      nothing <- memory.forSpecificType(integer, positive).storeAll(Map(1 -> -5)).failed
      empty <- typed.storeAll(Map.empty)
    } yield {
      assert(badKeyStore.getMessage.contains("not positive") || badKeyStore.isInstanceOf[SerializedException], badKeyStore)
      assert(badKeyRead.getMessage.contains("key serialisation failed"), badKeyRead.getMessage)
      assert(badKeyClear.getMessage.contains("Could not remove element"), badKeyClear.getMessage)
      assert(badValue.getMessage.contains("Could not deserialize successfully read value"), badValue.getMessage)
      assert(nothing.getMessage.contains("Could not serialize a single element"), nothing.getMessage)
      assertEquals((empty.elementsAdded, empty.elementsChanged, empty.elementsRemoved), (0, 0, 0))
    }
  }
}
