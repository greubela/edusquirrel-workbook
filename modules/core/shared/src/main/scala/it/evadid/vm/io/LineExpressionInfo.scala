package it.evadid.vm.io

import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.types.{BeChildRole, BeScope}

case class LineExpressionInfo
(
  expression: BeExpression,
  roleInParent: Option[BeChildRole],
  scope: BeScope,
)
