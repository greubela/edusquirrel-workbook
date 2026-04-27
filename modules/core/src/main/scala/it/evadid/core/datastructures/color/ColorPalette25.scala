package it.evadid.core.datastructures.color

trait ColorPalette25 extends AppColorPalette {

  def grayscale: List[AppColor]

  def primaryColor: List[AppColor]

  def secondaryColor: List[AppColor]

  def tertiaryColor: List[AppColor]

  def quaternaryColor: List[AppColor]

  override def allColors: List[AppColor] = grayscale ++ primaryColor ++ secondaryColor ++ tertiaryColor ++ quaternaryColor
}





