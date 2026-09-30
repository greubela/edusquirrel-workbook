package it.evadid.homepage.webElements.editor.code.SnapEditor.execution

import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.TurtleCommand
import it.evadid.homepage.webElements.editor.code.SnapEditor.SnapProgramDerivation
import it.evadid.homepage.workbook.legacy.interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideBackends.{CallbackOp, PythonRunConfig, PythonRunReport}
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingExerciseState
import it.evadid.workbook.elements.interactionElements.programming.SnapTurtleCatalog
import todomove.`export`.workers.PyodideWorkerClient

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

/** Asynchronous boundary used by the editor to execute its derived Python. */
trait TurtleCommandRunner:
  def execute(python: String): Future[List[TurtleCommand[Double]]]

/** Derives and executes a Snap project without modifying editor state. */
final class SnapTurtleCommandExecution(runner: TurtleCommandRunner):
  def commandsFor(state: ProgrammingExerciseState): Future[List[TurtleCommand[Double]]] =
    val derived = SnapProgramDerivation.fromState(state)
    if !derived.pythonCompatible then
      Future.failed(IllegalArgumentException(
        derived.applyBlockedMessage.getOrElse("The Snap project cannot be converted to Python")
      ))
    else runner.execute(derived.python)

/** Small seam around the existing Pyodide worker, allowing orchestration tests
  * to supply an already executed callback report.
  */
trait PythonCallbackExecutor:
  def addCallbacks(moduleName: String, methodNames: Seq[String]): Future[Unit]
  def run(code: String, config: PythonRunConfig): Future[PythonRunReport]

private final class WorkerPythonCallbackExecutor(worker: PyodideWorkerClient) extends PythonCallbackExecutor:
  override def addCallbacks(moduleName: String, methodNames: Seq[String]): Future[Unit] =
    worker.addCallbacks(moduleName, methodNames)

  override def run(code: String, config: PythonRunConfig): Future[PythonRunReport] =
    worker.run(code, config)

/** Executes derived Snap Python in the integrated Pyodide Web Worker. The
  * returned callbacks therefore describe runtime calls (including loop and
  * function expansion), rather than statically present blocks.
  */
final class PyodideTurtleCommandRunner(
    executor: PythonCallbackExecutor = PyodideTurtleCommandRunner.workerExecutor()
) extends TurtleCommandRunner:

  override def execute(python: String): Future[List[TurtleCommand[Double]]] =
    executor
      .addCallbacks(PyodideTurtleCommandRunner.ModuleName, SnapTurtleCatalog.AllowedPythonNames.toSeq.sorted)
      .flatMap { _ =>
        executor.run(
          s"from ${PyodideTurtleCommandRunner.ModuleName} import *\n$python",
          PythonRunConfig(resetGlobals = true)
        )
      }
      .map(report => PyodideTurtleCommandRunner.commandsFrom(report.callbackOps))

object PyodideTurtleCommandRunner:
  private[execution] val ModuleName = "turtle"

  private def workerExecutor(): PythonCallbackExecutor =
    val configured = js.Dynamic.global.selectDynamic("PYODIDE_WORKER_URL")
    val workerUrl =
      if !js.isUndefined(configured) && configured != null then configured.asInstanceOf[String]
      else "./js/pyodide-worker.js"
    new WorkerPythonCallbackExecutor(new PyodideWorkerClient(workerUrl))

  private[execution] def commandsFrom(callbacks: Seq[CallbackOp]): List[TurtleCommand[Double]] =
    callbacks.iterator
      .filter(_.module == ModuleName)
      .map(toCommand)
      .toList

  private def toCommand(callback: CallbackOp): TurtleCommand[Double] =
    val canonicalName = SnapTurtleCatalog.turtleCommandByPythonName.getOrElse(
      callback.method,
      throw IllegalArgumentException(s"Unsupported turtle callback: ${callback.method}")
    )
    val (numeric, textual) = callback.args.foldLeft((List.empty[Double], List.empty[String])) {
      case ((numbers, strings), value) if js.typeOf(value) == "number" =>
        val number = value.asInstanceOf[Double]
        if !number.isFinite then
          throw IllegalArgumentException(s"Non-finite argument for ${callback.method}: $number")
        (numbers :+ number, strings)
      case ((numbers, strings), value) if js.typeOf(value) == "string" =>
        (numbers, strings :+ value.asInstanceOf[String])
      case ((numbers, strings), value) if js.typeOf(value) == "boolean" =>
        (numbers, strings :+ value.asInstanceOf[Boolean].toString)
      case (_, value) =>
        throw IllegalArgumentException(
          s"Unsupported argument for ${callback.method}: ${js.typeOf(value)}"
        )
    }
    TurtleCommand(canonicalName, numeric, textual)
