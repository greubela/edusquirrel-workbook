package contentmanagement.webElements.shapes.meta

import datastructures.core.geometry.Dimension
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import util.numbers.*

case class ConfigDependentSizeConstraint[T: Fractional](
                                                         val widthConstraints: ValueDependentConstraints[BeRenderingConfig, T],
                                                         val heightConstraints: ValueDependentConstraints[BeRenderingConfig, T]
                                                       ) {
  def addConfigDependentWidthConstraint(constraint: BeRenderingConfig => NumberConstraint[T]): ConfigDependentSizeConstraint[T] =
    this.copy(widthConstraints = widthConstraints.addConstraint(constraint))

  def addConfigDependentHeightConstraint(constraint: BeRenderingConfig => NumberConstraint[T]): ConfigDependentSizeConstraint[T] =
    this.copy(heightConstraints = heightConstraints.addConstraint(constraint))

  def addWidthConstraint(constraint: NumberConstraint[T]): ConfigDependentSizeConstraint[T] =
    addConfigDependentWidthConstraint(_ => constraint)

  def addHeightConstraint(constraint: NumberConstraint[T]): ConfigDependentSizeConstraint[T] =
    addConfigDependentHeightConstraint(_ => constraint)

}

object ConfigDependentSizeConstraint {
  def empty[T: Fractional]: ConfigDependentSizeConstraint[T] = ConfigDependentSizeConstraint[T](ValueDependentConstraints.empty(), ValueDependentConstraints.empty())

  def fromMinDim[T: Fractional](minDim: Dimension[T]): ConfigDependentSizeConstraint[T] = fromMinDimConstraint(_ => minDim)

  def fromMinDimConstraint[T: Fractional](constraint: BeRenderingConfig => Dimension[T]): ConfigDependentSizeConstraint[T] = {
    val widthFunc: BeRenderingConfig => NumberConstraint[T] = renderingInfo => NumberConstraintImpl(Some(constraint.apply(renderingInfo).width), None)
    val heightFunc: BeRenderingConfig => NumberConstraint[T] = renderingInfo => NumberConstraintImpl(Some(constraint.apply(renderingInfo).height), None)
    empty[T].addConfigDependentWidthConstraint(widthFunc).addConfigDependentHeightConstraint(heightFunc)
  }
}