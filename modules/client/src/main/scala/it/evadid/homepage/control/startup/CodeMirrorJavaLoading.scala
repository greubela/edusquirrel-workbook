package it.evadid.homepage.control.startup

import com.raquo.airstream.state.Var
import org.scalajs.dom
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits.*

/** Share the optional native ESM import without putting readiness state in the JS bridge. */
object CodeMirrorJavaLoading {
  private val pending = Var(Option.empty[Future[js.Dynamic]])

  def preheat(): Unit = { load().failed.foreach(_ => ()); () }

  def load(): Future[js.Dynamic] = Future.unit.flatMap { _ =>
    pending.now().getOrElse {
      val result = CodeMirrorEditorLoading.load().flatMap { _ =>
        val libraries = dom.window.asInstanceOf[js.Dynamic].selectDynamic("EduSquirrelCodeMirrorLibraries")
        libraries.loadJavaModule().asInstanceOf[js.Promise[js.Dynamic]].toFuture
      }
      pending.set(Some(result))
      result.failed.foreach { _ => if pending.now().contains(result) then pending.set(None) }
      result
    }
  }
}
