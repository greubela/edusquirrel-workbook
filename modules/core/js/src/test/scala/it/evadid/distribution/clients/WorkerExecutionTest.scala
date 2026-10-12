package it.evadid.distribution.clients

import it.evadid.distribution.command.{ExecutionCommand, SerializedException}
import it.evadid.distribution.commandTypes.WorkerCommands.*
import it.evadid.distribution.formats.{ExecutionClientResponse, WorkerExecutionProtocol}
import it.evadid.distribution.formats.ExecutionClientResponse.ExecutionClientResponseTimestamps
import it.evadid.util.logging.Logger
import it.evadid.util.logging.derived.PrintToStdLogger
import munit.FunSuite
import upickle.default.*
import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable.ListBuffer

class WorkerExecutionTest extends FunSuite {
  private val logger = Logger.withNameAndPrefixes(Some("worker-test"), PrintToStdLogger.printWarnAndError)
  private class Fake extends WebWorkerExecutionClient.Transport {
    var receive: Any => Unit = _ => ()
    var fail: Throwable => Unit = _ => ()
    var terminated = false
    val sent = ListBuffer.empty[String]
    override def setHandlers(message: Any => Unit, failure: Throwable => Unit): Unit = { receive = message; fail = failure }
    override def postMessage(message: String): Unit = sent += message
    override def terminate(): Unit = terminated = true
    def reply(index: Int, value: String): Unit = {
      val request = read[WorkerExecutionProtocol.Request](sent(index))
      val now = LocalDateTime.now()
      receive(write(WorkerExecutionProtocol.Response(request.requestId,
        ExecutionClientResponse.forValue(ExecutionClientResponseTimestamps(now,now,now),value,request.command))))
    }
  }
  private def client(fake: Fake, timeout: Double = 1000) = new WebWorkerExecutionClient(fake, Set("test"), timeout)
  private val command = ExecutionCommand("test",ujson.Obj("text" -> "Grüße\n\"外\""))

  test("typed framework calls correlate concurrent responses even when returned in reverse order") {
    val fake = new Fake; val worker = client(fake)
    val first = worker.handleCommand[String](logger, command)
    val second = worker.handleCommand[String](logger, command)
    fake.reply(1,"second"); fake.reply(0,"first"); fake.reply(0,"duplicate")
    first.zip(second).map { (a,b) =>
      assertEquals(a.asInstanceOf[it.evadid.distribution.command.ExecutionInfo.ExecutionSuccess[String]].result,"first")
      assertEquals(b.asInstanceOf[it.evadid.distribution.command.ExecutionInfo.ExecutionSuccess[String]].result,"second")
      worker.close()
    }
  }
  test("worker crashes fail all pending and future requests") {
    val fake = new Fake; val worker = client(fake)
    val a = worker.executeResponse(logger,command); val b = worker.executeResponse(logger,command)
    fake.fail(SerializedException("crashed"))
    assert(!worker.canExecuteCommand(command)); assert(fake.terminated)
    intercept[SerializedException](worker.executeResponse(logger,command).value.get.get)
    a.failed.zip(b.failed).map((x,y) => { assertEquals(x.getMessage,"crashed"); assertEquals(y.getMessage,"crashed") })
  }
  test("malformed responses and explicit close do not leave unresolved promises") {
    val fake = new Fake; val worker = client(fake)
    val a = worker.executeResponse(logger,command)
    fake.receive("not-json")
    a.failed.map { _ => assert(fake.terminated) }
  }
  test("timeout terminates a stalled worker and fails concurrent requests") {
    val fake = new Fake; val worker = client(fake, 5)
    val a = worker.executeResponse(logger,command); val b = worker.executeResponse(logger,command)
    a.failed.zip(b.failed).map { (_,_) => assert(fake.terminated); assert(!worker.canExecuteCommand(command)) }
  }
  test("unsupported commands are rejected before being posted") {
    val fake = new Fake; val worker = client(fake)
    val other = ExecutionCommand("http-only",ujson.Obj())
    assert(!worker.canExecuteCommand(other))
    worker.executeResponse(logger,other).failed.map { _ => assertEquals(fake.sent.size,0); worker.close() }
  }
  test("typed factories work with the worker client") {
    val fake = new Fake; val worker = client(fake)
    val factory = it.evadid.distribution.command.ExecutionCommandFactory[String,String]("test")
    val reply = factory.sendCommandTo(worker,"外\nGrüße",Some(logger))
    assertEquals(read[WorkerExecutionProtocol.Request](fake.sent.head).command.json.str,"外\nGrüße")
    fake.reply(0,"factory result")
    reply.map(value => { assertEquals(value.result,"factory result"); worker.close() })
  }
  test("closing fails in-flight requests and prevents reuse") {
    val fake = new Fake; val worker = client(fake)
    val reply = worker.executeResponse(logger,command)
    worker.close(); worker.close()
    reply.failed.map { _ => assert(fake.terminated); assert(!worker.canExecuteCommand(command)) }
  }
  test("a mismatched response command fails all outstanding requests") {
    val fake = new Fake; val worker = client(fake)
    val reply = worker.executeResponse(logger,command)
    val now = LocalDateTime.now()
    fake.receive(write(WorkerExecutionProtocol.Response("1",ExecutionClientResponse.forValue(
      ExecutionClientResponseTimestamps(now,now,now),"wrong",ExecutionCommand("other",ujson.Obj())))))
    reply.failed.map { error => assert(error.getMessage.contains("does not match")); assert(fake.terminated) }
  }
  test("a worker failure can use the next explicitly configured pool client") {
    val fake = new Fake; val worker = client(fake)
    val local = it.evadid.distribution.command.ExecutionCommandFactory[ujson.Value,String]("test")
      .toLocalExecutionClient((_,_) => scala.concurrent.Future.successful("fallback"))
    val reply = worker.pooledWith(local).handleCommand[String](logger,command)
    fake.fail(SerializedException("worker unavailable"))
    reply.map(value => assertEquals(value.resultOp,Some("fallback")))
  }

}
