package datastructures.core.color

trait AppColorPalette {

  def allColors: List[AppColor]

}



object AppColorPalette {

  def defaultRGBYPalette25: ColorPalette25 with RGBYColorPalette = new ColorPalette25() with RGBYColorPalette {

    override def grayscale: List[AppColor] = List(
      WebColor("#282828"),
      WebColor("#2E2E2E"),
      WebColor("#4F4F4F"),
      WebColor("#797979"),
      WebColor("#989898"),
    )

    override def primaryColor: List[AppColor] = reds

    override def secondaryColor: List[AppColor] = greens

    override def tertiaryColor: List[AppColor] = blues

    override def quaternaryColor: List[AppColor] = yellows

    override def reds: List[AppColor] = List(
      WebColor("#8B0A00"),
      WebColor("#A00C00"),
      WebColor("#B02A20"),
      WebColor("#C15C54"),
      WebColor("#C18783"),
    )

    override def greens: List[AppColor] = List(
      WebColor("#006A13"),
      WebColor("#007B15"),
      WebColor("#18872B"),
      WebColor("#40955F"),
      WebColor("#64946C"),
    )

    override def blues: List[AppColor] = List(
      WebColor("#032E5B"),
      WebColor("#083768"),
      WebColor("#1C4673"),
      WebColor("#3C5C7E"),
      WebColor("#586B7E"),
    )

    override def yellows: List[AppColor] = List(
      WebColor("#8B5300"),
      WebColor("#A06000"),
      WebColor("#B07620"),
      WebColor("#C19554"),
      WebColor("#C1A883"),
    )
  }

}
