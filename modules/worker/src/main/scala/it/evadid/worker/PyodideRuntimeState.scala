package it.evadid.worker

import it.evadid.distribution.commandTypes.WorkerCommands.PythonCallback
import it.evadid.facades.PyodideRuntime
import it.evadid.worker.PyodideRuntimeState.*
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.util.{Failure, Success, Try}

/** The worker's Pyodide lifecycle and its job chain; every transition returns a new value. */
private[worker] final case class PyodideRuntimeState(interpreter: Interpreter, queue: Future[Unit]) {

  /** Runs `job` after every earlier job has settled, whatever its outcome. */
  def enqueue[A](job: () => Future[A])(using ExecutionContext): (PyodideRuntimeState, Future[A]) = {
    val result = queue.flatMap(_ => job())
    (copy(queue = result.transform(_ => Success(()))), result)
  }

  def startLoading(runtime: Future[PyodideRuntime]): PyodideRuntimeState = interpreter match {
    case Interpreter.NotStarted => copy(interpreter = Interpreter.Loading(runtime))
    case other => illegal("start loading", other)
  }

  /** A failed load returns to `NotStarted` so the next job retries. */
  def finishLoading(result: Try[PyodideRuntime]): PyodideRuntimeState = (interpreter, result) match {
    case (Interpreter.Loading(_), Success(runtime)) => copy(interpreter = Interpreter.Ready(runtime, Session.empty))
    case (Interpreter.Loading(_), Failure(_)) => copy(interpreter = Interpreter.NotStarted)
    case (other, _) => illegal("finish loading", other)
  }

  def withNamespace(namespace: js.Dynamic): PyodideRuntimeState = updateSession(_.copy(namespace = Some(namespace)))
  def withoutModules: PyodideRuntimeState = updateSession(_.copy(modules = Set.empty))
  def withModule(module: String): PyodideRuntimeState = updateSession(session => session.copy(modules = session.modules + module))

  def session: Session = interpreter match {
    case Interpreter.Ready(_, session) => session
    case other => illegal("read the session", other)
  }

  private def updateSession(change: Session => Session): PyodideRuntimeState = interpreter match {
    case Interpreter.Ready(runtime, session) => copy(interpreter = Interpreter.Ready(runtime, change(session)))
    case other => illegal("change the session", other)
  }

  private def illegal(action: String, state: Interpreter): Nothing =
    throw IllegalStateException(s"Cannot $action while the Python interpreter is $state")
}

private[worker] object PyodideRuntimeState {
  val initial: PyodideRuntimeState = PyodideRuntimeState(Interpreter.NotStarted, Future.successful(()))

  enum Interpreter {
    case NotStarted
    case Loading(runtime: Future[PyodideRuntime])
    case Ready(runtime: PyodideRuntime, session: Session)
  }

  /** Python globals and the callback modules registered for the latest job. */
  final case class Session(namespace: Option[js.Dynamic], modules: Set[String])
  object Session {
    val empty: Session = Session(None, Set.empty)
  }

  /** Output recorded by one Python job. */
  final case class JobOutput(stdout: Vector[String], stderr: Vector[String], callbacks: Vector[PythonCallback]) {
    def withStdout(text: String): JobOutput = copy(stdout = stdout :+ text)
    def withStderr(text: String): JobOutput = copy(stderr = stderr :+ text)
    def withCallback(callback: PythonCallback): JobOutput = copy(callbacks = callbacks :+ callback)
  }
  object JobOutput {
    val empty: JobOutput = JobOutput(Vector.empty, Vector.empty, Vector.empty)
  }
}
