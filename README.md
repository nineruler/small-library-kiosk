# 자이아람터 작은도서관 키오스크

태블릿 + USB 웹캠으로 이용자가 직접 **대출 / 반납**을 하는 Android 키오스크 앱.

- 타깃 기기: 10인치급 태블릿(Lenovo Tab K10 Pro), **세로 고정**
- `minSdk 30` (Android 11) / `targetSdk 37`

## 화면 흐름

```
홈 ─┬─ 대출하기 → 회원증 스캔 → 책 바코드 연속 스캔 → "다 했어요" → 결과
    └─ 반납하기 ───────────────→ 책 바코드 연속 스캔 → "다 했어요" → 결과
```

- 무입력이 이어지면 자동으로 홈으로 돌아간다(스캔 화면 90초, 결과 화면 20초).
- 회원증이 인식되지 않을 때를 대비해 회원번호 직접 입력 경로가 있다.

## 코라시스 연동 지점

연동 방식이 확정되기 전이라, 도서관 관리 시스템은 인터페이스 하나로만 추상화해 두었다.

- `data/LibrarySystem.kt` — 조회/대출/반납 네 개의 suspend 함수. **여기만 구현하면 된다.**
- `data/MockLibrarySystem.kt` — 지금 쓰는 메모리 기반 가짜 구현(회원 3명, 장서 5권).
- `KioskApplication.librarySystem` — 구현을 갈아끼우는 자리.

화면과 상태 코드는 `LibrarySystem`에만 의존하므로, 실제 연동(REST API / 웹 자동화 / DB)이
어느 쪽으로 가더라도 UI는 손대지 않는다. 실패는 `LibrarySystemException(KioskError)`로 던지면
`ui/Strings.kt`가 이용자용 문구로 옮긴다.

### Mock 테스트 데이터

| 회원번호 | 이름 | 상태 |
|---|---|---|
| 1000000001 | 김하늘 | 정상 (1/5권 대출 중) |
| 1000000002 | 박도윤 | 한도 초과 (5/5권) |
| 1000000003 | 이서아 | 대출 정지 |

| 책 바코드 | 제목 | 상태 |
|---|---|---|
| 9788936434120 | 소년이 온다 | 서가 |
| 9788932917245 | 참을 수 없는 존재의 가벼움 | 서가 |
| 9788954682152 | 완전한 행복 | 서가 |
| 9791196769321 | 아기 돼지 삼형제 | 서가 |
| 9788937473135 | 데미안 | 김하늘 대출 중 (3일 연체) |

## 바코드 입력 경로 세 가지

바코드는 어느 경로로 들어오든 `onBarcode: (String) -> Unit` 하나로 모인다.

1. **HID 바코드 리더기 (권장)** — `scan/HidScannerReader.kt`.
   리더기가 외장 키보드로 잡혀 문자를 빠르게 보내고 Enter 로 끝내는 동작을 모은다.
   `MainActivity.dispatchKeyEvent` 에서 받으므로 어느 화면에서나 스캔된다.
2. **USB 웹캠 (UVC)** — `scan/UvcBarcodeCamera.kt`.
   Lenovo TB128XU 는 커널에 uvcvideo 가 없어 camera2 가 외장 카메라를 노출하지 않는다.
   UVCAndroid(libusb/libuvc)가 usbfs 로 직접 열고, NV21 프레임을 ML Kit 에 넘긴다.
3. **내장 카메라 (CameraX)** — `scan/BarcodeCamera.kt`. 웹캠이 없으면 자동으로 이쪽을 쓴다.

인식 포맷은 EAN-13/8, CODE-128/39, CODABAR, ITF, QR (`scan/BarcodeDecoder.kt`).

### 실기기 측정 결과 (2026-10-04, Lenovo TB128XU + ABKO APC900 웹캠)

- HID 리더기 경로: 정상. 전체 대출 흐름 확인 완료
- UVC 웹캠 경로: 미리보기와 프레임 수신(1920x1080 MJPEG 30fps)은 정상이나,
  **ABKO APC900 은 고정 초점에 포커스/줌 제어를 노출하지 않아** 책 라벨 바코드를
  해상하지 못한다. 막대 경계가 번져 디코딩 불가. 카메라 교체 없이는 해결되지 않는다.

## 키오스크 모드

- 세로 고정은 매니페스트에서, 화면 꺼짐 방지와 시스템 바 숨김은 `MainActivity`에서 처리한다.
- 전용 기기에서 홈 런처로 고정하려면 `AndroidManifest.xml`의 `CATEGORY_HOME` 인텐트 필터를
  주석 해제하고, MDM이나 `startLockTask()`로 화면 고정을 건다.

## 빌드

```bash
./gradlew :app:assembleDebug        # APK
./gradlew :app:testDebugUnitTest    # 상태 머신 테스트
```
