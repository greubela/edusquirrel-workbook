package it.evadid.homepage.webElements.basic

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import org.scalajs.dom

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import DeferredRendering.Phase

class DeferredRenderingSpec extends EditorDomSuite {
  private given ExecutionContext = ExecutionContext.global
  private def text(id: LanguageMapContentId): Signal[String] = Val(id.entryKey)
  private def status(root: dom.Element): String = root.querySelector(".deferred-rendering__status").textContent
  private def drawn(root: dom.Element): String = Option(root.querySelector(".drawing")).fold("")(_.textContent)

  /** A view whose preparations are recorded, to show which states were actually drawn. */
  private final class Fixture(initial: String, delay: FiniteDuration) {
    val source = Var(initial)
    val prepared = Var(Vector.empty[String])
    val rendering = new DeferredRendering[String, String](source.signal, state => {
      prepared.update(_ :+ state)
      if state == "broken" then throw IllegalArgumentException("cannot draw") else scala.concurrent.Future.successful(state.toUpperCase)
    }, delay)
    val phases = Var(Vector.empty[DeferredRendering.Phase])
    val views = Var(0)
    val shown = Var(Vector.empty[String])
    /** Built once; it follows the results like a reused board. */
    def view(results: Signal[scala.util.Try[String]]): Element = {
      views.update(_ + 1)
      val text = results.map(_.fold(_.getMessage, identity))
      div(cls := "drawing", child.text <-- text, text --> (value => shown.update(_ :+ value)))
    }
    val element = div(rendering.element(text)(view), rendering.phase --> (phase => phases.update(_ :+ phase)))
  }

  test("the first state is drawn at once and rapid changes are drawn only after the source rests") {
    val fixture = Fixture("square", 150.millis)
    val close = mount(fixture.element)
    val root = fixture.element.ref
    (for {
      _ <- eventually(drawn(root) == "SQUARE", drawn(root))
      _ = assertEquals(fixture.prepared.now(), Vector("square"))
      _ = assert(root.querySelector(".deferred-rendering__status--hidden") != null)
      _ = fixture.source.set("s")
      _ = fixture.source.set("st")
      _ = fixture.source.set("star")
      _ = assertEquals(fixture.phases.now().last, Phase.Waiting)
      // The previous drawing stays visible, marked outdated, with the waiting status.
      _ = assertEquals((status(root), drawn(root)), (DeferredRendering.waitingText.entryKey, "SQUARE"))
      _ = assert(root.querySelector(".deferred-rendering--outdated") != null)
      _ = fixture.phases.set(Vector.empty)
      _ <- eventually(drawn(root) == "STAR", drawn(root))
      // Once the source rests the status changes to rendering before the preparation runs.
      _ = assertEquals(fixture.phases.now().distinct, Vector(Phase.Rendering, Phase.Ready))
      _ = assertEquals(fixture.prepared.now(), Vector("square", "star"), "intermediate states are never prepared")
      _ = assert(root.querySelector(".deferred-rendering__status--hidden") != null)
      _ = assert(root.querySelector(".deferred-rendering--outdated") == null)
    } yield ()).andThen(_ => close())
  }

  test("the view is built once; an equal result is not shown again, and a failure is shown by the same view") {
    val fixture = Fixture("line", 20.millis)
    val close = mount(fixture.element)
    val root = fixture.element.ref
    (for {
      _ <- eventually(drawn(root) == "LINE", drawn(root))
      drawing = root.querySelector(".drawing")
      shownBefore = fixture.shown.now()
      _ = fixture.source.set("Line")
      _ <- eventually(fixture.prepared.now() == Vector("line", "Line") && root.querySelector(".deferred-rendering__status--hidden") != null,
        fixture.prepared.now().toString)
      _ = assertEquals(fixture.shown.now(), shownBefore, "an equal result is not drawn again")
      _ = fixture.source.set("broken")
      _ <- eventually(drawn(root) == "cannot draw", drawn(root))
      _ = assert(root.querySelector(".deferred-rendering__status--hidden") != null, "a failure is a finished drawing")
      _ = assert(root.querySelector(".drawing") eq drawing)
      _ = assertEquals(fixture.views.now(), 1)
    } yield ()).andThen(_ => close())
  }

  test("views of one rendering share each preparation") {
    val fixture = Fixture("arc", 20.millis)
    val second = fixture.rendering.element(text)(fixture.view)
    val closeFirst = mount(fixture.element)
    val closeSecond = mount(second)
    (for {
      _ <- eventually(drawn(fixture.element.ref) == "ARC" && drawn(second.ref) == "ARC")
      _ = fixture.source.set("circle")
      _ <- eventually(drawn(fixture.element.ref) == "CIRCLE" && drawn(second.ref) == "CIRCLE")
      _ = assertEquals(fixture.prepared.now(), Vector("arc", "circle"))
    } yield ()).andThen { _ => closeFirst(); closeSecond() }
  }
}
