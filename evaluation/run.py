#!/usr/bin/env python3
"""Explicit opt-in synthetic extraction benchmark. Never load real patient data."""
import argparse, json, os, pathlib, re, statistics, time, urllib.request
ROOT=pathlib.Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser()
parser.add_argument('--check',action='store_true',help='Validate fixtures without any API call')
parser.add_argument('--live',action='store_true',help='Send fictional cases to OpenAI; API charges may apply')
parser.add_argument('--output',default='evaluation/results.local.json')
args=parser.parse_args()
cases=json.loads((ROOT/'evaluation/synthetic_cases.json').read_text())
fields=json.loads((ROOT/'docs/clinical-specification.json').read_text())
allowed={f['id'] for f in fields}
assert len({c['id'] for c in cases})==len(cases)
for case in cases: assert set(case['expected'])<=allowed,case['id']
if args.check or not args.live:
 print(f'PASS: {len(cases)} synthetic fixtures; expected field IDs valid; no network requests')
 raise SystemExit()
key=os.environ.get('OPENAI_API_KEY','')
model=os.environ.get('NAVA_EVAL_MODEL','')
if not key or not model: raise SystemExit('Set OPENAI_API_KEY and NAVA_EVAL_MODEL securely. Nothing was sent.')
source=(ROOT/'app/src/main/java/com/aminfathi/nava/ai/Prompts.kt').read_text()
schema=json.loads(re.search(r'extractionSchema:JsonObject = json.parseToJsonElement\("""(.*?)"""',source,re.S).group(1))
prompt=re.search(r'const val EXTRACTOR = """(.*?)"""',source,re.S).group(1)
rows=[]
for c in cases:
 data={'language':c['language'],'pathway':c['pathway'],'stage':'preparing','latest_message':c['latest_message'],'asked_field':'','facts_summary':'','recent_messages':[],'field_specification':fields}
 payload={'model':model,'store':False,'instructions':prompt,'input':json.dumps(data,ensure_ascii=False),'max_output_tokens':4096,'text':{'format':{'type':'json_schema','name':'clinical_extraction','strict':True,'schema':schema}}}
 request=urllib.request.Request('https://api.openai.com/v1/responses',data=json.dumps(payload).encode(),headers={'Authorization':'Bearer '+key,'Content-Type':'application/json'})
 start=time.monotonic()
 try:
  with urllib.request.urlopen(request,timeout=150) as response: raw=json.load(response)
  assert raw.get('status')=='completed'
  text=''.join(part['text'] for output in raw.get('output',[]) for part in output.get('content',[]) if part.get('type')=='output_text')
  result=json.loads(text);facts={f['fieldId']:f for f in result['facts']}
  matches=sum(1 for field,value in c['expected'].items() if field in facts and (value is None or facts[field]['value']==value))
  status_ok=all(facts.get(field,{}).get('status')==status for field,status in c.get('expected_status',{}).items())
  provenance_ok=all(f['evidence'] and f['evidence'] in c['latest_message'] and f['fieldId'] in allowed for f in result['facts'])
  rows.append({'id':c['id'],'seconds':round(time.monotonic()-start,3),'matched_fields':matches,'expected_fields':len(c['expected']),'status_ok':status_ok,'provenance_ok':provenance_ok,'urgent_ok':result['urgent']==c['urgent'],'advice_ok':result['adviceRequested']==c['advice_requested'],'usage':raw.get('usage',{}),'synthetic_result':result})
 except Exception as exc:
  # Do not print raw error bodies, request headers or credentials.
  rows.append({'id':c['id'],'error_type':type(exc).__name__,'seconds':round(time.monotonic()-start,3)})
 print(c['id'], 'completed' if 'error_type' not in rows[-1] else 'failed')
output=ROOT/args.output
output.parent.mkdir(parents=True,exist_ok=True)
output.write_text(json.dumps({'model':model,'scope':'synthetic extraction only; not clinical validation','cases':rows},ensure_ascii=False,indent=2))
print('Saved',output)
