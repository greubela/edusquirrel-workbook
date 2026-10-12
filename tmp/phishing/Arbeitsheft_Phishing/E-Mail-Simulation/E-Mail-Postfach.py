import sys
from PySide6.QtWidgets import (
    QApplication)
from skripts.MailGUI import EmailApp

if __name__ == "__main__":
    app = QApplication(sys.argv)
    window = EmailApp()
    window.show()
    sys.exit(app.exec())