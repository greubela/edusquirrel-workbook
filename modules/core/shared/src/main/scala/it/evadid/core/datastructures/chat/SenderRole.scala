package it.evadid.core.datastructures.chat

sealed trait SenderRole(val llmName: String, val showName: String) {

}

object SenderRole {

  case object USER extends SenderRole("user", "student")

  case object TEACHER extends SenderRole("user", "teacher")

  case object AGENT extends SenderRole("assistant", "aihelper")

  case object WORKBOOK extends SenderRole("user", "workbook")

  val allRoles: List[SenderRole] = List(USER, TEACHER, AGENT, WORKBOOK)
}