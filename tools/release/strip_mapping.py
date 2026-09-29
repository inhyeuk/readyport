"""AAB에서 R8 매핑 파일(BUNDLE-METADATA/.../proguard.map)을 빼고 업로드 키로 다시 서명한다.

브라우저로 Play 콘솔에 올릴 때 파일 크기 한도(10MB) 때문에 쓴다. 매핑이 없으면 Play의 비정상 종료 보고가
난독화된 이름으로 보이지만, 레디포트는 Crashlytics를 쓰지 않아 영향이 작다.

사용: python tools/release/strip_mapping.py <입력.aab> <출력.aab>
비밀번호는 ~/.readyport/keys/readyport_upload.properties(또는 READYPORT_UPLOAD_PROPS)에서 읽고 화면에 찍지 않는다.
"""
import os
import subprocess
import sys
import zipfile
from pathlib import Path

JARSIGNER = Path(os.environ.get("JAVA_HOME", r"E:\_PROGRAM_Installed\Android Studio\jbr")) / "bin" / "jarsigner.exe"


def props() -> dict:
    path = Path(os.environ.get("READYPORT_UPLOAD_PROPS", Path.home() / ".readyport/keys/readyport_upload.properties"))
    out = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            k, v = line.split("=", 1)
            out[k.strip()] = v.strip()
    base = path.parent
    store = Path(out["storeFile"])
    out["storeFile"] = str(store if store.is_absolute() else base / store)
    return out


def main(src: str, dst: str) -> None:
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w") as zout:
        for info in zin.infolist():
            name = info.filename
            if name.endswith("proguard.map") or name.startswith("META-INF/"):
                continue  # 매핑과 기존 서명 제거
            zout.writestr(info, zin.read(name))
    p = props()
    env = dict(os.environ, RP_STORE_PASS=p["storePassword"], RP_KEY_PASS=p["keyPassword"])
    subprocess.run(
        [str(JARSIGNER), "-keystore", p["storeFile"], "-storepass:env", "RP_STORE_PASS", "-keypass:env", "RP_KEY_PASS",
         "-sigalg", "SHA256withRSA", "-digestalg", "SHA-256", dst, p["keyAlias"]],
        check=True, env=env, stdout=subprocess.DEVNULL,
    )
    subprocess.run([str(JARSIGNER), "-verify", dst], check=True, stdout=subprocess.DEVNULL)
    print(f"{dst}: {os.path.getsize(dst):,} bytes (서명 확인됨)")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
