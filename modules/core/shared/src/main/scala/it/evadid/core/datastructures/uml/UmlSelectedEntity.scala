package it.evadid.core.datastructures.uml

import upickle.default.ReadWriter

final case class UmlSelectedEntity(kind: UmlEntityKind, name: String, fullCode: String) derives ReadWriter
