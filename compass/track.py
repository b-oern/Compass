"""
Liest einen Pfad der App Compass (Upload aus der Tagesliste, PathUpload.java).

Inhalt (JSON):
    {"version": 1, "day": "2026-11-03", "step_length": 0.9,
     "items": [{"id", "heading", "steps", "timestamp", "previous_id",
                "altitude"?, "pressure"?, "latitude"?, "longitude"?, "gps_accuracy"?}, ...]}

Eine reine Liste von Items wird ebenfalls akzeptiert.

Koordinaten werden wie in der App (PathList.computeCoordinates) neu berechnet: Jedes Item
beginnt am Endpunkt seines Vorgaengers (previous_id, sonst das vorherige Item; 0 = Ursprung)
und bewegt sich steps * step_length Meter in Richtung heading. x = Ost, y = Nord.

GPS: Das erste Item mit Position ist der Anker, seine GPS-Position liegt auf seiner
Schritt-Position. Die Abweichung der uebrigen zeigt die Genauigkeit der Schrittmessung.
"""
import json
import math

DEFAULT_STEP_LENGTH = 0.9
EARTH_RADIUS_M = 6371000.0


def _number(value):
    """ endliche Zahl oder None """
    if isinstance(value, bool) or value is None:
        return None
    try:
        v = float(value)
    except (TypeError, ValueError):
        return None
    return v if math.isfinite(v) else None


class Node:
    """ Ein Item mit berechneten Koordinaten (Start sx/sy, Ende x/y in Metern) """

    def __init__(self, item, prev, step_length):
        self.id = int(item['id'])
        self.heading = int(item['heading']) % 360
        self.steps = int(item['steps'])
        self.timestamp = int(item.get('timestamp') or 0)
        self.prev = prev
        self.sx = prev.x if prev else 0.0
        self.sy = prev.y if prev else 0.0
        rad = math.radians(self.heading)
        dist = self.steps * step_length
        self.x = self.sx + dist * math.sin(rad)
        self.y = self.sy + dist * math.cos(rad)
        self.altitude = _number(item.get('altitude'))
        lat, lon = _number(item.get('latitude')), _number(item.get('longitude'))
        self.lat_lon = (lat, lon) if lat is not None and lon is not None else None
        self.gps_accuracy = _number(item.get('gps_accuracy'))
        # GPS-Position in Pfad-Koordinaten und Abweichung, von Track.align_gps gesetzt
        self.gx = self.gy = self.deviation = None

    def steps_from_origin(self):
        """ Schritte vom Ursprung bis zum Endpunkt entlang der Vorgaenger """
        total, n = 0, self
        while n is not None:
            total += n.steps
            n = n.prev
        return total


class Track:

    def __init__(self, step_length=DEFAULT_STEP_LENGTH, day=''):
        self.step_length = step_length
        self.day = day
        self.nodes = []
        self.invalid = 0
        self.gps_anchor = None
        self._by_id = {}

    def add(self, item):
        """ Item anfuegen; ungueltige werden gezaehlt und verworfen """
        if not isinstance(item, dict) or any(_number(item.get(k)) is None for k in ('id', 'heading', 'steps')):
            self.invalid += 1
            return
        last = self.nodes[-1] if self.nodes else None
        pid = _number(item.get('previous_id'))
        if pid == 0:
            prev = None                                  # beginnt am Ursprung
        elif pid is not None and int(pid) in self._by_id:
            prev = self._by_id[int(pid)]                 # Verzweigung
        else:
            prev = last                                  # ohne/unbekannt: vorheriges Item
        node = Node(item, prev, self.step_length)
        self.nodes.append(node)
        self._by_id[node.id] = node

    def align_gps(self):
        """ GPS-Positionen auf den Pfad legen (equirektangulaere Naeherung, reicht fuer einige km) """
        gps = [n for n in self.nodes if n.lat_lon]
        if not gps:
            return
        anchor = self.gps_anchor = gps[0]
        lat0, lon0 = anchor.lat_lon
        cos0 = math.cos(math.radians(lat0))
        for n in gps:
            lat, lon = n.lat_lon
            n.gx = anchor.x + EARTH_RADIUS_M * math.radians(lon - lon0) * cos0
            n.gy = anchor.y + EARTH_RADIUS_M * math.radians(lat - lat0)
            n.deviation = math.hypot(n.gx - n.x, n.gy - n.y)

    def total_steps(self):
        return sum(n.steps for n in self.nodes)

    def displacement(self):
        """ Luftlinie vom Ursprung zum Endpunkt des letzten Items """
        return math.hypot(self.nodes[-1].x, self.nodes[-1].y) if self.nodes else 0.0

    def gps_summary(self):
        """ {'count', 'deviation', 'distance'} zum letzten Item mit GPS, None ohne GPS """
        gps = [n for n in self.nodes if n.gx is not None]
        if not gps:
            return None
        last = gps[-1]
        steps = abs(last.steps_from_origin() - self.gps_anchor.steps_from_origin())
        return {
            'count': len(gps),
            'deviation': round(last.deviation, 2),
            'distance': round(steps * self.step_length, 2),
        }

    def bounds(self):
        """ {'min': [x, y], 'max': [x, y]} ueber Pfad, GPS und Ursprung """
        xs, ys = [0.0], [0.0]
        for n in self.nodes:
            xs += [n.sx, n.x]
            ys += [n.sy, n.y]
            if n.gx is not None:
                xs.append(n.gx)
                ys.append(n.gy)
        return {'min': [round(min(xs), 2), round(min(ys), 2)], 'max': [round(max(xs), 2), round(max(ys), 2)]}

    def to_dict(self):
        """ Kompakte Daten fuer die Seite, Koordinaten auf cm gerundet """
        alts = [n.altitude for n in self.nodes if n.altitude is not None]
        items = []
        for n in self.nodes:
            d = {'id': n.id, 'heading': n.heading, 'steps': n.steps, 't': n.timestamp,
                 'sx': round(n.sx, 2), 'sy': round(n.sy, 2), 'x': round(n.x, 2), 'y': round(n.y, 2),
                 'prev': n.prev.id if n.prev else 0}
            if n.altitude is not None:
                d['alt'] = round(n.altitude, 1)
            if n.gx is not None:
                d['gx'] = round(n.gx, 2)
                d['gy'] = round(n.gy, 2)
                d['dev'] = round(n.deviation, 2)
                if n.gps_accuracy is not None:
                    d['gacc'] = round(n.gps_accuracy, 1)
            items.append(d)
        steps = self.total_steps()
        return {
            'items': items,
            'bounds': self.bounds(),
            'stats': {
                'items': len(items),
                'invalid': self.invalid,
                'steps': steps,
                'distance': round(steps * self.step_length, 1),
                'displacement': round(self.displacement(), 1),
                'step_length': self.step_length,
                'alt_min': round(min(alts), 1) if alts else None,
                'alt_max': round(max(alts), 1) if alts else None,
                'gps': self.gps_summary(),
            },
        }


def parse(data) -> Track:
    """ data: bytes oder str mit dem JSON des Uploads """
    if isinstance(data, (bytes, bytearray, memoryview)):
        data = bytes(data).decode('utf-8', errors='replace')
    try:
        doc = json.loads(data) if data else {}
    except ValueError:
        doc = {}
    if isinstance(doc, list):
        doc = {'items': doc}
    if not isinstance(doc, dict):
        doc = {}
    step_length = _number(doc.get('step_length'))
    track = Track(step_length if step_length and step_length > 0 else DEFAULT_STEP_LENGTH,
                  str(doc.get('day') or ''))
    items = doc.get('items') if isinstance(doc.get('items'), list) else []
    # Reihenfolge wie in der App: nach Zeit, dann id (Vorgaenger liegen immer davor)
    valid = [i for i in items if isinstance(i, dict)]
    track.invalid += len(items) - len(valid)
    valid.sort(key=lambda i: (_number(i.get('timestamp')) or 0, _number(i.get('id')) or 0))
    for item in valid:
        track.add(item)
    track.align_gps()
    return track
