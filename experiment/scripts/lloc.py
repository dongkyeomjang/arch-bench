#!/usr/bin/env python3
"""Java 논리 코드 줄 수(LLOC). 사용: lloc.py <디렉터리> [...]  → 디렉터리별 줄 수와 합계를 출력.

세는 줄: 디렉터리 아래 모든 *.java 의 줄 중 아래를 뺀 것.
  - 빈 줄(공백만)
  - 주석 줄: 앞 공백을 뗀 뒤 `//`, `/*`, `*`, `*/` 로 시작하는 줄 (코드 뒤에 붙은 주석은 코드 줄로 센다)
  - `}` 단독 줄 (앞뒤 공백 무시)
문법을 해석하지 않는 줄 단위 근사다. 같은 규칙으로 참조 구현과 에이전트 워크스페이스를 재서 비율(LLOC_ratio)로만 쓴다.
"""
import sys
from pathlib import Path

COMMENT_PREFIXES = ("//", "/*", "*", "*/")


def lloc(path: Path) -> int:
    n = 0
    for f in sorted(path.rglob("*.java")):
        for line in f.read_text(encoding="utf-8", errors="replace").splitlines():
            s = line.strip()
            if not s or s == "}" or s.startswith(COMMENT_PREFIXES):
                continue
            n += 1
    return n


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__, file=sys.stderr)
        sys.exit(2)
    total = 0
    for arg in sys.argv[1:]:
        n = lloc(Path(arg))
        total += n
        print(f"{n}\t{arg}")
    if len(sys.argv) > 2:
        print(f"{total}\ttotal")
