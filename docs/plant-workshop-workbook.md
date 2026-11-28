# Plant workshop workbook

The Girls' Day plant-watering workshop is a native workbook at
[plantWorkshopWorkbook](../homepage/plantWorkshopWorkbook/index.html), built by
[CreatePlantworkshopWorkbook.scala](../modules/client/src/main/scala/it/evadid/homepage/workbook/content/CreatePlantworkshopWorkbook.scala).
Seven sections lead from motivation and wiring through sensor, moisture and pump to the complete
system, testing and a bonus, combining checklists, slideshows, code tasks and Arduino downloads.

Code tasks use `CodeTaskToggle`: ordering tasks for beginners, code templates with requirements for
advanced learners. `TODO_*` markers in the templates are deliberate gaps for learners. Texts live in
`resources/languageMaps/eva/plantworkshop/`; the workbook uses the shared workbook styles.
The [Arduino reference sketch](arduino_reference_code.ino) is the subject-matter reference.

The older page [plantWorkshop](../homepage/plantWorkshop/index.html) and `homepage/css/plantWorkshop.css`
belong to the retired standalone application; startup registers no factory for it. Before removing
or redirecting that page, compare content and visuals with the workbook and check the hardware
instructions on real devices.

`client/testOnly *WorkbookDefinitionsSpec` checks the workbook's serialization; hardware behavior
and didactic parity need a manual run.
