worksheets.push({
  titleShort: "Vigenère",
  titleTechnical: "vigenere",
  title: 'Lässt sich die "unentzifferbare Chiffre" doch entziffern?',
  description:
    '<p>Es dauerte nicht lange, bis das Caesar-Verfahren einer breiteren Masse bekannt war und nicht mehr als sicher galt. Im 16. Jahrhundert begannen Mönche, das Verfahren um eine weitere Dimension zu erweitern: Statt einer einfachen Verschiebung des Alphabets wurde nun für jeden Buchstaben eine unterschiedliche Verschiebung genutzt. Wegen dieser für damalige Verhältnisse komplexe Methode galt sie lange Zeit als die "unentzifferbare Chiffre“ <a class="link-secondary" href="#schroedel2008">(Schrödel, 2008)</a></p><p>Später entwickelte Blaise de Vigenère das Verfahren weiter, weshalb es nach ihm benannt wurde.</p><p>Die verschiedenen Caesar-Verschiebungen im Vigenère-Chiffre werden durch ein Schlüsselwort bestimmt. Dieser Schlüssel wird wiederholt, um die Länge des Klartextes zu erreichen. Der Vigenère-Chiffre ist deutlich sicherer als der Caesar-Chiffre, da die Verschiebung für jeden Buchstaben unterschiedlich ist. Macht ihn das wirklich unentzifferbar?</p>',
  citations: [
    {
      short: "schroedel2008",
      citation:
        'Schrödel, T. (2008). Breaking Short Vigenère Ciphers. <i>Cryptologia</i>, 32(4), 334–347. <a href="https://doi.org/10.1080/01611190802336097" target="_blank">https://doi.org/10.1080/01611190802336097</a>',
    },
  ],
  image: "img/vigenere-chatgpt.webp",
  imageDescription: "Bild: Generiert mit ChatGPT, eigene Bearbeitung.",
  imageAlt:
    "Ein Mönch hält ein altes Buch hoch, das aufgeschlagen ist und zwei Tabellen mit dem Alphabet zeigt. Darüber steht Tabula Recta.",
  code: {
    python:
      'def vigenere_encrypt(text, key):\n    result = ""\n    for i in range(len(text)):\n        char = text[i]\n        if char.isalpha():\n            result += chr((ord(char) + ord(key[i % len(key)]) - 2 * ord("A")) % 26 + ord("A"))\n        else:\n            result += char\n    return result\n\nprint(vigenere_encrypt("HELLO, WORLD!", "KEY"))',
  },
  codeFilename: {
    python: "vigenere.py",
  },
  primaryLanguage: "python",
  codeHelpers: {
    python: [
      {
        title: "<code>String isalpha()</code>",
        content:
          "Diese Funktion prüft, ob der gegebene String nur Charaktere aus dem Alphabet, also Buchstaben, enthält.",
      },
      {
        title: "<code>ord()</code>",
        content:
          'Diese Funktion gibt die Zahl des Unicode-Codes eines Zeichens zurück. Beispiel: <code>ord("h")</code> gibt <code>104</code> zurück.',
      },
      {
        title: "<code>chr()</code>",
        content:
          'Diese Funktion gibt das Unicode-Zeichen zurück, das durch diese Nummer repräsentiert wird. Beispiel: <code>chr(104)</code> gibt <code>"h"</code> zurück.',
      },
    ],
  },
  tasks: [
    {
      title: "Aufgabe 1: Den Code beschreiben",
      subtasks: [
        {
          task: "Beschreiben Sie, was der gezeigte Code macht.",
          answerType: "textLong",
        },
        {
          task: "Nennen Sie die erwartete Ausgabe.",
          answerType: "text",
        },
      ],
      helpers: [
        {
          title: "Formulierungshilfen",
          content:
            "<ul><li>Die Funktion <code>caesar_encrypt()</code> wird verwendet, um...</li><li>Die Funktion nimmt zwei Parameter entgegen: ...</li><li>Der Parameter <code>text</code> ist der Eingabetext, der...</li><li>Die Variable <code>result</code> wird erstellt, um...</li><li>Falls das Zeichen ein Buchstabe ist, wird...</li></ul>",
        },
      ],
    },
    {
      title: "Aufgabe 2: Den Code ausführen",
      subtasks: [
        {
          task: '<a href="#worksheet-code">Kopieren Sie den Code auf ihren PC oder laden Sie ihn herunter</a>. Schreiben Sie ihn nicht ab.',
          answerType: "none",
        },
        {
          task: "Führen Sie den Code aus.",
          answerType: "none",
        },
        {
          task: "Vergleichen Sie das Ergebnis mit ihrer Vorhersage.",
          answerType: "textLong",
        },
      ],
    },
    {
      title: "Aufgabe 3: Den Code analysieren",
      subtasks: [
        {
          task: "Beschreiben Sie, welche Datentypen in den Parametern <code>text</code> und <code>shift</code> übergeben werden.",
          answerType: "text",
          multipleChoice: true,
          choices: [
            {
              text: "<code>text</code>: Char, <code>shift</code> Integer",
              correct: false,
            },
            {
              text: "<code>text</code>: String, <code>shift</code> Integer",
              correct: true,
            },
            {
              text: "<code>text</code>: Integer, <code>shift</code> String",
              correct: false,
            },
            {
              text: "<code>text</code>: String, <code>shift</code> String",
              correct: false,
            },
          ],
        },
        {
          task: "Beschreiben Sie, wie häufig die for-Schleife in Zeile 4 durchlaufen wird.",
          answerType: "text",
          multipleChoice: true,
          choices: [
            {
              text: "Genau 10 Mal",
              correct: false,
            },
            {
              text: "Genau 13 Mal",
              correct: true,
            },
            {
              text: "So oft, wie der Text Zeichen enthält",
              correct: true,
            },
            {
              text: "Genau 3 Mal",
              correct: false,
            },
          ],
        },
        {
          task: 'Erklären Sie die Verwendung der Funktionen <code class="language-specific task-language-python">ord()</code><code class="language-specific task-language-javascript hide-element">charCodeAt()</code> und <code class="language-specific task-language-python">chr()</code><code class="language-specific task-language-javascript hide-element">String.fromCharCode</code> im Code.',
          answerType: "text",
          multipleChoice: true,
          choices: [
            {
              text: "Mit den Funktionen werden die Zeichen des Textes in Großbuchstaben umgewandelt.",
              correct: false,
            },
            {
              text: "Die Funktionen verändern den Text, um ihn zu verschlüsseln.",
              correct: true,
            },
            {
              text: "Die Funktionen sind nicht relevant für den Code.",
              correct: false,
            },
            {
              text: "Mit den Funktionen werden die Unicode-Werte der Zeichen ermittelt und wieder in Zeichen umgewandelt.",
              correct: true,
            },
          ],
        },
        {
          task: "Erklären Sie den Code in Zeile 6: <code>newposition = ord(char) + shift</code>",
          answerType: "text",
          multipleChoice: true,
          choices: [
            {
              text: "Der Unicode-Wert des Zeichens wird um den Wert von <code>shift</code> erhöht.",
              correct: true,
            },
            {
              text: "Der Unicode-Wert des Zeichens wird um den Wert von <code>shift</code> verringert.",
              correct: false,
            },
            {
              text: "An der neuen Position wird das Zeichen <code>shift</code> eingefügt.",
              correct: false,
            },
            {
              text: "Die neue Position wird berechnet, indem das Zeichen <code>shift</code> hinzugefügt wird.",
              correct: false,
            },
          ],
        },
        {
          task: "Beschreiben Sie, was bei der Eingabe von Leer- oder Satzzeichen passieren würde. Welche Zeilen des Codes sind hier relevant?",
          answerType: "text",
          multipleChoice: true,
          choices: [
            {
              text: "Leer- und Satzzeichen werden ebenfalls um den Wert von <code>shift</code> verschoben (Zeile 6).",
              correct: false,
            },
            {
              text: "Leer- und Satzzeichen werden unverändert übernommen (Zeilen 5 und 10).",
              correct: true,
            },
            {
              text: "Es wird eine Fehlermeldung ausgegeben (Zeile 13).",
              correct: false,
            },
            {
              text: "Leer- und Satzzeichen werden aus dem Text entfernt (Zeile 9).",
              correct: false,
            },
          ],
        },
        {
          task: 'Erklären Sie, warum die Eingaben <code>"hello, world!"</code> und <code>"HELLO, WORLD!"</code> zu einer identischen Ausgabe führen.',
          answerType: "text",
          multipleChoice: true,
          choices: [
            {
              text: "Der Code ist fehlerhaft und sollte unterschiedliche Ausgaben liefern.",
              correct: false,
            },
            {
              text: "Da Klein- und Großbuchstaben immer um genau 26 Zeichen verschoben sind, ist die Ausgabe gleich.",
              correct: false,
            },
            {
              text: "Der Unicode-Code von Groß- und Kleinbuchstaben ist identisch, daher ist die Ausgabe gleich.",
              correct: false,
            },
            {
              text: "Der Code konvertiert in Zeile 3 alle Zeichen in Großbuchstaben, bevor er sie verschlüsselt.",
              correct: true,
            },
          ],
        },
      ],
    },
    {
      title: "Aufgabe 4: Den Code verbessern",
      subtasks: [
        {
          task: "Beschreiben Sie, welche Ausgabe Sie beim Ausführen des Codes mit dem Parameter <code>shift = 32</code>. erwarten.",
          answerType: "text",
        },
        {
          task: "Setzen Sie den Parameter <code>shift = 32</code> und führen den Code aus. Vergleichen Sie das Ergebnis mit Ihrer Vorhersage.",
          answerType: "text",
        },
        {
          task: "Erklären Sie ein mögliches Problem sowie einen potenziellen Lösungsansatz.",
          answerType: "textLong",
        },
        {
          task: "Verändern Sie den Code so, dass das in Teilaufgabe a beschriebene Problem behoben wird.",
          answerType: "code",
        },
      ],
      helpers: [
        {
          title: "Formulierungshilfen",
          content:
            "<ul><li>Bei einer Verschiebung um 32 Zeichen wird der Buchstabe...</li><li>...wird das Unicode-Zeichen an der Stelle ... ausgegeben.</li><li>Ein Problem könnte entstehen, wenn...</li><li>Um das Problem zu lösen, müsste der Code...</li></ul>",
        },
      ],
    },
    {
      title: "Aufgabe 5: Den Code erweitern",
      subtasks: [
        {
          task: "Ergänzen Sie den Code um eine Funktion zum Entschlüsseln eines Strings, der vorher mit dem Caesar-Chiffre um eine bekannte Länge verschlüsselt wurde. Die Funktion sollte wie bisher die Parameter <code>text</code> und <code>shift</code> als Eingabe erhalten.",
          answerType: "code",
        },
        {
          task: '<span class="text-muted">(Zusatzaufgabe)</span> Schreiben Sie eine Funktion, die in einer Brute-Force-Methode alle möglichen Varianten für Verschiebungen ausprobiert.',
          answerType: "code",
        },
        {
          task: '<span class="text-muted">(Zusatzaufgabe)</span> Erläutern Sie sinnvolle Optimierungsmöglichkeiten einer Funktion, wie sie in Teilaufgabe b erstellt werden soll.',
          answerType: "text",
        },
      ],
    },
  ],
});
