#!/usr/bin/env python3
"""Merge phased V6 labels without rerunning earlier texture/model generators."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def main():
    labels={'ru_ru':{},'en_us':{}}
    for name in ['v6_gameplay_lang.json','v6_tether_lang.json','v6_fauna_lang.json','v6_navigation_lang.json']:
        if not (ROOT/'tools'/name).exists():continue
        data=json.loads((ROOT/'tools'/name).read_text(encoding='utf-8'))
        if 'ru_ru' in data and 'en_us' in data:
            for lang in labels:labels[lang].update(data[lang])
        else:
            for key,pair in data.items():
                if not isinstance(pair,list) or len(pair)!=2:raise ValueError('Unknown label format: '+name+': '+key)
                for lang,index in [('ru_ru',0),('en_us',1)]:labels[lang][key]=pair[index]
    for lang,extra in labels.items():
        path=ROOT/f'src/main/resources/assets/interstice/lang/{lang}.json';data=json.loads(path.read_text(encoding='utf-8'));data.update(extra)
        path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('Merged V6 labels:',{lang:len(extra) for lang,extra in labels.items()})
if __name__=='__main__':main()
