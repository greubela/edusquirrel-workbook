package it.evadid.vm.parsing.source

/** Identity of an in-memory syntax/tree node; structural equality must not merge repeated source occurrences. */
final class SourceIdentity[A <: AnyRef](val value: A) {
  override def hashCode(): Int = System.identityHashCode(value)
  override def equals(other: Any): Boolean = other match {
    case key: SourceIdentity[?] => value eq key.value
    case _ => false
  }
}
