"""Fixed, public development endpoint only. No arbitrary URLs or credentials."""
import datetime,json,threading,time,urllib.request
URL='https://www.thesportsdb.com/api/v1/json/123/eventsnextleague.php?id=4419'
_lock=threading.Lock()
_cache=None
_expiry=0

def normalize(payload, fetched):
 events=payload.get('events')
 if not isinstance(events,list) or not events: raise ValueError('Missing events')
 rows=[]
 for e in events[:20]:
  if str(e.get('idLeague'))!='4419' or not all(isinstance(e.get(k),str) and e[k] for k in ['idEvent','strHomeTeam','strAwayTeam','dateEvent']): raise ValueError('Unexpected event shape')
  rows.append({k:e.get(k) for k in ['idEvent','strHomeTeam','strAwayTeam','dateEvent','strTime','strStatus','intHomeScore','intAwayScore']})
 return dict(mode='LIVE',status='REACHABLE_VALIDATED',source='TheSportsDB · SHL · free development API',sourceUrl=URL,fetchedAt=fetched,freshness='fresh',maxAgeSeconds=900,coverage='Partial: free API limited to one upcoming event. Timezone unverified; time is displayed as supplied.',fixtures=rows,broadcastAssignments=[],assignmentStatus='UNAVAILABLE: no verified assignment for these fixtures')

def live():
 global _cache,_expiry
 with _lock:
  if _cache and time.monotonic()<_expiry: return _cache
  fetched=datetime.datetime.now(datetime.timezone.utc).isoformat()
  try:
   # No redirects: never forward requests outside the reviewed host/endpoint.
   class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs): return None
   with urllib.request.build_opener(NoRedirect).open(urllib.request.Request(URL,headers={'User-Agent':'MinTV-DesignLab/1.0'}),timeout=8) as response:
    raw=response.read(524289)
    if len(raw)>524288: raise ValueError('Oversized response')
    value=normalize(json.loads(raw),fetched)
  except Exception:
   value=dict(mode='UNAVAILABLE',status='UNAVAILABLE',source='TheSportsDB',fetchedAt=None,attemptedAt=fetched,freshness='unknown',fixtures=[],broadcastAssignments=[],reason='Public API unavailable or invalid response; no mock fallback.')
  _cache=value;_expiry=time.monotonic()+(900 if value['mode']=='LIVE' else 120)
  return value
