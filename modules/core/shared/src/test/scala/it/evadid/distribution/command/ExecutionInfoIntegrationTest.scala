package it.evadid.distribution.command

import it.evadid.distribution.clients.{ExecutionClient, LocalExecutionClient, RoutedExecutionClient}
import it.evadid.distribution.commandTypes.WorkerCommands
import it.evadid.util.logging.{BasicLogger, Logger}
import upickle.default.*
import munit.FunSuite

import java.time.LocalDateTime
import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global

class ExecutionInfoIntegrationTest extends FunSuite {

  test("fixTime adds explicit timeline when no meta exists") {
    val requested = LocalDateTime.parse("2026-01-01T08:00:00")
    val received = LocalDateTime.parse("2026-01-01T08:00:01")
    val history = ExecutionHistory(requested.plusSeconds(10), received.plusSeconds(10), requested.plusSeconds(11), received.plusSeconds(11))
    val info = ExecutionInfo.ExecutionSuccess(ExecutionCommand("noop", ujson.Obj()), history, Map.empty[String, String])

    val fixed = info.withFixedTime(requested, received)

    assertEquals(fixed.successOrThrow().history.timestampCommandRequested, requested)
    assertEquals(fixed.successOrThrow().history.timestampCommandReceived, received)
  }

  private def client(result: () => Future[String]): ExecutionClient = new LocalExecutionClient[ujson.Value, String] {
    override def canExecuteCommand(command: ExecutionCommand): Boolean = true
    override protected def rawExecution(logger: Logger, input: ujson.Value): Future[String] = result()
  }

  test("routing is lazy and obtains the current backend even when the worker is unavailable") {
    val selected = Promise[Unit]()
    val credentialsChanged = Promise[Unit]()
    val first = client(() => Future.successful("first credentials"))
    val second = client(() => Future.successful("current credentials"))
    val router = RoutedExecutionClient { command =>
      selected.trySuccess(())
      if WorkerCommands.supportedCommands.contains(command.name) then throw new Exception("worker unavailable")
      else if credentialsChanged.isCompleted then second else first
    }
    val command = ExecutionCommand("feedback", ujson.Obj())
    assert(!selected.isCompleted)
    assert(!router.canExecuteCommand(ExecutionCommand(WorkerCommands.health.name, ujson.Obj())))
    assert(router.canExecuteCommand(command))
    assertEquals(router.allUnderlyingClients, List(router))
    assertEquals(router.pooledWith(first).allUnderlyingClients, List(router, first))
    router.handleCommand[String](BasicLogger(), command).flatMap { reply =>
      assertEquals(reply.successOrThrow().result, "first credentials")
      credentialsChanged.success(())
      router.handleCommand[String](BasicLogger(), command).map { reply =>
        assertEquals(reply.successOrThrow().result, "current credentials")
      }
    }
  }

  test("worker failures never send the command to the backend") {
    Future.traverse(List("future", "synchronous", "unavailable")) { mode =>
      val backendCalled = Promise[Unit]()
      val backend = client(() => {
        backendCalled.trySuccess(())
        Future.successful("wrong destination")
      })
      val worker = client(() => {
        if mode == "synchronous" then throw new Exception("worker failed")
        else Future.failed(new Exception("worker failed"))
      })
      val router = RoutedExecutionClient { command =>
        if WorkerCommands.supportedCommands.contains(command.name) then {
          if mode == "unavailable" then throw new Exception("worker unavailable")
          worker
        } else backend
      }
      val command = ExecutionCommand(WorkerCommands.javaExecutionEvidence.name, ujson.Obj("source" -> "private source"))
      assertEquals(router.canExecuteCommand(command), mode != "unavailable")
      router.handleCommand[String](BasicLogger(), command).map(_.successOrThrow().result).failed.map { error =>
        assert(error.getMessage.contains(if mode == "unavailable" then "worker unavailable" else "worker failed"))
        assert(!backendCalled.isCompleted)
      }
    }.map(_ => ())
  }

  test("worker commands use only their selected worker") {
    val backendCalled = Promise[Unit]()
    val worker = client(() => Future.successful("worker result"))
    val backend = client(() => {
      backendCalled.trySuccess(())
      Future.successful("backend result")
    })
    val router = RoutedExecutionClient(command =>
      if WorkerCommands.supportedCommands.contains(command.name) then worker else backend)
    Future.traverse(List(WorkerCommands.javaExecutionEvidence.name, WorkerCommands.runPython.name)) { name =>
      val command = ExecutionCommand(name, ujson.Obj())
      router.handleCommand[String](BasicLogger(), command).map { reply =>
        assertEquals(reply.successOrThrow().result, "worker result")
        assert(!backendCalled.isCompleted)
      }
    }.map(_ => ())
  }
}
