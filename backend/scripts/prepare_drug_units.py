"""Derive separate units from the pinned V21 source; never change that manifest."""
import argparse
import collections
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "scripts/data/outpatient-drugs-openfda.json"
OUTPUT = ROOT / "scripts/data/outpatient-drug-units.json"
CONTAINERS = {
    "BOTTLE": "chai", "TUBE": "tuýp", "JAR": "lọ", "INHALER": "bình hít",
    "CANISTER": "bình", "CAN": "bình", "CARTRIDGE": "ống", "AMPULE": "ống",
    "PACKET": "gói", "POUCH": "gói", "BAG": "túi", "CUP": "cốc",
    "BOX": "hộp", "CARTON": "hộp",
}
OUTER = {"CARTON", "BOX", "CASE", "TRAY", "PACKAGE"}


def derive(row):
    descriptions = [pack["description"] for pack in row["source"]["packaging"]]
    if row["unit"] == "viên":
        dose, dispensing, reason = "viên", "viên", "Chủ dự án chốt cấp phát theo viên."
    else:
        dose = row["unit"] if row["unit"] != "chai" else None
        if dose is None:
            measures = set(re.findall(r"\b([0-9.]+) (mL|g) in ", " / ".join(descriptions)))
            types = {unit for _, unit in measures}
            if len(types) == 1:
                dose = next(iter(types))
        candidates = set()
        for description in descriptions:
            for container in re.findall(r"\bin [0-9.]+ ([A-Z][A-Z ,]*?)(?= \(|$| /)", description):
                candidates.add(container.split(",")[0].strip())
        inner = candidates - OUTER
        chosen = inner if inner else candidates
        translated = {CONTAINERS.get(value) for value in chosen}
        dispensing = next(iter(translated)) if len(translated) == 1 and None not in translated else None
        reason = "Bao gói nguồn có một loại chứa trực tiếp." if dispensing else "Cần rà soát: nhiều loại bao gói hoặc loại chưa đối chiếu."
    return {
        "code": row["code"], "dose_unit": dose, "dispensing_unit": dispensing,
        "legacy_unit": row["unit"], "reason": reason,
        "product_ndc": row["source"]["product_ndc"],
        "source_url": row["source"]["api_url"], "packaging": descriptions,
    }


def literal(value):
    return "NULL" if value is None else "N'" + value.replace("'", "''") + "'"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--emit-sql", type=Path)
    args = parser.parse_args()
    source = json.loads(SOURCE.read_text(encoding="utf-8"))["records"]
    rows = [derive(row) for row in source]
    OUTPUT.write_text(json.dumps({"policy": "viên cấp phát theo viên; bao gói khác chỉ khi nguồn rõ; không quy đổi",
                                  "source": SOURCE.name, "records": rows}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if args.emit_sql:
        lines = [
            "-- Separate dose and dispensing units. Source: scripts/data/outpatient-drug-units.json.",
            "-- Legacy unit and issued snapshots are deliberately preserved.",
            "ALTER TABLE dbo.Drug ADD dose_unit NVARCHAR(50) NULL, dispensing_unit NVARCHAR(50) NULL;",
            "ALTER TABLE dbo.PrescriptionItem ADD dose_unit NVARCHAR(50) NULL, dispensing_unit NVARCHAR(50) NULL;",
            "GO",
            "ALTER TABLE dbo.Drug ADD CONSTRAINT CK_Drug_dose_unit CHECK (dose_unit IS NULL OR LEN(LTRIM(RTRIM(dose_unit)))>0);",
            "ALTER TABLE dbo.Drug ADD CONSTRAINT CK_Drug_dispensing_unit CHECK (dispensing_unit IS NULL OR LEN(LTRIM(RTRIM(dispensing_unit)))>0);",
        ]
        for old, new in zip(source, rows):
            # Do not impose source semantics on a catalogue record edited to another formulation.
            predicates = ["code=" + literal(old["code"])]
            for key in ["name", "ingredient", "strength", "dosage_form", "unit"]:
                predicates.append(key + " COLLATE Latin1_General_100_BIN2=" + literal(old[key]) + " COLLATE Latin1_General_100_BIN2")
            lines.append("UPDATE dbo.Drug SET dose_unit=" + literal(new["dose_unit"])
                         + ",dispensing_unit=" + literal(new["dispensing_unit"])
                         + ",updated_at=SYSUTCDATETIME() WHERE " + " AND ".join(predicates) + ";")
        args.emit_sql.parent.mkdir(parents=True, exist_ok=True)
        sql = "\n".join(lines) + "\n"
        if args.emit_sql.exists() and args.emit_sql.read_text(encoding="utf-8") != sql:
            raise ValueError("Refusing to overwrite a different migration; use a new version/path.")
        if not args.emit_sql.exists():
            args.emit_sql.write_text(sql, encoding="utf-8")
    print(json.dumps({"records": len(rows), "dose_units": dict(collections.Counter(row["dose_unit"] for row in rows)),
                      "dispensing_units": dict(collections.Counter(row["dispensing_unit"] or "REVIEW" for row in rows))}, ensure_ascii=False))


if __name__ == "__main__":
    main()
