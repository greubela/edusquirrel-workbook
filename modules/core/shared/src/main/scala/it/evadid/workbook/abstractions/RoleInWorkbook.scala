package it.evadid.workbook.abstractions

import upickle.default.*

enum RoleInWorkbook derives ReadWriter{
  //case CONTAINER_TITLE
  case EXERCISE_DESCRIPTION
  case IMAGE
}