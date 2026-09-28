# 레디포트 앱 아이콘 에셋

## 구성
- `play-store/readyport_play_512.png` : Play Console 업로드용 (512×512, 32비트 PNG, 정사각형 그대로 업로드)
- `res/` : 안드로이드 프로젝트의 `app/src/main/res/`에 그대로 덮어쓰기
  - `mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` : 적응형 아이콘 정의 (배경·전경·단색 레이어)
  - `mipmap-*/ic_launcher_background.png`, `ic_launcher_foreground.png`, `ic_launcher_monochrome.png` : 밀도별 레이어 (108dp)
  - `mipmap-*/ic_launcher.png`, `ic_launcher_round.png` : 구형 기기(Android 8.0 미만)용
- `source-svg/` : 원본 벡터. 색·비율을 바꿀 때 이 파일을 고친 뒤 다시 내보내면 됩니다.

## 확인할 것
- AndroidManifest.xml의 `android:icon="@mipmap/ic_launcher"`, `android:roundIcon="@mipmap/ic_launcher_round"`
- 단색(테마) 아이콘은 Android 13 이상에서 사용자가 테마 아이콘을 켰을 때 표시됩니다.
- 실제 기기에서 원형·둥근 사각 런처 모두 확인해 주세요.
