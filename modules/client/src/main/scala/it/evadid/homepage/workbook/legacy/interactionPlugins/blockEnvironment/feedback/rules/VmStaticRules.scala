package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.rules

import it.evadid.workbook.vm.types.BeChildRole.ConditionInControlStructure
import it.evadid.workbook.vm.types.BeScope.GlobalScope
import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.*
import it.evadid.workbook.vm.code.BeExpression
import it.evadid.workbook.vm.code.controlStructures.{BeIfElse, BeRepeatNr, BeSequence, BeWhile}
import it.evadid.workbook.vm.code.defining.BeDefineVariable
import it.evadid.workbook.vm.code.others.{BeReturn, BeStartProgram}
import it.evadid.workbook.vm.code.tree.BeExpressionReference
import it.evadid.workbook.vm.code.usage.{BeAssignVariable, BeUseValue}
import it.evadid.workbook.vm.types.{BeChildPosition, BeInfo, BeUseValueReference}

import scala.collection.mutable

/**
 * Static rules executed on the VM model (BeExpression).
 *
 * Goals:
 *  - validate program structure (empty vs. non-empty, nesting depth).
 *  - analyze variable definitions versus usage.
 *  - detect simple control-flow issues (unreachable code after return).
 */
object VmStaticRules {

  private def t(lang: HumanLanguage)(de: String, en: String): String =
    if lang == AppLanguage.German then de else en

  /** Executes all VM-based rules and returns a list of RuleResult. */
  def runAll(root: BeExpression, humanLanguage: HumanLanguage = AppLanguage.default()): Seq[RuleResult] = {
    val allExprs: Seq[BeExpression] = collectAllExpressions(root)

    val results = mutable.ListBuffer.empty[RuleResult]

    results ++= checkEmptyProgram(root, humanLanguage)

    results += checkMaxNesting(root, humanLanguage, maxDepthAllowed = 8)

    results ++= checkUnusedVariables(allExprs, humanLanguage)

    results ++= checkUnreachableAfterReturn(allExprs, humanLanguage)
    results ++= checkSyntaxErrors(root, humanLanguage)
    results.toList
  }

  private def checkSyntaxErrors(root: BeExpression, humanLanguage: HumanLanguage): Seq[RuleResult] = {
    val errors = root.staticInformationSubtree.syntaxErrors
    if errors.isEmpty then
      Seq(RuleResult(
        id = "VM_NO_SYNTAX_ERRORS",
        category = "VM_TYPES",
        severity = RuleSeverity.Info,
        passed = true,
        message = t(humanLanguage)(
          "Es wurden keine Typ-Inkonsistenzen oder nicht-parsbare Blöcke gefunden.",
          "No type inconsistencies or unparsable blocks were found."
        ),
        details = None
      ))
    else
      errors.map { info =>
        val msg = info.message.getInLanguage(humanLanguage)
        val (id, cat) = info.infoType match {
          case BeInfo.SyntaxError.UnparsableBlock => ("VM_UNPARSABLE_BLOCK", "VM_ERRORS")
          case BeInfo.SyntaxError.UnsupportedBlock => ("VM_UNSUPPORTED_BLOCK", "VM_ERRORS")
          case BeInfo.SyntaxError.TypeMismatch => ("VM_TYPE_MISMATCH", "VM_TYPES")
          case BeInfo.SyntaxError.StructureMismatch => ("VM_STRUCTURE_MISMATCH", "VM_ERRORS")
          case BeInfo.SyntaxError.MissingValue => ("VM_MISSING_VALUE", "VM_ERRORS")
          case _ => ("VM_SYNTAX_ERROR", "VM_ERRORS")
        }
        RuleResult(
          id = id, category = cat, severity = RuleSeverity.Warning, passed = false,
          message = msg, details = Some(info.infoType.toString)
        )
      }
  }
  
  /** Traverses the tree and collects all BeExpression nodes. */
  private def collectAllExpressions(root: BeExpression): List[BeExpression] = {
    val buffer = mutable.ListBuffer.empty[BeExpression]

    def loop(expr: BeExpression): Unit = {
      buffer += expr
      directChildren(expr).foreach(loop)
    }

    loop(root)
    buffer.toList
  }

  private def directChildren(expr: BeExpression): List[BeExpression] =
    expr
      .getChildren(withExtensions = false, GlobalScope())
      .collect { case BeExpressionReference(_, childExpr) => childExpr }

  private def directChildrenWithPos(expr: BeExpression): List[(BeChildPosition, BeExpression)] =
    expr
      .getChildren(withExtensions = false, GlobalScope())
      .collect { case BeExpressionReference(pos, childExpr) => (pos, childExpr) }

  /** Checks whether the program is empty or missing a start sequence. */
  private def checkEmptyProgram(root: BeExpression, humanLanguage: HumanLanguage): Seq[RuleResult] = root match {
    case BeStartProgram(None) =>
      Seq(
        RuleResult(
          id = "VM_EMPTY_PROGRAM",
          category = "VM_STRUCTURE",
          severity = RuleSeverity.Error,
          passed = false,
          message = t(humanLanguage)(
            "Das Programm besitzt keinen Start-Block oder keine auszuführenden Anweisungen.",
            "The program has no start block or no executable statements."
          ),
          details = Some("BeStartProgram ohne Start-Sequenz.")
        )
      )

    case BeStartProgram(Some(seq)) if seq.body.isEmpty =>
      Seq(
        RuleResult(
          id = "VM_EMPTY_PROGRAM",
          category = "VM_STRUCTURE",
          severity = RuleSeverity.Error,
          passed = false,
          message = t(humanLanguage)(
            "Die Start-Sequenz des Programms ist leer.",
            "The program start sequence is empty."
          ),
          details = Some("BeStartProgram mit leerer Start-Sequenz.")
        )
      )

    case _ =>
      Seq(
        RuleResult(
          id = "VM_NON_EMPTY_PROGRAM",
          category = "VM_STRUCTURE",
          severity = RuleSeverity.Info,
          passed = true,
          message = t(humanLanguage)(
            "Es wurden ausführbare Blöcke im Programm gefunden.",
            "Executable blocks were found in the program."
          ),
          details = None
        )
      )
  }

  /**
   * Computes an approximation of the maximum nesting depth of the student's logic.
   *
   * The VM expression tree contains many structural wrapper nodes and (especially for parsed Python)
   * many nested [[BeSequence]] blocks. Counting raw tree depth or sequence depth produces noisy
   * false positives.
   *
   * Instead we count only true control structures ([[BeIfElse]], [[BeWhile]], [[BeRepeatNr]]), which
   * better matches the intuition of “nested blocks”.
   */
  private def computeMaxNesting(root: BeExpression): Int = {
    def loop(expr: BeExpression, controlDepth: Int, inCondition: Boolean): Int = {
      val depth1 = expr match
        case _: BeIfElse if !inCondition   => controlDepth + 1
        case _: BeWhile if !inCondition    => controlDepth + 1
        case _: BeRepeatNr if !inCondition => controlDepth + 1
        case _                             => controlDepth

      val children = directChildrenWithPos(expr)
      if children.isEmpty then depth1
      else
        children
          .map { case (pos, childExpr) =>
            val childInCondition = inCondition || (pos.roleInParent == ConditionInControlStructure)
            loop(childExpr, depth1, childInCondition)
          }
          .max
    }

    loop(root, controlDepth = 0, inCondition = false)
  }

  private def checkMaxNesting(root: BeExpression, humanLanguage: HumanLanguage, maxDepthAllowed: Int): RuleResult = {
    val maxDepth = computeMaxNesting(root)
    if maxDepth <= maxDepthAllowed then
      RuleResult(
        id = "VM_MAX_NESTING",
        category = "VM_STRUCTURE",
        severity = RuleSeverity.Info,
        passed = true,
        message = t(humanLanguage)(
          s"Die Verschachtelungstiefe ($maxDepth) liegt im empfohlenen Bereich (≤ $maxDepthAllowed).",
          s"The nesting depth ($maxDepth) is within the recommended range (≤ $maxDepthAllowed)."
        ),
        details = Some(maxDepth.toString)
      )
    else
      RuleResult(
        id = "VM_MAX_NESTING",
        category = "VM_STRUCTURE",
        severity = RuleSeverity.Warning,
        passed = false,
        message = t(humanLanguage)(
          s"Die Verschachtelungstiefe ($maxDepth) überschreitet den empfohlenen Wert von $maxDepthAllowed Ebenen.",
          s"The nesting depth ($maxDepth) exceeds the recommended limit of $maxDepthAllowed levels."
        ),
        details = Some(maxDepth.toString)
      )
  }

  /** Finds defined variables and checks whether they are used at least once. */
  private def checkUnusedVariables(allExprs: Seq[BeExpression], humanLanguage: HumanLanguage): Seq[RuleResult] = {
    val defined = mutable.LinkedHashSet.empty[BeDefineVariable]
    val used    = mutable.LinkedHashSet.empty[BeDefineVariable]

    allExprs.foreach {
      case d: BeDefineVariable =>
        defined += d

      case BeUseValue(_, Some(ctx)) =>
        used += ctx

      case BeAssignVariable(target, _) =>
        used += target

      case uv: BeUseValue =>
        uv.value match {
          case BeUseValueReference(v) => used += v
          case _                      =>
        }

      case _ =>
    }

    if defined.isEmpty then Seq.empty
    else {
      val unused = defined.diff(used)

      if unused.isEmpty then
        Seq(
          RuleResult(
            id = "VM_UNUSED_VARIABLES",
            category = "VM_VARIABLES",
            severity = RuleSeverity.Info,
            passed = true,
            message = t(humanLanguage)(
              "Alle definierten Variablen werden im Programmverlauf verwendet.",
              "All defined variables are used in the program."
            ),
            details = None
          )
        )
      else {
        val names = unused.toList
          .map(_.name.getNameIn(humanLanguage, it.evadid.workbook.vm.naming.NamingStyle.SnakeCase))
          .mkString(", ")

        Seq(
          RuleResult(
            id = "VM_UNUSED_VARIABLES",
            category = "VM_VARIABLES",
            severity = RuleSeverity.Warning,
            passed = false,
            message = t(humanLanguage)(
              s"Die folgenden Variablen werden nie verwendet: $names.",
              s"The following variables are never used: $names."
            ),
            details = Some(names)
          )
        )
      }
    }
  }

  /** Checks whether sequences contain unreachable code after a 'return'. */
  private def checkUnreachableAfterReturn(allExprs: Seq[BeExpression], humanLanguage: HumanLanguage): Seq[RuleResult] = {
    val sequences = allExprs.collect { case s: BeSequence => s }

    val unreachablePerSeq: Seq[Int] = sequences.map { seq =>
      val body = seq.body
      val idx  = body.indexWhere(_.isInstanceOf[BeReturn])
      if idx >= 0 && idx < body.size - 1 then body.size - idx - 1
      else 0
    }

    val totalUnreachable = unreachablePerSeq.sum

    if totalUnreachable == 0 then
      Seq(
        RuleResult(
          id = "VM_NO_UNREACHABLE_AFTER_RETURN",
          category = "VM_CONTROL",
          severity = RuleSeverity.Info,
          passed = true,
          message = t(humanLanguage)(
            "Es wurde kein offensichtlich unerreichbarer Code nach einem 'return'-Block gefunden.",
            "No obviously unreachable code after a 'return' block was found."
          ),
          details = None
        )
      )
    else
      Seq(
        RuleResult(
          id = "VM_UNREACHABLE_AFTER_RETURN",
          category = "VM_CONTROL",
          severity = RuleSeverity.Warning,
          passed = false,
          message = t(humanLanguage)(
            s"Es wurden $totalUnreachable Anweisung(en) nach einem 'return'-Block gefunden, die niemals ausgeführt werden.",
            s"Found $totalUnreachable statement(s) after a 'return' block that will never execute."
          ),
          details = Some(s"$totalUnreachable Anweisungen nach return.")
        )
      )
  }
}
