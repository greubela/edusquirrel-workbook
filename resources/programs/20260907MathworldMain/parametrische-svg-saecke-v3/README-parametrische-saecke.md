# Parametrische SVG-Säcke

Die Dateien enthalten zwei eigenständig gezeichnete Cartoon-Varianten:

- `seed`: geschlossener Saatgutsack mit Blatt-/Samenemblem
- `fertilizer`: offener Düngersack mit Granulat und Naht

Die Grafiken werden **nicht** als fertiges Bild mit `scale(x, y)` verzerrt. Stattdessen berechnet der Generator die Silhouette für jede Breite und Höhe neu.

## Dateien

- `sack-generator.js` – wiederverwendbarer JavaScript-Generator
- `sack-demo.html` – eigenständige interaktive Demo mit Reglern und SVG-Export
- `sack-seed.svg` – statisches Saatgut-Beispiel, 320 × 420
- `sack-fertilizer.svg` – statisches Dünger-Beispiel, 320 × 420

## Einbau

```html
<script src="sack-generator.js"></script>
<div id="sack"></div>
<script>
  SackGraphics.mount('#sack', {
    width: 460,
    height: 300,
    kind: 'seed' // oder 'fertilizer'
  });
</script>
```

Alternativ lässt sich das Markup als String erzeugen:

```js
const markup = SackGraphics.markup({
  width: 240,
  height: 520,
  kind: 'fertilizer'
});
```

## Warum die Form stabil bleibt

1. **Parametrische Silhouette**
   Seiten, Schultern und Boden werden aus `width` und `height` neu berechnet.

2. **Feste Funktionszonen**
   Verschluss, Krempe, Knoten und Bodenrundungen orientieren sich an `min(width, height)`. Die mittlere Körperfläche übernimmt den größten Teil der Dehnung.

3. **Proportionale Dekorationen**
   Emblem, Blätter, Körner und Knoten werden nur gleichmäßig skaliert und anschließend neu positioniert.

4. **Konstante Konturen**
   Die SVG-Elemente verwenden `vector-effect="non-scaling-stroke"`, runde Linienenden und runde Linienverbindungen.

5. **Wiederholung statt Dehnung**
   Bei breiten Säcken werden zusätzliche Nahtzeichen verteilt. Einzelne Zeichen werden nicht in die Breite gezogen.

## Empfohlene Grenzen

Die vorhandene Silhouette ist für Seitenverhältnisse von ungefähr **0,45 bis 1,8** ausgelegt. Für extrem flache oder sehr schmale Säcke sollte eine zweite Silhouettenfamilie definiert werden, statt eine einzige Form unbegrenzt zu dehnen.

## Farben ändern

```js
SackGraphics.mount('#sack', {
  width: 360,
  height: 420,
  kind: 'seed',
  palette: {
    tan: '#cda16c',
    green1: '#d9e798',
    green2: '#9bc66b',
    outline: '#28170d'
  }
});
```

Die Beispiele sind eigenständige Illustrationen im allgemeinen, freundlichen Cartoon-Look und keine 1:1-Nachzeichnung der hochgeladenen Stockgrafiken.
