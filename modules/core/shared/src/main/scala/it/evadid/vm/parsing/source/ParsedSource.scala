package it.evadid.vm.parsing.source

import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.vm.conversion.dependencies.ProgrammingEnvironment
import java.util.concurrent.atomic.AtomicReference

/** One source and the pipeline's steps for it ([[SourceParsing]]); each step is a lazy value. */
trait ParsedSource {
  def source: String
  def language: ProgrammingLanguage
}

/** Step 3 depends on the exercise's environment: one result per environment, computed once. */
private[source] final class PerEnvironment[Result](create: ProgrammingEnvironment => Result) {
  private val results = AtomicReference(Map.empty[ProgrammingEnvironment, Result])

  def apply(environment: ProgrammingEnvironment): Result =
    results.get().get(environment).getOrElse(results.synchronized {
      results.get().getOrElse(environment, {
        val created = create(environment)
        results.set(results.get().updated(environment, created))
        created
      })
    })
}
