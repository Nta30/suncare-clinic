"""Build a reviewed, sourced generic Drug seed; never writes to a database.

Network responses are cached under ignored backend/target/drug-catalog-source.
The checked-in selected records preserve every source ingredient/strength pair.
"""
import argparse
import concurrent.futures
import hashlib
import json
from decimal import Decimal, localcontext
from fractions import Fraction
from pathlib import Path
import re
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "scripts/data/outpatient-drugs-openfda.json"
CACHE = ROOT / "target/drug-catalog-source"
AS_OF = "2026-11-27"

# Scope selection, not treatment recommendations or a ranking of local use.
GROUPS = {
    "NOI_TQ": "acetaminophen|ibuprofen|naproxen|amoxicillin|clavulanate potassium|azithromycin|clarithromycin|cephalexin|cefuroxime axetil|cefdinir|cefpodoxime proxetil|cefadroxil|cefixime|doxycycline|clindamycin|metronidazole|sulfamethoxazole|trimethoprim|ciprofloxacin|levofloxacin|nitrofurantoin|acyclovir|valacyclovir|oseltamivir|fluconazole|albendazole|mebendazole|ferrous sulfate|folic acid|cyanocobalamin",
    "TIM_MACH": "amlodipine|lisinopril|enalapril|captopril|perindopril|losartan|valsartan|irbesartan|telmisartan|candesartan cilexetil|olmesartan medoxomil|hydrochlorothiazide|indapamide|chlorthalidone|furosemide|spironolactone|bisoprolol|metoprolol|atenolol|carvedilol|nebivolol|diltiazem|verapamil|nifedipine|atorvastatin|rosuvastatin|simvastatin|pravastatin|fenofibrate|ezetimibe|aspirin|clopidogrel|warfarin|apixaban|rivaroxaban|nitroglycerin|isosorbide mononitrate",
    "NOI_TIET": "metformin hydrochloride|glipizide|glimepiride|glyburide|pioglitazone|acarbose|repaglinide|sitagliptin|linagliptin|saxagliptin|alogliptin|empagliflozin|dapagliflozin|canagliflozin|levothyroxine sodium|methimazole|propylthiouracil",
    "TIEU_HOA": "omeprazole|esomeprazole|pantoprazole|lansoprazole|rabeprazole|dexlansoprazole|famotidine|cimetidine|sucralfate|bismuth subsalicylate|calcium carbonate|magnesium hydroxide|aluminum hydroxide|simethicone|ondansetron|metoclopramide|loperamide|lactulose|polyethylene glycol 3350|bisacodyl|docusate sodium|sennosides|psyllium|mesalamine",
    "DA_LIEU": "hydrocortisone|triamcinolone acetonide|mometasone furoate|betamethasone dipropionate|betamethasone valerate|clobetasol propionate|desonide|fluocinonide|mupirocin|erythromycin|tretinoin|adapalene|benzoyl peroxide|azelaic acid|clotrimazole|miconazole nitrate|ketoconazole|terbinafine hydrochloride|ciclopirox|selenium sulfide|nystatin|permethrin|urea",
    "TAI_MUI_HONG": "cetirizine hydrochloride|loratadine|desloratadine|levocetirizine dihydrochloride|fexofenadine hydrochloride|chlorpheniramine maleate|diphenhydramine hydrochloride|fluticasone propionate|budesonide|azelastine hydrochloride|ipratropium bromide|oxymetazoline hydrochloride|dextromethorphan hydrobromide|guaifenesin|montelukast|albuterol sulfate|levalbuterol hydrochloride|formoterol fumarate|salmeterol xinafoate|tiotropium bromide|ofloxacin|ciprofloxacin hydrochloride|dexamethasone",
    "CTCH": "diclofenac sodium|diclofenac potassium|meloxicam|celecoxib|etodolac|nabumetone|prednisone|prednisolone|methylprednisolone|tizanidine hydrochloride|methocarbamol|cyclobenzaprine hydrochloride|gabapentin|pregabalin|allopurinol|colchicine|alendronate sodium|cholecalciferol",
    "NHI_TQ": "acetaminophen|ibuprofen|amoxicillin|clavulanate potassium|azithromycin|cefdinir|cefixime|cephalexin|cetirizine hydrochloride|loratadine|montelukast|albuterol sulfate|budesonide|prednisolone|oseltamivir|nystatin|lactulose|polyethylene glycol 3350|cholecalciferol",
    "NGOAI_TQ": "acetaminophen|ibuprofen|diclofenac sodium|cephalexin|amoxicillin|clavulanate potassium|metronidazole|mupirocin|clindamycin|polyethylene glycol 3350|lactulose|docusate sodium|bisacodyl",
}

# Exact FDA dosage-form translations; a dose unit, not an invented pack size.
FORMS = {
    "TABLET": ("Viên nén", "viên"),
    "TABLET, FILM COATED": ("Viên nén bao phim", "viên"),
    "TABLET, COATED": ("Viên nén bao", "viên"),
    "TABLET, CHEWABLE": ("Viên nén nhai", "viên"),
    "TABLET, ORALLY DISINTEGRATING": ("Viên nén phân tán trong miệng", "viên"),
    "TABLET, DELAYED RELEASE": ("Viên nén giải phóng muộn", "viên"),
    "TABLET, FILM COATED, DELAYED RELEASE": ("Viên nén bao phim giải phóng muộn", "viên"),
    "TABLET, EXTENDED RELEASE": ("Viên nén giải phóng kéo dài", "viên"),
    "TABLET, FILM COATED, EXTENDED RELEASE": ("Viên nén bao phim giải phóng kéo dài", "viên"),
    "TABLET, EFFERVESCENT": ("Viên nén sủi bọt", "viên"),
    "CAPSULE": ("Viên nang", "viên"),
    "CAPSULE, LIQUID FILLED": ("Viên nang chứa dịch", "viên"),
    "CAPSULE, DELAYED RELEASE": ("Viên nang giải phóng muộn", "viên"),
    "CAPSULE, DELAYED RELEASE PELLETS": ("Viên nang chứa vi hạt giải phóng muộn", "viên"),
    "CAPSULE, EXTENDED RELEASE": ("Viên nang giải phóng kéo dài", "viên"),
    "SOLUTION": ("Dung dịch", "mL"),
    "SUSPENSION": ("Hỗn dịch", "mL"),
    "SYRUP": ("Siro", "mL"),
    "ELIXIR": ("Elixir", "mL"),
    "SUSPENSION/ DROPS": ("Hỗn dịch nhỏ giọt", "mL"),
    "SOLUTION/ DROPS": ("Dung dịch nhỏ giọt", "mL"),
    "FOR SUSPENSION": ("Bột pha hỗn dịch", "chai"),
    "POWDER, FOR SUSPENSION": ("Bột pha hỗn dịch", "chai"),
    "CREAM": ("Kem", "g"),
    "OINTMENT": ("Thuốc mỡ", "g"),
    "GEL": ("Gel", "g"),
    "LOTION": ("Thuốc dùng ngoài dạng lotion", "mL"),
    "SHAMPOO": ("Dầu gội", "mL"),
    "SUPPOSITORY": ("Thuốc đạn", "viên"),
    "SPRAY, METERED": ("Thuốc xịt định liều", "liều xịt"),
    "AEROSOL, METERED": ("Khí dung định liều", "liều xịt"),
    "AEROSOL, POWDER": ("Bột hít", "liều hít"),
}
ROUTES = {"ORAL", "TOPICAL", "NASAL", "RESPIRATORY (INHALATION)", "RECTAL", "OTIC"}
CATEGORIES = {"ANDA", "NDA", "NDA AUTHORIZED GENERIC", "OTC MONOGRAPH DRUG"}


def clean(value):
    return unicodedata.normalize("NFC", re.sub(r"\s+", " ", value).strip())


def title(value):
    return clean(value).lower().capitalize()


def normalized_strength(value):
    value = clean(value)
    match = re.fullmatch(r"([0-9]+(?:\.[0-9]+)?)\s+([^/]+)/([0-9]+(?:\.[0-9]+)?)?([A-Za-z]+)?", value)
    if not match:
        return value
    amount, numerator, denominator, basis = match.groups()
    ratio = Fraction(amount) / Fraction(denominator or "1")
    if numerator in {"g", "mg", "ug", "ng"}:
        ratio *= {"g": 1000, "mg": 1, "ug": Fraction(1, 1000), "ng": Fraction(1, 1000000)}[numerator]
        numerator = "mg"
    remainder = ratio.denominator
    for factor in (2, 5):
        while remainder % factor == 0:
            remainder //= factor
    if remainder != 1:
        return value  # Never round a non-terminating source ratio.
    with localcontext() as context:
        context.prec = 50
        number = format(Decimal(ratio.numerator) / Decimal(ratio.denominator), "f").rstrip("0").rstrip(".") if ratio.denominator != 1 else str(ratio.numerator)
    return number + " " + numerator + ("/" + basis if basis else "")


def fetch(term):
    CACHE.mkdir(parents=True, exist_ok=True)
    path = CACHE / (hashlib.sha256(term.encode()).hexdigest()[:16] + ".json")
    query = (f'active_ingredients.name:"{term}" AND finished:true '
             'AND (marketing_category:"ANDA" OR marketing_category:"NDA" '
             'OR marketing_category:"NDA AUTHORIZED GENERIC" OR marketing_category:"OTC MONOGRAPH DRUG") '
             'AND NOT marketing_end_date:[* TO 20261127]')
    url = "https://api.fda.gov/drug/ndc.json?" + urllib.parse.urlencode({"search": query, "limit": 200})
    if path.exists():
        return term, json.loads(path.read_text(encoding="utf-8")), url
    for attempt in range(4):
        try:
            with urllib.request.urlopen(url, timeout=60) as response:
                data = json.load(response)
            path.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
            return term, data, url
        except urllib.error.HTTPError as error:
            if error.code == 404:
                return term, {"results": [], "meta": {}}, url
            if error.code not in (429, 500, 502, 503, 504) or attempt == 3:
                raise
            time.sleep(2 ** attempt)


def record(product, term, url, source_date):
    form = product.get("dosage_form", "")
    if form not in FORMS or not set(product.get("route", [])).issubset(ROUTES) or not product.get("route"):
        return None
    if len(product["route"]) != 1:
        return None  # Do not import ambiguous mixed oral/inhaled or other route listings.
    if product.get("product_type") not in {"HUMAN PRESCRIPTION DRUG", "HUMAN OTC DRUG"}:
        return None
    if product.get("marketing_category") not in CATEGORIES or product.get("dea_schedule"):
        return None
    if product.get("marketing_start_date", "99999999") > AS_OF.replace("-", ""):
        return None
    if product.get("listing_expiration_date", "00000000") < AS_OF.replace("-", ""):
        return None
    pairs = product.get("active_ingredients", [])
    if not 1 <= len(pairs) <= 3 or any(not p.get("name") or not p.get("strength") for p in pairs):
        return None
    pairs = sorted([{"name": title(p["name"]), "strength": clean(p["strength"])} for p in pairs], key=lambda p: p["name"].casefold())
    if any("c-13" in p["name"].casefold() for p in pairs):
        return None  # Diagnostic breath-test substrates are outside the medication seed.
    ingredient = " + ".join(p["name"] for p in pairs)
    strength = " + ".join(normalized_strength(p["strength"]) for p in pairs)
    dosage_form, unit = FORMS[form]
    if form == "AEROSOL, POWDER" and set(product["route"]) == {"TOPICAL"}:
        dosage_form, unit = "Bột phun dùng ngoài", "g"
    if unit == "chai" and not any("BOTTLE" in pack.get("description", "") for pack in product.get("packaging", [])):
        return None
    # Route is retained in the form to distinguish nasal/oral/topical solutions.
    route_text = {"ORAL": "uống", "TOPICAL": "dùng ngoài", "NASAL": "mũi", "RESPIRATORY (INHALATION)": "hít", "RECTAL": "trực tràng", "OTIC": "tai"}
    route = sorted(product["route"])
    dosage_form += " (" + ", ".join(route_text[r] for r in route) + ")"
    name = ingredient
    if any(len(v) > limit for v, limit in [(name, 200), (ingredient, 300), (strength, 100), (dosage_form, 100), (unit, 50)]):
        return None
    key = [ingredient.casefold(), strength.casefold(), dosage_form.casefold(), unit.casefold()]
    code = "SCG-" + hashlib.sha256("|".join(key).encode()).hexdigest()[:16].upper()
    set_ids = product.get("openfda", {}).get("spl_set_id", [])
    return {
        "code": code, "name": name, "ingredient": ingredient, "strength": strength,
        "dosage_form": dosage_form, "unit": unit, "is_active": True,
        "specialty_scope": sorted(k for k, terms in GROUPS.items() if term in terms.split("|")),
        "source": {
            "product_ndc": product["product_ndc"], "spl_id": product["spl_id"],
            "spl_set_id": set_ids, "generic_name": product["generic_name"],
            "active_ingredients": product["active_ingredients"], "dosage_form": form, "route": route,
            "packaging": product.get("packaging", []),
            "marketing_category": product["marketing_category"], "application_number": product.get("application_number"),
            "marketing_start_date": product.get("marketing_start_date"), "listing_expiration_date": product.get("listing_expiration_date"),
            "api_url": "https://api.fda.gov/drug/ndc.json?" + urllib.parse.urlencode({"search": f'product_ndc:"{product["product_ndc"]}"'}),
            "dailymed_url": f"https://dailymed.nlm.nih.gov/dailymed/drugInfo.cfm?setid={set_ids[0]}" if set_ids else None,
            "query_url": url, "last_updated": source_date,
        },
    }


def build():
    terms = sorted({term for group in GROUPS.values() for term in group.split("|")})
    selected = {}
    options = {}
    counts = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
        for term, data, url in executor.map(fetch, terms):
            candidates = {}
            for product in sorted(data.get("results", []), key=lambda p: p["product_ndc"]):
                item = record(product, term, url, data.get("meta", {}).get("last_updated"))
                if item:
                    candidates.setdefault(item["code"], item)
            # Bound contributions per searched ingredient; avoid filling the catalog with one drug.
            priority = {"TABLET": 0, "CAPSULE": 1, "TABLET, FILM COATED": 2, "SUSPENSION": 3,
                        "SOLUTION": 4, "FOR SUSPENSION": 5, "TABLET, EXTENDED RELEASE": 6}
            by_form = {}
            for item in sorted(candidates.values(), key=lambda item: (
                    len(item["source"]["active_ingredients"]), priority.get(item["source"]["dosage_form"], 10),
                    item["source"]["dosage_form"], item["strength"], item["code"])):
                by_form.setdefault(item["dosage_form"], []).append(item)
            ordered = [group[round_index] for round_index in range(4) for group in by_form.values() if round_index < len(group)]
            choices = ordered[:4]
            options[term] = choices
            counts[term] = len(choices)
            for item in choices:
                if item["code"] in selected:
                    selected[item["code"]]["specialty_scope"] = sorted(set(selected[item["code"]]["specialty_scope"] + item["specialty_scope"]))
                else:
                    selected[item["code"]] = item
            print(f"{term}: {len(candidates)} variants, {len(choices)} selected; total {len(selected)}", flush=True)
    selected = {}
    for variant in range(4):
        for term in terms:
            if variant >= len(options[term]):
                continue
            item = options[term][variant]
            if item["code"] in selected:
                selected[item["code"]]["specialty_scope"] = sorted(set(selected[item["code"]]["specialty_scope"] + item["specialty_scope"]))
            elif len(selected) < 400:
                selected[item["code"]] = item
    rows = sorted(selected.values(), key=lambda item: (item["ingredient"], item["dosage_form"], item["strength"]))
    DATA.write_text(json.dumps({"retrieved_on": AS_OF, "source": "FDA NDC via openFDA", "records": rows, "query_counts": counts}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(rows)} sourced records to {DATA}")


def validate():
    data = json.loads(DATA.read_text(encoding="utf-8"))
    rows = data["records"]
    assert 300 <= len(rows) <= 500, "Expected 300–500 records"
    codes, identities = set(), set()
    for row in rows:
        for field, limit in [("code", 40), ("name", 200), ("ingredient", 300), ("strength", 100), ("dosage_form", 100), ("unit", 50)]:
            assert row[field] and len(row[field]) <= limit, (row["code"], field)
            assert row[field] == clean(row[field]), (row["code"], "Unicode/whitespace", field)
        assert row["is_active"] is True
        pairs = sorted(row["source"]["active_ingredients"], key=lambda p: title(p["name"]).casefold())
        assert row["ingredient"] == " + ".join(title(p["name"]) for p in pairs)
        assert row["strength"] == " + ".join(normalized_strength(p["strength"]) for p in pairs)
        assert row["name"] == row["ingredient"]  # Generic only, not source brand labels.
        assert row["source"]["api_url"].startswith("https://api.fda.gov/drug/ndc.json?")
        assert row["source"]["marketing_category"] in CATEGORIES
        assert len(row["source"]["route"]) == 1
        identity = tuple(row[field].casefold() for field in ("ingredient", "strength", "dosage_form", "unit"))
        assert row["code"] == "SCG-" + hashlib.sha256("|".join(identity).encode()).hexdigest()[:16].upper()
        assert row["code"] not in codes and identity not in identities, row["code"]
        codes.add(row["code"])
        identities.add(identity)
    assert set(GROUPS) == {specialty for row in rows for specialty in row["specialty_scope"]}
    # Exact concentration equivalence, no binary floats and no rounded ratios.
    assert normalized_strength("250 mg/5mL") == normalized_strength("1000 mg/20mL") == "50 mg/mL"
    assert normalized_strength("0.5 g/1") == "500 mg"
    assert normalized_strength("1 mg/3mL") == "1 mg/3mL"
    print(f"VALID: {len(rows)} unique records; {sum(len(r['source']['active_ingredients']) > 1 for r in rows)} combinations; 9 specialty scopes")
    return rows


def sql_identity(alias, column):
    return f"REPLACE(REPLACE(REPLACE(COALESCE({alias}.{column},N''),N' ',N''),NCHAR(9),N''),NCHAR(160),N'') COLLATE Latin1_General_100_CI_AI"


def emit_sql(path):
    rows = validate()
    quoted = lambda value: "N'" + value.replace("'", "''") + "'"
    values = []
    for row in rows:
        values.append("    (" + ", ".join(quoted(row[key]) for key in ("code", "name", "ingredient", "strength", "dosage_form", "unit")) + ", 1)")
    equal = " AND\n        ".join(sql_identity("d", column) + "=" + sql_identity("s", column) for column in ("ingredient", "strength", "dosage_form", "unit"))
    digest = hashlib.sha256(DATA.read_bytes()).hexdigest()
    sql = f"""-- P22.catalog.seed: 400 sourced generic outpatient formulations, no schema changes.
-- Source: FDA NDC/openFDA, retrieved {AS_OF}; see docs/database/DRUG_CATALOG.md.
-- Manifest: backend/scripts/data/outpatient-drugs-openfda.json, SHA256 {digest}
-- Source ingredient/strength pairs are preserved in that manifest.
-- No price, inventory, treatment dose, patient data, UPDATE, DELETE or MERGE.
-- Flyway owns the transaction. Existing rows (including inactive rows) are retained.

DECLARE @SeedDrug TABLE (
    code varchar(40) PRIMARY KEY,
    name nvarchar(200) NOT NULL,
    ingredient nvarchar(300) NOT NULL,
    strength nvarchar(100) NOT NULL,
    dosage_form nvarchar(100) NOT NULL,
    unit nvarchar(50) NOT NULL,
    is_active bit NOT NULL
);

INSERT INTO @SeedDrug (code,name,ingredient,strength,dosage_form,unit,is_active) VALUES
{",\n".join(values)};

-- A different medication under a reserved seed code is a conflict, never overwritten.
IF EXISTS (
    SELECT 1 FROM dbo.Drug d JOIN @SeedDrug s ON s.code=d.code
    WHERE NOT ({equal})
)
    THROW 51021, 'Drug seed code conflicts with existing data; review without overwriting.', 1;

INSERT INTO dbo.Drug (code,name,ingredient,strength,dosage_form,unit,is_active)
SELECT s.code,s.name,s.ingredient,s.strength,s.dosage_form,s.unit,s.is_active
FROM @SeedDrug s
WHERE NOT EXISTS (SELECT 1 FROM dbo.Drug d WITH (UPDLOCK,HOLDLOCK) WHERE d.code=s.code)
  AND NOT EXISTS (
    SELECT 1 FROM dbo.Drug d WITH (UPDLOCK,HOLDLOCK)
    WHERE {equal}
  );
"""
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(sql, encoding="utf-8")
    print(f"Wrote {path}; manifest SHA256 {digest}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--fetch", action="store_true")
    parser.add_argument("--validate", action="store_true")
    parser.add_argument("--emit-sql", type=Path)
    args = parser.parse_args()
    if args.fetch:
        build()
    if args.validate:
        validate()
    if args.emit_sql:
        emit_sql(args.emit_sql)
