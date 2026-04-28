package workbook.model.interaction.history

enum UpdateImportance {
  case DEFAULT // default value for new variables
  case TEMPORARY // keep until the next real event comes
  case MINOR // keep until the next major event comes
  case MAJOR // always keep in history
}
