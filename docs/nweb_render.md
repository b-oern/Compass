# Compass für nweb

Python-Paket `compass` mit einem Dokument-Render für nweb. Pfade, die die App über „Hochladen (NWeb)“ in der Tagesliste als Dokument mit `kind=compass` speichert, zeigt nweb damit als Karte an:

- **Kompass-Track:** Jeder Eintrag ist ein Pfeil vom Endpunkt seines Vorgängers, Norden oben. Verzweigungen (aktiver Punkt in der App) werden wie in der App gezeichnet.
  - Farbe nach Höhe (Barometer): **orange** = tiefster, **rot** = höchster Punkt des Pfads. Ohne Höhenwert oder bei weniger als 1 m Unterschied: Mitte des Verlaufs.
- **GPS (grün):** Positionen, die beim Speichern mit aufgezeichnet wurden. Der erste Eintrag mit GPS ist der Anker, seine GPS-Position liegt auf seiner Schritt-Position. Die Legende zeigt die Abweichung am letzten GPS-Eintrag in Metern und in Prozent der Strecke seit dem Anker.
- **Bedienung:**
  - Ziehen verschiebt, Mausrad oder zwei Finger zoomen.
  - Klick auf einen Punkt zeigt Uhrzeit, Schritte, Kurs, Höhe bzw. GPS-Abweichung.
  - ⤢ zeigt alles an. Solange man nicht selbst verschoben oder gezoomt hat, passt sich die Ansicht bei Größenänderungen neu ein.
  - 📏 misst den Abstand zwischen zwei angeklickten Punkten.
  - ⛶ maximiert auf das ganze Browserfenster, ✕ oder Esc kehrt zurück.

## Installation

```bash
pip install .
```

`pyproject.toml` meldet den Render über die entry_point-Gruppe `nweb_doc` an, siehe `n-py/modules/nweb/README.md`:

```toml
[project.entry-points.nweb_doc]
compass = "compass.render:PathRender"
```

nweb findet ihn nach der Installation automatisch. In der Admin-Übersicht „Dokument-Render“ erscheint er als `compass.render:PathRender`.

## Upload aus der App

Die App bindet [NxDroidLib](https://github.com/nxware/NxDroidLib) als Unterprojekt ein (wie arscanner und NxMap). Der Checkout muss neben dem Projekt liegen:

```
dev/
  Compass/       dieses Repository
  NxDroidLib/    git clone https://github.com/nxware/NxDroidLib.git
```

```kotlin
// settings.gradle.kts
include(":nxdroidlib")
project(":nxdroidlib").projectDir = file("../NxDroidLib/lib")
```

- **Konto:** Einstellungen → „[NX] NWeb-Konto“ (`NWebAccountActivity` aus NxDroidLib, auch Import aus NxDroid).
- **Hochladen:** Tagesliste → Long-Tap auf einen Tag → „Hochladen (NWeb)“ (`PathUpload.java`). Ruft `NxDroid.userCreateDocument(…, "compass", titel, meta, datei, "application/json")` auf, also `POST /user/{userId}/doc?kind=compass`.
- NxDroidLib verlangt **minSdk 26**, deshalb läuft die App erst ab Android 8.

## Daten

| Quelle | Inhalt |
|---|---|
| `document__data.document_data` | Das hochgeladene JSON. Fehlt die Tabelle oder die Zeile, nimmt der Render `document.content`. |
| `document__meta`, ns `compass` | `day`, `items`, `steps`, `step_length`, `app_version`. Tag und Version stehen in der Kopfzeile. |

JSON des Uploads:

```json
{"version": 1, "day": "2026-11-03", "step_length": 0.9,
 "items": [{"id": 1, "heading": 0, "steps": 100, "timestamp": 1762160300, "previous_id": 0,
            "x": 0, "y": 90, "altitude": 50.2, "pressure": 1007.1,
            "latitude": 51.2277, "longitude": 6.7735, "gps_accuracy": 5.0}]}
```

Aufbereitung in `compass/track.py`:

- **Koordinaten** werden aus `heading`, `steps` und `step_length` neu berechnet, wie `PathList.computeCoordinates()` in der App. Vorgänger ist `previous_id` (0 = Ursprung); fehlt er oder ist er unbekannt, der zeitlich vorherige Eintrag.
- **Ungültige Einträge** (fehlende oder nicht endliche Werte) werden verworfen und in der Kopfzeile gezählt.
- **GPS** wird equirektangulär um den Anker in Meter umgerechnet; das reicht für Strecken bis einige Kilometer.
- Eine reine Liste von Einträgen (JSON-Export der App) wird ebenfalls angezeigt, dann mit 0,9 m Schrittweite.

## Dateien

Python-Paket (dieses Repository):

| Datei | Inhalt |
|---|---|
| `pyproject.toml` | Paket `compass`, Abhängigkeit `nweb`, entry_point `nweb_doc` |
| `compass/track.py` | JSON einlesen, Koordinaten und GPS-Abweichung berechnen |
| `compass/render.py` | `PathRender(DocRender)`, erzeugt das HTML |
| `tests/test_render.py` | Tests |

JavaScript und CSS liegen wie beim arscanner im Repository **static** (`D:\apache\htdocs\static`). Auf den Webservern ist es unter `/static` erreichbar:

| Datei | Inhalt |
|---|---|
| `js/compass/track.js` | Karte (Canvas 2D), stellt `CompassTrack.init(root)` bereit |
| `js/compass/compass.css` | Aussehen, dunkel |

Der Render bindet die Dateien über `PathRender.STATIC_URL` ein (Standard `/static/`). Liegt `static` woanders, passt man das in einer Unterklasse an. Mehrere Pfade auf einer Seite funktionieren.

Nach Änderungen an den JS-Dateien das static-Repository auf die Webserver bringen. Das Python-Paket muss dafür nicht neu installiert werden.

## Tests

```bash
PYTHONPATH=../nwebclient:../n-py/modules/nweb:. python -m unittest discover -s tests
```
