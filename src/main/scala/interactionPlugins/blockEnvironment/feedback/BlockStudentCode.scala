package interactionPlugins.blockEnvironment.feedback

import contentmanagement.model.vm.code.BeExpression

/** Source of the student code (mainly for texts/debugging purposes). */
enum BlockStudentCodeOrigin:
  case Blocks, PythonText

/** Unified representation of student code. */
enum BlockStudentCode:
  case FromBlocks(expr: BeExpression)
  case FromPython(source: String)

object BlockStudentCode:

  def originOf(code: BlockStudentCode): BlockStudentCodeOrigin =
    code match
      case BlockStudentCode.FromBlocks(_)  => BlockStudentCodeOrigin.Blocks
      case BlockStudentCode.FromPython(_)  => BlockStudentCodeOrigin.PythonText
