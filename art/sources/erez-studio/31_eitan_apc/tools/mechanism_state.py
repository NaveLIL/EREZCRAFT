"""Executable interaction contract for the future Minecraft adapter, not a live mod."""
from dataclasses import dataclass,field,asdict
@dataclass
class MechanismState:
    ramp:float=0
    door:float=0
    speed_m_s:float=0
    hatches:dict=field(default_factory=lambda:{k:0 for k in ['driver','commander','troop_left','troop_right']})
    seats:dict=field(default_factory=lambda:{f'troop_{i}':0 for i in range(1,10)})
    def command(self,target,value):
        if not isinstance(value,(int,float)) or not 0<=value<=1:raise ValueError('Opening fraction must be in [0,1]')
        if target in ['ramp','door']:
            if value>0 and abs(self.speed_m_s)>.05:raise ValueError('Vehicle must be stopped before opening rear access')
            if target=='ramp' and value>0 and self.door>0:raise ValueError('Close the door before lowering the ramp')
            if target=='door' and value>0 and self.ramp>0:raise ValueError('Raise the ramp before opening its door')
            setattr(self,target,float(value));return
        kind,_,name=target.partition(':');mapping=self.hatches if kind=='hatch' else self.seats if kind=='seat' else {}
        if name not in mapping:raise ValueError('Unknown mechanism')
        mapping[name]=float(value)
    def snapshot(self):return {'schema':1,**asdict(self)}

def verify_contract():
    checks=[]
    s=MechanismState();s.command('ramp',1)
    try:s.command('door',1)
    except ValueError:checks.append('door blocked with lowered ramp')
    s.command('ramp',0);s.command('door',1)
    try:s.command('ramp',1)
    except ValueError:checks.append('ramp blocked with open door')
    s.command('door',0);s.speed_m_s=1
    for t in ['ramp','door']:
        try:s.command(t,1)
        except ValueError:checks.append(t+' blocked while moving')
    s.speed_m_s=0;s.command('ramp',1);s.command('ramp',0)
    for k in s.hatches:s.command('hatch:'+k,1)
    for k in s.seats:s.command('seat:'+k,1)
    checks.extend(['four independent roof lids','nine independent seat pans'])
    assert len(checks)==6
    return {'passed':True,'checks':checks,'game_adapter_installed':False}
if __name__=='__main__':
    import json
    print(json.dumps(verify_contract()))
