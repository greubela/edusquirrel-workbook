package it.evadid.vm.parsing.source

import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.vm.conversion.dependencies.ProgrammingEnvironment
import java.util.concurrent.atomic.AtomicReference

/** One source and the pipeline's steps for it ([[SourceParsing]]). */
trait ParsedSource[Result] {
  def source: String
  def language: ProgrammingLanguage

  private val byEnvironment = AtomicReference(Map.empty[ProgrammingEnvironment, Result])

  /** A step-3 result per environment, computed once. */
  protected def inEnvironment(environment: ProgrammingEnvironment)(create: => Result): Result =
    byEnvironment.get().get(environment).getOrElse(byEnvironment.synchronized {
      byEnvironment.get().getOrElse(environment, {
        val created = create
        byEnvironment.set(byEnvironment.get().updated(environment, created))
        created
      })
    })
}
