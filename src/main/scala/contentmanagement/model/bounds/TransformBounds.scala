package contentmanagement.model.bounds

import contentmanagement.model.Bounds
import contentmanagement.model.transform.Transformation


trait TransformBounds extends Transformation[Bounds] {
  def preservesRatio: Boolean
}
