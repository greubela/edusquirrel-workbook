package interactionPlugins.blockEnvironment.feedback.rules

import contentmanagement.model.language.AppLanguage
import contentmanagement.model.language.HumanLanguage
import contentmanagement.model.language.LanguageMap
import contentmanagement.model.vm.code.BeExpression
import contentmanagement.model.vm.code.controlStructures.BeSequence
import contentmanagement.model.vm.code.defining.BeDefineVariable
import contentmanagement.model.vm.code.others.{BeReturn, BeStartProgram}
import contentmanagement.model.vm.code.tree.{BeExpressionNode, BeExpressionReference}
import contentmanagement.model.vm.code.usage.{BeAssignVariable, BeUseValue}
import contentmanagement.model.vm.types.{BeScope, BeUseValueReference}
import contentmanagement.model.vm.types.BeScope.GlobalScope

import scala.collection.mutable

/**
 * TODO translate (logik) the message / override via AI if necessary
 * Static rules executed on the VM model (BeExpression).
 *
 * Goals:
 *  - validate program structure (empty vs. non-empty, nesting depth).
 *  - analyze variable definitions versus usage.
 *  - detect simple control-flow issues (unreachable code after return).
 */
object VmStaticRules {

  /** Executes all VM-based rules and returns a list of RuleResult. */
  def runAll(root: BeExpression): Seq[RuleResult] = {
    val allExprs: Seq[BeExpression] = collectAllExpressions(root)

    val results = mutable.ListBuffer.empty[RuleResult]

    results ++= checkEmptyProgram(root)

    results += checkMaxNesting(root, maxDepthAllowed = 8)

    results ++= checkUnusedVariables(allExprs)

    results ++= checkUnreachableAfterReturn(allExprs)

    results.toList
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

  /** Checks whether the program is empty or missing a start sequence. */
  private def checkEmptyProgram(root: BeExpression): Seq[RuleResult] = root match {
    case BeStartProgram(None) =>
      Seq(
        RuleResult(
          id = "VM_EMPTY_PROGRAM",
          category = "VM_STRUCTURE",
          severity = RuleSeverity.Error,
          passed = false,
          message = "Das Programm besitzt keinen Start-Block oder keine auszuführenden Anweisungen.",
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
          message = "Die Start-Sequenz des Programms ist leer.",
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
          message = "Es wurden ausführbare Blöcke im Programm gefunden.",
          details = None
        )
      )
  }

  /** Computes the maximum nesting depth in the expression tree. */
  private def computeMaxNesting(root: BeExpression): Int = {
    def loop(expr: BeExpression, depth: Int): Int = {
      val children = directChildren(expr)
      if children.isEmpty then depth
      else children.map(ch => loop(ch, depth + 1)).max
    }

    loop(root, depth = 1)
  }

  private def checkMaxNesting(root: BeExpression, maxDepthAllowed: Int): RuleResult = {
    val maxDepth = computeMaxNesting(root)
    if maxDepth <= maxDepthAllowed then
      RuleResult(
        id = "VM_MAX_NESTING",
        category = "VM_STRUCTURE",
        severity = RuleSeverity.Info,
        passed = true,
        message = s"Die Verschachtelungstiefe ($maxDepth) liegt im empfohlenen Bereich (≤ $maxDepthAllowed).",
        details = Some(maxDepth.toString)
      )
    else
      RuleResult(
        id = "VM_MAX_NESTING",
        category = "VM_STRUCTURE",
        severity = RuleSeverity.Warning,
        passed = false,
        message = s"Die Verschachtelungstiefe ($maxDepth) überschreitet den empfohlenen Wert von $maxDepthAllowed Ebenen.",
        details = Some(maxDepth.toString)
      )
  }

  /** Finds defined variables and checks whether they are used at least once. */
  private def checkUnusedVariables(allExprs: Seq[BeExpression]): Seq[RuleResult] = {
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
            message = "Alle definierten Variablen werden im Programmverlauf verwendet.",
            details = None
          )
        )
      else {
        val english: HumanLanguage = AppLanguage.English
        val names = unused.toList
          .map(_.name.getInLanguage(english))
          .mkString(", ")

        Seq(
          RuleResult(
            id = "VM_UNUSED_VARIABLES",
            category = "VM_VARIABLES",
            severity = RuleSeverity.Warning,
            passed = false,
            message = s"Die folgenden Variablen werden nie verwendet: $names.",
            details = Some(names)
          )
        )
      }
    }
  }

  /** Checks whether sequences contain unreachable code after a 'return'. */
  private def checkUnreachableAfterReturn(allExprs: Seq[BeExpression]): Seq[RuleResult] = {
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
          message = "Es wurde kein offensichtlich unerreichbarer Code nach einem 'return'-Block gefunden.",
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
          message = s"Es wurden $totalUnreachable Anweisung(en) nach einem 'return'-Block gefunden, die niemals ausgeführt werden.",
          details = Some(s"$totalUnreachable Anweisungen nach return.")
        )
      )
  }
}
