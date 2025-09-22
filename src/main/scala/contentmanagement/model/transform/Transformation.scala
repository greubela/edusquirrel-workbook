package contentmanagement.model.transform

trait Transformation[T] {
  def getTransformed(in: T): T
}

