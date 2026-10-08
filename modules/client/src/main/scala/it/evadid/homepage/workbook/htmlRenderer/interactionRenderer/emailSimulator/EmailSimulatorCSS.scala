package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.emailSimulator

object EmailSimulatorCSS {
  
  val css: String = 
    """.mail-editor {
       |  display: flex;
       |  flex-direction: column;
       |  background: #F2F2F2;
       |  color: black;
       |  font-family: Arial;
       |  font-size: 14px;
       |}
       |
       |.mail-editor__header {
       |  display: flex;
       |  justify-content: space-between;
       |  align-items: center;
       |  padding: 12px 16px;
       |  background: #DDDDDD;
       |  border-bottom: 1px solid #000000;
       |}
       |
       |.mail-editor__header h3 {
       |  margin: 0;
       |  font-size: 16px;
       |  font-weight: 700;
       |}
       |
       |.mail-editor__container {
       |  display: flex;
       |  flex-direction: row;
       |  flex: 1;
       |  overflow: hidden;
       |}
       |
       |.mail-editor__folders {
       |  display: flex;
       |  flex-direction: column;
       |  gap: 4px;
       |  padding: 8px;
       |  width: 120px;
       |  background: #DDDDDD;
       |  border-right: 1px solid #1F1F1F;
       |}
       |
       |.mail-folder-card {
       |  display: flex;
       |  align-items: center;
       |  padding: 8px;
       |  border-radius: 0px;
       |  border: 1px solid #DDDDDD;
       |  cursor: pointer;
       |}
       |
       |.mail-folder-card:hover {
       |  background: #DDDDDD;
       |  border: 1px solid #D0D0D0;
       |}
       |
       |.mail-folder-card.selected {
       |  background: #EFEFEF;
       |  border: 1px solid #D0D0D0;
       |}
       |
       |.mail-folder-icon {
       |  margin-right: 8px;
       |}
       |
       |.mail-folder-name {
       |  font-weight: 500;
       |}
       |
       |.mail-folder-count {
       |  margin-left: auto;
       |  color: #777777;
       |  font-size: 12px;
       |}
       |
       |.mail-editor__mail-list {
       |  flex: 1;
       |  display: flex;
       |  flex-direction: column;
       |  gap: 4px;
       |  padding: 8px;
       |  overflow-y: auto;
       |}
       |
       |.mail-card {
       |  display: flex;
       |  flex-direction: column;
       |  padding: 10px;
       |  background: #FFFFFF;
       |  border: 1px solid #DDDDDD;
       |  border-radius: 0px;
       |}
       |
       |.mail-card:hover {
       |  background: #F9F9F9;
       |  border: 1px solid #DDDDDD;
       |}
       |
       |.mail-card.selected {
       |  background: #EFEFEF;
       |  border: 1px solid #D0D0D0;
       |}
       |
       |.mail-card__header {
       |  display: flex;
       |  justify-content: space-between;
       |  margin-bottom: 8px;
       |}
       |
       |.mail-card__sender {
       |  font-weight: 600;
       |  color: #272727;
       |}
       |
       |.mail-card__subject {
       |  font-weight: 600;
       |  color: #272727;
       |}
       |
       |.mail-card__body {
       |  color: #272727;
       |  line-height: 1.5;
       |  margin-bottom: 8px;
       |}
       |
       |.mail-card__footer {
       |  display: flex;
       |  justify-content: space-between;
       |  align-items: center;
       |  font-size: 12px;
       |  color: #777777;
       |}
       |
       |.mail-card__actions {
       |  display: flex;
       |  gap: 4px;
       |  margin-top: 8px;
       |}
       |
       |.mail-card__action {
       |  background: #EAEAEA;
       |  border: 1px solid #C4C4C4;
       |  color: black;
       |  padding: 4px 8px;
       |  border-radius: 0px;
       |  font-weight: 600;
       |  cursor: pointer;
       |  font-size: 12px;
       |}
       |
       |.mail-card__action:hover {
       |  background: #D6D6D6;
       |}
       |
       |.mail-editor__actions-bar {
       |  display: flex;
       |  gap: 8px;
       |  padding: 8px;
       |  background: #DDDDDD;
       |  border-bottom: 1px solid #000000;
       |}
       |
       |.mail-editor__actions-bar button {
       |  background: #EAEAEA;
       |  border: 1px solid #C4C4C4;
       |  color: black;
       |  padding: 6px 12px;
       |  border-radius: 0px;
       |  font-weight: 600;
       |  cursor: pointer;
       |}
       |
       |.mail-editor__actions-bar button:hover {
       |  background: #D6D6D6;
       |}
       |
       |.mail-editor__actions-bar button:disabled {
       |  background: #ECECEC;
       |  color: #777777;
       |  border: 1px solid #C4C4C4;
       |}
       |
       |.mail-editor__status-bar {
       |  display: flex;
       |  justify-content: space-between;
       |  padding: 6px 12px;
       |  background: #DDDDDD;
       |  border-top: 1px solid #000000;
       |  font-size: 12px;
       |  color: #272727;
       |}
       |
       |.mail-editor__status-bar .online-indicator {
       |  margin-right: 6px;
       |}
       |
       |.mail-editor__status-bar .url-display {
       |  font-family: monospace;
       |  color: #272727;
       |}
       |""".stripMargin
}
