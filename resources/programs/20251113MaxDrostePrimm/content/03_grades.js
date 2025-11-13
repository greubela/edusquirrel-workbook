worksheets.push({
  titleShort: "Grades",
  titleTechnical: "grades",
  title: "Frust oder Freude: Wie fällt mein Zeugnis aus?",
  description:
    "<p>Das Schuljahresende naht und damit auch der Termin, der entweder Grund zur Freude oder leider auch für Frust ist: Die Ausgabe der Zeugnisse.</p><p>Doch auf welche Emotion können Sie sich einstellen? In dieser Lektion lernen Sie, wie Sie mit Python ein Programm schreiben können, das Ihren Notendurchschnitt bewertet.",
  image: "img/zeugnis_bearbeitet.jpg",
  imageDescription:
    'Bild: <a href="https://www.schulministerium.nrw/wie-kommt-eine-zeugnisnote-zustande" target="_blank">iStock/Bildungsministerium NRW</a>, eigene Bearbeitung.',
  imageAlt:
    "Ein Zeugnis mit verschiedenen Schulnoten von sehr gut bis befriedigend.",
  code: {
    python:
      'def zeugnis_bewertung(noten):\n    gesamt = 0\n    for note in noten:\n        gesamt = gesamt + note\n    durchschnitt = gesamt / len(noten)\n\n    if durchschnitt <= 2:\n        return "🥳"\n    else:\n        if durchschnitt <= 4:\n            return "😐"\n        else:\n            return "😭"\n\nnoten = [1, 4, 3, 5, 2]\nprint(zeugnis_bewertung(noten))',
  },
  codeFilename: {
    python: "grades.py",
  },
  primaryLanguage: "python",
  codeHelpers: {
    python: [
      {
        title:
          '<code><span class="hljs-keyword">def</span> <span class="hljs-title">zeugnis_bewertung<span class="code-dark">(noten)</span></code>',
        content:
          "Mit dem Code <code class='hljs-keyword'>def</code> wird eine Funktion definiert, die immer wieder aufgerufen kann. In diesem Fall heißt die Funktion <code class='hljs-title'>zeugnis_bewertung</code>.<br><br>In den Klammern wird angegeben, welche Werte man der Funktion zum Ausführen mitgeben kann, in diesem Fall eine Variable <code class='code-dark'>noten</code>, in der verschiedene Werte gespeichert werden können.",
      },
      {
        title: '<code class="code-dark">gesamt = 0</code>',
        content:
          'Mit diesem Code wird eine Variable mit dem Namen <code class="code-dark">gesamt</code> erstellt, die den Wert <code>0</code> hat. Diese Variable wird später für die Berechnung des Notendurchschnitts verwendet.',
      },
      {
        title:
          '<code><span class="hljs-keyword">for</span> <span class="code-dark">note</span> <span class="hljs-keyword">in</span> <span class="code-dark">noten:</span></code>',
        content:
          'In dieser Zeile wird eine Schleife gestartet, die alle Werte in der Variable <code class="code-dark">noten</code> Wert für Wert durchläuft. Dabei wird der jeweils aktuelle Wert in der Variable <code class="code-dark">note</code> gespeichert.',
      },
      {
        title: '<code><span class="code-dark">/</span></code> (Division)',
        content:
          'Mit dem Operator <code class="code-dark">/</code> wird eine Division durchgeführt. Beispiel: <code class="code-dark">10 / 2</code> ergibt <code class="code-dark">5</code>.',
      },
      {
        title:
          '<code><span class="hljs-keyword">len</span><span class="code-dark">(noten)</span></code>',
        content:
          "Mit der Funktion <code class='hljs-keyword'>len</code> wird die Anzahl der Werte in der Variable <code class='code-dark'>noten</code> ermittelt. Beispiel: Wenn <code class='code-dark'>noten = [1, 2, 3]</code>, dann ergibt <code class='hljs-keyword'>len(noten)</code> den Wert <code class='code-dark'>3</code>.",
      },
      {
        title:
          '<code><span class="hljs-keyword">if</span> <span class="code-dark">... :</span> <span class="code-dark">...</span> <span class="hljs-keyword">else:</span> <span class="code-dark">...</span></code>',
        content:
          "Mit dem Code <code class='hljs-keyword'>if</code> wird eine Bedingung überprüft. Ist sie wahr, wird der darauffolgende eingerückte Code ausgeführt. Andernfalls wird der Code nach <code class='hljs-keyword'>else</code> ausgeführt.",
      },
      {
        title:
          '<code><span class="hljs-keyword">return</span> <span class="code-string">"..."</span></code>',
        content:
          "Mit dem Code <code class='hljs-keyword'>return</code> wird ein Wert aus der Funktion zurückgegeben, also das finale Ergebnis ausgegeben. Dieser Wert kann dann außerhalb der Funktion verwendet werden.",
      },
      {
        title: '<code class="code-dark">noten = [1, 4, 3, ...]</code>',
        content:
          "In der Variable <code class='code-dark'>noten</code> werden verschiedene Werte gespeichert. Eine Variable, in der mehrere Werte gleichzeitig erstellt werden, nennt man auch Array. In diesem Fall ist das Array eine Liste von Noten.",
      },
      {
        title:
          '<code><span class="hljs-keyword">print</span><span class="code-dark">(zeugnis_bewertung(noten))</span></code>',
        content:
          "Mit dem Code <code class='code-dark'>print()</code> wird das Ergebnis der Funktion <code class='hljs-title'>zeugnis_bewertung</code> ausgegeben. In diesem Fall wird die Variable <code class='code-dark'>noten</code> an die Funktion übergeben, um den Notendurchschnitt zu berechnen.",
      },
    ],
  },
  tasks: [
    {
      title: "Aufgabe 1: Den Code beschreiben",
      description:
        "In dieser Aufgabe sollen Sie den Code analysieren und verstehen, was er macht. Anschließend sollen Sie eine Prognose über die Ausgabe des Codes treffen. <strong>Führen Sie den Code dazu noch nicht aus</strong>, sondern versuchen Sie, die Ausgabe nur durch das Lesen des Codes zu ermitteln.",
      subtasks: [
        {
          task: 'Beschreiben Sie, was die Funktion <code class="language-specific task-language-python"><span class="hljs-title">zeugnis_bewertung</span><span class="code-dark">()</span></code> macht.',
          answerType: "textLong",
          phrasingHelpers: [
            "Die Funktion <code class='language-specific task-language-python'>zeugnis_bewertung()</code> erhält als Parameter ... übergeben.",
            "In einer Schleife werden ...",
            "Es wird zuerst überprüft, ob...",
            "Wenn der Wert der Variable ... ist, dann wird ... zurückgegeben.",
            "In der Variable ... wird ... gespeichert.",
            "Es wird eine Variable mit dem Namen ... erstellt, die ... enthält.",
          ],
          hints: [
            "Gehen Sie den Code Zeile für Zeile durch und versuchen Sie, die Funktion der Zeilen nachzuvollziehen und zu beschreiben.",
            "Ein möglicher Start: <em>Die Funktion <code class='language-specific task-language-python'>zeugnis_bewertung()</code> erhält eine Liste von Noten als Parameter übergeben. Anschließend wird der Wert der Variable ... auf ... gesetzt. ...</em>",
          ],
        },
        {
          task: 'Nennen Sie die erwartete Ausgabe der Funktion <code class="language-specific task-language-python"><span class="hljs-title">zeugnis_bewertung</span><span class="code-dark">()</span></code> beim Ausführen mit den Werten <code><span class="code-dark">noten = [1, 2, 1]</span></code>.',
          answerType: "multipleChoice",
          choices: [
            {
              text: "🥳",
              correct: true,
              feedbackText:
                "Addiert man die Werte <code>[1, 2, 1]</code> zusammen, erhält man <code>4</code>. Teilt man diesen Wert durch die Anzahl der Werte, also <code>3</code>, erhält man einen Wert von <code>2</code>. Da dieser Wert gleich <code>2</code> ist, wird das Emoji 🥳 zurückgegeben.",
            },
            {
              text: "😐",
              correct: false,
              feedbackText:
                "Addiert man die Werte <code>[1, 2, 1]</code> zusammen, erhält man <code>4</code>. Teilt man diesen Wert durch die Anzahl der Werte, also <code>3</code>, erhält man einen Wert von <code>2</code>. Da dieser Wert gleich <code>2</code> ist, wird das Emoji 🥳 zurückgegeben.",
            },
            {
              text: "😭",
              correct: false,
              feedbackText:
                "Addiert man die Werte <code>[1, 2, 1]</code> zusammen, erhält man <code>4</code>. Teilt man diesen Wert durch die Anzahl der Werte, also <code>3</code>, erhält man einen Wert von <code>2</code>. Da dieser Wert gleich <code>2</code> ist, wird das Emoji 🥳 zurückgegeben.",
            },
          ],
        },
        {
          task: 'Nennen Sie die erwartete Ausgabe der Funktion <code class="language-specific task-language-python"><span class="hljs-title">zeugnis_bewertung</span><span class="code-dark">()</span></code> beim Ausführen aller Code-Zeilen, also mit den Werten <code><span class="code-dark">noten = [1, 4, 3, 5, 2]</span></code>.',
          answerType: "textShort",
        },
      ],
    },
    {
      title: "Aufgabe 2: Den Code ausführen",
      subtasks: [
        {
          task: '<a href="#worksheet-code">Kopieren Sie den Code auf ihren PC bzw. ihr Tablet oder laden Sie ihn herunter</a>. Schreiben Sie ihn nicht ab.',
          answerType: "none",
        },
        {
          task: "Führen Sie den Code aus. Sie können den Code auch online in einem Python-Interpreter ausführen, zum Beispiel auf <a href='https://www.programiz.com/python-programming/online-compiler/' target='_blank'>programiz.com</a>.",
          answerType: "none",
        },
        {
          task: "Vergleichen Sie das Ergebnis mit Ihrer Vorhersage und erklären Sie, ob und warum Ihre Vorhersage korrekt war oder warum das Ergebnis abweicht.",
          answerType: "textLong",
        },
      ],
    },
    {
      title: "Aufgabe 3: Den Code analysieren",
      subtasks: [
        {
          task: 'Nennen Sie die Anzahl, wie häufig die <code class="hljs-keyword">for</code>-Schleife in Zeile <span class="language-specific task-language-python">3</span> durchlaufen wird.',
          answerType: "textShortCheckable",
          correctAnswers: ["5", "Fünf", "fünf", "5x", "5 mal", "Fünf mal"],
          feedbackText:
            "Die Schleife wird so häufig durchlaufen, wie es Elemente im Array <code class='code-dark'>noten</code> gibt, in diesem Fall also fünf mal.",
          hints: [
            "Welche Variable wird in der Schleife aufgerufen?",
            "Wie viele Werte sind in der Variable <code class='code-dark'>noten</code> gespeichert?",
            'Die Schleife wird so häufig durchlaufen, wie es Elemente im Array <code class="code-dark">noten</code> gibt.',
          ],
        },
        {
          task: 'Nennen Sie die Anzahl, wie oft die <code class="hljs-keyword">if</code>-Bedingungen in Zeile <span class="language-specific task-language-python">7</span> durchlaufen wird.',
          answerType: "textShortCheckable",
          correctAnswers: ["1", "Eins", "eins", "1x", "1 mal", "Einmal"],
          feedbackText:
            "Die <code class='hljs-keyword'>if</code>-Bedingung wird nur einmal durchlaufen, da sie sich außerhalb der Schleife befindet.",
          hints: [
            "Wie ist die <code class='hljs-keyword'>if</code>-Bedingung im Code platziert?",
            "Die <code class='hljs-keyword'>for</code>-Schleife führt nur die eingerückten Zeilen nach dem Doppelpunkt aus.",
            "Die <code class='hljs-keyword'>if</code>-Bedingung befindet sich außerhalb der Schleife.",
          ],
        },
        {
          task: 'Nennen Sie den Wert der Variable <code class="code-dark">gesamt</code> nach dem Durchlaufen der Schleife.',
          answerType: "textShortCheckable",
          correctAnswers: ["15"],
          feedbackText:
            "Die Variable <code class='code-dark'>gesamt</code> wird in der Schleife mit den Werten <code class='code-dark'>1 + 4 + 3 + 5 + 2</code> addiert, was den Wert <code class='code-dark'>15</code> ergibt.",
          hints: [
            "Die Eingabewerte sind <code class='code-dark'>noten = [1, 4, 3, 5, 2]</code>.",
            "Jede Zahl aus dem Array <code class='code-dark'>noten</code> ist in der Schleife als Variable <code class='code-dark'>note</code> verfügbar.",
            "Was passiert in der Schleife mit der Variable <code class='code-dark'>gesamt</code>?",
            "Wie häufig wird die Schleife durchlaufen?",
          ],
        },
        {
          task: "Begründen Sie, warum die Funktion <code class='hljs-title'>len()</code> in Zeile <span class='language-specific task-language-python'>5</span> nicht weggelassen werden kann.",
          answerType: "text",
          phrasingHelpers: [
            "Die Funktion <code class='hljs-title'>len()</code> wird benötigt, um ...",
            "Ohne die Funktion <code class='hljs-title'>len()</code> würde ...",
          ],
          hints: [
            "Die Funktion <code class='hljs-title'>len()</code> gibt die Anzahl der Werte in einem Array zurück.",
            "Wie wird der Notendurchschnitt berechnet?",
          ],
        },
        {
          task: "Erklären Sie den Code in Zeile <span class='language-specific task-language-python'>5</span>: <code><span class='code-dark'>durchschnitt = gesamt / </span><span class='hljs-title'>len</span><span class='code-dark'>(noten)</span></code>.",
          answerType: "text",
          phrasingHelpers: [
            "In dieser Zeile wird ...",
            "... um ... zu berechnen.",
            "Der Wert der Variable ...",
            "Die Variable ... wird erstellt, um ...",
          ],
          hints: [
            "Welchen Wert hat die Variable <code class='code-dark'>gesamt</code> zu diesem Zeitpunkt?",
            "Was gibt die Funktion <code class='hljs-title'>len()</code> zurück?",
            "Welche mathematische Operation wird hier durchgeführt und warum?",
          ],
        },
        {
          task: "Nennen Sie die Anzahl, wie viele <code class='hljs-keyword'>if</code>-Bedingungen bei einem Notendurchschnitt von <code class='code-dark'>5</code> geprüft werden.",
          answerType: "textShortCheckable",
          correctAnswers: [
            "2",
            "Zwei",
            "zwei",
            "Zwei mal",
            "zwei mal",
            "Zwei Mal",
            "zwei Mal",
            "2x",
            "2 mal",
            "2 Mal",
          ],
          feedbackText:
            "Die <code class='hljs-keyword'>if</code>-Bedingung wird zwei Mal geprüft: Einmal für den Vergleich mit <code class='code-dark'>2</code>. Da diese Bedingung falsch ist, wird in den <code class='hljs-keyword'>else</code>-Abschnitt gewechselt. Hier findet noch einmal ein Vergleich mit <code class='code-dark'>4</code> statt.",
          hints: [
            "Die <code class='hljs-keyword'>if</code>-Bedingung in Zeile <span class='language-specific task-language-python'>7</span> wird geprüft, wenn der Notendurchschnitt kleiner oder gleich <code class='code-dark'>2</code> ist.",
            "Wenn die Bedingung falsch ist, wird der <code class='hljs-keyword'>else</code>-Abschnitt ausgeführt.",
            "Im <code class='hljs-keyword'>else</code>-Abschnitt wird eine weitere <code class='hljs-keyword'>if</code>-Bedingung geprüft, die den Notendurchschnitt mit <code class='code-dark'>4</code> vergleicht.",
          ],
        },
        {
          task: "Begründen Sie, ob die <code class='hljs-keyword'>if</code>-Bedingung in Zeile <span class='language-specific task-language-python'>7</span> bei einem Notendurchschnitt von <code class='code-dark'>2</code> zutrifft, also wahr ist.",
          answerType: "text",
          phrasingHelpers: [
            "Die Bedingung <code class='hljs-keyword'>if</code> in Zeile <span class='language-specific task-language-python'>7</span> ist (nicht) wahr, wenn ...",
            "Die Bedingung trifft (nicht) zu, weil ...",
          ],
          hints: [
            "Welche Bedingung wird in der <code class='hljs-keyword'>if</code>-Bedingung überprüft?",
            "Der Operator <code class='code-dark'><=</code> bedeutet, dass der Wert kleiner oder gleich dem Vergleichswert sein muss.",
            "Beispiel: <code class='code-dark'>5 <= 5</code> ist wahr, aber <code class='code-dark'>5 < 5</code> ist falsch.",
          ],
        },
        {
          task: "Beschreiben Sie, wie sich eine Änderung der Noten in der Variable <code class='code-dark'>noten</code> auf die Ausgabe der Funktion <code class='hljs-title'>zeugnis_bewertung</span><span class='code-dark'>()</span></code> auswirkt. Gehen Sie auch darauf ein, was sich beim Ablauf der Schleife ändert.",
          answerType: "text",
          phrasingHelpers: [
            "Wenn die Noten in der Variable <code class='code-dark'>noten</code> geändert werden, dann ...",
            "Die Schleife wird (nicht) mehr/weniger oft durchlaufen, weil ...",
            "Die Ausgabe der Funktion könnte sich ändern/bleibt gleich/wird sich auf jeden Fall ändern, weil ...",
          ],
          hints: [
            "Welche Auswirkungen hat eine Änderung der Werte auf die Schleife und die Ausgabe der Funktion?",
          ],
        },
      ],
    },
    {
      title: "Aufgabe 4: Den Code verbessern",
      subtasks: [
        {
          task: "Nennen und begründen Sie, welche Ausgaben Sie beim Ausführen des Codes erwarten, wenn der Notendurchschnitt entweder <code>2.1</code> oder <code>4</code> beträgt.",
          answerType: "text",
          phrasingHelpers: [
            "Wenn der Notendurchschnitt <code>2.1</code> beträgt, dann ...",
            "In beifen Fällen wird ...",
            "Wenn der Notendurchschnitt <code>4</code> beträgt, dann ...",
            "... weil die if-Bedingung in Zeile ... für beide Fälle/für einen der beiden Fälle (nicht) zutrifft.",
          ],
          hints: [
            "Welche Bedingung wird in der <code class='hljs-keyword'>if</code>-Bedingung überprüft?",
            "Der Operator <code class='code-dark'><=</code> bedeutet, dass der Wert kleiner oder gleich dem Vergleichswert sein muss.",
            "Beispiel: <code class='code-dark'>5 <= 5</code> ist wahr, aber <code class='code-dark'>5 < 5</code> ist falsch.",
          ],
        },
        {
          task: "Setzen Sie erst die Variable <code class='code-dark'>noten</code> auf <code class='code-dark'>noten = [1, 1, 2, 2, 4, 3]</code> (Notendurchschnitt 2.16) und führen Sie den Code aus. Setzen Sie anschließend die Variable <code class='code-dark'>noten</code> auf <code class='code-dark'>noten = [4, 4, 4, 4]</code> (Notendurchschnitt 4) und führen Sie den Code erneut aus. Vergleichen Sie das Ergebnis mit Ihrer Vorhersage.",
          answerType: "textLong",
        },
        {
          task: "Erklären Sie, wie man für die Notendurchschnitte von <code>2.1</code> und <code>4</code> unterschiedliche Ausgaben zurückgeben lassen kann, zum Beispiel das Emoji 🤓 bei einem Durchschnitt von <code>2.1</code>.",
          answerType: "text",
          hints: [
            "Wie könnte man einen weiteren Vergleich hinzufügen, um die Ausgabe für einen Durchschnitt zwischen 2 und 3 abzubilden?",
            "An welcher Stelle im Code müsste dieser Vergleich eingefügt werden?",
          ],
        },
        {
          task: "Schreiben Sie den Code so um, dass die Funktion <code class='hljs-title'>zeugnis_bewertung</span><span class='code-dark'>()</span></code> bei einem Notendurchschnitt zwischen 2 und 3 das Emoji 🤓 und bei einem Notendurchschnitt zwischen 3 und 4 das Emoji 😐 zurückgibt.",
          answerType: "code",
          hints: [
            "Wenn Sie die Bedingung in Zeile <span class='language-specific task-language-python'>7</span> ändern, könnten Sie einen größeren Notenbereich abdecken.",

            "In der ersten <code class='hljs-keyword'>if</code>-Bedingung könnte nun eine weitere Bedingung hinzugefügt werden, ähnlich wie in der <code class='hljs-keyword'>else</code>-Bedingung.",
            "Die Bedingung in Zeile <span class='language-specific task-language-python'>7</span> könnte so aussehen: <code class='hljs-keyword'>if</code> <span class='code-dark'>durchschnitt <= 3:</span>",
            "Die zweite <code class='hljs-keyword'>if</code>-Bedingung könnte so aussehen: <code class='hljs-keyword'>if</code> <span class='code-dark'>durchschnitt <= 4:</span>",
            'In folgendem Code-Ausschnitt müssen Sie noch die passenden Zahlenbereiche statt des <code class="code-dark">X</code> ergänzen: <div class="li-code-wrap"><pre><code>if durchschnitt <= X:\n    if durchschnitt <= X:\n        return "🥳"\n    else:\n        return "🤓"\nelse:\n    if durchschnitt <= X:\n        return "😐"\n    else:\n        return "😭"</code></pre></div>',
          ],
        },
        {
          task: '<span class="text-muted">(Zusatzaufgabe)</span> Nennen und begründen Sie, welche Ausgabe Sie beim Ausführen des Codes mit den Werten <code class="code-dark">noten = [0]</code> erwarten.',
          answerType: "text",
          phrasingHelpers: [
            "Bei einem Notendurchschnitt von ... wird ...",
            "Da die Bedingung in Zeile ... (nicht) zutrifft, wird ...",
            "Bei einer Eingabe von einer Note mit dem Wert ...",
          ],
          hints: [
            "Welcher Durchschnittswert wird errechnet?",
            "Die einzige Note in der Liste ist <code class='code-dark'>0</code>. Die Anzahl der Noten ist <code class='code-dark'>1</code>.",
          ],
        },
        {
          task: '<span class="text-muted">(Zusatzaufgabe)</span> Setzen Sie die Variable <code class="code-dark">noten</code> auf <code class="code-dark">noten = [0]</code> und führen Sie den Code aus. Vergleichen Sie das Ergebnis mit Ihrer Vorhersage.',
          answerType: "text",
        },
        {
          task: '<span class="text-muted">(Zusatzaufgabe)</span> Schreiben Sie den Code so um, dass die Funktion <code class="hljs-title">zeugnis_bewertung</span><span class="code-dark">()</span></code> nicht gültige Notenwerte nicht in die Durchschnittsberechnung einbezieht.',
          answerType: "code",
          hints: [
            "Welche Notenwerte sind gültig?",
            "An welcher Stelle sollten Sie die Prüfung auf gültige Notenwerte einfügen?",
            "Sie könnten die Prüfung auf gültige Notenwerte in der Schleife einfügen, bevor die Note zur Variable <code class='code-dark'>gesamt</code> addiert wird.",
            "Denken Sie daran, dass Sie nicht gültige Notenwerte auch von der Anzahl der Noten abziehen müssen, um den Durchschnitt korrekt zu berechnen.",
            'In folgendem Code-Ausschnitt müssen Sie noch die passenden Zahlenbereiche statt des <code class="code-dark">X</code> ergänzen: <div class="li-code-wrap"><pre><code>if X <= note <= X:\n    gesamt = gesamt + note</code></pre></div>',
          ],
        },
      ],
    },
    {
      title: "Aufgabe 5: Neuen Code schreiben",
      subtasks: [
        {
          task: 'Schreiben Sie eine Funktion <code class="hljs-title">ausgaben_bewerten()</code>, die eine Liste von Geldbeträgen als Parameter erhält und die Gesamtausgaben berechnet. Die Funktion soll anschließend eine Bewertung zurückgeben, die angibt, ob die Ausgaben im Rahmen des Budgets liegen oder ob sie zu hoch sind.<br><br>Eine beispielhafte Ausgabenliste könnte so aussehen: <code class="code-dark">beträge = [5, 10, 3, 2]</code>. Wurden insgesamt weniger als 20 Euro ausgegeben, soll die Funktion <code class="hljs-string">"Budget eingehalten"</code> zurückgeben. Wurden zwischen 20 und 40 Euro ausgegeben, soll die Funktion <code class="hljs-string">"Budget überschritten"</code> zurückgeben. Wurden mehr als 40 Euro ausgegeben, soll die Funktion <code class="hljs-string">"Budget stark überschritten"</code> zurückgeben.',
          answerType: "code",
          hints: [
            "Die Funktion sollte eine Liste von Geldbeträgen als Parameter erhalten.",
            "Sie sollten eine Schleife verwenden, um die Gesamtausgaben zu berechnen.",
            "Die Funktion sollte eine Bewertung zurückgeben, die angibt, ob die Ausgaben im Rahmen des Budgets liegen oder ob sie zu hoch sind.",
            'Ein Beispiel für den Code könnte so aussehen: <div class="li-code-wrap"><pre><code>def taschengeld_ausgaben(beträge):\n    gesamt = 0\n    for betrag in beträge:\n        gesamt += betrag\n    if gesamt <= budget:\n        return "Budget eingehalten"\n    else:\n        return "Budget überschritten"</code></pre></div>',
          ],
        },
      ],
    },
  ],
});
