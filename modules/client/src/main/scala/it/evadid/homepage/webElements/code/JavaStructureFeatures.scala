package it.evadid.homepage.webElements.code

import upickle.default.ReadWriter

/** Structure actions the Java class diagram offers. Tasks fade them in as learners progress:
  * a procedural start only adds methods, object-oriented tasks add classes, attributes and constructors.
  */
final case class JavaStructureFeatures(
    addClass: Boolean = true,
    addField: Boolean = true,
    addConstructor: Boolean = true,
    addMethod: Boolean = true,
    deleteMember: Boolean = true
) derives ReadWriter

object JavaStructureFeatures {
  val Full: JavaStructureFeatures = JavaStructureFeatures()

  /** Static methods in given classes, as in turtle drawings and the first programming course. */
  val Procedural: JavaStructureFeatures = JavaStructureFeatures(addClass = false, addField = false, addConstructor = false)
}
