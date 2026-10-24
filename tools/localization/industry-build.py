"""Build a reviewable RU overlay against exact candidate JARs, preserving book mechanics."""
from pathlib import Path
import argparse
import collections, copy, hashlib, json, re, shutil, zipfile
from html import escape as html_escape

ROOT=Path(__file__).resolve().parents[2]
WORK=ROOT/'.verification/mod-candidates-20261024'
SOURCE=ROOT/'distribution/localization/industry-ru'
PACK=SOURCE/'pack'
TEXT_FIELDS={'name','text','title','description'}

def load(p):return json.loads(p.read_text('utf-8-sig'))
def save(p,d):
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n','utf-8')

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--allow-incomplete',action='store_true',help='Emit a local draft; never marks it ready to publish')
    args=parser.parse_args()
    audit=load(WORK/'audit.json');missing=load(WORK/'missing.json')
    reviewed={}
    for p in sorted(SOURCE.glob('*reviewed.json')):
        if p.name!='ei-guide-reviewed.json':reviewed.update(load(p))
    reviewed.update(load(SOURCE/'reviewed-strings.json'))
    upstream={}
    for filename in ('rtf-source.zip','rtf-extension-source.zip'):
        with zipfile.ZipFile(WORK/filename) as archive:
            for name in archive.namelist():
                if name.endswith('/lang/ru_ru.json') and name.split('/')[1] in missing:
                    upstream.setdefault(name.split('/')[1],{}).update(json.loads(archive.read(name).decode('utf-8-sig')))
    colors={'Black':'Чёрный','Blue':'Синий','Brown':'Коричневый','Cyan':'Бирюзовый',
            'Gray':'Серый','Green':'Зелёный','Light Blue':'Голубой','Light Gray':'Светло-серый',
            'Lime':'Лаймовый','Magenta':'Пурпурный','Orange':'Оранжевый','Pink':'Розовый',
            'Purple':'Фиолетовый','Red':'Красный','White':'Белый','Yellow':'Жёлтый',
            'Amber':'Янтарный','Aqua':'Аквамариновый','Beige':'Бежевый','Coral':'Коралловый',
            'Forest':'Лесной','Ginger':'Рыжий','Indigo':'Индиговый','Maroon':'Бордовый',
            'Mint':'Мятный','Navy':'Тёмно-синий','Olive':'Оливковый','Rose':'Розовый',
            'Slate':'Аспидный','Tan':'Песочный','Teal':'Сине-зелёный','Verdant':'Изумрудный'}
    colors.update({'Aquamarine':'Аквамариновый','Bubblegum':'Жвачно-розовый','Cherenkov':'Черенковский',
                   'Conifer':'Хвойный','Fluorescent':'Флуоресцентный','Honey':'Медовый',
                   'Icy Blue':'Ледяной голубой','Lavender':'Лавандовый','Peach':'Персиковый',
                   'Persimmon':'Хурмовый','Spring Green':'Весенний зелёный','Ultramarine':'Ультрамариновый',
                   'Wine':'Винный','Bleachdew':'Обесцвечивающий','Bleached':'Обесцвеченный'})
    processes={'chocolate coating':'покрытия шоколадом','exploding':'взрывания','glooming':'мрачной обработки',
               'honey coating':'покрытия мёдом','purifying':'очищения','resonance':'резонансной обработки',
               'sculking':'скалковой обработки','soul stripping':'извлечения душ','transmutation':'трансмутации'}
    names={'Chocolate Coating':'шоколадного покрытия','Glooming':'мрачной обработки','Honey Coating':'медового покрытия',
           'Purifying':'очищения','Resonance':'резонанса','Sculking':'скалковой обработки',
           'Soul Stripping':'извлечения душ','Transmutation':'трансмутации'}
    upgrades={'Volume':'объёма','Dispenser':'раздатчика','Inventory':'инвентаря','Item Life':'срока жизни предметов',
              'Entity Tracker':'сканера существ','Block Tracker':'сканера блоков','Search':'поиска',
              'Coordinate Tracker':'координатного трекера','Range':'дальности','Security':'безопасности',
              'Magnet':'магнита','Thaumcraft':'Thaumcraft','Charging':'зарядки','Armor':'брони',
              'Jet Boots':'реактивных ботинок','Jumping':'прыжка','Night Vision':'ночного зрения',
              'Scuba':'акваланга','Flippers':'ласт','Standby':'ожидания','Minigun':'минигана',
              'Air Conditioning':'кондиционирования','Radiation Shielding':'радиационной защиты',
              'Gilded':'позолоты','Ender Visor':'эндер-визора','Stomp':'топота','Chunkloader':'загрузки чанков'}
    literal={'%s','%d%%',', ','°C','PackagedDraconic','PackagedAuto','Create: Connected',
             'Create: Dragons Plus','Create: Enchantment Industry','Create: Apotheotic Industry',
             'Extended Industrialization','Alt','Patchouli','PneumaticCraft','Super Factory Manager','Discord',
             '#title','#text','#tier','#energy'}
    unresolved=[];origins=collections.Counter()
    native=next(l for l in audit['languages'] if l['ns']=='pneumaticcraft')
    native_pairs=collections.defaultdict(set)
    for k,en in native['en'].items():
        ru=native['ru'].get(k)
        if ru and re.search('[\u0400-\u04ff]',ru):native_pairs[en].add(ru)
    book_names={en:next(iter(values)) for en,values in native_pairs.items() if len(values)==1}
    def translate(text,ns,key):
        if text in reviewed:origins['reviewed']+=1;return reviewed[text]
        if key in upstream.get(ns,{}):origins['RTF']+=1;return upstream[ns][key]
        if text in literal or not re.search(r'[A-Za-z]',text):origins['technical_literal']+=1;return text
        if ns=='pneumaticcraft' and (re.fullmatch(r'[A-Za-z_]\w*\(\)',text) or text in {
            'Apotheosis','Thaumcraft','CraftTweaker','ComputerCraft','Open Computers','Curios',
            '#header','#pressure','#name','#desc'}):
            origins['technical_literal']+=1;return text
        crafting=re.fullmatch(r'Crafting (?:a|an) (.+?)(\.)?',text)
        if ns=='pneumaticcraft' and crafting and crafting[1] in book_names:
            origins['reviewed_pattern']+=1
            return 'Рецепт: '+book_names[crafting[1]]+('.' if crafting[2] else '')
        continued=re.fullmatch(r'(.+) \(cont\.?\)',text)
        if ns=='pneumaticcraft' and continued and continued[1] in book_names:
            origins['reviewed_pattern']+=1;return book_names[continued[1]]+': продолжение'
        dye=re.fullmatch(r'(?:(Dye Depot|Dyenamics|Arts And Crafts) )?(.+) Dye( Bucket| Buckets)?',text)
        if dye and dye[2] in colors:
            color=colors[dye[2]].lower();brand=(' '+dye[1]) if dye[1] else ''
            origins['reviewed_pattern']+=1
            if dye[3]:return ('Вёдра' if dye[3]==' Buckets' else 'Ведро')+' красителя'+brand+' ('+color+')'
            return colors[dye[2]]+' краситель'+brand
        for color,russian in colors.items():
            if text==color+' Fan Dyeing Catalyst':origins['reviewed_pattern']+=1;return russian+' катализатор окрашивания вентилятором'
        for process,russian in processes.items():
            if text==f'Dedicated _bulk {process}_ device for compatible mods. Content of the catalyst does not interact with the environment.':
                origins['reviewed_pattern']+=1
                return f'Устройство для _массового {russian}_ в совместимых модах. Содержимое катализатора не взаимодействует с окружением.'
        for name,russian in names.items():
            if text=='Fan '+name+' Catalyst':origins['reviewed_pattern']+=1;return 'Катализатор '+russian+' вентилятором'
        m=re.fullmatch(r'Crafting (?:a|an) (.+) Upgrade(?:: Tier ([IVX]+))?',text)
        if m and m[1] in upgrades:
            origins['reviewed_pattern']+=1
            return 'Создание улучшения '+upgrades[m[1]]+(' — уровень '+m[2] if m[2] else '')
        unresolved.append({'namespace':ns,'key':key,'en':text});return text
    language_patches={}
    for ns,entries in missing.items():
        language_patches[ns]={k:translate(v,ns,k) for k,v in entries.items()}
    # Minecraft merges language keys globally after visiting each namespace.
    # Common tags/Xaero keys must agree in every contributor, regardless of order.
    shared=load(SOURCE/'global-language-keys.json')
    for row in audit['languages']:
        overrides={k:v for k,v in shared.items() if k in row['en'] or k in row['ru']}
        if overrides:language_patches.setdefault(row['ns'],{}).update(overrides)
    for ns,patch in language_patches.items():
        base=ROOT/f'distribution/resourcepacks/erezcraft-ru/assets/{ns}/lang/ru_ru.json'
        previous=load(base) if base.is_file() else {}
        save(PACK/f'assets/{ns}/lang/ru_ru.json',previous|patch)
    dictionary=load(WORK/'pnc-book-dictionary.json')
    dictionary={en:ru for en,ru in dictionary.items() if re.search('[А-Яа-яЁё]',ru)}
    # Reuse unambiguous Russian names from this exact installed version as well.
    dictionary.update(book_names)
    # English 1.21 strings must exactly match a pinned 1.20 English paragraph before reusing RTF.
    def book(o,ns,location):
        if isinstance(o,list):return [book(v,ns,location+f'/{i}') for i,v in enumerate(o)]
        if not isinstance(o,dict):return o
        result=copy.deepcopy(o)
        for k,v in o.items():
            if k in TEXT_FIELDS and isinstance(v,str):
                if v in reviewed:result[k]=translate(v,ns,location+'/'+k)
                elif ns=='pneumaticcraft' and v in dictionary:
                    result[k]=dictionary[v];origins['exact_english_book_reuse']+=1
                else:result[k]=translate(v,ns,location+'/'+k)
            else:result[k]=book(v,ns,location+'/'+k)
        return result
    books=0
    for row in audit['books']:
        if row['ru'] or not any(s in row['origin'].lower() for s in ('pneumaticcraft','packagedauto','packageddraconic')):continue
        ns=row['target'].split('/')[1];save(PACK/row['target'],book(row['en'],ns,row['source']));books+=1
    guides=load(SOURCE/'ei-guide-reviewed.json')
    lock=load(WORK/'candidate-lock.json')
    jar=WORK/lock['mods']['extended-industrialization']['filename']
    assert hashlib.sha256(jar.read_bytes()).hexdigest()==lock['mods']['extended-industrialization']['sha256']
    guide_prefix='assets/extended_industrialization/guides/extended_industrialization/guide/'
    with zipfile.ZipFile(jar) as archive:
        for relative,paragraphs in guides['paragraphs'].items():
            original=archive.read(guide_prefix+relative).decode('utf-8').replace('\r\n','\n')
            front,body=original.split('---\n',2)[1:]
            for en,ru in guides['titles'].items():front=front.replace('title: "'+en+'"','title: "'+ru+'"')
            parts=[];position=0
            for block in body.split('\n\n'):
                trimmed=block.strip()
                if not trimmed or trimmed.startswith('<'):parts.append(block);continue
                if trimmed.startswith('#'):
                    level,en=trimmed.split(' ',1);ru=guides['titles'][en]
                    anchor=re.sub(r'\s+','-',en.strip().lower())
                    parts.append('<a name="'+html_escape(anchor)+'"></a>\n'+level+' '+ru);continue
                parts.append(paragraphs[position]);position+=1
            assert position==len(paragraphs),(relative,position,len(paragraphs))
            target=PACK/(guide_prefix+'_ru_ru/'+relative);target.parent.mkdir(parents=True,exist_ok=True)
            target.write_text('---\n'+front+'---\n'+'\n\n'.join(parts),'utf-8')
    unique={r['en']:r for r in unresolved}
    save(WORK/'unresolved-translations.json',list(unique.values()))
    save(WORK/'translation-build.json',{'generated_language_keys':sum(map(len,missing.values())),
         'generated_book_files':books,'origins':dict(origins),'unresolved_unique':len(unique),
         'ready_for_publication':not unique})
    print('TRANSLATION_OVERLAY',dict(origins),'unresolved',len(unique),'books',books)
    if unique and not args.allow_incomplete:
        raise SystemExit('Publication blocked: unresolved book translations; --allow-incomplete is for isolated checks only')

if __name__=='__main__':main()
