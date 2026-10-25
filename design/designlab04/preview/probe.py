import urllib.request, urllib.error, json, datetime
from pathlib import Path
URLS={'sportsdb':'https://www.thesportsdb.com/api/v1/json/123/eventsnextleague.php?id=4419','sportsdb_tv':'https://www.thesportsdb.com/api/v1/json/123/lookuptv.php?id=2476548','twitch':'https://api.twitch.tv/helix/streams?user_login=ohnepixel','tmdb':'https://api.themoviedb.org/3/configuration','swehockey_robots':'https://stats.swehockey.se/robots.txt','tvmatchen_robots':'https://www.tvmatchen.nu/robots.txt'}
def probe():
 results={}
 for name,url in URLS.items():
  item={'source':url,'fetchedAt':datetime.datetime.now(datetime.timezone.utc).isoformat()}
  try:
   with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'MinTV-DesignLab-Readiness/1.0'}),timeout=8) as r:
    raw=r.read(524289)
    if len(raw)>524288: raise ValueError('Response exceeds bound')
    item.update(http=r.status,bytes=len(raw))
    if name.startswith('sportsdb'):
     data=json.loads(raw); item['keys']=list(data);item['data']=data
    elif name.endswith('robots'): item['robots']=raw.decode('utf-8',errors='replace')
  except urllib.error.HTTPError as e: item.update(http=e.code,status='AUTH_REQUIRED' if e.code==401 else 'HTTP_RESPONSE')
  except Exception as e: item.update(status='ENV_BLOCKED',errorType=type(e).__name__)
  results[name]=item
 return results
if __name__=='__main__':
 results=probe();Path(__file__).with_name('probes.json').write_text(json.dumps(results,indent=2))
 print(json.dumps({k:{x:y for x,y in v.items() if x not in ['data','robots']} for k,v in results.items()},indent=2))
