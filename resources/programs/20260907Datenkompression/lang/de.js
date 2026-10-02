window.LANG_DE = {
  meta: {
    lang: "de",
    title: "Datenkompression und gesellschaftliche Verantwortung"
  },
  header: {
    overline: "",
    title: "Datenkompression und gesellschaftliche Verantwortung",
    subtitle: ""
  },
  nav: {
    toggle: "Inhalt",
    contentAriaLabel: "Inhalt",
    sectionNavAriaLabel: "Sektionsnavigation",
    title: "Navigation",
    intro: "Einstieg",
    section0: "Sektion 0: Vom Problem zur Technik",
    lossless: "Sektion 1: Verlustfreie Kompression",
    losslessOptional: "Zusatz: Verschlüsselung",
    lossy: "Sektion 2: Verlustbehaftete Kompression",
    filetypes: "Sektion 3: Dateiformate und Metadaten",
    final: "Sektion 4: Datenmanagement",
    prev: "Zurück",
    next: "Weiter",
    backToLossless: "Zurück zu Sektion 1"
  },
  common: {
    answerPlaceholder: "Deine Antwort ...",
    showHint: "Tipp anzeigen",
    hideHint: "Tipp ausblenden",
    openSimulation: "Simulation öffnen",
    closeSimulation: "Schließen",
    challengeTitle: "Challenges",
    challengeReset: "Zurücksetzen",
    challengeSuccess: "Alle Challenges geschafft!",
    challengeCloseWarning: "Du hast noch nicht alle Challenges geschafft. Trotzdem schließen?",
    challengeCloseOpen: "Schließen (Challenges offen)",
    materialFolderGloss: "Wenn vom Materialordner gesprochen wird, ist der Ordner \"Material\" gemeint, welcher im gleichen Ordner wie die index.html Datei liegt. Darin sind die Dateien nach Sektion und Aufgabe sortiert. Klicke auf „Material-Ordner“, um alle Dateien als ZIP herunterzuladen."
  },
  intro: {
    heading: "Einstieg",
    scenario: "Ein Freund arbeitet bei einem staatlichem Geheimdienst und hat mitbekommen, dass schwere Verbrechen an der Gesellschaft verheimlicht werden sollen. Er hat Zugriff auf Videoaufnahmen, Berichte und Fotos, welche diese Umstände zeigen und ist entschlossen, diese an die Presse weiterzugeben, da ihm in einem Gespräch mit seinem Vorgesetzten gesagt wurde, er solle das einfach ignorieren. Da diese Daten jedoch auf Computern in einer sehr gut bewachten Anlage mit stark überwachter Internetanbindung sind, ist es ihm nicht möglich diese einfach an Journalisten zu senden. Außerdem handelt es sich um tausende Dateien, welche sehr viel Speicherplatz einnehmen und deshalb hunderte USB-Sticks füllen könnten. Da das Herausschmuggeln durch die Taschenkontrolle am Ausgang sehr riskant ist, will er nach Möglichkeit so wenig Gegenstände wie möglich bei sich haben, die auf sein Vorhaben hindeuten. Er weiß, dass du technisch fit bist und bittet dich um Hilfe:",
    scenarioQuote: "Was soll ich tun? Wie kriege ich diese riesigen Datenmengen unbemerkt aus dem Gebäude?",
    ethicsIntro: "Bevor du technisch planst: Was denkst du über sein Vorhaben?",
    ethicsQuestion: "Fandest du es gut, dass er so handeln will?",
    ethicsYes: "Ja",
    ethicsNo: "Nein",
    ethicsMaybe: "Vielleicht",
    ethicsFollowupYes: "Was spricht aus deiner Sicht dafür, die Informationen trotz des Risikos weiterzugeben?",
    ethicsFollowupNo: "Was könnte eine Situation sein, in der es hilfreich und sinnvoll wäre, Daten unbemerkt herauszuschmuggeln?",
    ethicsFollowupMaybe: "Was macht die Entscheidung für dich schwierig? Fällt dir eine Situation ein, in der deine Antwort eindeutig ja wäre?",
    technicalQuestion: "Was würdest du deinem Freund technisch raten?",
    answerTask: "Schreibe deine Empfehlung auf."
  },
  section0: {
    heading: "Sektion 0: Vom Problem zur Technik",
    sectionOpener: "Du hast im Einstieg über das Dilemma deines Freundes nachgedacht. Jetzt schauen wir uns an, warum Dateigröße und Dateityp in seiner Situation überhaupt eine Rolle spielen.",
    bridgeGoal: "Snowden musste riesige Datenmengen auf winzige Speicherkarten bringen. Dafür musste er wissen, wie viel Platz verschiedene Dateitypen brauchen und wie man sie kleiner machen kann. Genau das wollen wir als Nächstes untersuchen.",
    snowdenIntro: "Dieses Einstiegsbeispiel ist nicht völlig realitätsfern. Es basiert auf der wahren Geschichte von Edward Snowden, der als technischer Mitarbeiter der <span class=\"gloss\" title=\"Die National Security Agency (deutsch Nationale Sicherheitsbehörde) ist der größte Auslandsgeheimdienst der Vereinigten Staaten. Die NSA ist für die weltweite Überwachung, Entzifferung und Auswertung elektronischer Kommunikation zuständig.\">NSA</span> Daten gestohlen hat und sie an die Presse weitergegeben hat. Diese Daten enthalten Informationen über die Überwachung der Menschen und die Verletzung von Privatsphäre und Menschenrechten. Edward Snowden ist ein herausragender Fall von Whistleblowing und hat die Welt durch seine Enthüllungen verändert. Im Folgenden findest du einen Artikel aus einer Zeitung, der über die Geschehnisse berichtet.",
    articleSummary: "Artikeltext (Futurezone, 2019)",
    quoteSummary: "Ausschnitt aus Autobiografie: Permanent Record (Edward Snowden, 2019)",
    article: {
      p1: "Der amerikanische Whistleblower Edward Snowden hat die beim US-Geheimdienst NSA kopierten Daten unter den Aufklebern von Zauberwürfeln, in Socken oder in seiner Backe aus dem Gebäude geschmuggelt. Das berichtete die \"Süddeutsche Zeitung\" am Samstag nach einem Interview mit Snowden über einen verschlüsselten Video-Chat. Der 36-Jährige veröffentlicht in wenigen Tagen seine Memoiren.",
      p2: "Snowden hatte 2013 die ausufernde Überwachung durch den US-Geheimdienst NSA öffentlich gemacht. Die US-Behörden wollen ihm den Prozess machen. Deshalb lebt er in Russland im Exil.",
      p3: "Er habe die Daten seinerzeit auf sehr kleinen Micro- und Mini-SD-Karten gespeichert. \"Die passen überall hin\", meinte er. \"Zunächst einmal habe ich allen Kollegen Zauberwürfel geschenkt. Die waren also überall, die Wachen waren den Anblick gewöhnt und ich war schnell als \"der Zauberwürfel-Typ\" bekannt\", sagte Snowden. Die Karten hätten unter Aufkleber von Zauberwürfeln gepasst, ebenso in eine Socke oder seine Backe.",
      p4: "Die geringe Größe der SD-Karten hat aber auch einen Nachteil: Sie lassen sich nur äußerst langsam beschreiben. Das Kopieren großer Datenmengen dauert immer sehr lange, zumindest länger als man will, aber diese Zeit dehnt sich noch mehr, wenn man nicht auf eine schnelle Festplatte schreibt, sondern auf einen winzigen, in Plastik eingebetteten Siliziumchip. Außerdem kopierte ich nicht nur. Ich deduplizierte, komprimierte, verschlüsselte, und alle diese Prozesse lassen sich nicht gleichzeitig ausführen. Ich bediente mich aller Fähigkeiten, die ich in meiner Arbeit mit Datenspeicherung erworben hatte.",
      sourceLabel: "Quelle:",
      sourceLink: "futurezone.at/netzpolitik/edward-snowden-schmuggelte-daten-mit-zauberwuerfeln-raus/400605791"
    },
    task1: {
      title: "Aufgabe 1 - Wie viel Video passt auf die SD-Karte?"
    },
    task2: {
      title: "Aufgabe 2 - Wie viele Fotos würden passen?"
    },
    task3: {
      title: "Aufgabe 3 - Was hat Kompression damit zu tun?"
    },
    videoBlockTitle: "Wie viel Video passt auf die SD-Karte?",
    taskText: "Mit dem Rechner kannst du ermitteln, wie viel Video auf eine 2-GB-SD-Karte passt. Probiere verschiedene Bitraten und Videolängen aus.",
    reflectionTask: "Inwiefern hat es Edward Snowden geholfen, dass er u.a. durch sein umfangreiches Wissen über Datenkompression Dateigrößen reduzieren konnte? Gib mindestens zwei Antworten. Schreibe deine Vermutungen auf.",
    reflectionHint: "Nutze den Inhalt aus dem Buchzitat: Welchen Vorteil bringt ein kleineres Datenvolumen, wenn man unter Zeitdruck Daten auf einen langsamen Chip kopieren muss? Außerdem musste Snowden die SD-Karten aus dem Gebäude schmuggeln. Welchen Vorteil haben hier kleinere Dateien?",
    inputBitrateLabel: "Datenrate (Mbps)",
    inputBitratePlaceholder: "z. B. 99",
    inputDurationLabel: "Videolänge (Minuten)",
    inputDurationPlaceholder: "z. B. 10",
    calcButton: "Größe berechnen",
    resultTemplate: "Gesamtgröße: {sizeMb} Mb (ca. {sizeMB} MB). Das entspricht {percent}% einer 2-GB-Karte. Auf die Karte passen etwa {videoCount} Videos dieser Länge und Qualität.",
    resultInvalid: "Bitte gib gültige Zahlen größer als 0 ein.",
    photoBlockTitle: "Wie viele Fotos würden passen?",
    photoTaskText: "Videos sind nicht die einzigen Beweise, die dein Freund mitnehmen müsste. Probiere aus, wie viele Fotos bei einer bestimmten Dateigröße auf dieselbe Karte passen würden.",
    inputPhotoSizeLabel: "Dateigröße pro Foto (MB)",
    inputPhotoSizePlaceholder: "z. B. 3",
    photoCalcButton: "Anzahl berechnen",
    photoResultTemplate: "Auf die 2-GB-Karte passen etwa {photoCount} Fotos à {sizeMB} MB (zusammen ca. {totalMB} MB).",
    photoResultInvalid: "Bitte gib eine gültige Dateigröße größer als 0 ein.",
    snowdenContrast: "Snowden hat nach eigenen Angaben rund 1,7 Millionen Dateien mitgenommen. Wenn nur ein einziges Video oder ein paar Fotos einen großen Teil der Karte füllt, wird klar: Ohne Kompression wäre das unmöglich gewesen.",
    compressionBlockTitle: "Was hat Kompression damit zu tun?",
    ratesTitle: "Kurzinfo <span class=\"gloss\" title=\"Die Bitrate bezeichnet die Datenmenge, die in einer bestimmten Zeitspanne in einer Mediendatei gespeichert wird. Sie wird in Bit pro Sekunde (Bit/s) angegeben. Eine höhere Bitrate bedeutet grundsätzlich eine bessere Bild-/Tonqualität, führt aber auch zu größeren Dateigrößen.\">Bitraten</span>",
    rates720pLow: "720p@30fps: ca. 660 Mbps unkomprimiert",
    rates720pHigh: "720p@30fps: ca. 99 Mbps bei <span class=\"gloss\" title=\"Vorgang zur Verkleinerung von Daten, meist durch Entfernen von überflüssigen oder unwichtigen Informationen; die Videos verlieren an diesem Punkt keine sichtbare Qualität und werden oft direkt so gespeichert\">leichter Kompression</span>",
    rates720pStandard: "720p@30fps: ca. 5 Mbps bei <span class=\"gloss\" title=\"Standardkompression meint hier typische Kameraeinstellungen im Alltag, etwa bei Überwachungskameras oder Handykameras. Die Videos sind etwas stärker komprimiert und werden oft direkt so gespeichert, damit sie weniger Speicher brauchen\">Standardkompression</span>",
    rates4kLow: "2160p@30fps: ca. 5970 Mbps unkomprimiert",
    rates4kHigh: "2160p@30fps: ca. 890 Mbps bei <span class=\"gloss\" title=\"Vorgang zur Verkleinerung von Daten, meist durch Entfernen von überflüssigen oder unwichtigen Informationen; die Videos verlieren an diesem Punkt keine sichtbare Qualität und werden oft direkt so gespeichert\">leichter Kompression</span>",
    rates4kStandard: "2160p@30fps: ca. 40 Mbps bei <span class=\"gloss\" title=\"Standardkompression meint hier typische Kameraeinstellungen im Alltag, etwa bei Überwachungskameras oder Handykameras. Die Videos sind etwas stärker komprimiert und werden oft direkt so gespeichert, damit sie weniger Speicher brauchen\">Standardkompression</span>",
    photoRatesTitle: "Kurzinfo <span class=\"gloss\" title=\"Die Dateigröße eines Fotos hängt von Auflösung, Farbtiefe und Kompression ab. Je mehr Pixel und je weniger komprimiert, desto größer die Datei.\">typische Fotogrößen</span>",
    photoRatesPhone: "Handy-Foto (JPEG): ca. 2 bis 5 MB",
    photoRatesCamera: "Spiegelreflex (JPEG): ca. 5 bis 15 MB",
    photoRatesRaw: "RAW/unkomprimiert: ca. 20 bis 50 MB",
    sdCardFill: "Füllstand: {percent}%",
    sdCardAriaLabel: "Füllstand der SD-Karte"
  },
  lossless: {
    heading: "Sektion 1: Verlustfreie Kompression",
    sectionOpener: "In Sektion 0 hast du gesehen, wie schnell Speicher voll wird. Jetzt lernst du Verfahren kennen, die Dateien kleiner machen, ohne Informationen zu verlieren.",
    einstiegPattern: "Ente Ente Ente Ente Ente Ente Ente Ente Ente Ente Fuchs Dachs Dachs Dachs Dachs",
    task1: {
      title: "Aufgabe 1",
      question: "Wie würdest du diesen Text einer Person beschreiben, die ihn gerade nicht sehen kann?",
      hint: "Würdest du wirklich 10x Ente vorlesen oder vielleicht einfach sagen, wie oft es da steht?"
    },
    afterTask1: "Wie viel Platz eine Datei benötigt, hängt nicht nur von ihrem Inhalt ab, sondern auch davon, wie dieser Inhalt beschrieben wird. Es gibt Strategien, denselben Inhalt mit weniger Bits darzustellen, ohne irgendetwas wegzulassen. Solche Verfahren nennt man Kompression.",
    task2: {
      title: "Aufgabe 2 - Run-Length Encoding (RLE)",
      intro: "Run-Length Encoding (RLE) arbeitet mit genau der Idee von oben. Öffne die Simulation, experimentiere mit Zeichenketten und beobachte, wie sich Wiederholungen als Tupel darstellen lassen. Löse außerdem die Challenges in der Seitenleiste.",
      challengesIntro: "Diese Challenges findest du in der Seitenleiste der Simulation. Jede Challenge zählt nur mit einer Zeichenkette von mindestens 5 Zeichen:",
      challenge1: "Mindestens 50 % Ersparnis erzielen (Zeichenkette mindestens 5 Zeichen lang).",
      challenge2: "Negative Ersparnis erzielen (mehr Zeichen als vorher; Zeichenkette mindestens 5 Zeichen lang).",
      challenge3: "Gib eine Zeichenkette mit mindestens 10 gleichen Zeichen hintereinander ein.",
      widgetPlaceholder: "",
      a: {
        text: "a) Wie lässt sich aus der Tupelkette die ursprüngliche Zeichenkette wiederherstellen?"
      },
      b: {
        text: "b) Beschreibe das RLE-Verfahren in Pseudocode. Wie würde ein Programm aussehen, das eine Zeichenkette in Tupel umwandelt und aus Tupeln die Zeichenkette wiederherstellt?",
        skeleton: "def encode(text):\n    result = []  # 2D-Array: z.B. [[Zeichen, Anzahl], ...]\n    i = 0\n    while i < len(text):\n        # TODO\n    return result\n\ndef decode(tuples):\n    result = \"\"\n    for item in tuples:\n        # TODO\n    return result"
      }
    },
    task3: {
      title: "Aufgabe 3 - Wörterbuchkompression",
      intro: "Als Nächstes wird RLE auf einen normalen Fließtext angewendet. Schau dir die Darstellung an und beantworte danach die Fragen.",
      widgetPlaceholder: "Simulation: Wörterbuch-Codierer",
      dictFraming: "Du hast gerade gemerkt, dass RLE bei normalem Fließtext oft nicht gut funktioniert. Schau dir jetzt im Detail an, wie ein anderes Verfahren damit umgeht.",
      a: {
        text: "a) Hier wird das RLE-Verfahren auf den bereitgestellten Beispieltext angewendet. Beschreibe das Problem, das sich hierbei im Vergleich zur Kompression der Zeichenkette aus Aufgabe 2 ergibt."
      },
      b: {
        text: "b) Formuliere eine erste Vermutung: Wie müsste ein neuer Kompressionsalgorithmus im Gegensatz zu RLE vorgehen, um die typischen Strukturen und Wiederholungen eines normalen Fließtextes effektiver ausnutzen zu können?"
      },
      c: {
        text: "c) Formuliere das Wörterbuchverfahren in Pseudocode. Hinweis: In dem Gerüst ist <code>text</code> bereits eine Liste von Wörtern (ohne Leerzeichen).",
        skeleton: "def compress(text):\n    dictionary = []  # 2D-Array: z.B. [[Code, Wort], ...]\n    result = []\n    next_code = 1\n    for word in text:\n        # TODO\n    return result, dictionary\n\ndef decompress(data, dictionary):\n    result = \"\"\n    for item in data:\n        # TODO\n    return result"
      }
    },
    task4: {
      title: "Aufgabe 4 - Verschlüsselung und Kompression",
      intro: "Verschlüsselung hat ein anderes Ziel als Kompression: Sie soll verhindern, dass jemand Muster im Inhalt erkennt. Gute Verschlüsselung macht Daten absichtlich zufällig aussehend, also versucht sichtbare Strukturen der Daten zu verstecken. Mehr dazu findest du in der Zusatzsektion \"Verschlüsselung\".",
      quotePlain: "Die Überwachung der Zielperson wurde am Donnerstag fortgesetzt. Während der Überwachung der Zielperson wurden alle digitalen Daten erfasst. Die Erfassung der Daten erfolgte ohne richterlichen Beschluss. Da die Daten der Zielperson als streng geheim eingestuft sind, wurden die Daten direkt an die Zentrale übermittelt. Eine Löschung der Daten der Überwachung ist nicht vorgesehen.",
      quoteEncrypted: "RGllIMOcYmVyd2FjaHVuZyBkZXIgWmllbHBlcnNvbiB3dXJkZSBhbSBEb25uZXJzdGFnIGZvcnRnZXNldHp0LiBXw6RocmVuZCBkZXIgw5xiZXJ3YWNodW5nIGRlciBaaWVscGVyc29uIHd1cmRlbiBhbGxlIGRpZ2l0YWxlbiBEYXRlbiBlcmZhc3N0LiBEaWUgRXJmYXNzdW5nIGRlciBEYXRlbiBlcmZvbGd0ZSBvaG5lIHJpY2h0ZXJsaWNoZW4gQmVzY2hsdXNzLiBEYSBkaWUgRGF0ZW4gZGVyIFppZWxwZXJzb24gYWxzIHN0cmVuZyBnZWhlaW0gZWluZ2VzdHVmdCBzaW5kLCB3dXJkZW4gZGllIERhdGVuIGRpcmVrdCBhbiBkaWUgWmVudHJhbGUgw7xiZXJtaXR0ZWx0LiBFaW5lIEzDtnNjaHVuZyBkZXIgRGF0ZW4gZGVyIMOcYmVyd2FjaHVuZyBpc3QgbmljaHQgdm9yZ2VzZWhlbi4=",
      a: {
        text: "a) Vergleiche den lesbaren und den verschlüsselten Text. Was fällt dir auf? Warum lässt sich der verschlüsselte Text mit RLE oder Wörterbuchkompression kaum verkleinern?"
      }
    },
    closing: {
      title: "Abschlussaufgabe",
      intro: "Dir liegen vier Dateien vor: ein Schwarz-Weiß-Scan eines Dokuments, ein langer unverschlüsselter Bericht, ein verschlüsseltes Dokument und eine Tabelle mit Namen und Adressen.",
      widgetPlaceholder: "Zuordnung: Datei → Kompressionsverfahren",
      text: "Ordne jeder Datei das geeignetste verlustfreie Verfahren zu."
    }
  },
  losslessOptional: {
    heading: "Zusatz: Verschlüsselung",
    task1: {
      title: "Aufgabe 1 - Muster in den Daten",
      intro: "Erinnere dich an RLE und das Wörterbuchverfahren aus Sektion 1.",
      a: {
        text: "a) Beschreibe getrennt für RLE und für das Wörterbuchverfahren, worauf das jeweilige Verfahren angewiesen ist, damit es Daten verkleinern kann."
      },
      b: {
        text: "b) Formuliere daraus die gemeinsame Voraussetzung beider Verfahren. Begründe, warum beide scheitern, wenn diese Voraussetzung in den Daten fehlt."
      },
      hint: "Denk an die Ente-Fuchs-Dachs-Zeichenkette und an den Fließtext aus Sektion 1. Was wurde dort jeweils ausgenutzt, und was haben beide Fälle gemeinsam?"
    },
    task2: {
      title: "Aufgabe 2 - Verschlüsselung und Strukturen",
      intro: "Vergleiche die drei Texte.",
      quotePlain: "Die Überwachung der Zielperson wurde fortgesetzt. Während der Überwachung der Zielperson wurden alle Daten erfasst.",
      quoteCaesar: "Glh Üehuzdfkxqj ghu Clhosuhuvrq zxugh iruwjhvhwcw. Zäkuhqg ghu Üehuzdfkxqj ghu Clhosuhuvrq zxughq dooh Gdwhq huidvvw.",
      quoteEncrypted: "RGllIMOcYmVyd2FjaHVuZyBkZXIgWmllbHBlcnNvbiB3dXJkZSBmb3J0Z2VzZXR6dC4gV8OkaHJlbmQgZGVyIMOcYmVyd2FjaHVuZyBkZXIgWmllbHBlcnNvbiB3dXJkZW4gYWxsZSBEYXRlbiBlcmZhc3N0Lg==",
      a: {
        text: "a) Welche Strukturen sind im Klartext erkennbar? Inwiefern bleiben sie im zweiten Text erkennbar, und was passiert damit im dritten Text?"
      },
      b: {
        text: "b) Welcher der beiden verschlüsselten Texte eignet sich besser, um Inhalte geheim zu halten? Begründe danach warum gute Verschlüsselungsmethoden die Muster in den Daten verstecken."
      },
      hint: "Achte auf wiederholte Wörter und darauf, ob du im zweiten bzw. dritten Text noch entsprechende Muster erkennen kannst. Welchen Text kann man vermutlich leichter entschlüsseln ohne den Schlüssel zu kennen?"
    },
    closing: {
      title: "Abschlussaufgabe",
      a: {
        text: "a) Dein Freund verschlüsselt einen langen Bericht und will ihn danach noch mit RLE oder Wörterbuchkompression verkleinern. Begründe, warum das kaum gelingt. Beziehe dich auf beide vorigen Aufgaben."
      },
      b: {
        text: "b) Die Datei liegt noch unverschlüsselt vor. Wäre es besser, zuerst zu komprimieren und danach zu verschlüsseln? Begründe deine Einschätzung."
      },
      hint: "Überlege, in welchem Schritt die Muster in den Daten noch vorhanden sind und wann sie verloren gehen."
    }
  },
  lossy: {
    heading: "Sektion 2: Verlustbehaftete Kompression",
    sectionOpener: "In Sektion 1 ging es darum, Dateien exakt zu verkleinern. Manchmal reicht das nicht, oder die Daten enthalten gar keine einfachen Wiederholungen. Dann kommen andere Werkzeuge ins Spiel.",
    task1: {
      title: "Aufgabe 1 - Bitfehler in Passwort und Bild",
      intro: "Bevor du verlustbehaftete Verfahren am Bild ausprobierst, schau dir an, was passiert, wenn in einem Passwort oder Bild genau ein Bit verändert wird.",
      widgetPlaceholder: "Demonstration: Bitfehler",
      a: {
        text: "a) Verändere jeweils ein Bit beim Passwort und beim Bild. Beobachte, was passiert. Für welchen der beiden Dateitypen sind Bitfehler tolerierbar, und für welchen nicht?"
      }
    },
    afterTask1: "Verfahren wie JPEG nutzen Eigenschaften des menschlichen Auges: Wir nehmen nicht alle Bildinformationen gleich genau wahr. Verlustbehaftete Verfahren entfernen gezielt Informationen, die kaum wahrgenommen werden. Das nennt man <span class=\"gloss\" title=\"Irrelevanzminderung: Es werden Daten entfernt, die für den Zweck der Datei voraussichtlich nicht gebraucht werden, etwa feine Farbunterschiede, die das Auge kaum sieht.\">Irrelevanzminderung</span>. Dazu muss man jedoch zunächst herausfinden, welche Informationen man weglassen kann.",
    task2: {
      title: "Aufgabe 2 - Wie JPEG grob funktioniert",
      intro: "Blättere durch die Folien und lies die Erklärungen Schritt für Schritt.",
      slideLabels: {
        pixels8: "8×8 Pixel",
        average: "Durchschnitt",
        blocks8: "8×8 Blöcke",
        blocks32: "32×32 Blöcke",
        flowBlocks: "Blöcke",
        flowTransform: "Transformation",
        flowLossless: "verlustfreie Kompression",
        fallbackSlide: "Folie {n}"
      },
      slides: [
        {
          caption: "Ein digitales Bild besteht aus vielen Pixeln, jeder speichert Farbe und Helligkeit."
        },
        {
          caption: "JPEG teilt ein Bild in gleichmäßige Blöcke und speichert pro Block nur eine grobe Zusammenfassung von Helligkeit und Farbe."
        },
        {
          caption: "Statt jeden Pixel einzeln zu speichern, fasst JPEG die Werte innerhalb eines Blocks zusammen."
        },
        {
          caption: "Größere Blöcke bedeuten weniger Einzelwerte und damit kleinere Dateien, aber auch weniger sichtbare Details innerhalb jedes Blocks."
        },
        {
          caption: "JPEG arbeitet zusätzlich mit einer mathematischen Transformation und anschließender verlustfreien Kompression. Das Blockmodell in dieser Sektion erklärt deshalb nur das Grundprinzip, nicht jeden Schritt des echten Verfahrens."
        },
        {
          caption: "Bei einigen Verfahren wie MJPEG (Motion JPEG) wird jedes Einzelbild eines Videos komprimiert."
        }
      ]
    },
    task3: {
      title: "Aufgabe 3 - Was nimmt das Auge wahr?",
      intro: "Öffne die Simulation. Experimentiere zuerst frei und löse die Challenges in der Seitenleiste. Beantworte danach die Fragen.",
      challengesIntro: "Diese Challenges findest du in der Seitenleiste der Simulation:",
      challenge1: "Dateigröße unter 20 % des Originals.",
      challenge2: "Gesichtsdetails erkennbar.",
      challenge3: "Augenfarbe erkennbar.",
      widgetPlaceholder: "Simulation: Block-Averaging",
      a: {
        text: "a) Welcher der beiden Regler lässt mehr Reduktion zu, bevor das Bild für dich unbrauchbar wird? Woran begründest du deine Entscheidung?"
      },
      b: {
        text: "b) Dein Freund schlägt nun vor, beide Regler nur ein bisschen zu reduzieren, also auf 4×4 Pixel zu setzen. Man kann noch ganz gut erkennen, was auf dem Bild ist, und spart etwas Platz ein. Würdest du das genauso machen?"
      }
    },
    task4: {
      title: "Aufgabe 4 - Wie viel Ersparnis, und wann?",
      intro: "In dieser Simulation kannst du die Blockgröße schrittweise erhöhen und die geschätzte Dateigröße beobachten. Probiere die Stufen aus und beantworte danach die Fragen.",
      widgetPlaceholder: "Simulation: Block-Averaging mit Blockgrößen-Regler",
      a: {
        text: "a) Erhöhe die Blockgröße schrittweise von 1×1 auf 64×64 und beobachte die verhältnismäßige Größenanzeige. Bestimme, wo der größte Sprung in der Ersparnis auftritt."
      },
      b: {
        text: "b) Dein Freund schlägt vor, ein Beweisfoto so lange zu komprimieren, bis Details gerade noch erkennbar sind, da er so am meisten Speicher spare. Beurteile diesen Vorschlag anhand deiner Erkenntnisse aus Aufgabe 4a."
      }
    },
    task5: {
      title: "Aufgabe 5 - Verlustbehaftete Kompression bei Texten",
      intro: "Bisher hast du verlustbehaftete Verfahren am Bild kennengelernt: Sie entfernen Informationen, die das Auge kaum wahrnimmt. Ob dasselbe bei Textdokumenten sinnvoll ist, hängt von einer anderen Frage ab: Was passiert, wenn einzelne Bits verändert werden?",
      widgetPlaceholder: "Simulation: Text-Screenshot komprimieren",
      a: {
        text: "a) In Aufgabe 1 dieser Sektion hast du gesehen, was passiert, wenn genau ein Bit in einem Passwort oder Bild verändert wird. Stelle dir nun vor, ein <span class=\"gloss\" title=\"Verfahren, die Daten verkleinern, indem sie Informationen absichtlich entfernen oder verändern, wie bei den Bildreglern in Aufgabe 3 und 4.\">verlustbehaftetes Verfahren</span> würde dasselbe mit einem Text tun und einzelne Bits verändern oder entfernen.<br><br>Was würde bei einem <span class=\"gloss\" title=\"Ein normaler Fließtext, den du mit einem Textprogramm lesen kannst, wie z. B. eine .txt-Datei mit einem Bericht.\">lesbaren Text</span> passieren? Und was bei einer <span class=\"gloss\" title=\"Eine Tabelle mit strukturierten Einträgen wie Namen, Telefonnummern oder Adressen, z. B. als .csv-Datei. Jeder Wert muss exakt stimmen, sonst ist der Eintrag unbrauchbar.\">Kontakttabelle</span>? Beschreibe den Unterschied.",
        hint: "Orientiere dich an deinen Beobachtungen aus Aufgabe 1: Bei welchem Dateityp war ein einzelner Bitfehler sofort sichtbar bzw. fatal? Bei lesbarem Text hängt jeder Buchstabe von exakt den richtigen Bits ab, was passiert, wenn eines davon verändert wird?<br><br>Beispiel: Aus dem Satz \"Die Überwachung wurde fortgesetzt\" könnte \"Die Üxerwachung wurde fortgesetzt\" werden – der Text bleibt oft noch verständlich.<br><br>Bei einer Kontakttabelle kann ein einzelner Bitfehler eine Telefonnummer oder Adresse verfälschen. Überlege, ob der Eintrag dann noch brauchbar ist – ähnlich wie beim Passwort in Aufgabe 1."
      },
      b: {
        text: "b) Dein Freund schlägt daraufhin vor, statt der Textdatei ein Foto des Textes zu verwenden, da Bilder verlustbehaftet gut komprimiert werden können. Schaue dir im Material-Ordner (<code>Sektion 2</code>) die beiden Dateien im Dateiexplorer an: <code>test.txt</code> und <code>Screenshot.jpg</code> (JPEG-Screenshot eines Ausschnitts desselben Textes). Betrachte die Dateigrößen. Man könnte das Bild auch noch stärker komprimieren, als JPEG es standardmäßig tut. Das kannst du anhand des Simulators ausprobieren. Beurteile, ob er damit wirklich Speicherplatz einsparen kann.",
        hint: "Ist der Text noch lesbar, nachdem du ihn so stark komprimiert hast, dass er unter der Originalgröße der Textdatei liegt? Und könnte man nicht auch die Textdatei z. B. mit dem Wörterbuchverfahren komprimieren?"
      }
    },
    closing: {
      title: "Abschlussaufgabe",
      intro: "Dein Freund muss drei Beweisfotos und ein Kurzvideo aus der Überwachung weitergeben, jedes mit anderem Zweck. Zusätzlich liegen ein verschlüsseltes Dokument und ein kurzer Bericht vor.",
      widgetPlaceholder: "Zuordnung: Dateityp → Kompressionsstrategie",
      a: {
        text: "a) Ordne jedem der vier Dateitypen die passende Kompressionsstrategie zu und begründe deine Entscheidung."
      },
      b: {
        text: "b) Formuliere eine Entscheidungsregel in drei Sätzen:",
        s1: "Verlustbehaftete Kompression ist sinnvoll, wenn …",
        s2: "Sie ist problematisch, wenn …",
        s3: "In der Situation meines Freundes würde ich deshalb …"
      }
    }
  },
  filetypes: {
    heading: "Sektion 3: Dateiformate und Metadaten",
    sectionOpener: "Du weißt jetzt, wie man Dateien kleiner macht. Aber welches Format du wählst, entscheidet mit darüber, welche Zusatzinformationen mitgeschickt werden.",
    intro: {
      p1: "Bisher haben wir Kompression als Werkzeug betrachtet, das man auf eine bestehende Datei anwendet. Es gibt aber eine Frage, die davor steht: In welchem Format liegt die Datei überhaupt vor?",
      p2: "Dateiformate entscheiden darüber, welche Informationen zusätzlich zum eigentlichen Inhalt gespeichert werden. Diese nennt man <span class=\"gloss\" title=\"Metadaten sind Zusatzinformationen zu einer Datei, z. B. Autor, Erstellungsdatum, Kameraeinstellungen oder Bearbeitungshistorie. Sie sind nicht der eigentliche Inhalt, können aber Rückschlüsse auf die Quelle zulassen.\">Metadaten</span>, und sie bestimmen mit, wie der Inhalt gespeichert wird. Manche dieser Zusatzinformationen sind nützlich. Manche können in bestimmten Situationen jedoch gefährlich werden."
    },
    task1: {
      title: "Aufgabe 1 - Overhead in Dateiformaten",
      intro: "Das Werkzeug zeigt dir zwei Dateien mit identischem Textinhalt: einmal als .txt, einmal als .docx.",
      widgetPlaceholder: "Vergleich: .txt vs. .docx",
      a: {
        text: "a) Beschreibe deine Beobachtungen: Was unterscheidet die beiden Dateien, obwohl ihr Inhalt gleich ist? Betrachte die interne Struktur der .docx-Datei. Welche <span class=\"gloss\" title=\"Metadaten sind Zusatzinformationen zu einer Datei, z. B. Autor, Erstellungsdatum, Kameraeinstellungen oder Bearbeitungshistorie. Sie sind nicht der eigentliche Inhalt, können aber Rückschlüsse auf die Quelle zulassen.\">Metadaten</span> und anderen Bestandteile sind nicht Teil des sichtbaren Texts?"
      },
      b: {
        text: "b) Erläutere, inwiefern <span class=\"gloss\" title=\"Metadaten sind Zusatzinformationen zu einer Datei, z. B. Autor, Erstellungsdatum, Kameraeinstellungen oder Bearbeitungshistorie. Sie sind nicht der eigentliche Inhalt, können aber Rückschlüsse auf die Quelle zulassen.\">Metadaten</span> in einer Datei für deinen Freund ein Problem darstellen könnten."
      },
      c: {
        text: "c) Dein Freund sagt nun: \"Für Textdokumente reicht dann doch immer .txt.\" Überlege dir einen Fall, in dem .docx trotzdem sinnvoller wäre.",
        hint: "Denke an Situationen, in denen die Formatierung Teil der Information ist, etwa Tabellen, Überschriften, Fußnoten oder ein festes Layout, das in .txt verloren geht."
      }
    },
    task2: {
      title: "Aufgabe 2 - Metadaten als Sicherheitsrisiko",
      intro: "Schaue dir im Material-Ordner die Word-Datei an und lies die <span class=\"gloss\" title=\"Metadaten sind Zusatzinformationen zu einer Datei, z. B. Autor, Erstellungsdatum, Kameraeinstellungen oder Bearbeitungshistorie. Sie sind nicht der eigentliche Inhalt, können aber Rückschlüsse auf die Quelle zulassen.\">Metadaten</span> aus:<br><strong>Windows:</strong> Rechtsklick → Eigenschaften → Details<br><strong>macOS:</strong> Datei markieren → ⌘+I → Weitere Informationen<br><strong>Linux:</strong> Bei den meisten Desktop-Umgebungen wie bei Windows: Rechtsklick → Eigenschaften. Sonst frag deine Lehrkraft.",
      a: {
        text: "a) Nenne die gespeicherten <span class=\"gloss\" title=\"Metadaten sind Zusatzinformationen zu einer Datei, z. B. Autor, Erstellungsdatum, Kameraeinstellungen oder Bearbeitungshistorie. Sie sind nicht der eigentliche Inhalt, können aber Rückschlüsse auf die Quelle zulassen.\">Metadaten</span> und beurteile jeden Eintrag für die Situation deines Freundes: unkritisch oder problematisch?"
      },
      c: {
        text: "c) Dein Freund will die gefährlichen Einträge einzeln von Hand löschen. Beurteile diesen Ansatz und benenne eine grundlegendere Lösung.",
        hint: "Eine .txt-Datei speichert nur den lesbaren Text als Zeichenfolge. Autor, Bearbeitungsverlauf und andere Metadaten aus der .docx werden beim Export in .txt nicht mit übernommen. Die Originaldatei bleibt dabei unverändert."
      }
    },
    task3: {
      title: "Aufgabe 3 - Offene vs. <span class=\"gloss\" title=\"Proprietär bedeutet „im (alleinigen) Eigentum befindlich“ oder „herstellerspezifisch“. Es beschreibt Produkte, Technologien, Formate oder Systeme, die rechtlich geschützt sind und deren Kontrolle ausschließlich beim jeweiligen Entwickler oder Unternehmen liegt.\">proprietäre</span> Formate",
      intro: "Im Material-Ordner liegen <code>kontakte.csv</code> und <code>kontakte.accdb</code> mit denselben Daten. Öffne beide mit einem Texteditor und vergleiche.",
      a: {
        text: "a) Was passiert, wenn die empfangende Person die Software für .accdb nicht hat?"
      }
    },
    task3optional: {
      title: "Optional: <span class=\"gloss\" title=\"Proprietär bedeutet „im (alleinigen) Eigentum befindlich“ oder „herstellerspezifisch“. Es beschreibt Produkte, Technologien, Formate oder Systeme, die rechtlich geschützt sind und deren Kontrolle ausschließlich beim jeweiligen Entwickler oder Unternehmen liegt.\">Proprietäre</span> Formate vertiefen",
      intro: "Dieser Abschnitt ist optional. Er vertieft den Unterschied zwischen offenen und <span class=\"gloss\" title=\"Proprietär bedeutet „im (alleinigen) Eigentum befindlich“ oder „herstellerspezifisch“. Es beschreibt Produkte, Technologien, Formate oder Systeme, die rechtlich geschützt sind und deren Kontrolle ausschließlich beim jeweiligen Entwickler oder Unternehmen liegt.\">proprietären</span> Formaten anhand konkreter Dateitypen.",
      b: {
        text: "b) Vergleiche offene und <span class=\"gloss\" title=\"Proprietär bedeutet „im (alleinigen) Eigentum befindlich“ oder „herstellerspezifisch“. Es beschreibt Produkte, Technologien, Formate oder Systeme, die rechtlich geschützt sind und deren Kontrolle ausschließlich beim jeweiligen Entwickler oder Unternehmen liegt.\">proprietäre</span> Formate. Welche Kategorie würdest du für die Weitergabe wählen, und welche Nachteile nimmst du in Kauf?"
      },
      formatsSummary: "Formatinfos zu den Dateitypen ein- und ausklappen",
      formats: {
        txt: {
          title: ".txt (offen)",
          text: "Ein reiner Textdatei-Standard ohne Zusatzstruktur. Der Inhalt ist als lesbare Zeichenfolge gespeichert. Jeder Texteditor kann die Datei öffnen. Es gibt keine eingebaute Formatierung, keine Metadaten und kaum Overhead. Für Whistleblower oft die sicherste Wahl, wenn nur der Textinhalt zählt."
        },
        docx: {
          title: ".docx (offener Container, aber komplex)",
          text: "Technisch ein ZIP-Archiv mit XML-Dateien für Text, Formatierung, Styles und Metadaten. Der sichtbare Text ist nur ein Teil der Datei. Vorteil: Überschriften, Tabellen und Layout bleiben erhalten. Nachteil: größere Dateien, mehr Metadaten und interne Struktur, die Rückschlüsse zulassen kann."
        },
        csv: {
          title: ".csv (offen)",
          text: "Tabellendaten als Klartext mit Trennzeichen (meist Komma oder Semikolon). In jedem Texteditor lesbar, ohne Spezialsoftware. Gut für den Datenaustausch, wenn Struktur einfach bleiben soll. Keine Formeln, keine Datenbanklogik, keine Benutzeroberfläche."
        },
        accdb: {
          title: ".accdb (<span class=\"gloss\" title=\"Proprietär bedeutet „im (alleinigen) Eigentum befindlich“ oder „herstellerspezifisch“. Es beschreibt Produkte, Technologien, Formate oder Systeme, die rechtlich geschützt sind und deren Kontrolle ausschließlich beim jeweiligen Entwickler oder Unternehmen liegt.\">proprietär</span>)",
          text: "Microsoft-Access-Datenbankformat. Speichert Tabellen, Abfragen, Formulare und Makros in einer <span class=\"gloss\" title=\"Proprietär bedeutet „im (alleinigen) Eigentum befindlich“ oder „herstellerspezifisch“. Es beschreibt Produkte, Technologien, Formate oder Systeme, die rechtlich geschützt sind und deren Kontrolle ausschließlich beim jeweiligen Entwickler oder Unternehmen liegt.\">proprietären</span> Struktur. Ohne Access oder kompatible Software lässt sich die Datei nicht sinnvoll nutzen. Für den Empfänger ein hohes Abhängigkeitsrisiko."
        },
        png: {
          title: ".png (offen)",
          text: "Verlustfreies Bildformat mit weit verbreiteter Unterstützung. Geeignet für Screenshots, Grafiken und Beweisfotos, wenn keine Bearbeitungshistorie mitgeschickt werden soll. Dateien sind meist größer als stark komprimierte JPEGs, aber universell lesbar."
        },
        psd: {
          title: ".psd (<span class=\"gloss\" title=\"Proprietär bedeutet „im (alleinigen) Eigentum befindlich“ oder „herstellerspezifisch“. Es beschreibt Produkte, Technologien, Formate oder Systeme, die rechtlich geschützt sind und deren Kontrolle ausschließlich beim jeweiligen Entwickler oder Unternehmen liegt.\">proprietär</span>)",
          text: "Adobe-Photoshop-Format mit Ebenen, Masken, Bearbeitungshistorie und weiteren Arbeitsdaten. Nur mit passender Software vollständig nutzbar, oft deutlich größer als .png. Ein Whistleblower müsste es nur weitergeben, wenn genau diese Arbeitsdaten für den Beweis nötig sind."
        }
      },
      psdQuestion: "c) In welcher Situation könnte ein Whistleblower trotzdem ein <span class=\"gloss\" title=\"Proprietär bedeutet „im (alleinigen) Eigentum befindlich“ oder „herstellerspezifisch“. Es beschreibt Produkte, Technologien, Formate oder Systeme, die rechtlich geschützt sind und deren Kontrolle ausschließlich beim jeweiligen Entwickler oder Unternehmen liegt.\">proprietäres</span> Format wie .psd weitergeben müssen?"
    },
    task4: {
      title: "Aufgabe 4 - Containerformate",
      intro: "Archivformate wie ZIP, 7z oder RAR sind weltweit verbreitet, um Dateien weiterzugeben. Ein solches Format kombiniert zwei Funktionen in einem Schritt: Es fungiert als Container, fügt also viele Dateien zu einer Datei zusammen, und komprimiert den Inhalt verlustfrei. Dabei nutzt es unter anderem das Wörterbuchverfahren, das du bereits kennst, jedoch mit einer Erweiterung. Es erstellt das Wörterbuch dateiübergreifend für den gesamten Container, anstatt für jede Datei einzeln von vorn zu beginnen.",
      widgetPlaceholder: "Simulation: ZIP-Archiv-Builder",
      a: {
        text: "a) Das Werkzeug simuliert das Versenden von 10 einzelnen Textdokumenten im Vergleich zum Versand eines einzigen Archivs, in dem diese 10 Dateien gebündelt sind. Nenne Vorteile, die das reine Bündeln in eine einzige Datei für den Sender und den Empfänger hat, unabhängig davon, ob die Datei dadurch kleiner wird."
      },
      b: {
        text: "b) Nun betrachten wir die Kompression: Du komprimierst die 10 Textdokumente zuerst einzeln mit dem Wörterbuchverfahren und addierst ihre Dateigrößen. Steckst du die 10 unkomprimierten Texte zusammen in ein RAR-Archiv, siehst du, dass das Archiv deutlich kleiner ist als die Summe der einzeln komprimierten Dateien. Erkläre, warum das passiert."
      },
      c: {
        text: "c) Dein Freund ist begeistert von Archivformaten und packt 10 verschlüsselte Datenbankdateien sowie 20 JPEG-Beweisfotos in ein Archiv. Wird dieses Archiv durch das Bündeln eine große Speicherersparnis bringen? Stelle eine begründete Vermutung auf und nutze dafür deine Erkenntnisse über Verschlüsselung und Kompressionsverfahren aus den vorherigen Sektionen."
      }
    },
    closing: {
      title: "Abschlussaufgabe",
      a: {
        text: "a) Schaue dir im Material-Ordner die Dateien im Dateiexplorer an. Analysiere die Dokumente und beurteile für jedes, ob eine Formatkonvertierung ausreicht oder ob eine händische Bereinigung notwendig ist. Begründe jede Entscheidung. Optional: Wenn du (z.B. aus dem ITG-Unterricht) bereits mit Word und Excel (oder alternativer Office-Software) vertraut bist, kannst du diese Maßnahmen auch direkt umsetzen."
      },
      b: {
        text: "b) Formuliere 3 Tipps für deinen Freund, jeweils ein Satz mit vorgegebenem Anfang:",
        t1: "Wenn du Videos versenden möchtest, dann …",
        t2: "Wenn du Textdokumente anonym weitergeben willst, dann …",
        t3: "Wenn du viele Dateien auf eine kleine Speicherkarte packen musst, dann …"
      }
    }
  },
  final: {
    heading: "Sektion 4: Datenmanagement als Abschlussaufgabe",
    sectionOpener: "Du kennst jetzt die Werkzeuge. In dieser Sektion geht es darum, sie auf ein echtes Dateipaket anzuwenden.",
    intro: {
      p1: "Du hast drei Werkzeuge kennengelernt: verlustfreie Kompression, verlustbehaftete Kompression und die bewusste Wahl von Dateiformaten.",
      p2: "In der Realität liegt selten eine einzige Datei vor, sondern ein Paket aus sehr unterschiedlichen Dateitypen. Welches Werkzeug für welche Datei sinnvoll ist, entscheidest du anhand ihrer Eigenschaften.",
      p3: "Die Treemap zeigt drei Szenarien mit realistischen Dateigrößen in Megabyte. Zusätzlich gibt es ein kurzes Tutorial (Szenario 0) zur Bedienung. Ziel: alles auf eine 16-GB-Karte, problematische Metadaten entfernt. Aktuell passt nichts davon."
    },
    task1: {
      title: "Aufgabe 1 - Überblick verschaffen",
      note: "Es gibt drei verschiedene Pakete auf unterschiedlichen Datenträgern (plus Tutorial). Dein Ziel ist es, die jeweiligen Daten vollständig auf eine 16-GB-Speicherkarte zu bekommen und problematische Metadaten zu entfernen. Jedes Paket erfordert dafür andere Maßnahmen. Genauere Informationen zu den Dateien erhälst du beim drüberhovern. Schaue dir ersteinmal die Szenarien an und schau, ob du alle Dateitypen kennst. Falls nicht: Es gibt unten eine Erklärung zu allen Dateitypen.",
      widgetPlaceholder: "[Widget: Dateisystem-Treemap mit drei Szenarien]",
      a: {
        text: "a) Starte mit dem Tutorial (Szenario 0), um die Treemap-Bedienung zu üben. Öffne danach für jedes Szenario den Simulator. Wähle oben eine Maßnahme (in metadatenfreies Format umwandeln / Metadaten entfernen, verlustfrei komprimieren, verlustbehaftet komprimieren oder archivieren) und klicke auf die betroffenen Dateien in der Treemap. Ziel: Alles muss auf die 16-GB-Karte passen, und problematische Metadaten müssen entfernt sein. Beim Archivieren wählst du mehrere Dateien aus und fasst sie mit einem Klick zusammen. Falsche Klicks zählen mit, auch wenn sich nichts ändert.",
        s0: "Szenario 0 (Tutorial): Simulator öffnen",
        s1: "Szenario 1: Simulator öffnen",
        s2: "Szenario 2: Simulator öffnen",
        s3: "Szenario 3: Simulator öffnen"
      },
      simPlaceholder: "Simulator wird geladen …",
      b: {
        text: "b) Dein Freund hatte dich ganz am Anfang im Einstieg um technischen Rat gebeten, bevor du dieses Heft bearbeitet hast.",
        thenLabel: "Damals hast du empfohlen:",
        nowLabel: "Was würdest du ihm jetzt empfehlen?",
        emptyIntro: "(noch keine Eingangsantwort im Einstieg)"
      }
    },
    closing: {
      title: "Aufgabe 2 - Reflexion",
      agree: "Ich stimme zu",
      disagree: "Ich stimme nicht zu",
      q1: "Ich kann für eine neue, unbekannte Datei selbst entscheiden, welches Kompressionsverfahren geeignet ist.",
      q2: "Ich könnte den Unterschied zwischen verlustfreier und verlustbehafteter Kompression einer anderen Person erklären.",
      q3: "Ich kann überprüfen, ob eine Datei problematische Metadaten enthält.",
      q4: "Ich glaube, dass Informatikwissen in der beschriebenen Situation einen echten Unterschied für die Sicherheit des Freundes und seiner Quellen gemacht hätte.",
      q5: "Ich sehe einen positiven Zusammenhang zwischen technischem Wissen und persönlicher Freiheit oder Sicherheit.",
      q6: "Das Heft hat mir kaum neue Erkenntnisse gebracht.",
      q7: "Ich hatte vor dem Arbeitsheft bereits Wissen zu Kompressionsverfahren.",
    },
    plenum: {
      title: "Plenumsdiskussion",
      note: "Die folgenden Fragen sind Impulse für das gemeinsame Gespräch:",
      q1: "Wäre die Situation im Szenario ohne Informatikkenntnisse lösbar gewesen? Was folgt daraus für die Frage, wer Zugang zu diesem Wissen haben sollte?",
      q2: "In welchen Situationen könntest du selbst in den nächsten Jahren/Jahrzehnten in eine ähnliche Lage kommen?",
      q3: "Was wäre nötig, damit dieses Wissen nicht nur Einzelnen nützt, sondern gesellschaftlich verfügbar ist?"
    }
  },
  footer: {
    text: "Yanneck Dimitrov, 2026"
  }
};
