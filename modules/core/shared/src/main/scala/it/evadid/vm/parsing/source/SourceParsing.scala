package it.evadid.vm.parsing.source

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import java.util.concurrent.atomic.AtomicReference
import scala.collection.immutable.VectorMap

/** One textual parsing boundary for Java, Python and Snap. Each returned source owns lazy lexical, syntax
  * and conversion stages; typed models enter converters directly. Recent sources are reused.
  */
object SourceParsing {
  /** A language this pipeline reads, typed by the source it produces. */
  sealed abstract class SourceLanguage[S <: ParsedSource[?]](val language: ProgrammingLanguage, private[source] val create: String => S)
  object SourceLanguage {
    case object Java extends SourceLanguage[JavaSource](AppLanguage.Java, JavaSource(_))
    case object Python extends SourceLanguage[PythonSource](AppLanguage.Python, PythonSource(_))
    case object Snap extends SourceLanguage[SnapSource](AppLanguage.SnapLanguage, SnapSource(_))
  }

  private val capacity = 32
  // Snap projects can embed large assets. Their owner keeps the stages; the global cache must not retain them.
  private val maxCachedCharacters = 256 * 1024
  private val recent = AtomicReference(VectorMap.empty[(SourceLanguage[?], String), ParsedSource[?]])

  def parse[S <: ParsedSource[?]](source: String, language: SourceLanguage[S]): S = {
    if source != null && source.length > maxCachedCharacters then return language.create(source)
    val key = (language, source)
    recent.get().get(key).getOrElse(recent.synchronized {
      recent.get().getOrElse(key, {
        val created = language.create(source)
        recent.set((recent.get() + (key -> created)).takeRight(capacity))
        created
      })
    }).asInstanceOf[S]
  }
}
