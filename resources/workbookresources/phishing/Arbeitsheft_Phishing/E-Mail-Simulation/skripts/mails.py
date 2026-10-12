import datetime
import random

class Email:
    def __init__(self, sender,sender_name, subject, body, real_folder, attachment,timestamp):
        self.sender = sender
        self.sender_name = sender_name
        self.receiver = "opa.jürgen@gmail.com"
        self.subject = subject
        self.body = body
        self.folder = "Posteingang"
        if timestamp:
            self.timestamp = timestamp
        else:
            self.timestamp = datetime.datetime.now()
        self.read = False
        self.real_folder = real_folder
        self.attachment = attachment

emails = [

    Email(
        sender="angebote@check25.net",
        sender_name="Check24",
        subject="25% Rabatt auf alle Reisen nach Griechenland.",
        body="""
        Ein echter Knaller! <br><br>
        Noch bis 25.6.2026 ihr Tagesgeld mit bis zu 5,25% unter:<br><br>
        <a href="http://check35.net/angebot">
        http://check35.net/angebot
        </a><br>
        sichern.,<br><br>
        Hier check‘ ich alles
        """,
        real_folder= "Papierkorb",
        attachment = None,
        timestamp = "23.06.26 13:12"
    ),

    Email(
        sender="schnurzilla@gmail.com",
        sender_name="Karin Müller",
        subject="Letzte Erinnerung: Anmeldung 45 Jahre Klassentreffen",
        body="""
        <p>Hallo ihr Lieben,</p>
        <p>
        dieses Jahr jährt sich unser Abschluss um 45 Jahre. Das wollen wir am 10.10 groß
        feiern! Ihr habt mir immer noch nicht geschrieben, ob ihr teilnehmen wollt.
        Bitte gebt mir <u>bis Freitag</u> Bescheid, ob ihr bei der Sause dabei seid, damit ich planen kann.
        </p>
        <p>
        Liebe Grüße<br>
        Eure Karin
        </p>
        """,
        real_folder= "Markiert",
        attachment = None,
        timestamp = "20.06.26 15:42"
    ),

    # 3. Normale Mail
    Email(
        sender="lotto@gewinn.ior",
        sender_name="Lotto123",
        subject="Sie haben 200€ gewonnen",
        body="""
        <p>
        Herzlichen Glückwunsch! Sie haben bei der letzte Lotto-Ziehung 200€ gewonnen. Holen Sie jetzt ihren Gewinn ab.
        </p>
        <a href="http://www.lotto-gewinne-gewinne.io/gewinne-abholen">
        www.lotto-gewinne-gewinne.io/gewinne-abholen
        </a>
        """,
        real_folder= "Papierkorb",
        attachment = None,
        timestamp = "17.06.26 14:32"
    ),

    # 4. Spam
    Email(
        sender="klarna@zahl-erinnerung.net",
        sender_name="Klarna Zahlungserinnerung",
        subject="Letzte Mahnung - offene Rechnung",
        body="""
        <img src="pics/Klarna.png" alt="Klarna Logo" width="150">
        <h3><b>Letzte Mahnung - offene Rechnung</b></h3> 
        <p>Sehr geehrte Kundin,</p>
        <p>Trotz mehrerer Zahlungsaufforderungen ist deine Rechnung weiterhin offen</p>
        <p>Bitte begleiche den Rückstand <b> spätestens bis zum 25. Juni 2026</b>. Erfolgt bis zu diesem Datum keine Zahlungseingang,
        sehen wir uns gezwungen, ein Inkassobüro zu beauftragen. Dadurch entstehen zusätzliche Gebühren und es können weitere rechtliche 
        Schritte eingeleitet werden</p>
        <p>Um zusätzliche Kosten und Einschränkungen zu vermeiden, bezahle jetzt unmittelbar nach Erhalt dieser Mail.</p>
        <a href="http://www.klarna.bezahlen.ru">       
        www.klarna.einloggen.ru
        </a>
        <p>Dieser Link ist nach dem Aussenden zwei Tage lang gültig.</p>
        """,
        real_folder= "Papierkorb",
        attachment = None,
        timestamp= "23.06.26, 12:00"
    ),

    # 5. Spam
    Email(
        sender="info@infos.joyn.de",
        sender_name="Joyn",
        subject= "Deine Joyn+ Kündigung",
        body="""
        <img src="pics/joyn.png" alt="Joyn Logo" width="610">
        <h3 align="center">Hiermit bestätigen wir deine Kündigung.</h3>
        <hr style="border: none; border-bottom: 1px solid #000000; margin: 16px;">
        <p>Schade, dass du Joyn+ nicht mehr nutzen möchtest. Selbstverständlich bestätigen wir
        dir deine Kündigung.</p>
        <p>Du kannst dein Joyn+ Abo bis zum Ende deiner Vertragslaufzeit nutzen.</p>
        <p>Dein Joyn Account bleibt weiterhin bestehen. Auch nach Ablauf der Vertragslaufzeit bleibt dein Joyn Account weiterhin bestehen und du kannst alle kostenlosen Vorteile nutzen. </p>
        <p>Die Joyn+ Inhalte sind ab dem 01.07.2026 nicht mehr zugänglich. Nach Ende deines
        Abos kannst du jederzeit ein neues Joyn+ Abo abschließen.</p>
        <p>Wende dich bei weiteren Fragen zu deiner Kündigung gerne an unseren
        Kundenservice.</p>
        <p>Viele Grüße <br>
        dein Joyn Team</p>
        <hr style="border: none; border-bottom: 1px solid #aaaaaa; width: 100px; margin: 20px auto;">
        <p style="color:#aaaaaa; text-align: center"> Seven.One Entertainment Group GmbH, Medienallee 7, 85774 Unterföhring, <br>
         Sitz: Unterföhring, Landkreis München –<br>
        Geschäftsführer: Nicole Agudo Berbel, Marco Giordani, Nicola Lussana,<br>
        Dr. Markus Messerer, Henrik Pabst (Programmv.), Bobby Rajan </p>
        """,
        real_folder= "Archiv",
        attachment = None,
        timestamp = "24.06.26, 12:00"
    ),

    Email(
        sender="post.b@nk.to",
        sender_name="Postbank",
        subject="Verifizieren Sie ihr Konto!",
        body="""
        <img src="pics/postbank.png" alt="Postbank Logo" width="630">
        <p>Geschätzte Kunden,</p>
        <p>wir aktualisieren unser Nutzungsbedingungen und bitten um eine Verifizierung Ihrer
        Daten, damit Sie unsere Dienste weiter verwenden können.</p>
        <p><b>Frist:</b> Bitte verifizieren Sie Ihre Daten bis zum <b>27. Juni2026</b> , da sonst eine temporäre
        Nutzungssperre veranlasst wird.</p>
        <p>Jetzt verifizieren: <a href="https://www.post.bankde.ve/rify"> www.postbank.de/verifizieren </a></p>
        <p>Mit freundlichen Grüßen<br>
        Ihr Postbank-Team</p>
        <div style="background-color:#f2f2f2; font-size:10pt; color:#555; text-align:center; padding: 60px 30px; margin-top: 30px">
        <br>
        © 2026 Postbank - eine Niederlassung der Deutsche Bank AG
        <br>
        </div>
    """,
        real_folder= "Papierkorb",
        attachment = None,
        timestamp = "23.06.26, 12:10"
    ),

    Email(
        sender="info@medimarkl.com",
        sender_name="MediaMarkt Angebote",
        subject="Apple Week",
        body="""
        <img src="pics/MediaMarkt.png" alt="MediaMarkt Logo" width="630">
        <p>Hast du schon gehört?</p>
        <p>Bei MediaMarkt gibt es aktuell die große Apple Week mit großartigen Angeboten von
        Apple & unseren passenden Services. Jetzt anmelden und Angebote sichern.</p>
        <p>Nur bis zum <b>15.07.26</b></p>
        <a href="bityl.ve/R2D2"> https://www.mediamarkt.de/de/campaign/apple-week</a>
        <p>Ihr MediaMarkt Team</p>
        """,
        real_folder= "Papierkorb",
        attachment = None,
        timestamp = "22.06.26, 16:17"
    ),

    Email(
        sender="sonnenblume123@web.de",
        sender_name="Brunhilde Jaspers",
        subject="Super Angebot",
        body="""
        <p>Lieber Jürgen,</p>
        <p>wir haben doch erst letztens darüber geredet, dass du nach einem neuen
        Telefonvertrag für deine Enkelin suchst. Ich habe <a href="https://www.verivox.de/handyvertrag/">hier</a> ein super Angebot gefunden.</p>
        <p>Vielleicht ist das ja was für euch.</p>
        <p>Ich hoffe, es geht die gut.</p>
        <p>Liebe Grüße<br>
        Deine Brunhilde</p>
        """,
        real_folder= "Markiert",
        attachment = None,
        timestamp = "17.06.26 16:41"
    ),

    Email(
        sender="Deutsche-Telekom@hawai.kr",
        sender_name="Deutsche Telekom Service",
        subject=" Exklusives Dankeschön für Ihre Treue",
        body="""
        <img src="pics/telekom.png" alt="Button" width="630">
        <p align="center">Sehr geehrte Kunde,</p>
        <p align="center"> Sie sind seit bereits 20 Jahren Kunde bei uns. Dafür möchten wir Danke sagen!
        Sie erhalten eine exklusiven 65% Rabatt-Code auf Telekom WLAN-Verstärker.</p>
        <p align="center">Ihr persönlicher Code: FIRST200</p>
        <p align="center">Füllen sie in nur 3 Minuten folgendes Formular aus:</p>
        <div style="text-align:center">
        <a href="https://www.bityl.co/Z0i1">
            <img src="pics/button_telekom.png" align="center" alt="Button" width="350" >
        </a>
        </div>
        <p align="center">Achtung: Dieses Angebot gilt nur für die ersten 200 Kunden.</p>
        <p align="center">Mit freundlichen Grüßen <br>
        Ihre Telekom-Service</p>
        <div style="background-color:#f2f2f2; font-size:10pt; color:#aaaaaa; text-align:center; padding: 60px 30px; margin-top: 30px">
        <p align="center">
        <br>
        Diese E-Mail wurde an Ihre registrierte Telekom-Adresse gesendet.<br>
        Das Angebot ist personalisiert und nicht übertragbar. 
        <br>
        <a href="https://www.bityl.co/Z0i1" align="center">
            <u>Abmelden</u>
        </a>
        <br><br>
        © 2026 Deutsche Telekom AG - Alle Rechte vorbehalten
        <br>
        </p>
        </div>
        """,
        real_folder= "Papierkorb",
        attachment = None,
        timestamp = "24.06.26 17:24"
    ),

    Email(
        sender="no-reply@toogoodtogo.com",
        sender_name="Too Good To Go",
        subject="Aktion erforderlich, um deinen Too Good To Go-Account aktiv zu halten",
        body="""
        <img src="pics/toogoodtogo.png" alt="toogoodtogo" width="600">
        <p>Hallo Jürgen, <br><br>
        Wir haben festgestellt, dass du deinen Too Good To Go-Account seit einiger Zeit nicht genutzt hast. Accounts, die drei Jahre lang inaktiv sind, werden gemäß unserer Richtlinie zur Datenspeicherung gelöscht.<br><br>
        <b>Dein Account wird am 26.06.26 gelöscht.</b> Nach diesem Datum werden dein Account und alle zugehörigen Daten dauerhaft aus unserem System gelöscht.<br><br>
        Wenn du deinen Account behalten möchtest, melde dich einfach vor dem 26.05.26 in der App an. Wenn du ausgeloggt bist, musst du dich erneut anmelden, damit wir deine Aktivität erfassen können.</p>
        <div style="text-align:center">
        <a href="https://toogoodtogo.com/">
            <img src="pics/button2g2g.png" align="center" alt="Button" width="350" >
        </a>
        </div>
        <p>
        Wenn du deinen Account nicht behalten möchtest, musst du nichts weiter tun.<br><br>
        Wenn du Too Good To Go in Zukunft wieder nutzen möchtest, kannst du jederzeit mit derselben E-Mail-Adresse einen neuen Account erstellen.
        </p>
        <p>
        Freundliche Grüße,<br>
        Too Good To Go 
        </p>
        <p style="font-size:10pt;">Dies ist eine Service-E-Mail zu deinem Account in der Too Good To Go App. Da sie den Status deines Accounts betrifft, ist es nicht möglich, sich von dieser Art von essenziellen Service-Benachrichtigungen abzumelden.
        <div style="font-size:10pt; color:grey; text-align:center">
        <a href="https://share.toogoodtogo.com/contactus/">
            <u>Kontaktiere uns</u></a>
        |
        <a href="https://www.toogoodtogo.com/de/legal/terms-and-conditions-using-the-app">
            <u>Allgemeine Geschäfftsbedingungen</u></a>
        |
        <a href="https://www.toogoodtogo.com/de/legal/privacy">
            <u>Datenschutzbestimmungen</u></a>
        <br>
        Too Good To Go - Köpenicker Straße 154 A - 10997 Berlin - DE
        </div>
        """,
        real_folder= "Markiert",
        attachment = None,
        timestamp = "15.06.26 08:12"
    ),
    Email(
        sender="sg_tt_berlin@info-tischtennis.net",
        sender_name="SG Tischtennis Berlin e.V.",
        subject="Erinnerung: Anmeldung Sommer-Turnier",
        body="""
        <p>Hallo <b>Jürgen</b>,</p>
        <p>dein Team <b>PiranYards</b> hast dich noch immer nicht für unser Sommer-Tischtennis-Turnis am 15. Juli angemeldet.</p>
        <p>Falls ihr doch daran teilnehmen wollt, fülle bitte das beigefügte Formular aus und sende es mir bis zum <u>30.06</u> per Mail zurück.</p>
        <p>Danch ist eine Teilnahme nicht mehr möglich.</p>
        <p>Viele Grüße <br>
        Dein SG Tischtennis Berlin Team</p>
        """,
        real_folder= "Papierkorb",
        attachment = "Anmeldefomular.pdf.exe",
        timestamp = "17.06.26 14:14"
    ),
    Email(
        sender="noreply-de@info.riverty.com",
        sender_name="Riverty",
        subject="Dringend: deine Riverty Rechnung ist überfällig",
        body="""
        <img src="pics/riverty.png" alt="riverty" width="200">
        <p>Guten Tag Jürgen,</p>
        <p>die Rechnung für deine Bestellung bei Fressnapf steht noch immer zur Zahlung offen.</p>
        <p> Der Gesamtbetrag der Forderung beträgt 135,32 €. </p>
        <br>
        <a href="https://url601.info.riverty.com/ls/click?upn=u001isbfubBUbf-lm73648nu3z47JHBgjH-Bbfj7637usnkhbw7r83489ibD">
            <img src="pics/Weiter.png" alt="button" >
        </a>
        <br>
        <p>Bitte bezahle bis zum 06.07.2026 über den Zahlungslink oder die unten genannten Überweisungsdetails. Wenn deine Zahlung nicht rechtzeitig eingeht, können weitere Mahnkosten anfallen.</p>
        <p>Je früher du deine Rechnung bezahlst, desto wahrscheinlicher kannst du auch in Zukunft mit Riverty zahlen. </p>
        <br>
        <p><b>Zahlungsinformationen</b></p>
        <p>Empfänger:                       Riverty</p>
        <p>IBAN:                            DE35478400807625148353 </p>
        <p>Verwendungszweck:                2601056078719730 </p>
        <p>Betrag:                          135,32 €</p>
        <br>
        <p><b>Zusammenfassung</b></p>
        <p>Riverty Bestellreferenz:         HWT470OP</p>
        <p>Bestelldatum:                    26. Mai 2026</p>
        <p>Bezahlt:                         0,00€ </p>
        <p>Ausstehend:                      135,32 €</p>
        <br>
        <p>Hast du die Rechnung bereits bezahlt? Es kann ein paar Tage dauern, bis deine Zahlung bei uns eingeht. Bitte überprüfe den Status deiner Rechnung nach 5 Werktagen in der Riverty App.</p>
        <br>
        <p><b>Hast du Fragen zur Zahlung?</b><br>
        Erhalte schnell erste Antworten in unseren <a href="https://de.riverty.support/hc/de/categories/18537541506705-Zahlung?utm_campaign=website&utm_medium=email&utm_source=sendgrid.com">
            <u>FAQ</u></a></p>
        <p>Alle Zahlungen, die über Riverty getätigt werden, unterliegen den allgemeinen Zahlungsbedingungen der Riverty GmbH, Gütersloher Straße 123, 33415 Verl, USt-IdNr. DE815539954, HRB 9923 – Amtsgericht Gütersloh, USt-IdNr. DE815539954.</p>
        <p></p>
        """,
        real_folder= "Markiert",
        attachment = None,
        timestamp = "25.06.26 11:14"
    ),
    Email(
        sender="service@deutschestheater.de",
        sender_name="Deutsches Theater Berlin ",
        subject="Ihre Anmeldung am Webshop",
        body="""
        <p>Sehr geehrter Herr Radler, </p>
        <p>vielen Dank für die Anmeldung in unserem Online-Shop.</p>
        <p>Sie können sich mit Ihrer E-Mail-Adresse und Ihrem Kennwort jederzeit am Webshop anmelden und Ihre Kundendaten einsehen und ändern.</p>
        <a href="https://ticket.deutschestheater.de/eventim.webshop/webticket/shop?page=customereditdetails.secure">
            Zu den Kundendaten
        </a>
        <p>Mit freundlichen Grüßen</p>
        <p>Deutsches Theater Berlin</p>
        <p>Schumannstr. 13a</p>
        <p>10117 Berlin</p>
        <p><b>DT Besucherservice</b></p>
        <br>
        <img src="pics/DT.png" alt="DT"></p>
        <p><small>Kartentelefon: +49 30 284 41 225</small></p>
        <p><small>täglich 15 bis 18.30 Uhr</small></p>
        <p><small>service@deutschestheater.de</small></p>
        <p><a href="https://www.deutschestheater.de/">
            <small>www.deutschestheater.de </small>
        </a></p>
        <p><small>Bitte beachten Sie unsere Hinweise zum Datenschutz: https://www.deutschestheater.de/datenschutz/</small></p>
        <p><small></small></p>
        """,
        real_folder= "Archiv",
        attachment = None,
        timestamp = "17.06.26 12:51"
    ),
    Email(
        sender="peter.sänger@stiftung-naturschutz.de",
        sender_name="Peter Sänger",
        subject="Bitte um Rückmeldung zu unserer automatischen Mail",
        body="""
        <p>Liebe Teilnehmerinnen, liebe Teilnehmer für nächste Woche,</p>
        <p>ich habe Sie in unserem automatischen Anmeldesystem auf die Warteliste gesetzt. 
        Da wir gerade unser Anmeldesystem umstellen und Sie die ersten in diesem Jahr sind, in dem wir alles automatische machen möchten, bitte ich Sie um eine Rückmeldung:</p>
        <p>Haben Sie eine Mail bekommen</p>
        <p>Wie lautet der Text der Antwortmail? Könnten Sie mir auch ihre Antwortmail, in der die Rechnung war, zurückschicken (dann kann ich den Text evtl. für die nächsten noch korrigieren)?</p>

        <p>Vielen Dank für Ihre Unterstützung.
        Falls Sie keine Mail von unserem System bekommen haben, bitte auch bei mir melden.</p>
        <p>Mit der Bitte um Verständnis,<br>
        Peter Sänger</p>
        """,
        real_folder= "Markiert",
        attachment = None,
        timestamp = "13.06.26 11:36"
    ),
    Email(
        sender="netflix@suppor.de",
        sender_name="Netflix",
        subject="Ihr Netflix-Abo läuft ab",
        body="""
        <img src="pics/netflix.png" alt="button" width="120">
        <h3>Ihr Netflix-Abo läuft ab.</h3>
        <p>Sehr geehrter Kunde</p>
        <p>Wir haben versucht, Ihr Abo am Ende eines Abrechnungszyklus zu verlängern, aber Ihre monatliche Zahlung ist fehlgeschlagen. 
        <b>Wir mussten daher Ihr Abo kündigen.</b> Selbstverständlich würden wir Sie gerne wieder bei uns begrüßen.
        Wenn Sie Ihr Abo erneuern möchten, klicken Sie auf diesen Link.</p>
        <p>Wie lautet der Text der Antwortmail? Könnten Sie mir auch ihre Antwortmail, in der die Rechnung war, zurückschicken (dann kann ich den Text evtl. für die nächsten noch korrigieren)?</p>
        <br>
        <a href="https://bityl.co/Jb756JBJGSvd6">
            <img src="pics/reaktivieren.png" alt="button">
        </a>
        <p><b>Achtung:</b> Bei Nichtbeachtung werden Ihre Dienste innerhalb von <b>24 Stunden</b> gemäß den in unseren Verträgen definierten Bedingungen <b>vollständig eingestellt.</b></p>
        
        """,
        real_folder= "Papierkorb",
        attachment = None,
        timestamp = "26.06.26 13:36"
    ),
]

random.shuffle(emails)

