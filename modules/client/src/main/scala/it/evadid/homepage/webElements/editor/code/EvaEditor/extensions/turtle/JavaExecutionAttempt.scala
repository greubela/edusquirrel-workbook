package it.evadid.homepage.webElements.editor.code.EvaEditor.extensions.turtle

import com.raquo.airstream.ownership.ManualOwner
import com.raquo.airstream.state.Var

private[turtle] final class JavaExecutionAttempt {
  val owner = new ManualOwner
  val invalidated = Var(false)
  def invalidate(): Unit = { invalidated.set(true); owner.killSubscriptions() }
}
