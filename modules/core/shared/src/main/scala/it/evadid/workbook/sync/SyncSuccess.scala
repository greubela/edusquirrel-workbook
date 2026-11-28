package it.evadid.workbook.sync

import it.evadid.core.util.UtcTime

import java.time.LocalDateTime

import upickle.default.*
import it.evadid.core.util.io.serializer.DefaultSerializer.given

/** Committed outcome of a write, naming the affected elements: [[it.evadid.workbook.history.events.EventId]]s
  * for history stores, [[SyncContext]]s for history removal and raw keys for key-value stores.
  * A write acknowledgment is evidence of storage: every element in [[elementsStored]] was present when the write committed.
  */
case class SyncSuccess[T](elementsAdded: Set[T], elementsChanged: Set[T], elementsUnchanged: Set[T], elementsRemoved: Set[T],
    timestampCommitted: LocalDateTime) derives ReadWriter {
  def elementsStored: Set[T] = elementsAdded ++ elementsChanged ++ elementsUnchanged

  def combine(other: SyncSuccess[T]): SyncSuccess[T] = SyncSuccess(elementsAdded ++ other.elementsAdded,
    elementsChanged ++ other.elementsChanged, elementsUnchanged ++ other.elementsUnchanged, elementsRemoved ++ other.elementsRemoved,
    if timestampCommitted.isBefore(other.timestampCommitted) then other.timestampCommitted else timestampCommitted)

  def map[U](f: T => U): SyncSuccess[U] =
    SyncSuccess(elementsAdded.map(f), elementsChanged.map(f), elementsUnchanged.map(f), elementsRemoved.map(f), timestampCommitted)

  /** Keeps the elements `f` names; for example the history keys among the raw keys of a cleared store. */
  def collect[U](f: PartialFunction[T, U]): SyncSuccess[U] = SyncSuccess(elementsAdded.collect(f), elementsChanged.collect(f),
    elementsUnchanged.collect(f), elementsRemoved.collect(f), timestampCommitted)

  /** The part of a shared (for example buffered) write that concerns the given elements. */
  def restrictedTo(elements: Set[T]): SyncSuccess[T] = SyncSuccess(elementsAdded.intersect(elements),
    elementsChanged.intersect(elements), elementsUnchanged.intersect(elements), elementsRemoved.intersect(elements), timestampCommitted)
}

object SyncSuccess {
  def emptyNow[T](): SyncSuccess[T] = SyncSuccess(Set.empty, Set.empty, Set.empty, Set.empty, UtcTime.now())

  def removed[T](elements: Set[T]): SyncSuccess[T] = emptyNow[T]().copy(elementsRemoved = elements)

  /** Classifies a written element by its previous and new value. */
  def written[T, V](element: T, previous: Option[V], value: V): SyncSuccess[T] = previous match {
    case None => emptyNow[T]().copy(elementsAdded = Set(element))
    case Some(old) if old == value => emptyNow[T]().copy(elementsUnchanged = Set(element))
    case Some(_) => emptyNow[T]().copy(elementsChanged = Set(element))
  }

  /** Immutable elements are either new or already present; `stored` excludes elements the destination dropped. */
  def immutable[T](sent: Set[T], previous: Set[T], stored: Set[T]): SyncSuccess[T] = {
    val kept = sent.intersect(stored)
    emptyNow[T]().copy(elementsAdded = kept -- previous, elementsUnchanged = kept.intersect(previous))
  }
}
