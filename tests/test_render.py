"""
python -m unittest discover -s tests

Braucht nweb und nwebclient im Pfad, z.B.
PYTHONPATH=../nwebclient:../n-py/modules/nweb
"""
import json
import math
import re
import unittest

from compass.track import parse
from compass.render import PathRender

# 0,9 m Schrittweite: 100 Schritte Nord, 50 Ost, dann Verzweigung ab Item 1 nach West
DOC = {
    'version': 1,
    'day': '2026-11-03',
    'step_length': 0.9,
    'items': [
        {'id': 1, 'heading': 0, 'steps': 100, 'timestamp': 100, 'previous_id': 0,
         'altitude': 50.0, 'latitude': 51.0, 'longitude': 6.0, 'gps_accuracy': 5.0},
        {'id': 2, 'heading': 90, 'steps': 50, 'timestamp': 200, 'previous_id': 1, 'altitude': 54.0},
        {'id': 3, 'heading': 270, 'steps': 30, 'timestamp': 300, 'previous_id': 1,
         'latitude': 51.0, 'longitude': 5.999, 'gps_accuracy': 8.0},
    ],
}


class ParseTest(unittest.TestCase):

    def test_coordinates_and_branch(self):
        t = parse(json.dumps(DOC).encode())
        n1, n2, n3 = t.nodes
        self.assertAlmostEqual(n1.y, 90.0)
        self.assertAlmostEqual(n2.x, 45.0)
        self.assertAlmostEqual(n2.y, 90.0)
        # Item 3 setzt an Item 1 fort, nicht an Item 2
        self.assertIs(n3.prev, n1)
        self.assertAlmostEqual(n3.sx, 0.0)
        self.assertAlmostEqual(n3.x, -27.0)

    def test_without_previous_id_chains(self):
        items = [{'id': 1, 'heading': 0, 'steps': 10}, {'id': 2, 'heading': 90, 'steps': 10}]
        t = parse(json.dumps(items))   # reine Liste, Standard-Schrittweite 0,9
        self.assertIs(t.nodes[1].prev, t.nodes[0])
        self.assertAlmostEqual(t.nodes[1].x, 9.0)
        self.assertAlmostEqual(t.nodes[1].y, 9.0)

    def test_unknown_previous_falls_back(self):
        items = [{'id': 1, 'heading': 0, 'steps': 10, 'timestamp': 1},
                 {'id': 2, 'heading': 0, 'steps': 10, 'timestamp': 2, 'previous_id': 99}]
        t = parse(json.dumps(items))
        self.assertIs(t.nodes[1].prev, t.nodes[0])

    def test_invalid_items(self):
        items = [{'id': 1, 'heading': 0, 'steps': 10}, {'id': 2, 'heading': 'x', 'steps': 1}, 'kaputt',
                 {'id': 3, 'heading': float('nan'), 'steps': 1}]
        t = parse(json.dumps(items))
        self.assertEqual(len(t.nodes), 1)
        self.assertEqual(t.invalid, 3)

    def test_broken_json(self):
        t = parse(b'{kein json')
        self.assertEqual(t.nodes, [])

    def test_gps_alignment(self):
        t = parse(json.dumps(DOC))
        n1, n2, n3 = t.nodes
        # Anker: GPS von Item 1 liegt auf seiner Schritt-Position
        self.assertAlmostEqual(n1.gx, n1.x)
        self.assertAlmostEqual(n1.deviation, 0.0)
        self.assertIsNone(n2.gx)
        # 0,001 Grad Laenge bei 51 Grad Breite ~ 70 m nach West
        expected = 6371000.0 * math.radians(-0.001) * math.cos(math.radians(51.0))
        self.assertAlmostEqual(n3.gx, expected, places=3)
        gps = t.gps_summary()
        self.assertEqual(gps['count'], 2)
        self.assertAlmostEqual(gps['distance'], 27.0)   # 30 Schritte ab Anker
        self.assertAlmostEqual(gps['deviation'], round(abs(expected - n3.x), 2))

    def test_to_dict(self):
        d = parse(json.dumps(DOC)).to_dict()
        self.assertEqual(d['stats']['steps'], 180)
        self.assertEqual(d['stats']['distance'], 162.0)
        self.assertEqual(d['stats']['alt_min'], 50.0)
        self.assertEqual(d['stats']['alt_max'], 54.0)
        self.assertEqual(d['items'][2]['prev'], 1)
        self.assertNotIn('alt', d['items'][2])
        self.assertIn('gx', d['items'][2])
        self.assertLessEqual(d['bounds']['min'][0], d['items'][2]['gx'])
        self.assertEqual(d['bounds']['max'][1], 90.0)


class FakeDoc:
    def __init__(self, content='', document_id=7, meta=None):
        self.content = content
        self.document_id = document_id
        self.meta = meta or {}

    def getMetaValue(self, ns, name, default_value=''):
        return self.meta.get((ns, name), default_value)


class FakeNw:
    def __init__(self, rows=None, fail=False):
        self.rows = rows or []
        self.fail = fail
        self.sql = None

    def select(self, sql, args=None):
        if self.fail:
            raise Exception('no such table: document__data')
        self.sql = (sql, args)
        return self.rows


class FakeUi:
    def __init__(self, nw):
        self._nw = nw
        self.p = None

    def nw(self):
        return self._nw


def render(doc, nw):
    return PathRender(FakeUi(nw), doc).toHtml()


class RenderTest(unittest.TestCase):

    def test_data_from_document_data(self):
        nw = FakeNw(rows=[(json.dumps(DOC).encode(),)])
        res = render(FakeDoc(meta={('compass', 'app_version'): '1.14.11'}), nw)
        self.assertIn('SELECT document_data FROM document__data', nw.sql[0])
        self.assertEqual(nw.sql[1], [7])
        self.assertIn('id="compass_7"', res)
        self.assertIn('<canvas>', res)
        self.assertIn('CompassTrack.init', res)
        self.assertIn('src="/static/js/compass/track.js"', res)
        self.assertIn('href="/static/js/compass/compass.css"', res)
        self.assertIn('data-act="max"', res)
        self.assertIn('03.11.2026', res)
        self.assertIn('App 1.14.11', res)
        self.assertIn('Höhe 50 m', res)
        self.assertIn('GPS: Abweichung am Ende', res)
        data = json.loads(re.search(r'class="compass_data">(.*?)</script>', res, re.S).group(1))
        self.assertEqual(data['stats']['items'], 3)

    def test_fallback_content(self):
        res = render(FakeDoc(content=json.dumps(DOC)), FakeNw(fail=True))
        self.assertIn('CompassTrack.init', res)

    def test_empty(self):
        self.assertIn('Keine Einträge', render(FakeDoc(content='{"items": []}'), FakeNw()))

    def test_script_escape(self):
        # Meta wird escaped, JSON kann den Script-Block nicht beenden
        doc = dict(DOC, day='</script><script>x</script>')
        res = render(FakeDoc(content=json.dumps(doc),
                             meta={('compass', 'app_version'): '<b>x</b>'}), FakeNw())
        self.assertNotIn('<script>x</script>', res)
        self.assertNotIn('<b>x</b>', res)
        self.assertIn('&lt;b&gt;', res)


if __name__ == '__main__':
    unittest.main()
