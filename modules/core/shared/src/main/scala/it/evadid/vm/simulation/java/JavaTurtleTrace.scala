package it.evadid.vm.simulation.java

import it.evadid.core.datastructures.state.State
import it.evadid.vm.parsing.java.turtle.{JavaTurtleResolution as R}
import it.evadid.vm.simulation.java.{JavaTurtleEvaluation as E, JavaTurtleRuntime as T}
import scala.annotation.tailrec

object JavaTurtleTrace {
  // Structural paths belong to one compiled source; they are not source positions.
  case class SourceNode(method: R.MethodId, path: Vector[String]) {
    def child(role: String): SourceNode = copy(path = path :+ role)
  }

  enum Kind {
    case Call(method: R.MethodId, arguments: Vector[E.Value])
    case Return(explicit: Boolean)
    case Read(variable: R.Variable, value: E.Value)
    case Write(variable: R.Variable, value: E.Value)
    case Expression(value: E.Value)
    case Condition(value: Boolean)
    case Turtle(index: Int, command: T.Command)
  }

  case class Event(id: Int, invocation: Int, node: SourceNode, kind: Kind,
      dataDependencies: Vector[Int], controlDependencies: Vector[Int],
      callDependencies: Vector[Int], orderDependencies: Vector[Int]) {
    def dependencies(includeOrder: Boolean): Vector[Int] =
      (dataDependencies ++ controlDependencies ++ callDependencies ++
        (if includeOrder then orderDependencies else Vector.empty)).distinct
  }

  case class Slice(events: Vector[Event], recordingTruncated: Boolean) {
    def sourceNodes: Vector[SourceNode] = events.map(_.node).distinct
  }

  case class Capture(events: Vector[Event], truncated: Boolean) {
    /** Observed data/call dependencies and conservative prior-condition dependencies, not a minimal slice. */
    def backwardSlice(target: Int, includeOrder: Boolean = false): Option[Slice] = {
      val indexed = events.map(event => event.id -> event).toMap
      @tailrec def collect(pending: List[Int], selected: Set[Int]): Option[Set[Int]] = pending match {
        case Nil => Some(selected)
        case id :: rest if selected(id) => collect(rest, selected)
        case id :: rest => indexed.get(id) match {
          case None => None
          case Some(event) => collect(event.dependencies(includeOrder).toList ::: rest, selected + id)
        }
      }
      collect(List(target), Set.empty).map(ids => Slice(events.filter(event => ids(event.id)), truncated))
    }
  }

  case class RecordedExecution(execution: T.Execution, trace: Capture)

  case class Budget(maxEvents: Int = 4096, maxDependencies: Int = 32768) {
    require(maxEvents >= 0 && maxEvents <= 10000, "Invalid trace event budget.")
    require(maxDependencies >= 0 && maxDependencies <= 100000, "Invalid trace dependency budget.")
  }

  private case class Recording(events: Vector[Event] = Vector.empty, dependencies: Int = 0,
      truncated: Boolean = false, nextInvocation: Int = 0, lastTurtle: Option[Int] = None)

  private[java] final class Recorder(budget: Budget) {
    private val state = State(Recording())

    def isRecording: Boolean = !state.now().truncated

    def capture: Capture = { val current = state.now(); Capture(current.events, current.truncated) }

    def invocation(): Int = {
      val current = state.now()
      state.set(current.copy(nextInvocation = current.nextInvocation + 1))
      current.nextInvocation
    }

    def lastTurtle: Option[Int] = state.now().lastTurtle

    def record(invocation: Int, node: SourceNode, kind: Kind, data: Vector[Int] = Vector.empty,
        controls: Vector[Int] = Vector.empty, calls: Vector[Int] = Vector.empty,
        order: Vector[Int] = Vector.empty): Option[Int] = {
      val current = state.now()
      if current.truncated then None
      else {
        val event = Event(current.events.size, invocation, node, kind,
          data.distinct, controls.distinct, calls.distinct, order.distinct)
        val added = event.dataDependencies.size + event.controlDependencies.size +
          event.callDependencies.size + event.orderDependencies.size
        if current.events.size >= budget.maxEvents || added > budget.maxDependencies - current.dependencies then {
          state.set(current.copy(truncated = true))
          None
        } else {
          state.set(current.copy(events = current.events :+ event, dependencies = current.dependencies + added,
            lastTurtle = kind match { case _: Kind.Turtle => Some(event.id); case _ => current.lastTurtle }))
          Some(event.id)
        }
      }
    }
  }
}
