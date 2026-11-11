"""ABS SAL 2021 - POA 2021 (MMM 2023) 엑셀을 앱용 CSV로 바꾼다.

입력: docs/sources/mmm23_sal21_poa21.xlsx
  data.gov.au "Suburbs and Localities (SAL) 2021 - Postal Areas (POA) 2021 - Modified Monash Model 2023"
  라이선스 CC BY 2.5 AU, 2026-11-11 받음.
출력: src/main/resources/seed/postcode_localities.csv
  postcode,state,main,names   (names는 | 로 구분, 대표 지역명이 들어간 동네 먼저, 그다음 면적 작은 순)

대표 지역명(main): 같은 포스트코드 안 여러 동네 이름에 2번 이상 나오는 단어 (예: 4350의 Toowoomba).
없으면 면적이 가장 작은 동네 이름 (보통 시내 중심).
실행: python3 docs/sources/build_localities.py
"""
import collections
import csv
import re
import sys

import openpyxl

SRC = "docs/sources/mmm23_sal21_poa21.xlsx"
OUT = "src/main/resources/seed/postcode_localities.csv"
STATES = {"New South Wales": "NSW", "Victoria": "VIC", "Queensland": "QLD", "South Australia": "SA",
          "Western Australia": "WA", "Tasmania": "TAS", "Northern Territory": "NT",
          "Australian Capital Territory": "ACT", "Other Territories": "OT"}
GENERIC = {"north", "south", "east", "west", "upper", "lower", "central", "city", "heights", "park", "hill",
           "hills", "mountain", "mount", "point", "beach", "creek", "valley", "island", "the", "lake", "lakes",
           "river", "bay", "plains", "vale", "gardens", "estate", "junction", "station", "range", "downs",
           "flat", "springs", "village", "head", "heads", "port", "new", "old", "st", "saint", "of", "and"}


def clean(name):
    # "Freshwater (Qld)", "Boat Harbour (Lismore - NSW)" 처럼 붙은 괄호 설명을 뗀다
    return re.sub(r"\s*\([^)]*\)\s*$", "", name).strip()


def main():
    wb = openpyxl.load_workbook(SRC, read_only=True)
    ws = wb.worksheets[0]
    by_pc = collections.defaultdict(dict)
    for row in ws.iter_rows(min_row=9, values_only=True):
        code, name, area, poa, state = row[1], row[2], row[3], row[4], row[7]
        if not code or not poa or not name:
            continue
        n = clean(str(name))
        pc = str(poa).zfill(4)
        prev = by_pc[pc].get(n)
        a = float(area or 0)
        if prev is None or a < prev[0]:
            by_pc[pc][n] = (a, STATES.get(state, state))
    rows = []
    for pc in sorted(by_pc):
        items = sorted(by_pc[pc].items(), key=lambda x: (x[1][0], x[0]))
        names = [n for n, _ in items]
        state = collections.Counter(s for _, (_, s) in items).most_common(1)[0][0]
        words = collections.Counter()
        for n in names:
            for w in set(re.findall(r"[A-Za-z']+", n)):
                if w.lower() not in GENERIC and len(w) > 2:
                    words[w] += 1
        main_word = None
        if words:
            w, c = max(words.items(), key=lambda x: (x[1], -names.index(next(n for n in names if x[0] in n))))
            if c >= 2:
                main_word = w
        if main_word:
            first = [n for n in names if main_word in n]
            names = first + [n for n in names if n not in first]
            main = main_word
        else:
            main = names[0]
        rows.append((pc, state, main, "|".join(names)))
    with open(OUT, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["postcode", "state", "main", "names"])
        w.writerows(rows)
    print(f"{len(rows)} postcodes -> {OUT}", file=sys.stderr)


if __name__ == "__main__":
    main()
