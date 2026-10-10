package it.evadid.vm.parsing.generic.abstractions

/** Persist type descriptions, rebuilding their language-specific literal serializers on read. */
private[parsing] case class AstTypeDescriptor(kind: String, arguments: List[AstTypeDescriptor] = Nil,
                                             label: String = "") derives upickle.default.ReadWriter
