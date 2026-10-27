#!/usr/bin/env python3
"""Summarize raw synthetic probe files; no invented missing metrics or speed thresholds."""
import argparse,json,math,statistics
p=argparse.ArgumentParser();p.add_argument('files',nargs='+');args=p.parse_args()
rows=[json.load(open(f)) for f in args.files]
assert len({r['dataset'] for r in rows})==1
for key in sorted({k for r in rows for k in r['measurements_ms']}):
    values=[v for r in rows for v in r['measurements_ms'].get(key,[])]
    print(f'{key}: n={len(values)} median={statistics.median(values):.1f} p95={sorted(values)[math.ceil(len(values)*.95)-1]:.1f} ms')
for r in rows:
    frames=[v/1e6 for v in r['frame_duration_ns']]
    print(r['label'], 'PSS KiB samples=',r['pss_kib_samples'], 'CPU ms=',r['cpu_process_ms'],
          'UID network Rx/Tx=',(r['network_rx_bytes'],r['network_tx_bytes']),
          'frame callbacks=',len(frames),'callback losses=',r['frame_callback_drops'])
    if frames: print(f'Frame TOTAL_DURATION median={statistics.median(frames):.1f} p95={sorted(frames)[math.ceil(len(frames)*.95)-1]:.1f} ms; >16.7ms={sum(v>16.7 for v in frames)}/{len(frames)}')
    print('Unmeasured:', ', '.join(r['unmeasured']))
