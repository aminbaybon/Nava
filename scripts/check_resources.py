#!/usr/bin/env python3
"""Structural checks only. Does not certify translation or clinical quality."""
import pathlib, xml.etree.ElementTree as ET, re
root=pathlib.Path(__file__).resolve().parents[1]
resources=root/'app/src/main/res'
base=ET.parse(resources/'values/strings.xml').getroot()
keys={e.attrib['name'] for e in base}
count=0
for path in resources.glob('values*/strings.xml'):
 entries=list(ET.parse(path).getroot()); names=[e.attrib['name'] for e in entries]
 assert len(names)==len(set(names)),path
 assert set(names)==keys,(path,keys-set(names))
 assert all(e.text for e in entries),path
 count+=1
assert count==8,count
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
assert 'android:allowBackup="false"' in manifest
assert 'android:usesCleartextTraffic="false"' in manifest
assert '<provider' not in manifest # SAF export, no broad FileProvider
src='\n'.join(p.read_text() for p in (root/'app/src/main/java').rglob('*.kt'))
assert 'FLAG_SECURE' in src
assert 'HttpLoggingInterceptor' not in src
assert 'put("store",false)' in src
assert 'https://api.openai.com/v1/' in src
assert not re.search(r'\bsk-[A-Za-z0-9]{15,}',src)
print(f'PASS: {len(keys)} keys across {count} locales; manifest/network/privacy source checks')
