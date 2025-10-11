package contentmanagement.model.color

trait AppColorPalette {

  def allColors: List[AppColor]

}



object AppColorPalette {

  def defaultRGBYPalette25: ColorPalette25 with RGBYColorPalette = new ColorPalette25() with RGBYColorPalette {

    override def grayscale: List[AppColor] = List(
      AppColor.fromWebStyleString("#282828"),
      AppColor.fromWebStyleString("#2E2E2E"),
      AppColor.fromWebStyleString("#4F4F4F"),
      AppColor.fromWebStyleString("#797979"),
      AppColor.fromWebStyleString("#989898"),
    )

    override def primaryColor: List[AppColor] = reds

    override def secondaryColor: List[AppColor] = greens

    override def tertiaryColor: List[AppColor] = blues

    override def quaternaryColor: List[AppColor] = yellows

    override def reds: List[AppColor] = List(
      AppColor.fromWebStyleString("#8B0A00"),
      AppColor.fromWebStyleString("#A00C00"),
      AppColor.fromWebStyleString("#B02A20"),
      AppColor.fromWebStyleString("#C15C54"),
      AppColor.fromWebStyleString("#C18783"),
    )

    override def greens: List[AppColor] = List(
      AppColor.fromWebStyleString("#006A13"),
      AppColor.fromWebStyleString("#007B15"),
      AppColor.fromWebStyleString("#18872B"),
      AppColor.fromWebStyleString("#40955F"),
      AppColor.fromWebStyleString("#64946C"),
    )

    override def blues: List[AppColor] = List(
      AppColor.fromWebStyleString("#032E5B"),
      AppColor.fromWebStyleString("#083768"),
      AppColor.fromWebStyleString("#1C4673"),
      AppColor.fromWebStyleString("#3C5C7E"),
      AppColor.fromWebStyleString("#586B7E"),
    )

    override def yellows: List[AppColor] = List(
      AppColor.fromWebStyleString("#8B5300"),
      AppColor.fromWebStyleString("#A06000"),
      AppColor.fromWebStyleString("#B07620"),
      AppColor.fromWebStyleString("#C19554"),
      AppColor.fromWebStyleString("#C1A883"),
    )
  }

}
