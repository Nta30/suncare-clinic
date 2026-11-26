"""Extract the official 29-column ICD-10 VN annex; never infer codes from prose."""
import hashlib
import json
import logging
import pathlib
import re
import urllib.request
from pypdf import PdfReader

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = 'https://datafiles.chinhphu.vn/cpp/files/vbpq/2026/4/06-byt-kem.pdf'
pdf = ROOT / 'target/icd-source/06-byt-kem.pdf'
pdf.parent.mkdir(parents=True, exist_ok=True)
if not pdf.exists():
    urllib.request.urlretrieve(SOURCE, pdf)
logging.getLogger('pypdf').setLevel(logging.ERROR)
reader = PdfReader(pdf)
rows = []
current = None

def visit(text, cm, tm, font, size):
    global current
    raw = text
    text = text.strip()
    x = tm[4] + cm[4] / cm[0] if cm[0] else tm[4]
    y = tm[5] + cm[5] / cm[3] if cm[3] else tm[5]
    if not text:
        return
    if 34 <= x <= 36 and text.isdigit():
        ordinal = int(text)
        if current is None or ordinal != current['ordinal']:
            current = {'ordinal': ordinal, 'page': page_number + 1, 'code': [], 'name': [],
                       'english': [], 'notPrimary': [], 'notSpecific': [], 'deathOnly': []}
            rows.append(current)
        return
    if current is None or text.startswith('Page '):
        return
    for low, high, field in [(859, 891, 'code'), (1070, 1160, 'name'), (925, 995, 'english'),
                             (1230, 1277, 'notPrimary'), (1320, 1370, 'notSpecific'), (1370, 1415, 'deathOnly')]:
        if low <= x < high:
            line_key = (field, round(y, 1), page_number)
            if current.get('_line') == line_key and x > current.get('_x', x):
                current[field][-1] += raw.rstrip('\n\r')
            else:
                current[field].append(raw.rstrip('\n\r'))
            current['_line'] = line_key
            current['_x'] = x
            break

for page_number, page in enumerate(reader.pages):
    page.extract_text(visitor_text=visit)
    if page_number % 100 == 0:
        print(f'page {page_number + 1}/{len(reader.pages)}; rows={len(rows)}', flush=True)

(ROOT / 'target/icd-source/rows.json').write_text(json.dumps(rows, ensure_ascii=False), encoding='utf-8')
items = []
seen = set()
for row in rows:
    raw_code = ''.join(row['code']).strip()
    code = raw_code.replace('†', '').replace('*', '').strip()
    name = re.sub(r'\s+', ' ', ' '.join(row['name'])).strip()
    # The official annex itself prints U13/9 (row 15750, page 1258), while its
    # name says U13.9. Preserve that source discrepancy and quarantine the row.
    source_issue = 'Official code column prints U13/9; name says U13.9. Pending authoritative correction.' if code == 'U13/9' else None
    if (not re.fullmatch(r'[A-Z][0-9]{2}(?:\.[A-Z0-9]{1,4})?', code) and not source_issue) or not name or code in seen:
        raise ValueError(f'Invalid or duplicate row: {row}')
    seen.add(code)
    items.append({'code': code, 'name': name, 'englishName': re.sub(r'\s+', ' ', ' '.join(row['english'])).strip(),
                  'selectable': not bool(row['notSpecific'] or row['deathOnly'] or source_issue),
                  'primaryAllowed': not bool(row['notPrimary'] or source_issue), 'sourcePage': row['page'],
                  **({'sourceIssue': source_issue} if source_issue else {})})
if [row['ordinal'] for row in rows] != list(range(1, len(rows) + 1)):
    raise ValueError('Non-contiguous source ordinals; extraction must be reviewed.')
out = ROOT / 'scripts/data/icd10-vn-2026.json'
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps({'system': 'ICD-10-VN', 'version': '2026-07-01', 'source': SOURCE,
                          'sourceSha256': hashlib.sha256(pdf.read_bytes()).hexdigest(),
                          'sourcePages': len(reader.pages), 'count': len(items), 'items': items},
                         ensure_ascii=False, indent=2), encoding='utf-8')
print(f'Wrote {len(items)} codes, {sum(x["selectable"] for x in items)} selectable; {out}', flush=True)
