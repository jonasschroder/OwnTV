import unittest
from unittest.mock import patch
import readiness
class ReadinessTests(unittest.TestCase):
 def test_partial_fixture_without_broadcast_inference(self):
  e=dict(idLeague='4419',idEvent='1',strHomeTeam='A',strAwayTeam='B',dateEvent='2026-10-24',strTime='13:00:00',strThumb='secret')
  d=readiness.normalize({'events':[e]},'2026-10-24T12:00:00Z')
  self.assertEqual(d['broadcastAssignments'],[]);self.assertNotIn('strThumb',d['fixtures'][0]);self.assertEqual(d['mode'],'LIVE')
 def test_invalid_shape_rejected(self):
  for d in ({},{'events':None},{'events':[]},{'events':[{'idLeague':'4328'}]}):
   with self.assertRaises(ValueError):readiness.normalize(d,'now')
 def test_failure_is_unavailable_cached_without_mock(self):
  readiness._cache=None;readiness._expiry=0
  with patch('urllib.request.build_opener',side_effect=OSError('private error')) as p:
   a=readiness.live();b=readiness.live();self.assertEqual(a,b);self.assertEqual(p.call_count,1)
  self.assertEqual(a['mode'],'UNAVAILABLE');self.assertEqual(a['fixtures'],[]);self.assertNotIn('private',str(a))
if __name__=='__main__':unittest.main()
