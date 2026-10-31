"""Matched phase2 review/candidate; retain every journey and sampled tradeoff."""
from pathlib import Path
import json,math,statistics
out=Path(__file__).resolve().parent

def summary(v):
    v=sorted(v)
    return {'n':len(v),'median_ms':round(statistics.median(v),2),'p95_ms':round(v[math.ceil(len(v)*.95)-1],2)}

result={'conditions':{'baseline':'review 7698b1a production-equivalent phase2 APK; same counters and final harness',
    'locale':'sv','weather':False,'dataset':'synthetic-12000-channel-v1','warmup_cycles':2,
    'measured_cycles_per_process':20,'processes_per_variant_resolution':1,
    'device':'disposable Android TV ARM64 emulator, SwiftShader software GPU',
    'limitations':['Instrumentation/idling included; not remote-to-photon or physical Chromecast',
        'One instrumented useful-Home launch per process is not cold-start TTID',
        'PSS is sampled, not peak or retained-object/leak evidence',
        'Counts cover app-owned readCatalog/guide summary and CompanionHttp only, not all Core/provider/updater I/O',
        'Frame metrics are software-GPU rendering samples, not measured TV FPS',
        'Earlier main/phase2 comparisons remain preserved in ../phase2']},'resolutions':{}}
for size in ['1920x1080','1280x720']:
    variants={}
    for variant in ['before','after']:
        x=json.loads((out/'performance'/f'perf-{variant}-{size}.json').read_text())
        memory=x['memory_summary_samples']
        gc={k:int(x['art_runtime_after'][k])-int(x['art_runtime_before'][k]) for k in x['art_runtime_after']
            if k in x['art_runtime_before'] and str(x['art_runtime_after'][k]).isdigit() and str(x['art_runtime_before'][k]).isdigit()}
        frames=[f/1e6 for f in x['frame_duration_ns']]
        variants[variant]={'latency':{k:summary(v) for k,v in x['measurements_ms'].items()},
            'cpu_process_ms':x['cpu_process_ms'],'sampled_max_pss_kib':max(x['pss_kib_samples']),
            'sampled_median_pss_kib':statistics.median(x['pss_kib_samples']),
            'mean_memory_summary_kib':{k:round(statistics.mean(int(s[k]) for s in memory),1) for k in memory[0]},
            'art_runtime_delta':gc,'app_reader_work_delta':x['app_reader_work_delta'],
            'uid_network_rx_bytes':x['network_rx_bytes'],'uid_network_tx_bytes':x['network_tx_bytes'],
            'frame_duration':summary(frames),'frame_over_16_67_ms_fraction':round(sum(f>16.67 for f in frames)/len(frames),4),
            'frame_callback_drops':x['frame_callback_drops']}
    result['resolutions'][size]=variants
(out/'comparison.json').write_text(json.dumps(result,indent=2)+'\n')
for size,variants in result['resolutions'].items():
    print(size)
    for key in variants['before']['latency']:
        a=variants['before']['latency'][key];b=variants['after']['latency'][key]
        print(key, f"median {a['median_ms']} -> {b['median_ms']}; p95 {a['p95_ms']} -> {b['p95_ms']} ({(b['p95_ms']/a['p95_ms']-1)*100:+.1f}%)")
    for key in ['cpu_process_ms','sampled_max_pss_kib','app_reader_work_delta','frame_duration','mean_memory_summary_kib','art_runtime_delta']:
        print(key,variants['before'][key],variants['after'][key])
