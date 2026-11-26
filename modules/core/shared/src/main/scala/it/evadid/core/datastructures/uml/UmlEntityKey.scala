package it.evadid.core.datastructures.uml

import upickle.default.ReadWriter

final case class UmlEntityKey(className: String, member: Option[UmlMemberKey]) derives ReadWriter
