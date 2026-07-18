package todomove.`export`.workers

import org.scalajs.dom
import todomove.`export`.traits.AbstractWorkerClient

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

final class MathWorkerClient()
    extends AbstractWorkerClient(
      bootstrapUrl = "./js/worker-bootstrap.js",
      moduleUrl = "../../target/scala-3.3.3/workbookapp-fastopt/main.js",
      exportedName = "startMathWorkerServer",
      workerOptions = Some(js.Dynamic.literal(`type` = "module").asInstanceOf[dom.WorkerOptions]),
      paramsForInit = Map(),
      canvasForInit = None
    ) {

  def add(a: Int, b: Int): Future[Int] =
    execute("add", a, b)

  def multiply(a: Int, b: Int): Future[Int] =
    execute("multiply", a, b)

  private def execute(name: String, a: Int, b: Int): Future[Int] =
    enqueue(name, Map("a" -> a.toString, "b" -> b.toString))
      .map(_.data.get("value").flatMap(_.toIntOption).getOrElse(0))

}
