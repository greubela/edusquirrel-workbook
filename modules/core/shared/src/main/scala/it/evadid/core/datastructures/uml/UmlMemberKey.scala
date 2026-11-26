package it.evadid.core.datastructures.uml

import upickle.default.ReadWriter

final case class UmlMemberKey(kind: UmlEntityKind, name: String, occurrence: Int) derives ReadWriter
