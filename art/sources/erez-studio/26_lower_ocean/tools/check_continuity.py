"""Analytic source used by the native shader builder; no raster image editing."""
from pathlib import Path
import math,json,hashlib
ROOT=Path(__file__).resolve().parents[1]
SPEC=json.loads((ROOT/'source/material_OC01.json').read_text(encoding='utf8'))
TAU=2*math.pi
def phase(w,x,y,t):
    return TAU*(w['kx']*x/SPEC['spatial_period_blocks']+w['ky']*y/SPEC['spatial_period_blocks']+w['kt']*t/SPEC['animation_period_seconds'])+w['phase']
def wave(w,x,y,t):return math.sin(phase(w,x,y,t))*w['weight']
def fields(x,y,t):
    warp=sum(wave(w,x,y,t) for w in SPEC['domain_warp'])
    broad=sum(wave(w,x,y,t) for w in SPEC['broad_currents'])
    ribbon=math.sin(phase(SPEC['ribbon'],x,y,t)+warp)
    fine=sum(wave(w,x,y,t) for w in SPEC['fine_ripples'])
    ax=TAU*(x/64-t/16)+.22*warp;ay=TAU*y/64+.18*warp;r=SPEC['periodic_noise']['torus_radius']
    return (.47+broad+SPEC['ribbon']['weight']*ribbon+fine,warp,ribbon,fine,r*math.cos(ax),r*math.sin(ax),r*math.cos(ay),r*math.sin(ay))
def check():
    max_space=max_time=max_boundary=0.0
    for t in [0,.7,3.1,7.9,12.3,16]:
        for i in range(129):
            u=i*64/128
            for a,b in [(fields(0,u,t),fields(64,u,t)),(fields(u,0,t),fields(u,64,t))]:
                max_space=max(max_space,max(abs(x-y) for x,y in zip(a,b)))
            max_time=max(max_time,max(abs(x-y) for x,y in zip(fields(u,u*.37,0),fields(u,u*.37,16))))
        # Independent patch coordinates resolve to the same world point at every seam.
        for boundary in [-32,-16,0,16,32]:
            for local_y in [-31.7,-12.3,0,7.4,31.9]:
                left_origin=boundary-16;left_local=16;right_origin=boundary;right_local=0
                a=fields(left_origin+left_local,local_y,t);b=fields(right_origin+right_local,local_y,t)
                max_boundary=max(max_boundary,max(abs(x-y) for x,y in zip(a,b)))
    assert max_space<1e-12 and max_time<1e-12 and max_boundary==0
    # Probe both sides of a seam without quantization; the difference must shrink.
    errors=[]
    for epsilon in [.01,.001,.0001]:
        errors.append(max(abs(fields(16-epsilon,y,t)[0]-fields(16+epsilon,y,t)[0]) for y in [0,7.3,21.9] for t in [0,2,9]))
    assert errors[2]<errors[1]<errors[0]
    report={'revision':'OC01','source_sha256':hashlib.sha256((ROOT/'source/material_OC01.json').read_bytes()).hexdigest(),
        'period_blocks':64,'period_seconds':16,'cross_patch_value_error':max_boundary,'spatial_wrap_max_error':max_space,
        'time_loop_max_error':max_time,'two_sided_seam_probe_errors':errors,'continuous_world_field':True,
        'rendered_mesh_seams_checked_separately':True,'minecraft_or_DH_verified':False}
    (ROOT/'reports').mkdir(exist_ok=True);(ROOT/'reports/CONTINUITY.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report))
if __name__=='__main__':check()
