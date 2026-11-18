<p align="center">
  <img src="docs/images/icon.png" width="96" height="96" alt="KoriyoSCAN 아이콘">
</p>

# KoriyoSCAN

시험 감독관용 Android 앱입니다. 수험생이 들어오기 전 빈 교실의 와이파이·블루투스 전파를 기록해 두고, 시험 중에 그 기록에 없던 전파가 잡히면 화면으로 알립니다.

> **이 앱은 모든 기기를 탐지하지 못합니다.** 전파를 차단하지 않고, 부정행위 여부를 판정하지 않으며, 육안 감독을 대체하지 않습니다. 실제 시험장에서의 수신률·누락률은 검증하지 않았습니다.

| 항목 | 내용 |
| --- | --- |
| 지원 환경 | Android 10(API 29) 이상. 아이폰은 지원하지 않습니다. |
| 보는 것 | 와이파이 접속점(공유기·핫스팟)과 저전력 블루투스(BLE) 알림 신호 |
| 데이터 | 휴대폰 안에만 저장합니다. 앱에 인터넷 권한이 없습니다. |
| 측정 조건 | 비행기 모드가 켜져 있어야 시작됩니다. 측정·감시 중에는 휴대폰을 자동으로 무음(방해 금지)으로 바꿉니다. |
| 화면 | 감독관 화면, 위치 찾기, 안내 |

<p align="center">
  <img src="docs/images/screen-1-ready.png" width="250" alt="빈 교실 기록 화면">
  <img src="docs/images/screen-2-found.png" width="250" alt="시험 중 감시 화면에 새 전파 1개가 뜬 모습">
  <img src="docs/images/screen-3-find.png" width="250" alt="위치 찾기 화면">
</p>
<p align="center"><sub>자동 테스트가 가상 전파로 그린 실제 앱 화면입니다. 왼쪽부터 빈 교실 기록, 시험 중 감시, 위치 찾기.</sub></p>

## 상태

- **현장 검증 전입니다.** 자동 테스트는 통과하지만 실제 시험장에서 얼마나 잡고 얼마나 놓치는지는 측정하지 않았습니다.
- 시험 주관 기관의 지침이 감독관의 휴대폰 반입이나 사용을 제한할 수 있습니다. 쓰기 전에 해당 시험의 지침을 확인해야 합니다.
- 실기기에서 써 본 결과는 [Issues](https://github.com/reform-dev-team/exam-rf-guard/issues)로 알려 주세요. 적는 방법은 [제보](#제보)에 있습니다.

## 사용 순서

**준비**

앱을 열면 "시작 전 준비" 화면이 아래 순서대로 하나씩 안내합니다. 모두 마쳐야 측정을 시작할 수 있습니다.

1. 근처 기기·위치 권한 허용
2. 방해 금지 권한 허용
3. 비행기 모드 켜기
4. 와이파이 다시 켜기
5. 블루투스 다시 켜기
6. 위치(GPS) 켜기

충전기를 꽂아 두는 것이 좋습니다. 측정·감시 중에는 화면이 꺼지지 않습니다.

**측정과 감시**

1. **빈 교실 기록** — 수험생 입실 전 2분 동안 잡히는 전파를 "원래 있던 것"으로 저장합니다. 감독관 본인의 시계와 이어폰은 이때 켜 두면 함께 기록되어 나중에 알리지 않습니다.
2. **시험 중 감시** — 빈 교실 기록에 없던 전파가 잡히면 목록에 올립니다. 사라지면 목록에서 빠집니다.
3. **위치 찾기** — 한 전파를 골라 걸으면 수신 세기가 막대와 진동 간격으로 바뀝니다.

측정과 감시는 앱 화면이 켜져 있는 동안에만 동작합니다. 화면을 끄거나 다른 앱으로 가면 수신이 멈추고, 돌아오면 이어서 감시하며 비어 있던 횟수와 시간을 표시합니다. 측정·감시 중에는 앱이 휴대폰을 무음(방해 금지)으로 바꾸고 끝나면 되돌립니다. 위치 찾기의 진동은 그대로 울립니다.

**제대로 잡히는지 확인하기**

빈 교실 기록을 저장한 뒤 차례로 해 봅니다.

1. 무선 이어폰 케이스를 엽니다. 30초쯤 뒤 블루투스 칸에 떠야 합니다.
2. 다른 휴대폰의 핫스팟을 켭니다. 1분쯤 안에 와이파이 칸에 떠야 합니다.
3. 15분 넘게 그대로 둡니다. 개수가 늘지 않아야 합니다.

## 동작 방식

### 비행기 모드와 무음을 강제하는 이유

시험실에서 감독관의 휴대폰이 울리거나, 전화 때문에 감시가 끊기는 일을 막기 위해서입니다.

- **비행기 모드가 꺼져 있으면 측정을 시작할 수 없습니다.** 전화나 문자가 오면 감시 화면이 가려지고 수신이 멈추기 때문입니다. 앱이 비행기 모드를 직접 켤 수는 없어, 사용자가 켠 뒤 와이파이와 블루투스만 다시 켜야 합니다.
- **방해 금지 권한이 없어도 측정을 시작할 수 없습니다.** 권한이 있으면 측정·감시를 시작할 때 전화·알림·알람이 울리지 않게 휴대폰을 무음으로 바꾸고, 끝내거나 처음으로 되돌리면 원래 설정으로 돌립니다.
- 앱을 잠시 벗어난 동안에도 무음은 유지합니다. 시험이 끝난 것이 아니기 때문입니다.
- 위치 찾기 진동은 무음 중에도 울립니다.

### 같은 기기를 새 전파로 세지 않는 방법

블루투스 주소는 10~15분마다 바뀌고 공유기 한 대는 주소를 여러 개 씁니다. 주소만으로 비교하면 아무것도 켜지 않아도 "새 전파"가 계속 늘어납니다. 그래서 주소 대신 다음 기준으로 묶습니다.

- 기기가 알린 이름이 있으면 이름으로, 없으면 블루투스 회사 코드와 종류 단서로 묶습니다.
- 빈 교실 때 같은 묶음이 몇 개였는지 세어 두고, 그보다 늘어났을 때만 알립니다.
- 새 주소는 30초 넘게 이어져야 늘어난 것으로 봅니다. 주소가 바뀌는 순간 옛 주소와 겹치는 것을 거르기 위해서입니다.
- 빈 교실에 있던 공유기와 주소 다섯 자리가 같은 와이파이는 같은 공유기로 봅니다.
- 와이파이 이름이 같아도, 빈 교실에서 그 이름이 쓰지 않던 임의 주소(휴대폰 핫스팟이 주로 쓰는 주소)로 잡히면 따로 묶어 새 전파로 알립니다. 핫스팟 이름을 학교 와이파이 이름으로 바꿔 켠 경우를 위한 것입니다. 임의 주소의 둘째·셋째 자리가 빈 교실 와이파이의 고유 주소와 같으면 같은 회사 공유기가 낸 주소로 보고 알리지 않습니다. 제조사 고유 주소를 쓰는 기기가 이름을 따라 지으면 알아보지 못합니다.
- 전원을 끈 아이폰과 에어태그의 '나의 찾기' 신호는 알리지 않고 개수만 따로 보여 줍니다. 꺼서 제출한 휴대폰도 이 신호를 내기 때문입니다.
- 가장 셀 때도 −85 dBm보다 약한 전파는 "멀리서 약하게 잡히는 것"으로 따로 모읍니다.

자세한 설계 배경은 [docs/design/same-device-grouping.md](docs/design/same-device-grouping.md)에 있습니다.

### 스마트 안경·헤드셋을 알아보는 방법

**노리는 것은 시험 중에 AI로 답을 받는 것을 막는 일입니다.** AI 안경이 AI에게 묻고 답을 듣거나, 보는 장면을 실시간으로 보내려면 연결된 휴대폰이 있어야 합니다. 휴대폰 없이 되는 것은 사진·영상 촬영 정도이고, 찍은 것은 안경 안에 저장됐다가 나중에 휴대폰으로 옮겨집니다. 답을 구하지 못하는 안경은 시험에 도움이 되지 않습니다. 수능처럼 시험이 끝나면 문제지를 공개하는 시험에서는 문제를 찍어 가는 것보다 시험 중에 답을 받는 것이 더 큰 위험입니다.

#### 미리 연결해 둔 안경은 이 앱으로 찾지 못합니다

- **Ray-Ban Meta 2세대는 휴대폰과 연결되면 블루투스 알림 신호를 아예 멈춥니다.** Mobile Hacker의 시험에서, 이 앱과 같은 방식으로 안경을 찾는 앱 Nearby Glasses는 연결된 Ray-Ban Meta 2세대 앞에서 아무것도 찾지 못했습니다. 일반 블루투스 스캐너에도 보이지 않았습니다. 보도 원문은 "Meta Ray-Ban Gen 2 stops broadcasting advertisement packets entirely after connecting to a phone, making the device invisible not only to Nearby Glasses but to general-purpose Bluetooth scanners as well"이고, 고장이 아니라 원래 그렇게 설계된 동작이라고 적었습니다([Next Reality](https://virtual.reality.news/news/app-to-detect-meta-glasses-nearby-has-a-critical-blind-spot/)).
- 이 앱도 기기가 내보내는 알림 신호만 봅니다. 아래 [탐지 범위](#탐지-범위)에도 "연결된 뒤 알림 신호를 멈추는 제품은 연결 전에만 보입니다"라고 적혀 있습니다. 이 저장소의 가상 폰 시험에서도, 안경 신호를 끄자 15초 안에 목록에서 빠졌습니다.

이 근거의 한계는 다음과 같습니다.

- 시험한 것은 Ray-Ban Meta 2세대 한 모델뿐입니다. 1세대, Oakley Meta, Meta Ray-Ban Display, 다른 회사 안경은 다를 수 있습니다.
- 이 저장소가 한 시험이 아니라 Mobile Hacker의 시험을 Next Reality가 전한 것입니다. 펌웨어 버전과 시험 조건은 기사에 없습니다.
- 연결이 끊긴 안경이 다시 신호를 내는지는 기사에 없습니다.
- 이 저장소에는 스마트 안경이 없어 실제 안경으로 확인하지 못했습니다.

그래도 위험은 분명합니다. 수험생이 교실 밖에서 휴대폰과 안경을 미리 연결해 두면, 휴대폰이 안경 가까이에서 켜져 있는 동안 안경은 이 앱에 보이지 않습니다.

#### 해결: 휴대폰을 안경에서 떼어 놓습니다

1. **걷은 휴대폰은 교무실처럼 시험실에서 먼 곳에 보관합니다. 같은 시험실에는 절대 두지 않습니다.** 수험생은 휴대폰을 몰래 켜 둔 채 낼 수 있습니다. 걷은 휴대폰을 같은 교실 앞쪽에 모아 두면, 켜진 휴대폰이 안경과 계속 연결되어 AI 답을 받을 수 있고, 연결된 안경은 이 앱에도 보이지 않습니다. 교무실, 1층, 건물의 가장 바깥처럼 블루투스가 닿지 않는 곳에 두면 미리 연결해 둔 안경도 연결이 끊겨 AI 답을 받을 수도, 실시간으로 보낼 수도 없습니다. 같은 교실에 두면 켜 둔 휴대폰이 시험 중 새 전파로 잡혀, 걷은 휴대폰과 내지 않은 휴대폰을 구분할 수 없습니다. 2번 안내는 걷은 휴대폰이 교실 밖에 있어야 성립합니다.
2. **내지 않은 휴대폰은 위반으로 봅니다.** 안경으로 답을 받으려면 휴대폰을 몸에 숨겨야 합니다. 시험 중 교실에서 휴대폰 신호(켜져 있는 Apple 기기, 휴대폰 핫스팟 등)가 잡히면 내지 않은 휴대폰으로 보고 확인합니다. 다만 휴대폰은 종류와 설정에 따라 알림 신호를 내지 않을 수 있어, 숨긴 휴대폰을 모두 찾지는 못합니다.
3. **이 앱으로 남은 신호를 찾습니다.** 휴대폰과 끊긴 안경이 회사 번호를 담아 다시 신호를 내면 "스마트 안경·헤드셋 후보"로 뜹니다. 끊긴 안경이 다시 신호를 내는지는 아직 확인한 자료가 없습니다. 그래도 1번과 2번으로 AI 답 받기는 막힙니다.

휴대폰 없이도 사진·영상 촬영은 됩니다. 수능처럼 시험 뒤 문제지를 공개하는 시험에서는 이 정도로 충분하다고 봅니다. 문제지를 공개하지 않는 시험에서는 입실할 때 안경을 확인하는 절차가 더 필요합니다([제도 제안](docs/POLICY-PROPOSALS.ko.md)).

#### 작동 원리

블루투스 기기는 주변에 "여기 있다"는 짧은 알림 신호를 반복해서 내보냅니다. 휴대폰과 연결되기 전이나 연결이 끊긴 뒤에 특히 그렇습니다. 이 신호에는 기기가 정해 넣는 값이 몇 가지 들어 있습니다.

| 신호에 든 값 | 예 | 바꾸기 |
| --- | --- | --- |
| 이름 | `Ray-Ban Meta` | 설정에서 바꾸거나 지울 수 있습니다. |
| 회사 번호 | `0x0D53`(Luxottica). Bluetooth SIG가 회사마다 나눠 준 번호로, 제조사 데이터 맨 앞 두 바이트에 들어 있습니다. | 기기 프로그램이 넣는 값이라 설정으로는 바꿀 수 없습니다. |
| 표준 종류 번호 | 안경 분류(0x07) | 기기가 넣을 때만 있습니다. 실제 안경이 넣는지는 확인하지 못했습니다. |

앱은 먼저 빈 교실 기록과 비교해 늘어난 신호를 "새 전파"로 알립니다. 그다음 새 전파에 종류 이름을 붙입니다.

1. **1단계 — 이름.** "Ray-Ban Meta", "Oakley Meta" 같은 이름이면 "스마트 안경"으로 표시합니다. 부정행위를 하려는 사람은 이름을 지울 수 있으므로, 이름은 기본 이름을 그대로 둔 기기만 잡습니다.
2. **2단계 — 회사 번호.** 이름도 다른 종류 단서도 없어 "종류 모름"이 될 신호에서 회사 번호를 아래 목록과 맞춰 보고, 있으면 "스마트 안경·헤드셋 후보"로 표시합니다. 예: 이름을 지운 레이밴 메타 → "스마트 안경·헤드셋 후보 · Luxottica Group S.p.A". 칩 회사 번호(Jieli)는 "무선 이어폰·스마트 안경 후보"로 표시합니다.
3. 기기가 표준 종류 번호로 안경이라고 알리면 "스마트 안경"으로 표시합니다.

이 목록은 화면에 붙는 종류 이름만 바꿉니다. 무엇을 새 전파로 세는지는 바꾸지 않습니다. 자동 테스트가 목록의 번호마다 목록에 없는 번호와 똑같이 묶이고 똑같이 세는지 확인합니다.

회사 번호는 그 회사가 만든 신호라는 뜻일 뿐 제품을 증명하지 않습니다. 예를 들어 Meta 번호는 Quest 헤드셋도 씁니다. 시험장에서는 둘 다 반입할 수 없으므로 함께 "후보"로 묶습니다. 칩 회사 번호도 같습니다. Jieli 칩은 값싼 카메라 안경보다 값싼 이어폰에 더 흔하므로, 감독관이 안경만 보지 않고 귀도 보도록 제목을 "무선 이어폰·스마트 안경 후보"로 합니다. 이어폰도 시험장에 반입할 수 없으므로 후보에서 빼지 않습니다. 휴대폰·이어폰·노트북과 번호를 같이 쓰는 회사(Apple, 삼성, 구글, 마이크로소프트, 소니)는 넣지 않습니다. 숨긴 휴대폰이 "안경 후보"로 뜨면 엉뚱한 곳을 찾게 되기 때문입니다. 이 회사들의 신호도 빈 교실 기록에 없으면 새 전파로 그대로 알립니다.

#### 회사 번호 목록 — 지금까지 찾은 것

빠진 회사가 많을 것입니다. 계속 추가할 수 있게 만들었습니다.

| 회사 번호 | 공식 회사 이름 | 찾으려는 제품 | 같은 번호를 쓰는 다른 기기 | 근거 |
| --- | --- | --- | --- | --- |
| 0x0D53 | Luxottica Group S.p.A | Ray-Ban Meta·Oakley Meta 안경 | 알려진 것 없음 | 공개 앱 두 곳 |
| 0x01AB | Meta Platforms, Inc. | Meta 안경·Quest 헤드셋 | Portal 화상통화 기기 | 공개 앱 두 곳 |
| 0x058E | Meta Platforms Technologies, LLC | Meta 안경·Quest 헤드셋 | 알려진 것 없음 | 공개 앱 두 곳 |
| 0x03C2 | Snapchat Inc | Snap Spectacles 안경 | 알려진 것 없음 | 공개 앱 두 곳 |
| 0x05D6 | Zhuhai Jieli technology Co.,Ltd | 값싼 카메라 안경(Rogbird VisionPro·Rollme VistaView 등) | 이 회사 칩을 쓰는 값싼 이어폰·스피커 | 공개 앱 한 곳 |
| 0x060C | Vuzix Corporation | Vuzix 안경 | 알려진 것 없음 | 등록부에 회사만 확인 |
| 0x10F9 | Even Realities Ltd. | Even Realities 안경 | 알려진 것 없음 | 등록부에 회사만 확인 |
| 0x055D | Valve Corporation | Valve Index 헤드셋 | 알려진 것 없음 | 등록부에 회사만 확인 |
| 0x02ED | HTC Corporation | HTC Vive 헤드셋 | 알려진 것 없음 | 등록부에 회사만 확인 |

읽는 법: "공개 앱"은 이 번호로 실제 제품을 찾는다고 공개한 앱([Nearby Glasses](https://github.com/yjeanrenaud/yj_nearbyglasses), [NoPeek](https://github.com/getnopeek/nopeek-android))입니다. "등록부에 회사만 확인"은 회사 번호는 공식 등록부에 있지만, 그 회사 제품이 이 번호를 신호에 싣는다는 자료는 찾지 못했다는 뜻입니다. 이 목록의 어느 줄도 이 저장소에서 실제 기기로 확인하지 않았습니다.

#### 한 줄 추가하는 법

1. [WearableCompanyCatalog.kt](app/src/main/java/com/classroom/rfdetector/core/WearableCompanyCatalog.kt)의 `entries`에 한 줄을 넣습니다.

   ```kotlin
   WearableCompany(0x0D53, "Luxottica Group S.p.A", "Ray-Ban Meta·Oakley Meta 안경", null, "근거 링크"),
   ```

   순서대로 회사 번호, 공식 회사 이름, 찾으려는 제품, 같은 번호를 쓰는 다른 기기(없으면 `null`), 근거(실제 제품 확인 자료가 없으면 `null`)입니다.

   안경보다 다른 기기가 더 흔한 번호는 끝에 `label = "무선 이어폰·스마트 안경 후보"`처럼 목록 제목을 따로 적습니다. 안 적으면 "스마트 안경·헤드셋 후보"입니다. 제목은 "후보"로 끝나야 하고, 아니면 테스트가 실패합니다.

   목록을 코드 파일에 둔 이유: 화면이 종류를 정하는 자리에서 앱이 켜지자마자 바로 쓸 수 있고, 따옴표나 쉼표를 빠뜨리면 빌드가 멈춰 바로 알 수 있습니다. 표 파일로 두면 앱이 켜질 때 읽어 화면까지 넘기는 코드가 따로 필요하고, 칸을 잘못 나눈 줄은 아무 경고 없이 빠질 수 있습니다.
2. 회사 이름은 [bluetooth-companies.tsv](app/src/main/assets/bluetooth-companies.tsv)에서 번호로 찾아 **한 글자도 바꾸지 말고** 그대로 씁니다. 이 파일의 번호는 10진수입니다(0x0D53 = 3411). 이름이 다르면 테스트가 실패합니다. 다른 앱의 목록을 옮길 때 특히 확인하세요. 예: 한 공개 앱에 VR 헤드셋 회사(Pico) 번호로 적힌 0x0BA7은 공식 등록부에서는 청각 기기 회사(hearX) 번호입니다.
3. 화면에 어떻게 떠야 하는지 [WearableKindCasesTest.kt](app/src/test/java/com/classroom/rfdetector/ui/WearableKindCasesTest.kt)에 사례를 한 줄 추가하면, 그 사례가 그대로 시험됩니다.
4. `./gradlew testDebugUnitTest`로 확인하고 PR을 보냅니다. 실제 기기에서 그 번호를 봤다면 기기 모델과 화면을 PR에 함께 적어 주세요.

#### 한계

- 미리 연결해 둔 안경은 찾지 못합니다. 근거와 해결은 위 [미리 연결해 둔 안경](#미리-연결해-둔-안경은-이-앱으로-찾지-못합니다) 절에 있습니다.
- "스마트 안경·헤드셋 후보" 같은 후보 표시가 뜨지 않았다고 안경이 아닌 것은 아닙니다. XREAL, Rokid, Brilliant Labs처럼 공식 등록부에 회사 번호가 없는 안경 회사가 있고, 칩 회사의 번호를 쓰거나 회사 번호를 싣지 않는 안경도 있을 수 있습니다. 이런 안경도 빈 교실 기록에 없던 신호면 새 전파로 뜹니다. 표시와 관계없이 새 전파는 모두 확인하고, 위치 찾기로 다가갔을 때 안경을 쓴 수험생 쪽에서 세지면 안경을 확인하세요.
- 이름 없는 신호는 회사 번호로 묶어 셉니다. 빈 교실을 잴 때 같은 회사 번호의 기기(예: 감독관의 Meta 기기)가 있었다면, 안경이 들어와도 그 묶음의 개수가 늘어난 상태가 30초 넘게 이어져야 알립니다. 감독관 기기가 나가고 안경이 들어오는 식으로 맞바뀌면 구분하지 못합니다. 빈 교실을 잴 때는 감독관 기기도 꺼 두세요.
- 회사 번호를 흉내 내는 장치는 만들 수 있습니다. 표시는 후보일 뿐 증거가 아닙니다.
- 이 앱이 막지 못하는 것을 법·표준·시험 규정으로 막는 방법은 [docs/POLICY-PROPOSALS.ko.md](docs/POLICY-PROPOSALS.ko.md)에 따로 정리했습니다.

## 탐지 범위

앱이 보는 것은 기기가 주변에 내보내는 **알림 신호**뿐입니다. 이미 연결된 기기끼리 주고받는 통신은 보지 못합니다.

그래서 다음 두 가지를 함께 지켜야 합니다. 자세한 내용은 [휴대폰을 안경에서 떼어 놓습니다](#해결-휴대폰을-안경에서-떼어-놓습니다)에 있습니다.

- **걷은 휴대폰은 교실 밖, 블루투스가 닿지 않는 곳에 둡니다.**
- **시험 중 새로 잡힌 휴대폰 신호는 내지 않은 휴대폰으로 보고 확인합니다.**

| 구분 | 예 |
| --- | --- |
| 탐지 | 자체 와이파이를 여는 카메라, 핫스팟을 켠 휴대폰, 휴대용 와이파이. 빈 교실 기록에 없던 이름과, 이름은 같지만 빈 교실에 없던 임의 주소를 쓰는 것을 알리며, 이름과 설정에 따라 알리지 않는 경우가 있습니다. |
| 조건부 탐지 | 무선 이어폰, 스마트워치, 스마트 안경, 블루투스를 켠 휴대폰. 연결된 뒤 알림 신호를 멈추는 제품은 연결 전에만 보입니다. |
| 탐지 불가 | 블루투스·와이파이를 끄고 LTE·5G만 쓰는 기기, 선으로 연결하거나 저장만 하는 카메라, 목걸이형·귓속 수신기, 구형(클래식) 블루투스만 쓰는 기기, 교실 밖의 기기 |

위 목록은 전부가 아닙니다. 다음 사항도 함께 알아 두어야 합니다.

- 교실 안팎을 구분하지 못합니다. 휴대폰에 닿는 전파는 벽 너머의 것도 잡힙니다.
- 와이파이 탐색은 30초 간격입니다. 켠 뒤 화면에 뜨기까지 30초~1분이 걸릴 수 있습니다.
- 가장 셀 때도 −85 dBm보다 약한 전파는 따로 모으고 새 전파로 세지 않습니다.
- **아무것도 잡히지 않아도 기기가 없다는 뜻이 아닙니다.**
- **새 전파는 확인이 필요하다는 표시일 뿐 증거가 아닙니다.** 옆 교실, 복도, 감독관 본인의 기기도 잡힙니다.
- 종류·제조사·모델 표시는 기기가 스스로 알린 값에서 읽은 후보입니다. 바꾸거나 흉내 낼 수 있습니다.
- 거리는 수신 세기로 어림한 값이며 벽과 사람에 따라 달라집니다.
- 주소나 세기만으로 소유자나 좌석을 특정할 수 없습니다.

## 권한

| 권한 | 쓰는 곳 |
| --- | --- |
| 근처 기기(블루투스 탐색·연결) | BLE 알림 신호 수신, 블루투스 상태 확인 |
| 정확한 위치, 근처 Wi-Fi 기기 | Android가 와이파이 탐색 결과에 요구합니다. 위치를 기록하지 않습니다. |
| Wi-Fi 상태 확인·변경 | 와이파이 탐색 요청 |
| 방해 금지 접근 | 측정·감시 중 전화·알림·알람 무음 |
| 진동 | 위치 찾기 |

인터넷 권한은 선언하지 않습니다. 제조사 조회는 앱에 포함된 등록부로 합니다.

## 저장하는 데이터

- 마지막 빈 교실 기록 하나를 앱 전용 저장소에 JSON으로 저장합니다. 측정 시각, 잡힌 전파의 주소·이름·수신 세기·회사 코드가 들어갑니다.
- 주변 기기의 이름에는 개인 이름이 들어 있을 수 있습니다(예: "○○의 AirPods"). 이 기록은 기기 백업 대상에서 제외하며 앱 밖으로 내보내는 기능이 없습니다.
- 앱을 지우면 함께 지워집니다.

## 빌드

JDK 17 이상과 Android SDK Platform 36이 필요합니다. 프로젝트 루트의 `local.properties`에 `sdk.dir`을 지정합니다.

```shell
./gradlew testDebugUnitTest assembleDebug lintDebug
```

APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생깁니다. 구조와 수신 규칙은 [docs/DEVELOPMENT.ko.md](docs/DEVELOPMENT.ko.md), 정식 서명 빌드는 [docs/RELEASE.ko.md](docs/RELEASE.ko.md), 버전별 변경은 [CHANGELOG.md](CHANGELOG.md)를 참고하세요.

`local.properties`, 서명 키, 실제 현장에서 받은 기록은 저장소에 올리지 않습니다.

## 제보

기능보다 관측 중심으로 적어 주세요. "이 조건에서 이 신호가 누락되거나 잘못 표시됐다"는 형태가 도움이 됩니다.

1. 앱 버전, 휴대폰 모델, Android 버전
2. 누른 순서, 기대한 결과, 실제 결과
3. 화면에 뜬 오류 문구
4. 화면을 첨부한다면 이름·주소·장소를 가려 주세요.

## 기여

코드 기여는 저장소를 포크한 뒤 `main`을 대상으로 Pull Request를 보내 주세요. PR마다 단위 테스트·빌드·Lint가 자동으로 돌고, 통과한 뒤 관리자가 검토해 머지합니다. 절차와 PR 기준은 [CONTRIBUTING.md](CONTRIBUTING.md)에 있습니다.

**PR에는 실제로 테스트한 결과를 최대한 함께 담아 주세요.** 코드만 고친 PR도 좋지만, 관리자가 직접 돌려 보며 확인해야 해서 검토에 시간이 오래 걸립니다. 실기기에서 실행해 확인할 수 있는 변경이라면 그렇게 확인한 PR이 가장 좋습니다. 에뮬레이터는 실제 전파를 받지 못하므로 실기기(실제 Android 휴대폰)로 테스트해 주세요.

- 무엇을 어떤 휴대폰에서 해 봤고 어떤 결과가 나왔는지 PR 본문에 적습니다. 휴대폰 모델, Android 버전, 누른 순서, 화면 캡처나 로그가 있으면 됩니다.
- 확인하지 못한 부분은 확인하지 못했다고 적습니다.
- Claude 같은 AI 도구로 이 저장소를 분석하거나 PR을 만들 때도 같습니다. 코드를 고치는 데서 끝내지 말고, 실행해서 확인할 수 있는 것은 실기기에서 실제로 실행해 그 결과를 PR에 담아 주세요.

## 출처

제조사 정보는 앱에 포함한 공개 등록부(2026년 11월 16일 미국 동부 / 17일 한국 시각 수집)를 씁니다. 실시간 조회가 아닙니다.

- [IEEE OUI 등록부](https://standards-oui.ieee.org/oui/oui.csv)
- [Bluetooth SIG 회사 식별자](https://www.bluetooth.com/specifications/assigned-numbers/)
- [앱에 포함한 등록부의 출처와 보관 필드](app/src/main/assets/REGISTRY-SOURCES.txt)
- [이름 기반 제품 후보의 제품명 출처](docs/DEVICE-NAME-SOURCES.md)
- 안경·헤드셋 회사 번호: [Nearby Glasses](https://github.com/yjeanrenaud/yj_nearbyglasses)(AGPL-3.0, 코드는 가져오지 않고 번호만 공식 등록부와 대조), [NoPeek](https://github.com/getnopeek/nopeek-android)(MIT, 같은 방식)
- 연결된 안경이 신호를 멈춘다는 시험 보고: [Next Reality](https://virtual.reality.news/news/app-to-detect-meta-glasses-nearby-has-a-critical-blind-spot/)(시험: Mobile Hacker, Ray-Ban Meta 2세대)
- 휴대폰 없이 촬영한 사진·영상이 안경에 저장되는 방식: [Meta 도움말](https://www.meta.com/help/ai-glasses/1427588664906909/)
- [Android BLE 기기 탐색](https://developer.android.com/develop/connectivity/bluetooth/ble/find-ble-devices), [Android Wi-Fi 탐색과 제한](https://developer.android.com/develop/connectivity/wifi/wifi-scan)

앱 아이콘은 화성시 캐릭터 코리요를 변형한 것입니다. 원저작자는 화성시이며 [공공누리 제1유형](https://www.kogl.or.kr/info/licenseType1.do)(출처표시) 조건으로 이용합니다.

제품명과 회사명은 관측 결과를 설명하기 위해 사용합니다. 해당 제조사가 이 앱을 인증하거나 보증했다는 의미는 아닙니다.

## 라이선스

소스 코드는 [Apache License 2.0](LICENSE)으로 공개합니다. 누구나 사용·수정·재배포할 수 있으며, 저작권 표시와 라이선스 문구를 유지하고 고친 파일에는 고쳤다는 표시를 남겨야 합니다.

이 라이선스는 코드에만 적용됩니다. 앱 아이콘(코리요), 앱에 포함한 등록부, "KoriyoSCAN"이라는 이름에는 각 권리자의 조건이 적용됩니다. 자세한 내용은 [NOTICE](NOTICE)에 있습니다.

## English

KoriyoSCAN is an Android app for exam proctors. It records the Wi-Fi access points and Bluetooth Low Energy advertisements present in an empty room, then flags signals that were not in that record once the exam is under way, and helps walk toward one by signal strength.

It cannot detect every device. It only sees Wi-Fi access points and BLE advertisements, so it misses devices that use cellular data only, wired or record-only cameras, Bluetooth Classic devices, and anything that stops advertising once connected. It does not block signals and does not decide whether anyone cheated. Field accuracy has not been measured.

Unnamed BLE signals that carry a listed Bluetooth company ID (Luxottica, Meta, Snap and others) are labelled as smart glasses or headset candidates. A chip vendor ID (Jieli) is labelled as an earphone or smart glasses candidate, because cheap earphones use that chip more often; earphones are banned in exam rooms too. This only changes the label; what counts as a new signal is unchanged. The list is one line per company in [WearableCompanyCatalog.kt](app/src/main/java/com/classroom/rfdetector/core/WearableCompanyCatalog.kt), and tests check every entry against the bundled Bluetooth SIG registry.

Records stay on the phone; the app declares no internet permission. The UI and the rest of this document are in Korean. Licensed under Apache-2.0; see [NOTICE](NOTICE) for the icon and bundled registry data.

Contributions are welcome through a fork and a pull request against `main`; CI must pass and a maintainer reviews before merging. Please include the results of testing on a real device (not an emulator) in the PR whenever the change can be checked by running the app: code-only PRs are welcome but take much longer to review. See [CONTRIBUTING.md](CONTRIBUTING.md) (in Korean).
