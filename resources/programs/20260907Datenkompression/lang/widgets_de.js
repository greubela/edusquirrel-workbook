window.WIDGETS_DE = {
  common: {
    emptyValue: "…"
  },
  bitflip: {
    passwordLabel: "Passwort",
    imageLabel: "Bild (128 × 128 Pixel)",
    encodingLabel: "UTF-8",
    flipButton: "Ein Bit flippen",
    resetButton: "Zurücksetzen",
    bitsInfo: "{bits} Bits zur Darstellung · {changed} Bit{plural} geändert",
    passwordCorrect: "✓ Passwort wiederhergestellt",
    passwordChanged: "Passwort verändert, nicht mehr lesbar",
    pixelChanged: "1 Pixel geändert, Bild noch erkennbar?",
    pixelChangedAt: "1 Pixel geändert (Bit {bit}), Bild noch erkennbar?",
    password: "M3inGeh3im!",
    flipBitsPassword: [47, 12, 83, 31, 96],
    flipBitsImage: [8192, 1337, 12000, 2048, 10000]
  },
  rle: {
    inputLabel: "Zeichenkette",
    loadExample: "Beispiel laden",
    originalSize: "Originalgröße",
    rleSize: "RLE-Größe",
    saving: "Ersparnis",
    tuples: "Tupel",
    chars: "Zeichen",
    savingNegative: "+{count} Zeichen ({pct}% mehr)",
    savingPositive: "−{count} Zeichen ({pct}%)",
    savingZero: "±0 Zeichen",
    emptyValue: "…",
    default: "AAABBBCCDDDDDA",
    challenges: {
      save50: "Mindestens 50 % Ersparnis erzielen (Zeichenkette mindestens 5 Zeichen lang).",
      negativeSaving: "Negative Ersparnis erzielen (mehr Zeichen als vorher; Zeichenkette mindestens 5 Zeichen lang).",
      longRun: "Gib eine Zeichenkette mit mindestens 10 gleichen Zeichen hintereinander ein."
    }
  },
  rleText: {
    originalLabel: "Originaltext, Wiederholungsläufe hervorgehoben",
    tuplesLabel: "RLE-Tupel (Zeichen, Länge), nur Läufe > 1 sind hervorgehoben",
    originalChars: "Originaltext: <strong>{count}</strong> Zeichen",
    rleChars: "RLE: <strong>{tuples}</strong> Tupel × 2 = <strong>{units}</strong> Zeichen",
    savingSmaller: "−{count} ({pct}% kleiner)",
    savingLarger: "+{count} ({pct}% größer)",
    moreTuples: "… +{count} weitere",
    sampleText:
      "Die Überwachung der Zielperson wurde am Donnerstag fortgesetzt. " +
      "Während der Überwachung der Zielperson wurden alle digitalen Daten erfasst. " +
      "Die Erfassung der Daten erfolgte ohne richterlichen Beschluss. " +
      "Da die Daten der Zielperson als streng geheim eingestuft sind, wurden die Daten direkt an die Zentrale übermittelt. " +
      "Eine Löschung der Daten der Überwachung ist nicht vorgesehen."
  },
  dict: {
    originalLabel: "Originaltext, aktuell verarbeitetes Wort hervorgehoben",
    dictTableLabel: "Wörterbuchtabelle",
    codeHeader: "Code",
    wordHeader: "Wort",
    compressedLabel: "Komprimierter Text",
    originalChars: "Originaltext: <strong id=\"dict-orig-len\">…</strong> Zeichen",
    compressedChars: "Komprimiert: <strong id=\"dict-comp-len\">…</strong> Zeichen",
    playStart: "▶ Start",
    playContinue: "▶ Fortfahren",
    playPause: "⏸ Pause",
    playDone: "✓ Fertig",
    stepButton: "Schritt vor",
    stepBackButton: "Schritt zurück",
    speedLabel: "Tempo",
    speedSlow: "Langsam",
    speedFast: "Schnell",
    tableToggleExpand: "Ganze Tabelle",
    tableToggleCollapse: "Gekürzt anzeigen",
    resetButton: "↺ Zurücksetzen",
    progress: "{current} / {total} Wörter",
    progressStart: "0 / {total} Wörter",
    noEntries: "noch keine Einträge",
    notCompressed: "noch nichts komprimiert",
    unusedRemoved: "{count} nicht genutzte Eintrage entfernt",
    savingSmaller: "−{count} ({pct}% kleiner)",
    savingLarger: "+{count} ({pct}% größer)",
    savingZero: "±0",
    sampleText:
      "Die Überwachung der Zielperson wurde am Donnerstag fortgesetzt. " +
      "Während der Überwachung der Zielperson wurden alle digitalen Daten erfasst. " +
      "Die Erfassung der Daten erfolgte ohne richterlichen Beschluss. " +
      "Da die Daten der Zielperson als streng geheim eingestuft sind, wurden die Daten direkt an die Zentrale übermittelt. " +
      "Eine Löschung der Daten der Überwachung ist nicht vorgesehen."
  },
  blockAvg: {
    brightnessLabel: "Helligkeitsstufen",
    colorLabel: "Farbgenauigkeit",
    blockSingle: "1×1 px",
    blockPlural: "{size}×{size} px Blöcke",
    loadError: "Bilddaten konnten nicht geladen werden.",
    sizeEstimate: "Geschätzte Dateigröße:",
    sizePct: "~{pct} % des Originals",
    challenges: {
      sizeUnder20: "Dateigröße unter 20 % des Originals.",
      faceDetails: "Gesichtsdetails erkennbar.",
      eyeColor: "Augenfarbe erkennbar."
    }
  },
  blockSize: {
    loading: "Bild wird geladen …",
    blockSizeLabel: "Blockgröße",
    original: "Original",
    compressed: "Komprimiert",
    saving: "Ersparnis",
    savingBits: "Ersparnis in Bits: ",
    zeroBit: "0 Bit",
    bitUnit: " Bit",
    imageLoaded: "Bild: {width} × {height} Pixel",
    imageUnavailable: "Bilddaten nicht verfügbar",
    imageLoadError: "Bild konnte nicht geladen werden",
    savingPositive: "−{count} Bit ({pct}%)",
    savingNegative: "+{count} Bit ({pct}%)",
    savingZero: "±0 Bit",
    deltaLabel: "{count} Bit ({pct}%)",
    emptyValue: "…"
  },
  sortWidget: {
    poolLabel: "Noch nicht zugeordnet",
    submitButton: "Zuordnung prüfen",
    resetButton: "Zurücksetzen",
    placeAllFirst: "Bitte ordne zuerst alle Karten zu.",
    errorCountTemplate: "{count} Fehler",
    guessNote: "Für diese Datei zuerst eine Vermutung mit Begründung eintragen:",
    guessPrompt: "Deine Vermutung mit Begründung:",
    guessRequired: "Bitte zuerst eine Vermutung mit Begründung eintragen.",
    reasonPrompt: "Begründung für deine Zuordnung:",
    reasonRequired: "Bitte gib für jede Datei eine Begründung an.",
    summaryAllCorrect: "Alles richtig!",
    columnPrefix: "→ "
  },
  efficiency: {
    submitButton: "Auswertung",
    resetButton: "↺ Zurücksetzen",
    poolLabel: "Noch nicht zugeordnet",
    placeAllFirst: "Bitte ordne zuerst alle Dateien zu.",
    errorCountTemplate: "{count} Fehler",
    columnPrefix: "→ ",
    reasonPrompt: "Begründung für deine Zuordnung:",
    reasonRequired: "Bitte gib für jede Datei eine Begründung an.",
    optionRle: "RLE",
    optionDict: "Wörterbuch",
    optionNone: "Keines",
    selectAllFirst: "Bitte wähle für jede Datei ein Verfahren aus, bevor du die Auswertung startest.",
    summaryCorrect: "{correct} von {total} richtig",
    summaryAllCorrect: "✓ Alle Zuordnungen sind korrekt!",
    summaryWrongLine: "✗ {label}: <span style=\"font-weight:600\">{answer}</span>",
    files: [
      {
        id: "report",
        label: "Langer unverschlüsselter Bericht",
        desc: "Ein mehrseitiger Fließtext (.txt), in dem Wörter wie \"die\", \"der\", \"Überwachung\" oder \"Zielperson\" immer wieder auftauchen.",
        correct: "dict",
        feedbackCorrect: "Richtig! Ein Fließtext enthält oft viele wiederkehrende Wörter, also genau die Stärke der Wörterbuchkompression.",
        feedbackWrong: "In einem Fließtext wiederholen sich nicht einzelne Zeichen, sondern ganze Wörter. Welches Verfahren kann solche wiederholten Wörter durch kurze Verweise ersetzen?"
      },
      {
        id: "encrypted",
        label: "Verschlüsseltes Dokument",
        desc: "Eine verschlüsselte Datei. Der Inhalt ist ohne Schlüssel nicht lesbar und erscheint als zufällige Bitfolge.",
        correct: "none",
        feedbackCorrect: "Richtig! Verschlüsselte Daten erscheinen wie Zufallsdaten. Darin lassen sich keinerlei Muster oder Wiederholungen finden, daher funktioniert weder RLE noch Wörterbuchkompression.",
        feedbackWrong: "Überlege: Wie sieht eine verschlüsselte Datei aus? Enthält sie überhaupt noch erkennbare Wiederholungen oder Muster, die ein Kompressionsverfahren ausnutzen könnte?"
      },
      {
        id: "table",
        label: "Tabelle mit Namen und Adressen",
        desc: "Eine CSV-Tabelle mit 50.000 Einträgen. Viele Personen teilen sich denselben Ort, dieselbe Straße, dieselbe Postleitzahl oder denselben Nachnamen.",
        correct: "dict",
        feedbackCorrect: "Richtig! In einer Tabelle mit Adressen wiederholen sich viele Werte (Ortsnamen, Straßen, Titel). Die Wörterbuchkompression kann diese Mehrfachnennungen durch kurze Codes ersetzen.",
        feedbackWrong: "In der Tabelle kommen viele identische Wörter mehrfach vor (gleiche Städte, gleiche Straßennamen). Welches Verfahren kann diese wiederholten Wörter durch kurze Verweise ersetzen?"
      },
      {
        id: "scan",
        label: "Schwarz-Weiß-Scan eines Dokuments",
        desc: "Ein eingescanntes Schriftstück in Schwarz-Weiß (z. B. ein handschriftlicher Brief in einem Bildformat). Große weiße Flächen mit schwarzer Textspur.",
        correct: "rle",
        feedbackCorrect: "Richtig! Ein Schwarz-Weiß-Scan besteht aus vielen aufeinanderfolgenden Pixeln derselben Farbe, also ideale Voraussetzung für RLE.",
        feedbackWrong: "Ein Scan in Schwarz-Weiß hat viele lange Läufe identischer Pixel (weiße Flächen, schwarze Linien). Welches Verfahren nutzt genau solche Wiederholungen auf Pixelebene aus?"
      }
    ]
  },
  fileInspector: {
    txtFilename: "bericht.txt",
    txtSize: "144 Bytes",
    txtContent:
      "Sehr geehrte Damen und Herren,\n\n" +
      "hiermit möchten wir uns für die\n" +
      "gute Zusammenarbeit bedanken.\n\n" +
      "Mit freundlichen Grüßen\n" +
      "Ihr Muster-Team",
    docxFilename: "bericht.docx",
    docxSize: "~13,5 KB (13.460 Bytes)",
    contentTypesDesc: "Definitionen für Bilder, XML, Stylesheet usw.",
    relsDesc: "Beziehungsstrukturen zwischen Dateien",
    coreDesc: "Autor, Erstelldatum, Änderungsdatum",
    appDesc: "Anwendungsdetails (Word-Version, Seiten)",
    documentDesc: "Der formatierte Text (welcher dann in Word angezeigt wird)",
    stylesDesc: "Schriftarten und Layout",
    visualLabel: "So wirkt der sichtbare Inhalt",
    structureLabel: "Interne Struktur (.docx als ZIP-Container)"
  },
  textVsJpeg: {
    brightnessLabel: "Helligkeitsstufen",
    colorLabel: "Farbgenauigkeit",
    blockSingle: "1×1 px",
    blockPlural: "{size}×{size} px Blöcke",
    loadError: "Screenshot konnte nicht geladen werden.",
    sizeEstimate: "Geschätzte Dateigröße:",
    sizePct: "~{pct} % des Originals",
    textCompare: "Textdatei test.txt: {textSize} · Screenshot: {jpegSize}: {verdict}",
    compareLarger: "größer als die Textdatei (+{diff})",
    compareSmaller: "kleiner als die Textdatei (−{diff})",
    compareEqual: "ungefähr gleich groß wie die Textdatei",
    zoomLabel: "Zoom",
    zoomHint: "Mausrad über dem Bild zum Zoomen · bei Vergrößerung scrollen"
  },
  lossyClosing: {
    submitButton: "Auswertung",
    resetButton: "↺ Zurücksetzen",
    poolLabel: "Noch nicht zugeordnet",
    placeAllFirst: "Bitte ordne zuerst alle Dateitypen zu.",
    errorCountTemplate: "{count} Fehler",
    columnPrefix: "→ ",
    reasonPrompt: "Begründung für deine Zuordnung:",
    reasonRequired: "Bitte gib für jeden Dateityp eine Begründung an.",
    optionLossy: "Verlustbehaftet",
    optionLossless: "Nur verlustfrei",
    optionNone: "Nicht sinnvoll",
    selectAllFirst: "Bitte ordne zuerst alle Dateitypen zu, bevor du die Auswertung startest.",
    summaryCorrect: "{correct} von {total} richtig",
    summaryAllCorrect: "✓ Alle Zuordnungen sind korrekt!",
    summaryWrongLine: "✗ {label}: <span style=\"font-weight:600\">{answer}</span>",
    files: [
      {
        id: "photos",
        label: "Beweisfotos",
        desc: "Drei Fotos mit unterschiedlichem Zweck: Tatort mit Kennzeichen, Person zur Identifikation, Dokument als Bild.",
        correct: "lossy",
        feedbackCorrect: "Richtig! Bei Fotos nutzt verlustbehaftete Kompression Eigenschaften des Auges. Wichtig ist nur, kritische Details nicht zu stark zu reduzieren.",
        feedbackWrong: "In dieser Sektion hast du Bilder mit Reglern komprimiert. Bei welchem Dateityp ist gezieltes Weglassen von Bildinformationen sinnvoll?"
      },
      {
        id: "video",
        label: "Überwachungsvideo",
        desc: "Ein kurzes Video aus der Überwachung (z. B. MJPEG).",
        correct: "lossy",
        feedbackCorrect: "Richtig! Video besteht aus vielen Einzelbildern. Verfahren wie MJPEG komprimieren jedes Bild verlustbehaftet, ähnlich wie JPEG bei Fotos.",
        feedbackWrong: "Ein Video ist eine Folge von Bildern. Welche Kompressionsart hast du in dieser Sektion bei Bildern ausprobiert?"
      },
      {
        id: "report",
        label: "Kurzer unverschlüsselter Bericht",
        desc: "Ein Textdokument (.txt) mit lesbarem Fließtext. Jeder Buchstabe hängt von exakt den richtigen Bits ab.",
        correct: "lossless",
        feedbackCorrect: "Richtig! Lesbarer Text verträgt keine veränderten Bits. Verlustfreie Verfahren wie das Wörterbuch aus Sektion 1 können aber Wiederholungen nutzen.",
        feedbackWrong: "Was passiert bei lesbarem Text, wenn einzelne Bits verändert werden? Welche Kompressionsart aus Sektion 1 wäre hier passend?"
      },
      {
        id: "encrypted",
        label: "Verschlüsseltes Dokument",
        desc: "Eine verschlüsselte Datei. Der Inhalt ist ohne Schlüssel nicht lesbar und erscheint als zufällige Bitfolge.",
        correct: "none",
        feedbackCorrect: "Richtig! Verschlüsselte Daten enthalten keine nutzbaren Muster. Verlustbehaftete Kompression würde Bits verändern und den Inhalt unbrauchbar machen, verlustfreie bringt kaum Ersparnis.",
        feedbackWrong: "Wie sieht verschlüsselter Inhalt aus? Enthält er Muster, die Kompression nutzen könnte? Was passiert, wenn Bits verändert werden?"
      }
    ]
  },
  zipArchive: {
    overheadLabel: "Overhead",
    pendingMark: "…",
    simulateButton: "Versand simulieren",
    simulateAgain: "Erneut simulieren",
    scenarioA: "Szenario A: Einzelversand",
    scenarioADesc: "10 Textdokumente einzeln übermitteln",
    scenarioB: "Szenario B: Archivversand",
    scenarioBDesc: "Alle Dokumente in einem Archiv gebündelt",
    archiveName: "beweise.zip",
    filesToSend: "Dateien zum Versand:",
    transfers: "Übertragungsvorgänge:",
    totalSize: "Gesamtgröße:",
    processIdle: "Bereit",
    processDone: "Fertig",
    processSteps: [
      "Speicher reservieren",
      "Datei öffnen",
      "Daten kopieren",
      "Datei schließen"
    ],
    overheadGloss:
      "Beim Kopieren vieler kleiner Dateien muss das Betriebssystem für jede einzelne Datei extra Verwaltungsarbeit leisten, " +
      "wie zum Beispiel sie im Dateisystem suchen, öffnen und wieder schließen. Dieser Overhead summiert sich bei Hunderten oder Tausenden von Dateien. " +
      "Packt man sie vorher in ein ZIP-Archiv, fällt diese Arbeit beim Kopieren nur einmal für eine einzige Datei an, was deutlich schneller ist.",
    faqSummary: "Hinweise zur Simulation ein- und ausklappen",
    faq: [
      {
        q: "Warum sind die Gesamtgrößen fast gleich?",
        a: "Die Gesamtgröße ist in beiden Szenarien fast gleich ({sizeHint}). Hier wird noch nicht komprimiert, sondern nur die Dateien in einen Container gebündelt."
      },
      {
        q: "Warum ist der Archivversand beim Kopieren schneller?",
        a: "Der {overhead} zum Kopieren einer neuen Datei fällt bei Archiven nur einmal an, während bei einzelnen Dateien der Overhead für jede Datei einzeln berechnet wird. Je mehr Dateien, desto bemerkbarer der Unterschied."
      },
      {
        q: "Warum wirkt die Simulation so langsam?",
        a: "Für diese kleinen, wenigen Dateien wäre der Unterschied auf modernen Festplatten kaum beobachtbar. Die Simulation zeigt deshalb eine stark verlangsamte Darstellung des Kopierens."
      },
      {
        q: "Kann ich den Unterschied auf meinem Rechner nachvollziehen?",
        a: "Falls du viele kleine Dateien (in Summe >10 GB, bei einer SSD) hast, kannst du das nachvollziehen, indem du die einzelnen Dateien auf einmal kopierst und das mit der Kopierdauer für das Archiv vergleichst."
      }
    ],
    files: [
      {
        name: "notiz_01.txt",
        text: "Zeuge: M. Keller, 14.03., ca. 22:15 Uhr\nBeobachtung: Zwei Personen verließen das Gebäude über den Hintereingang.\nHinweis: Kennzeichen nicht lesbar, Fahrzeug dunkelblau."
      },
      {
        name: "notiz_02.txt",
        text: "Zeuge: M. Keller, 14.03., ca. 22:15 Uhr\nBeobachtung: Kurz darauf folgte ein weiteres Fahrzeug in gleicher Richtung.\nHinweis: Kein direkter Kontakt zwischen den Personen erkennbar."
      },
      {
        name: "notiz_03.txt",
        text: "Zeuge: S. Brandt, 15.03., Vormittag\nBeobachtung: Mehrere Anrufe über Festnetz, Gespräche in gedämpftem Ton.\nHinweis: Inhalt der Gespräche nicht verständlich."
      },
      {
        name: "notiz_04.txt",
        text: "Zeuge: S. Brandt, 15.03., Vormittag\nBeobachtung: Kurz nach den Anrufen verließ eine Person das Haus mit Aktenmappe.\nHinweis: Richtung Bahnhof, zu Fuß."
      },
      {
        name: "notiz_05.txt",
        text: "Zeuge: L. Nguyen, 16.03., Nachmittag\nBeobachtung: Treffen an der Ecke Hauptstraße / Parkweg, zwei Personen.\nHinweis: Übergabe eines versiegelten Umschlags."
      },
      {
        name: "notiz_06.txt",
        text: "Zeuge: L. Nguyen, 16.03., Nachmittag\nBeobachtung: Treffen dauerte unter fünf Minuten, danach getrennte Wege.\nHinweis: Keine weiteren Personen in unmittelbarer Nähe."
      },
      {
        name: "notiz_07.txt",
        text: "Zeuge: R. Hoffmann, 17.03., Abend\nBeobachtung: Licht im oberen Stockwerk bis nach Mitternacht.\nHinweis: Schatten von mindestens zwei Personen am Fenster."
      },
      {
        name: "notiz_08.txt",
        text: "Zeuge: R. Hoffmann, 17.03., Abend\nBeobachtung: Gegen 01:10 Uhr verlässt ein Fahrzeug den Hof.\nHinweis: Kennzeichen teilweise verdeckt, nur Endziffern lesbar."
      },
      {
        name: "notiz_09.txt",
        text: "Zeuge: A. Peters, 18.03., früher Morgen\nBeobachtung: Abfallbehälter am Haus wurde geleert, unüblich früh.\nHinweis: Eine Person mit Handschuhen, Gesicht nicht erkennbar."
      },
      {
        name: "notiz_10.txt",
        text: "Zeuge: A. Peters, 18.03., früher Morgen\nBeobachtung: Kurz darauf Abfahrt eines Lieferwagens ohne Firmenlogo.\nHinweis: Fahrzeug fuhr in Richtung Autobahnauffahrt Süd."
      }
    ]
  },
  filesystem: {
    memoryCard: "Speicherkarte:",
    fileTypesOverview: {
      title: "Dateitypen im Paket",
      items: [
        {
          ext: ".mp4",
          label: "Videodatei",
          text:
            "Bündelt Bild- und Tonspuren zusammen. Metadaten wie Aufnahmedauer, Autor und oft Erstellungsdatum werden zusammen mit den Bilddaten gespeichert."
        },
        {
          ext: ".txt",
          label: "Textdokument",
          text:
            "Enthält nur Klartext ohne Formatierung. Keine eingebetteten Metadaten im Dateiinhalt, nur der Dateisystem-Eintrag (Name, Änderungsdatum) liefert Zusatzinfos."
        },
        {
          ext: ".csv",
          label: "Tabellenkalkulation",
          text:
            "Textformat mit Trennzeichen zwischen Spalten und wie .txt ohne eingebettete Metadaten"
        },
        {
          ext: ".docx",
          label: "Textdokument (Word)",
          text:
            "Ein Archiv mit mehreren XML-Dateien. Enthält ausführliche Metadaten (Autor, Erstellungsdatum, Anwendung) in separaten XML-Dateien und Formatierungsinformationen."
        },
        {
          ext: ".raw",
          label: "Bilddatei (Rohdaten)",
          text:
            "Unbearbeitete Daten der Kamera, sehr groß. Metadaten wie Belichtung und Kameramodell sind oft eingebettet oder in einer separaten Datei gespeichert."
        },
        {
          ext: ".tiff",
          label: "Bilddatei",
          text:
            "Verlustfreies Bildformat, oft für Scans und Archivierung. Metadaten (z. B. Auflösung, Aufnahmezeitpunkt, Standort) werden gespeichert."
        },
        {
          ext: ".psd",
          label: "Bilddatei (Photoshop)",
          text:
            "Speichert Ebenen, Bearbeitungsstände und Arbeitsinformationen neben dem Bild, daher sehr groß. Metadaten und Bearbeitungshistorie sind fester Bestandteil des Formats."
        },
        {
          ext: ".enc + .7z / .zip / .rar",
          label: "Verschlüsseltes Archiv",
          text:
            "Bündelt mehrere Dateien in einem Container mit Verzeichnisliste (Dateinamen, Größen). Verschlüsselter Inhalt ist ohne Schlüssel nicht lesbar; darauf greift Kompression kaum."
        }
      ]
    },
    typeLabels: {
      video: "Videodatei",
      text: "Textdokument",
      spreadsheet: "Tabellenkalkulation",
      image: "Bilddatei",
      encrypted: "Verschlüsseltes Archiv"
    },
    scenarios: {
      s1: "Szenario 1",
      s2: "Szenario 2",
      s3: "Szenario 3"
    },
    statsUsed: "Belegt:",
    statsFiles: "Dateien",
    statsOver: "+{size} über Kapazität",
    statsFree: "{size} frei",
    statsFull: "exakt voll",
    tooltipFiles: "{count} Dateien",
    tooltipMetadata: "Metadaten:"
  },
  filesystemSimulator: {
    scenarios: {
      s0: "Tutorial",
      s1: "Szenario 1",
      s2: "Szenario 2",
      s3: "Szenario 3"
    },
    noToolHint: "Wähle zuerst eine Maßnahme. Dateiinfos siehst du beim Überfahren mit der Maus.",
    tools: {
      convert: {
        label: "In metadatenfreies Format umwandeln / Metadaten entfernen",
        hint: "Entfernt alle Metadaten und wandelt das Format um, z. B. .docx → .txt oder .raw → .png. Klicke eine Datei an."
      },
      lossless: {
        label: "Verlustfrei komprimieren",
        hint: "Wörterbuchverfahren oder verlustfreie Bildformate. Klicke eine Datei an."
      },
      lossy: {
        label: "Verlustbehaftet komprimieren",
        hint: "Stärkere Reduktion, z. B. Video neu encodieren oder JPEG. Klicke eine Datei an."
      },
      archive: {
        label: "Archivieren",
        hint: "Mehrere Dateien auswählen (gelber Rahmen), dann „RAR-Archiv erstellen“."
      }
    },
    statsUsed: "Belegt:",
    statsFiles: "Dateien",
    statsOver: "+{size} über Kapazität",
    statsFree: "{size} frei",
    steps: "Schritte: {count}",
    metadataLabel: "Problematische Metadaten:",
    metadataYes: "noch vorhanden",
    metadataNo: "keine mehr",
    archiveSelected: "{count} ausgewählt",
    archiveCreate: "RAR-Archiv erstellen",
    archiveMetadataError: "Im Archiv siehst du die Metadaten nicht mehr. Beim Entpacken wären sie aber wieder da. Entferne problematische Metadaten zuerst.",
    reset: "Zurücksetzen",
    success: "Geschafft! Alles passt auf die Karte und es sind keine problematischen Metadaten mehr übrig! Du hast {steps} Schritte gebraucht.",
    empty: "Keine Dateien",
    deltaSaved: "−{size}",
    deltaMetaOnly: "Problematische Metadaten entfernt",
    deltaUnchanged: "Keine Größenänderung",
    tutorialTitle: "Bedienung üben",
    tutorialIntro: "Folge den Schritten. Ziel wie später: alles auf 16 GB, problematische Metadaten weg.",
    tutorialSteps: {
      hover: "Datei mit der Maus überfahren (Infos im Tooltip)",
      tool: "Oben eine Maßnahme wählen",
      click: "Eine Datei anklicken",
      status: "Statusleiste prüfen (Belegt / Metadaten)"
    }
  },
  s4Scenarios: {
    meta: {
      authorKeller: "Autor: M. Keller",
      gpsParkSouth: "GPS: Park Süd, Leipzig",
      cameraIdNorth: "Kamera-ID: CAM-04-Nord",
      authorSecurityNorth: "Autor: Sicherheitsdienst Nord",
      cameraNikon: "Kamera: Nikon D780",
      iso6400: "ISO 6400",
      captureTime: "Aufnahmezeit: 23:41",
      authorEditorial: "Autor: Redaktion",
      editedByWeber: "Bearbeitet von: A. Weber",
      authorUnknown: "Autor: Unbekannt",
      comments14: "14 Kommentare",
      authorHoffmann: "Autor: L. Hoffmann",
      authorSecretariat: "Autor: Sekretariat",
      authorGuest: "Autor: Gast",
      gpsWorkyard: "GPS: Werkshof Tor 2",
      scannerFujitsu: "Scanner: Fujitsu fi-7160",
      layers28: "Ebenen: 28",
      editHistory96: "Bearbeitungshistorie: 96 Schritte",
      layers12: "Ebenen: 12",
      authorPhoneRecording: "Autor: Tonaufnahme Handy",
      gpsEmbedded: "GPS eingebettet"
    },
    resolutionRaw: "Auflösung: 4032×3024",
    resolutionTiff: "Auflösung: 4960×7016",
    logGroupName: "log_{start}.txt … log_{end}.txt"
  }
};
