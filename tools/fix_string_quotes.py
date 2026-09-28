"""strings.xml 의 <string> 값 안에서 이스케이프 안 된 작은따옴표(')를 \\' 로 바꾼다.

Bash heredoc 으로 문구를 넣으면 백슬래시가 사라지는 일이 잦아서 만든 도구.
사용: python tools/fix_string_quotes.py app/src/main/res/values/strings.xml
"""
import re
import sys

path = sys.argv[1]
text = open(path, encoding="utf-8").read()
pattern = re.compile(r"(<string\b[^>]*>)(.*?)(</string>)", re.S)


def fix(m):
    body = re.sub(r"(?<!\\)'", r"\\'", m.group(2))
    return m.group(1) + body + m.group(3)


fixed, n = pattern.subn(fix, text)
changed = sum(1 for a, b in zip(text.splitlines(), fixed.splitlines()) if a != b)
open(path, "w", encoding="utf-8", newline="").write(fixed)
print(f"checked {n} strings, fixed {changed} lines")
