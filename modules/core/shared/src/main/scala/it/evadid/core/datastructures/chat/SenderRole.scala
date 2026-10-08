package it.evadid.core.datastructures.chat

import upickle.default.*

import it.evadid.workbook.abstractions.WorkbookStructuringType.WORKBOOK

sealed trait SenderRole(val llmName: String, val showName: String) derives ReadWriter {

}

object SenderRole {

  case object USER extends SenderRole("user", "student")

  case object TEACHER extends SenderRole("user", "teacher")

  case object AGENT extends SenderRole("assistant", "aihelper")

  case object SYSTEM extends SenderRole("user", "system")

  val allRoles: List[SenderRole] = List(USER, TEACHER, AGENT, SYSTEM)
}