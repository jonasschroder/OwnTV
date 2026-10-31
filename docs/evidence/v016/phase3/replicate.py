"""Keep both predeclared orders; pool latency only, retain per-process memory/CPU."""
from pathlib import Path
import json,statistics,math
root=Path(__file__).resolve().parent

def stats(xs):
 xs=sorted(xs)
 return {'n':len(xs),'median_ms':round(statistics.median(xs),2),'p95_ms':round(xs[math.ceil(.95*len(xs))-1],2)}
result={'conditions':{'processes_per_variant_resolution':2,'measured_cycles_per_process':20,
 'orders':['before,after','after,before'],'baseline':'7698b1a production-equivalent phase2 APK',
 'same_final_test_harness':True,'note':'Single bounded replication due first-pass p95/PSS differences. Both runs retained; no dropped samples. Instruments/software GPU, not physical performance.'},'resolutions':{}}
for size in ['1920x1080','1280x720']:
 result['resolutions'][size]={}
 for variant in ['before','after']:
  ds=[json.loads((root/'performance'/f'perf-{prefix}{variant}-{size}.json').read_text()) for prefix in ['','repeat-']]
  r={'latency':{k:stats([x for d in ds for x in d['measurements_ms'][k]]) for k in ds[0]['measurements_ms']},
     'cpu_process_ms':[d['cpu_process_ms'] for d in ds],
     'sampled_max_pss_kib':[max(d['pss_kib_samples']) for d in ds],
     'sampled_median_pss_kib':[statistics.median(d['pss_kib_samples']) for d in ds],
     'mean_pss_kib':[round(statistics.mean(d['pss_kib_samples'])) for d in ds],
     'frame_duration':stats([n/1e6 for d in ds for n in d['frame_duration_ns']]),
     'reader_counts':[d['app_reader_work_delta'] for d in ds]}
  result['resolutions'][size][variant]=r
 print(size)
 a,b=[result['resolutions'][size][v] for v in ['before','after']]
 for k in a['latency']:
  x,y=a['latency'][k],b['latency'][k];print(k,x['median_ms'],x['p95_ms'],'->',y['median_ms'],y['p95_ms'])
 print('PSS max',a['sampled_max_pss_kib'],b['sampled_max_pss_kib'],'mean',a['mean_pss_kib'],b['mean_pss_kib'])
 print('CPU',a['cpu_process_ms'],b['cpu_process_ms'],'frames',a['frame_duration'],b['frame_duration'])
(root/'replication.json').write_text(json.dumps(result,indent=2)+'\n')
