package it.evadid.homepage.webElements.editor.code.worker

import com.raquo.airstream.state.Var

/** The result for the latest key only: an editor needs the analysis of its current text, not of earlier ones. */
final class LatestResult[K, V] {
  private val latest = Var(Option.empty[(K, V)])
  def get(key: K): Option[V] = latest.now().filter(_._1 == key).map(_._2)
  def put(key: K, value: V): Unit = latest.set(Some(key -> value))
}
