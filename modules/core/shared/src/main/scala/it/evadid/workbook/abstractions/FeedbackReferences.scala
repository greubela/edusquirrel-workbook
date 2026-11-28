package it.evadid.workbook.abstractions

import it.evadid.core.util.io.Serializer
import upickle.default.ReadWriter

/** Identifies a grader by its content key in saved feedback, so results do not copy their grader. Saved as
  * `GraderReference(<key>)`, distinct from other identifiers.
  */
final case class GraderReference(key: String)

object GraderReference {
  given ReadWriter[GraderReference] = Serializer.constructorLikeSerializer[GraderReference]("GraderReference")(_.key)(GraderReference(_))
    .uPickleReadWrite
}

/** Identifies an answer by the fingerprint its [[InteractionFormat]] derives, so feedback does not repeat the answer.
  * Saved as `StateReference(<fingerprint>)`; equal answers of one format have equal references.
  */
final case class StateReference(fingerprint: String)

object StateReference {
  given ReadWriter[StateReference] = Serializer.constructorLikeSerializer[StateReference]("StateReference")(_.fingerprint)(StateReference(_))
    .uPickleReadWrite
}
