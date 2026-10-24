"""Machine-assisted drafts of PUBLIC upstream guide text only; never accepted automatically.

No account, local settings, credentials or private source is sent. Output stays in
.verification until semantic review. Source macros, commands and numeric literals
are protected and checked after translation.
"""
from pathlib import Path
import concurrent.futures, html, json, re, time, urllib.parse, urllib.request

ROOT=Path(__file__).resolve().parents[2]
WORK=ROOT/'.verification/mod-candidates-20261024'
TOKEN=re.compile(r'\$\([^)]*\)|/\$|@[A-Za-z_]+(?:\([^)]*\))?|\$[a-z_]+|[a-z_]+:[a-z0-9_/#.:-]+|%(?:\d+\$)?[sd]|(?<!\w)-?\d+(?:[.,]\d+)*(?:%)?')

def protect(text):
    restore={};stack=[];pieces=[];last=0;sequence=[]
    def opaque(value,label=None):
        tag='c'+str(len(restore));restore[tag]=value
        return '<'+tag+'>'+html.escape(label or value)+'</'+tag+'>'
    for match in TOKEN.finditer(text):
        pieces.append(html.escape(text[last:match.start()]));value=match[0];sequence.append(value)
        if value.startswith('$(') and not value.startswith(('$(p)','$(br)','$(li)','$(k:','$(pncr)','$(pc)')):
            tag='x'+str(len(restore));restore[tag]=value;stack.append(tag);pieces.append('<'+tag+'>')
        elif value=='/$' and stack:
            closing=list(reversed(stack));stack.clear()
            for i,tag in enumerate(closing):restore['/'+tag]=value if i==len(closing)-1 else '';pieces.append('</'+tag+'>')
        else:pieces.append(opaque(value,'PneumaticCraft' if value=='$(pncr)' else (' ¶ ' if value in ('$(p)','$(br)','$(li)') else value)))
        last=match.end()
    pieces.append(html.escape(text[last:]))
    for tag in reversed(stack):restore['/'+tag]='';pieces.append('</'+tag+'>')
    def decode(translated):
        for tag,value in restore.items():
            if not tag.startswith('c'):continue
            pattern=r'<'+tag+r'\s*>.*?</'+tag+r'\s*>'
            translated,n=re.subn(pattern,lambda m:value,translated,flags=re.I|re.S)
            if n!=1:raise ValueError('Lost opaque token '+tag)
        for tag,value in restore.items():
            if tag.startswith('c'):continue
            translated,n=re.subn(r'<'+re.escape(tag)+r'\s*>',lambda m:value,translated,flags=re.I)
            if n!=1:raise ValueError('Lost style token '+tag)
        translated=html.unescape(translated)
        # Non-breaking spaces are harmless visually, but stable JSON is easier to audit.
        translated=translated.replace('\xa0',' ')
        if TOKEN.findall(translated)!=sequence:raise ValueError('Changed protected token order')
        if not re.search('[А-Яа-яЁё]',translated):raise ValueError('No Russian text')
        return translated
    return ''.join(pieces),decode

def main():
    pending=json.loads((WORK/'unresolved-translations.json').read_text('utf-8'))
    rows=[r['en'] for r in pending if r['namespace']=='pneumaticcraft']
    output=WORK/'pneumatic-book-drafts.json'
    drafts=json.loads(output.read_text('utf-8')) if output.exists() else {}
    rows=[r for r in rows if r not in drafts]
    batches=[];current=[];size=0
    for row in rows:
        protected,decode=protect(row)
        if size+len(protected)>3500 and current:batches.append(current);current=[];size=0
        current.append((row,protected,decode));size+=len(protected)+40
    if current:batches.append(current)
    errors=[]
    def request(batch):
        query=''.join('<div id="e'+str(i)+'">'+p+'</div>' for i,(_,p,_) in enumerate(batch))
        url='https://translate.googleapis.com/translate_a/single?'+urllib.parse.urlencode({'client':'gtx','sl':'en','tl':'ru','dt':'t','q':query})
        for attempt in range(4):
            try:
                with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0'}),timeout=30) as response:data=json.load(response)
                translated=''.join(s[0] for s in data[0] if s[0]);result={};bad=[]
                for i,(source,_,decode) in enumerate(batch):
                    found=re.search(r'<div\s+id=["\']e'+str(i)+r'["\']\s*>(.*?)</div>',translated,re.I|re.S)
                    try:
                        if found is None:raise ValueError('Lost paragraph boundary')
                        result[source]=decode(found[1])
                    except ValueError as error:bad.append({'en':source,'error':str(error)})
                return result,bad
            except Exception:
                if attempt==3:return {},[{'en':row[0],'error':'Public translation request failed'} for row in batch]
                time.sleep(2*(attempt+1))
    print('PUBLIC_GUIDE_DRAFTS',len(rows),'paragraphs',len(batches),'batches; NOT APPROVED',flush=True)
    with concurrent.futures.ThreadPoolExecutor(2) as pool:
        for i,(result,bad) in enumerate(pool.map(request,batches)):
            drafts.update(result);errors.extend(bad)
            output.write_text(json.dumps(drafts,ensure_ascii=False,indent=2)+'\n','utf-8')
            print('DRAFT_BATCH',i+1,'of',len(batches),'saved',len(drafts),'token-check failures',len(errors),flush=True)
    (WORK/'pneumatic-book-draft-errors.json').write_text(json.dumps(errors,ensure_ascii=False,indent=2)+'\n','utf-8')

if __name__=='__main__':main()
