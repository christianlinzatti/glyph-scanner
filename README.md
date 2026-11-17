# Glyph Scanner (Ingress)

Android-Overlay, das die Glyph-Sequenz beim Ingress-Glyph-Hack **automatisch per Bildschirmanalyse erkennt**
und oben am Bildschirm einblendet – mit Glyph-Bild und Namen. Ähnlich wie Glyph Predictor, nur ohne manuelles Eintippen.

## Installation
APK unter **Releases → latest** herunterladen (`GlyphScanner.apk`) und installieren
(„Installation aus unbekannten Quellen“ für den Browser erlauben).

Jeder Push auf `main` baut automatisch eine neue APK (GitHub Actions).

## Benutzung
1. App öffnen → **Overlay-Berechtigung erteilen**.
2. **Starten** → Bildschirmaufnahme erlauben („Gesamter Bildschirm“).
3. Ingress öffnet sich, Glyph-Hack starten. Während der Anzeige („Eingehende Glyph-Sequenz“)
   füllt sich die Leiste, in der Eingabephase ist das Feld, das du gerade zeichnest, orange umrandet.

Overlay-Bedienung: **✕** beendet, **↻** leert die Anzeige, **↻ lange drücken** schaltet die Diagnose-Zeile ein.
Die Leiste kann vertikal verschoben werden – sie darf die Hexagon-Reihe von Ingress nicht verdecken,
sonst kann die App die Sequenz nicht lesen.

## Wie die Erkennung funktioniert
- Aufnahme per MediaProjection in halber Auflösung, ca. 10 Bilder/s.
- Hexagon-Reihe oben → Anzahl der Glyphen und welche gerade dran ist (orange).
- Roter Countdown sichtbar → Eingabephase, sonst Anzeigephase.
- Das 11-Punkte-Gitter wird über die drei Punkte auf der Mittelachse automatisch kalibriert;
  danach wird jede der 38 möglichen Verbindungslinien auf Leuchten geprüft.
- Pro Glyphe gewinnt das am häufigsten stabil gesehene Linienmuster; Namen aus dem Wörterbuch.
  Unbekannte Muster werden trotzdem gezeichnet (mit „?“).

Die Kernlogik (`core/`) ist reines Java und lässt sich ohne Android gegen Screenshots testen.

## Hinweise
- Inoffizielles Hilfsprogramm, nicht mit Niantic verbunden. Nutzung auf eigenes Risiko (Ingress-ToS).
- Glyph-Wörterbuch: [gm9/ingress-glyph-tools](https://github.com/gm9/ingress-glyph-tools) (MIT).
- Der Signatur-Keystore liegt bewusst im Repo, damit Updates sich drüber installieren lassen.
