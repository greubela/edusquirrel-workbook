package it.evadid.facades

import com.raquo.airstream.state.Var
import org.scalajs.dom
import scala.concurrent.Future

/** Models the browser's script loader: every inserted script either runs and fires `load`, fails with `error`, or
  * stays pending. jsdom itself never fetches scripts.
  */
private[facades] final class BrowserScripts(respond: String => BrowserScripts.Outcome) {
  import BrowserScripts.*
  private val inserted = Var(Vector.empty[String])
  private val waiting = Var(Map.empty[String, dom.html.Script])

  private val observer = new dom.MutationObserver((records, _) => records.foreach(_.addedNodes.foreach {
    case node if node.nodeName == "SCRIPT" =>
      val script = node.asInstanceOf[dom.html.Script]
      inserted.update(_ :+ script.src)
      respond(script.src) match {
        case Load(run) => run(); script.dispatchEvent(new dom.Event("load"))
        case Fail => script.dispatchEvent(new dom.Event("error"))
        case Pending => waiting.update(_ + (script.src -> script))
      }
    case _ => ()
  }))
  observer.observe(dom.document.head, new dom.MutationObserverInit { childList = true })

  /** Script URLs in insertion order. */
  def scripts: Vector[String] = inserted.now()

  def complete(src: String): Unit = waiting.now().get(src).foreach { script =>
    waiting.update(_ - src)
    script.dispatchEvent(new dom.Event("load"))
  }

  def close(): Unit = observer.disconnect()
}

private[facades] object BrowserScripts {
  sealed trait Outcome
  final case class Load(run: () => Unit = () => ()) extends Outcome
  case object Fail extends Outcome
  case object Pending extends Outcome

  def using[A](respond: String => Outcome)(body: BrowserScripts => Future[A]): Future[A] = {
    val scripts = new BrowserScripts(respond)
    val result = try body(scripts) catch { case error: Throwable => scripts.close(); throw error }
    result.andThen(_ => scripts.close())(using scala.scalajs.concurrent.JSExecutionContext.queue)
  }
}
