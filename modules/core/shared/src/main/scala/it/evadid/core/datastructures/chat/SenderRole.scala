package it.evadid.core.datastructures.chat

import upickle.default.*

import it.evadid.workbook.abstractions.WorkbookStructuringType.WORKBOOK

sealed trait SenderRole(val llmName: String, val showName: String) derives ReadWriter {

}

object SenderRole {

  @upickle.implicits.key("USER")
  case object USER extends SenderRole("user", "student")

  @upickle.implicits.key("TEACHER")
  case object TEACHER extends SenderRole("user", "teacher")

  @upickle.implicits.key("AGENT")
  case object AGENT extends SenderRole("assistant", "aihelper")

  @upickle.implicits.key("SYSTEM")
  case object SYSTEM extends SenderRole("user", "system")

  val allRoles: List[SenderRole] = List(USER, TEACHER, AGENT, SYSTEM)
}