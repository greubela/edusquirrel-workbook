package it.evadid.workbook.abstractions

import upickle.default.*

enum RoleInWorkbook derives ReadWriter{
  //case CONTAINER_TITLE
  @upickle.implicits.key("EXERCISE_DESCRIPTION")
  case EXERCISE_DESCRIPTION
  @upickle.implicits.key("IMAGE")
  case IMAGE
}