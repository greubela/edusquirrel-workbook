package it.evadid.core.datastructures.chat


case class Person(name: String, personId: String, role: SenderRole, avatarSvg: Option[String])
