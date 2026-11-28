package it.evadid.vm.parsing.source

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import java.util.concurrent.atomic.AtomicReference
import scala.collection.immutable.VectorMap

/** The single parsing entry for Java, Python and Snap text. The returned source exposes the pipeline's steps as
  * lazy values: `structured` (tolerant fastparse lexing and declarations, accepts unfinished and invalid code),
  * `syntax` (the language grammar, or a located [[SyntaxFailure]]) and `parsed` (the shared expression tree).
  * Typed models (e.g. a Snap document) enter the converters directly. Recent sources are reused.
  */
object SourceParsing {
  /** A language this pipeline reads, typed by the source it produces. */
  sealed abstract class SourceLanguage[S <: ParsedSource](val language: ProgrammingLanguage, private[source] val create: String => S)
  object SourceLanguage {
    @upickle.implicits.key("Java")
    case object Java extends SourceLanguage[JavaSource](AppLanguage.Java, JavaSource(_))
    @upickle.implicits.key("Python")
    case object Python extends SourceLanguage[PythonSource](AppLanguage.Python, PythonSource(_))
    @upickle.implicits.key("Snap")
    case object Snap extends SourceLanguage[SnapSource](AppLanguage.SnapLanguage, SnapSource(_))
  }

  private val capacity = 32
  // Snap projects can embed large assets. Their owner keeps the stages; the global cache must not retain them.
  private val maxCachedCharacters = 256 * 1024
  private val recent = AtomicReference(VectorMap.empty[(SourceLanguage[?], String), ParsedSource])

  def parse[S <: ParsedSource](source: String, language: SourceLanguage[S]): S = {
    val key = (language, source)
    if source.length > maxCachedCharacters then language.create(source)
    else recent.get().get(key).getOrElse(recent.synchronized {
      recent.get().getOrElse(key, {
        val created = language.create(source)
        recent.set((recent.get() + (key -> created)).takeRight(capacity))
        created
      })
    }).asInstanceOf[S]
  }
}
