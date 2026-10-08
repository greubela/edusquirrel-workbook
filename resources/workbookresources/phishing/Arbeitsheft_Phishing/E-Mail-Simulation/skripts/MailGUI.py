from PySide6.QtCore import Qt
from PySide6.QtWidgets import (
    QMainWindow, QPushButton, QWidget, QVBoxLayout, QHBoxLayout,
    QListWidget, QListWidgetItem, QTextBrowser, QLabel, QMessageBox,
    QSplitter, QTreeWidget, QTreeWidgetItem, QFrame, QDialogButtonBox
)

from skripts.mails import Email, emails


class EmailApp(QMainWindow):
    def __init__(self):
        super().__init__()

        self.setWindowTitle("Mail Simulator")
        self.resize(1200, 750)

        self.current_folder = "Posteingang"

        self.create_layout()

        self.status_bar = self.statusBar()
        self.online_label = QLabel("🌐")
        self.online_label.setToolTip("Sie sind derzeit online")
        self.online_label.setAttribute(Qt.WA_Hover)
        self.online_label.setContentsMargins(6, 0, 6, 0)

        self.link_label = QLabel()
        self.link_label.setTextInteractionFlags(Qt.TextSelectableByMouse)

        self.status_bar.addWidget(self.online_label)
        self.status_bar.addWidget(self.link_label)

        self.load_folder()
        self.load_mails()
        self.apply_style()

        #Auswertung
        self.total_sorted = 0
        self.correct_sorted = 0
        self.evaluated = False


    def create_layout(self) -> None:
        central_widget = QWidget()

        main_layout = QVBoxLayout(central_widget)
        main_layout.setContentsMargins(0, 0, 0, 0)
        main_layout.setSpacing(0)

        #Layout Topbar
        top_bar_widget = QWidget()
        top_bar_widget.setFixedHeight(45)

        top_bar = QHBoxLayout(top_bar_widget)
        top_bar.setContentsMargins(0,0,0,0)
        top_bar.setSpacing(0)


            #Linke Spalte
        left_widget = QWidget()
        left_widget.setObjectName("Top_bar_left_widget")
        left_widget.setFixedSize(216,45)
        left_layout = QHBoxLayout(left_widget)
        left_layout.setContentsMargins(8, 4, 8, 4)

        button_send = QPushButton("Neue E-Mail erstellen")
        button_send.setToolTip("Eine neue Nachricht verfassen (nicht möglich)")
        button_send.setEnabled(False)

        left_layout.addWidget(button_send)
        left_layout.addStretch()

            #Mittlere Spalte
        middle_widget = QWidget()
        middle_widget.setObjectName("Top_bar_middle_widget")
        middle_widget.setFixedSize(308,45)
        middle_layout = QVBoxLayout(middle_widget)
        middle_layout.setContentsMargins(8, 2, 8, 2)
        middle_layout.setSpacing(0)

        self.folder_title = QLabel('    Posteingang')
        self.folder_title.setObjectName("folder_title")
        font = self.folder_title.font()
        font.setBold(True)
        self.folder_title.setFont(font)

        self.folder_count = QLabel('    0 E-Mails')
        self.folder_count.setObjectName("folder_count")

        middle_layout.addWidget(self.folder_title)
        middle_layout.addWidget(self.folder_count)
        middle_layout.addStretch()

            #Rechte Spalte
        right_widget = QWidget()
        right_widget.setObjectName("Top_bar_right_widget")
        right_layout = QHBoxLayout(right_widget)
        right_layout.setContentsMargins(8, 4, 8, 4)

        button_answer = QPushButton("Antworten")
        button_forward = QPushButton("Weiterleiten")
        button_archiv = QPushButton("Archivieren")
        button_mark = QPushButton("Markieren")
        button_delete = QPushButton("Löschen")

        button_answer.setToolTip("Dem Absender antworten (nicht möglich)")
        button_forward.setToolTip("Diese Nachricht weiterleiten (nicht möglich)")
        button_archiv.setToolTip("Diese Nachricht archivieren")
        button_mark.setToolTip("Diese Nachricht markieren")
        button_delete.setToolTip("Diese Nachricht löschen")

        button_answer.setEnabled(False)
        button_forward.setEnabled(False)
        button_archiv.clicked.connect(self.archiv_mail)
        button_mark.clicked.connect(self.mark_mail)
        button_delete.clicked.connect(self.delete_mail)

        right_layout.addStretch()
        right_layout.addWidget(button_answer)
        right_layout.addWidget(button_forward)
        right_layout.addWidget(button_archiv)
        right_layout.addWidget(button_mark)
        right_layout.addWidget(button_delete)

            #zusammenführen
        top_bar.addWidget(left_widget)
        top_bar.addWidget(middle_widget,1)
        top_bar.addWidget(right_widget)

        #Topbar-Line
        top_bar_line = QFrame()
        top_bar_line.setObjectName("top_bar_line")
        top_bar_line.setFrameShape(QFrame.HLine)
        top_bar_line.setFrameShadow(QFrame.Plain)
        top_bar_line.setFixedHeight(1)


        #Layout Main
        splitter = QSplitter(Qt.Horizontal)
        splitter.setContentsMargins(12,12,12,12)


        #Tree
        self.folder_tree = QTreeWidget()
        self.folder_tree.setHeaderHidden(True)
        self.folder_tree.setIndentation(0)
        self.folder_tree.setFixedWidth(200)
        self.folder_tree.setHorizontalScrollBarPolicy(Qt.ScrollBarAlwaysOff)
        self.folder_tree.itemClicked.connect(self.select_folder)

        #List
        self.mail_list = QListWidget()
        self.mail_list.setFixedWidth(300)
        self.mail_list.setHorizontalScrollBarPolicy(Qt.ScrollBarAlwaysOff)
        self.mail_list.setWordWrap(True)
        self.mail_list.setTextElideMode(Qt.ElideRight)

        for mail in emails:
            item = QListWidgetItem(
                f"{mail.sender_name}\n{mail.subject}\n{mail.timestamp}"
            )
            item.setData(Qt.UserRole, mail)
            self.mail_list.addItem(item)

        self.mail_list.itemClicked.connect(self.select_mail)


        #Mailbox
        mailbox_container = QWidget()
        mailbox_layout = QVBoxLayout(mailbox_container)
        mailbox_layout.setContentsMargins(0,0,0,0)
        mailbox_layout.setSpacing(0)

        #self.mail_label = QLabel("Betreff")
        self.mail_label = QLabel("Keine Nachricht ausgewählt")
        self.mail_label.setObjectName("mail_label")
        self.mail_label.setTextInteractionFlags(Qt.TextSelectableByMouse)
        #self.mail_label.setWordWrap(True)

        #self.mail_meta = QLabel("Von: Absender <email>\nDatum")
        self.mail_meta = QLabel("")
        self.mail_meta.setObjectName("mail_meta")
        self.mail_meta.setTextInteractionFlags(Qt.TextSelectableByMouse)

        self.mail_body = QTextBrowser()
        self.mail_body.setOpenExternalLinks(True)
        self.mail_body.setHtml("Wähle eine E-Mail aus.")

        #Links
        self.mail_body.setOpenLinks(False)
        self.mail_body.setOpenExternalLinks(False)
        self.mail_body.highlighted.connect(self.show_hover_url)
        self.mail_body.anchorClicked.connect(lambda _: None)

        self.attachment_label = QLabel()
        self.attachment_label.setObjectName("attachment_label")
        self.attachment_label.setTextInteractionFlags(Qt.TextSelectableByMouse)
        self.attachment_label.setContentsMargins(0, 0, 0, 0)
        self.attachment_label.hide()

        #Füge zusammen
        splitter.addWidget(self.folder_tree)
        splitter.addWidget(self.mail_list)
        splitter.addWidget(mailbox_container)
        splitter.setSizes([200, 300, 700])

        main_layout.addWidget(top_bar_widget)
        main_layout.addWidget(top_bar_line)
        main_layout.addWidget(splitter)

        mailbox_layout.addWidget(self.mail_label)
        mailbox_layout.addWidget(self.mail_meta)
        mailbox_layout.addWidget(self.mail_body, 1)
        mailbox_layout.addWidget(self.attachment_label)

        self.setCentralWidget(central_widget)

    def apply_style(self) -> None:
        with open("skripts/style.qss", "r", encoding="utf-8") as file:
            self.setStyleSheet(file.read())


    def select_folder(self, item: QTreeWidgetItem) -> None:
        self.current_folder = item.data(0, Qt.UserRole)
        self.load_mails()

    def show_mail(self, mail:Email) -> None:
        self.mail_label.setText(mail.subject)
        self.mail_meta.setText(f"Von: {mail.sender_name} <{mail.sender}>\n{mail.timestamp}")
        self.mail_body.setHtml(f"""
            <html>
            <body style='color:black; font-size:15px;'>
                {mail.body}
            </body>
            </html>"""
            )

        attachment = mail.attachment
        if attachment:
            self.attachment_label.setText(f" 📎 Anhang: {attachment}")
            self.attachment_label.show()
        else:
            self.attachment_label.hide()

    def select_mail(self, item: QListWidgetItem) -> None:
        mail = item.data(Qt.UserRole)
        mail.read = True
        item.setText(self.format_mail(mail))

        self.show_mail(mail)

    def format_mail(self, mail: Email) -> str:
        unread_marker = "● " if not mail.read else ""
        return f"{unread_marker}{mail.sender_name}\n{mail.subject}\n{mail.timestamp}"

    def load_mails(self) -> None:
        self.mail_list.clear()

        visible_mails = [mail for mail in emails if mail.folder == self.current_folder]

        for mail in visible_mails:
            item = QListWidgetItem(self.format_mail(mail))
            item.setData(Qt.UserRole, mail)
            self.mail_list.addItem(item)

        self.folder_title.setText(f"    {self.current_folder}")
        count = len(visible_mails)
        if count == 1:
            self.folder_count.setText(f"    {count} E-Mail")
        else:
            self.folder_count.setText(f"    {count} E-Mails")

        return

    def load_folder(self) -> None:
        self.folder_tree.clear()

        folders =[
            ("📥 Posteingang","Posteingang"),
            ("📤 Gesendet","Gesendet"),
            ("⭐️ Markiert","Markiert"),
            ("🏛️ Archiv", "Archiv"),
            ("🗑️ Papierkorb", "Papierkorb")
        ]

        for label, name in folders:
            item = QTreeWidgetItem([label])
            item.setData(0, Qt.UserRole, name)
            self.folder_tree.addTopLevelItem(item)
            if name == self.current_folder:
                self.folder_tree.setCurrentItem(item)

    def move_mail(self, target_folder: str) -> None:
        item = self.mail_list.currentItem()

        if item is None:
            return

        mail = item.data(Qt.UserRole)
        if mail.folder == "Posteingang":
            self.total_sorted += 1

        if mail.real_folder == target_folder:
            self.correct_sorted += 1

        mail.folder = target_folder

        self.load_mails()
        self.reload_mail()
        self.check_evaluate()

    def archiv_mail(self) -> None:
        self.move_mail("Archiv")

    def mark_mail(self) -> None:
        self.move_mail("Markiert")

    def delete_mail(self) -> None:
        self.move_mail("Papierkorb")

    def reload_mail(self) -> None:
        if self.mail_list.count() > 0:
            self.mail_list.setCurrentRow(0)
            self.select_mail(self.mail_list.currentItem())
        else:
            self.mail_label.setText("Keine Nachricht ausgewählt")
            self.mail_meta.setText("")
            self.mail_body.setHtml("")
            self.attachment_label.hide()

    def check_evaluate(self) -> None:
        posteingang_mails = [m for m in emails if m.folder == "Posteingang"]

        if len(posteingang_mails) == 0 and not self.evaluated:
            self.evaluated = True
            self.show_results()

    def show_results(self) -> None:
        msg = QMessageBox(self)
        msg.setWindowTitle("Auswertung")
        msg.setTextFormat(Qt.RichText)

        if self.correct_sorted != self.total_sorted:
            text = (
                f"""
                <h3> Auswertung </h3>
                
                <div style="font-weight: normal;">
                <p align="justify">
                Dein Opa bedankt sich bei dir und bearbeitet die markierten Mails. Doch nach einigen Tagen passiert etwas Seltsames. Es stellt sich raus, dass du mindestens eine Nachricht markiert hast, obwohl sie nicht echt war. Dein Opa und du seid auf (einen) Täuschungsversuch(e) reingefallen, wodurch Kriminelle nun seine Zugangsdaten haben.
                </p>
                <p align="justify">
                Aber alles gut! Das war nur ein Test. Aber du hast gesehen, wie schnell es passieren kann.
                </p>
                <br>
                <p align="center">
                Es wurden <b>{self.correct_sorted}</b> von <b>{self.total_sorted}</b> E-Mails wurden korrekt sortiert.
                </p>
                <br>
                </div>
                """
            )
        else:
            text = (
                f"""
                <h3> Auswertung </h3>
                <div style="font-weight: normal;">
                Dein Opa bedankt sich bei dir. Und noch besser: einige Tage später schaut sich deine Mama nochmal das Postfach an und sieht, dass unter den gelöschten Mails Täuschungsversuche waren. Hätte dein Opa diese beantwortet, hätten Kriminelle seine Zugangsdaten erhalten können.
                </p>
                <p align="justify">
                Doch du hast alles erfolgreich erkannt und gelöscht. Sehr gut!
                </p> 
                <br>
                <p align="center">
                Es wurden <b>{self.correct_sorted}</b> von <b>{self.total_sorted}</b> E-Mails wurden korrekt sortiert.
                </p>
                <br>
                </div>
                """
            )

        msg.setText(text)
        button_ok = msg.addButton("Zurück zum Postfach", QMessageBox.RejectRole)
        button_end = msg.addButton("Programm beenden", QMessageBox.AcceptRole)

        button_box = msg.findChild(QDialogButtonBox)
        button_box.setCenterButtons(True)

        msg.exec()

        if msg.clickedButton() == button_end:
            exit(0)



    def show_hover_url(self,url) -> None:
        if url.isEmpty():
            self.link_label.clear()
        else:
            self.link_label.setText(url.toString())



