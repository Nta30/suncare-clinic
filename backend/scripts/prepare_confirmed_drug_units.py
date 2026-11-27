"""Generate V23 from the owner's 2026-11-27 decisions, not FDA packaging facts."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONFIRMED = {
    'SCG-2518BC83FE3C6376': 'chai',
    'SCG-CADBA3EF416D237D': 'ống',
    'SCG-71904691D2018147': 'chai',
    'SCG-A67A02672F3239DD': 'lọ',
    'SCG-46288571F49C9C9E': 'bình xịt',
    'SCG-9BC704484ACD12C9': 'chai',
    'SCG-0C6026C0C1C21D00': 'ống',
    'SCG-4AAC771137FA19AF': 'tuýp',
    'SCG-16796B955116AC4B': 'chai',
    'SCG-734E3A623E57D278': 'chai',
    'SCG-ECEC0E2B356D5AB4': 'tuýp',
}


def literal(value):
    return "N'" + value.replace("'", "''") + "'"


def main():
    source = json.loads((ROOT / 'scripts/data/outpatient-drugs-openfda.json').read_text(encoding='utf-8'))['records']
    units = json.loads((ROOT / 'scripts/data/outpatient-drug-units.json').read_text(encoding='utf-8'))['records']
    assert {row['code'] for row in units if row['dispensing_unit'] is None} == set(CONFIRMED)
    doses = {row['code']: row['dose_unit'] for row in units}
    records = []
    lines = ['-- Owner-confirmed dispensing units, 2026-11-27; not inferred FDA packaging.',
             '-- Preserve prior migrations, legacy units, dose units and prescription snapshots.']
    for row in source:
        if row['code'] not in CONFIRMED:
            continue
        record = {key: row[key] for key in ['code', 'name', 'ingredient', 'strength', 'dosage_form']}
        record.update(dose_unit=doses[row['code']], dispensing_unit=CONFIRMED[row['code']])
        records.append(record)
        predicates = ['code=' + literal(row['code']), 'dispensing_unit IS NULL']
        for key in ['name', 'ingredient', 'strength', 'dosage_form', 'unit']:
            predicates.append(key + ' COLLATE Latin1_General_100_BIN2=' + literal(row[key]) + ' COLLATE Latin1_General_100_BIN2')
        predicates.append('dose_unit COLLATE Latin1_General_100_BIN2=' + literal(record['dose_unit']) + ' COLLATE Latin1_General_100_BIN2')
        lines.append('UPDATE dbo.Drug SET dispensing_unit=' + literal(record['dispensing_unit'])
                     + ',updated_at=SYSUTCDATETIME() WHERE ' + ' AND '.join(predicates) + ';')
    manifest = {'source': 'Quyết định chủ dự án trong hội thoại ngày 2026-11-27',
                'policy': 'Chỉ chốt đơn vị cấp phát; không chốt dung tích/quy cách hoặc tỷ lệ quy đổi.', 'records': records}
    outputs = {
        ROOT / 'scripts/data/outpatient-drug-units-confirmed.json': json.dumps(manifest, ensure_ascii=False, indent=2) + '\n',
        ROOT / 'src/main/resources/db/migration/V23__complete_confirmed_drug_dispensing_units.sql': '\n'.join(lines) + '\n',
    }
    for path, content in outputs.items():
        if path.exists() and path.read_text(encoding='utf-8') != content:
            raise ValueError('Refusing to overwrite different content: ' + str(path))
        if not path.exists():
            path.write_text(content, encoding='utf-8')
    print('Confirmed records: ' + str(len(records)))


if __name__ == '__main__':
    main()
