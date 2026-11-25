package it.evadid.homepage.webElements.editor.code.SnapEditor

import com.raquo.airstream.ownership.Owner
import com.raquo.airstream.state.Var
import it.evadid.core.datastructures.language.AppLanguage.SnapLanguage
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingStateRaw, ProgrammingStateSnapScalaModel}

/** Binds a native Snap project to the answer: edits in Snap are written into `project`, and other values of `project`
  * are opened in Snap.
  *
  * Snap has no change events and serializes a project in its own normalized form. The binding therefore keeps what
  * the native editor shows ([[SnapProjectBinding.Shown]]): an answer equal to `shown.answer` is already open, and only
  * a serialization different from `shown.native` is an edit. Without it, every opened answer would be written back
  * in Snap's spelling.
  */
private[SnapEditor] final class SnapProjectBinding(
    project: Var[Option[ProgrammingStateSnapScalaModel]],
    native: SnapProjectBinding.Native
) {
  import SnapProjectBinding.*

  private val shown = Var(Option.empty[Shown])
  private val lastCheckAt = Var(Double.NegativeInfinity)

  /** Records `answer` as open in Snap, after Snap opened it. */
  def opened(answer: ProgrammingStateSnapScalaModel): Unit =
    shown.set(native.serialized().map(xml => Shown(answer, typed(xml))))

  /** Snap re-serialized the open project (e.g. after a language change); that is not an edit. */
  def reserialized(): Unit =
    shown.update(current => for { value <- current; xml <- native.serialized() } yield value.copy(native = typed(xml)))

  /** Opens answers that reach `project` from elsewhere (restores, other editors). */
  def observe(owner: Owner): Unit =
    project.signal.changes.collect { case Some(answer) => answer }
      .distinctByFn((previous, next) => previous.meaningfullyEquals(next))
      .foreach(open)(using owner)

  private def open(answer: ProgrammingStateSnapScalaModel): Unit =
    if !shown.now().exists(_.answer.meaningfullyEquals(answer)) && native.open(answer.toSnapXml) then opened(answer)

  /** Writes Snap's current project into `project` when it differs from what was shown and from the answer itself
    * (Snap may re-serialize an unchanged answer differently, e.g. around a language change).
    */
  def publish(): Unit =
    native.serialized().map(typed).filterNot(live => shown.now().exists(_.native.meaningfullyEquals(live))).foreach { live =>
      shown.set(Some(Shown(live, live)))
      if !project.now().exists(_.meaningfullyEquals(live)) then project.set(Some(live))
    }

  /** [[publish]], at most every [[checkIntervalMs]]: serializing a project is too heavy for every frame. */
  def publishIfDue(now: Double): Unit =
    if now - lastCheckAt.now() >= checkIntervalMs then {
      lastCheckAt.set(now)
      publish()
    }
}

private[SnapEditor] object SnapProjectBinding {
  val checkIntervalMs = 500.0

  /** The native editor's project operations. */
  trait Native {
    /** The open project's XML, once an editor exists. */
    def serialized(): Option[String]
    /** Opens `xml`; false when Snap cannot replace its project now (e.g. while a slot is being edited). */
    def open(xml: ProgrammingStateRaw): Boolean
  }

  /** The answer the native editor shows, and Snap's own serialization of it. */
  final case class Shown(answer: ProgrammingStateSnapScalaModel, native: ProgrammingStateSnapScalaModel)

  /** Snap XML without regenerated images, as the answer stores it. */
  def typed(xml: String): ProgrammingStateSnapScalaModel =
    ProgrammingStateRaw(SnapLanguage, xml).removeBloatFromXml.toSnapScalaModel.fold(failure => throw failure.unparsable(), _.persistent)
}
