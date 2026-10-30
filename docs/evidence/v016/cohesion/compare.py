from pathlib import Path
import json,statistics,math
out=Path(__file__).resolve().parent
result={'environment':'Disposable Android 14 Android TV ARM64 emulator; software GPU; instrumentation/idle overhead included; not physical Chromecast','dataset':'synthetic-12000-channel-v1','warmup_cycles_per_process':2,'measured_cycles_per_process':20,'processes_per_variant_resolution':2,'resolutions':{}}
def summary(values):
 v=sorted(values);return {'n':len(v),'median_ms':round(statistics.median(v),2),'p95_ms':round(v[math.ceil(len(v)*.95)-1],2)}
for size in ['1920x1080','1280x720']:
 variants={}
 for name in ['baseline','candidate']:
  js=[json.loads((out/name/size/f'journey-{i}.json').read_text()) for i in [1,2]]
  keys=js[0]['measurements_ms'].keys()
  variants[name]={'latency':{k:summary([x for j in js for x in j['measurements_ms'][k]]) for k in keys},'mean_cpu_process_ms':statistics.mean(j['cpu_process_ms'] for j in js),'max_sampled_pss_kib':max(x for j in js for x in j['pss_kib_samples']),'network_rx_bytes':[j['network_rx_bytes'] for j in js],'network_tx_bytes':[j['network_tx_bytes'] for j in js],'frame_duration':summary([x/1e6 for j in js for x in j['frame_duration_ns']]),'frame_callback_drops':sum(j['frame_callback_drops'] for j in js)}
 result['resolutions'][size]=variants
(out/'android-comparison.json').write_text(json.dumps(result,indent=2)+'\n')
for size,v in result['resolutions'].items():
 print(size)
 for key in ['enter_home_ms','enter_tv_ms','enter_sport_ms','enter_profile_ms','background_resume_ms']:
  a=v['baseline']['latency'][key];b=v['candidate']['latency'][key];print(key,a,b,'p95 change %',round((b['p95_ms']/a['p95_ms']-1)*100,1))
 print('CPU ms',v['baseline']['mean_cpu_process_ms'],v['candidate']['mean_cpu_process_ms'],'sampled max KiB',v['baseline']['max_sampled_pss_kib'],v['candidate']['max_sampled_pss_kib'])
