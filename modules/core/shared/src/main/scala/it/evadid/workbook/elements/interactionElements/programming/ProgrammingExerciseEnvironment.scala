package it.evadid.workbook.elements.interactionElements.programming

import it.evadid.vm.conversion.dependencies.ProgrammingEnvironment

/** An exercise whose programs may call the libraries of `environment` without defining them. Its graders, editors,
  * previews and analyses read programs in it; calls into other libraries are the program's unknown dependency.
  * Content may set `environment`; each exercise type has a default.
  */
trait ProgrammingExerciseEnvironment {
  def environment: ProgrammingEnvironment
}
