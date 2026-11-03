"""
nweb_doc-Render fuer kind "compass" (siehe nweb/doc.py):

    [project.entry-points.nweb_doc]
    compass = "compass.render:PathRender"

Die App laedt Pfade ueber POST /user/{userId}/doc?kind=compass hoch (PathUpload.java), das JSON
liegt dann in document__data.document_data. Angezeigt wird eine Karte (Canvas): der Kompass-Track
als Pfeile, nach Hoehe orange (tief) bis rot (hoch) eingefaerbt, die GPS-Referenz gruen.

JavaScript und CSS liegen nicht im Paket, sondern im Repository static
(D:/apache/htdocs/static), auf den Webservern unter /static erreichbar:
    js/compass/track.js, compass.css
"""
import datetime
import html
import json

from nweb.doc import DocRender

from compass.track import parse

# Rohdaten des Uploads; die Tabelle legt die PHP-Anwendung (4.0) an
DATA_SQL = "SELECT document_data FROM document__data WHERE document_id = ?"


def _script_json(obj):
    """ JSON fuer <script type="application/json">, ohne dass </script> den Block beendet """
    return json.dumps(obj, separators=(',', ':')).replace('</', '<\\/')


def _num(v, digits=0):
    """ Zahl im deutschen Format, z.B. 1.234 bzw. 0,90 """
    s = f"{v:,.{digits}f}"
    return s.replace(',', '#').replace('.', ',').replace('#', '.')


class PathRender(DocRender):
    show_title = True

    # URL des static-Repositories auf dem Webserver
    STATIC_URL = '/static/'

    def asset_url(self, name):
        return self.STATIC_URL + 'js/compass/' + name

    def load(self):
        """ JSON aus document__data.document_data, sonst (alte Uploads, Tests) aus content """
        try:
            rows = self.ui.nw().select(DATA_SQL, [self.d.document_id])
        except Exception:
            rows = []
        for row in rows:
            if row[0]:
                return row[0]
        return self.d.content or ''

    def info(self, track, stats):
        parts = []
        day = track.day or self.d.getMetaValue('compass', 'day', '')
        if day:
            try:
                parts.append(datetime.date.fromisoformat(str(day)).strftime('%d.%m.%Y'))
            except ValueError:
                parts.append(html.escape(str(day)))
        parts.append(f"{stats['items']} Einträge")
        parts.append(f"{_num(stats['steps'])} Schritte · {_num(stats['distance'])} m")
        parts.append(f"Luftlinie {_num(stats['displacement'])} m")
        parts.append(f"Schrittweite {_num(stats['step_length'], 2)} m")
        if stats['invalid']:
            parts.append(f"{stats['invalid']} ungültige verworfen")
        version = self.d.getMetaValue('compass', 'app_version', '')
        if version:
            parts.append('App ' + html.escape(str(version)))
        return ' · '.join(parts)

    def legend(self, stats):
        parts = []
        if stats['alt_min'] is not None:
            parts.append(f"Höhe {_num(stats['alt_min'])} m <span class=\"compass_scale\"></span> "
                         f"{_num(stats['alt_max'])} m (Δ {_num(stats['alt_max'] - stats['alt_min'])} m)")
        gps = stats['gps']
        if gps:
            if gps['distance'] > 0:
                pct = 100 * gps['deviation'] / gps['distance']
                parts.append(f"<span class=\"compass_gps\">GPS: Abweichung am Ende {_num(gps['deviation'])} m "
                             f"({_num(pct)} % von {_num(gps['distance'])} m)</span>")
            else:
                parts.append('<span class="compass_gps">GPS: nur ein Eintrag mit Position</span>')
        return ' · '.join(parts)

    def toHtml(self, params={}):
        track = parse(self.load())
        if not track.nodes:
            return '<div class="empty">Keine Einträge im Pfad</div>'
        data = track.to_dict()
        uid = f"compass_{int(self.d.document_id)}"
        return f"""
<link rel="stylesheet" href="{self.asset_url('compass.css')}" />
<div class="compass_track" id="{uid}">
  <div class="compass_bar">
    <span class="compass_info">{self.info(track, data['stats'])}</span>
    <button type="button" data-act="max" title="Maximieren (Esc beendet)">⛶</button>
  </div>
  <div class="compass_view">
    <canvas></canvas>
    <div class="compass_tools">
      <button type="button" data-act="fit" title="Alles anzeigen">⤢</button>
      <button type="button" data-act="measure" title="Messen: zwei Punkte anklicken">📏</button>
    </div>
    <div class="compass_tip"></div>
  </div>
  <div class="compass_legend">{self.legend(data['stats'])}</div>
  <script type="application/json" class="compass_data">{_script_json(data)}</script>
</div>
<script src="{self.asset_url('track.js')}"></script>
<script>CompassTrack.init(document.getElementById('{uid}'));</script>
"""
