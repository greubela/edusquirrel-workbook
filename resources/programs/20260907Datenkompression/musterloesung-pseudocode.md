# Lösungsvorschlag: Pseudocode (Sektion 1)

## Aufgabe 2b – Run-Length Encoding (RLE)

```python
def encode(text):
    result = []  # 2D-Array: z.B. [[Zeichen, Anzahl], ...]
    i = 0
    while i < len(text):
        zeichen = text[i]
        anzahl = 1
        while i + anzahl < len(text) and text[i + anzahl] == zeichen:
            anzahl += 1
        result.append([zeichen, anzahl])
        i += anzahl
    return result

def decode(tuples):
    result = ""
    for item in tuples:
        zeichen = item[0]
        anzahl = item[1]
        result += zeichen * anzahl
    return result
```

---

## Aufgabe 3c – Wörterbuchverfahren

Das Gerüst nutzt `for word in text`. Darunter wird angenommen, dass `text` bereits eine Liste von Wörtern ist (ohne Leerzeichen), z. B. nach dem Aufteilen am Leerzeichen.

```python
def compress(text):
    dictionary = []  # 2D-Array: z.B. [[Code, Wort], ...]
    result = []
    next_code = 1
    for word in text:
        gefunden = False
        for eintrag in dictionary:
            if eintrag[1] == word:
                result.append(eintrag[0])  # bekannten Code speichern
                gefunden = True
                break
        if not gefunden:
            code = "W" + str(next_code)
            dictionary.append([code, word])
            result.append(word)  # Erstvorkommen: Wort selbst
            next_code += 1
    return result, dictionary

def decompress(data, dictionary):
    result = ""
    for item in data:
        # item ist entweder noch das Wort oder ein Code wie "W1"
        if isinstance(item, str) and item.startswith("W") and item[1:].isdigit():
            for eintrag in dictionary:
                if eintrag[0] == item:
                    result += eintrag[1] + " "
                    break
        else:
            result += item + " "
    return result.strip()
```
