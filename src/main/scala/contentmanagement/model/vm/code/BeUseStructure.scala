package contentmanagement.model.vm.code

import contentmanagement.model.vm.types.BeScope

trait BeUseStructure extends BeExpression{
  
  def availableInScope: BeScope 

}
