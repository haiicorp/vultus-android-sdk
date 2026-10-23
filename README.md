# Vultus SDK Guide for Android

Vultus SDK는 안드로이드 카메라 프레임을 기반으로 비접촉 rPPG(remote Photoplethysmography, 원격 맥박 측정) 및 FER(Facial Expression Recognition, 감정 분석) 생체 신호를 실시간 추론 및 측정합니다.

> [!IMPORTANT]
> **SDK Version**: `v1.0.0` (Maven: `io.haii:vultus-aos:1.0.0`)

---

## 📋 목차
1. [개요 및 시스템 아키텍처](#1-개요-및-시스템-아키텍처)
2. [프로젝트 설정 및 의존성](#2-프로젝트-설정-및-의존성)
3. [SDK 초기화 (Initialization)](#3-sdk-초기화-initialization)
4. [카메라와 프리뷰 — 두 길 (Default / Custom)](#4-카메라와-프리뷰--두-길-default--custom)
5. [측정 라이프사이클](#5-측정-라이프사이클)
6. [상태 구독](#6-상태-구독)
7. [프리뷰와 오버레이](#7-프리뷰와-오버레이)
8. [외장(USB) 카메라 — 거치형 기기](#8-외장usb-카메라--거치형-기기)
9. [ProGuard / R8 설정](#9-proguard--r8-설정)
10. [라이선스 검증 및 예외 처리 (License & Error Handling)](#10-라이선스-검증-및-예외-처리-license--error-handling)
11. [샘플 앱 구동 방법 (Sample App Guide)](#11-샘플-앱-구동-방법-sample-app-guide)
12. [모범 사례 및 트러블슈팅](#12-모범-사례-및-트러블슈팅)
13. [스토어 제출 — 수출 규정 신고](#13-스토어-제출--수출-규정-신고)
14. [스토어 제출 — 데이터 보안 신고](#14-스토어-제출--데이터-보안-신고)

---

## 1. 개요 및 시스템 아키텍처

Vultus SDK는 다음과 같은 주요 컴포넌트로 구성되어 있습니다.

| 컴포넌트 | 역할 및 설명 |
| :--- | :--- |
| **`Vultus`** | SDK 의 진입점. 초기화 · 라이선스 · 세션 열기(`openDefaultSession` / `openCustomSession`) |
| **`VultusDefaultSession`** | **Default 길.** SDK 가 카메라 · 프리뷰 · 자동 맞춤을 쥡니다 |
| **`VultusCustomSession`** | **Custom 길.** 앱이 카메라를 쥐고 프레임만 넘깁니다 |
| **`VultusSession`** | 두 세션이 공통으로 내는 것(측정 · 상태 · 공통 override · 외장 카메라). 테스트에서 이 인터페이스로 가로챌 수 있습니다 |
| **`VultusPreviewView`** | 프리뷰 표시. 표시 미러 · 표시 회전 · 종횡비 · 얼굴 가이드 · 좌표 변환 |

측정과 상태는 **두 길이 같은 것을 냅니다** — `VultusSession` 한 벌뿐이라 길에 따라 동작이 갈리지 않습니다. 어느 길을 고르든 §5 · §6 은 그대로 적용됩니다.

---

## 2. 프로젝트 설정 및 의존성

### 2.1 Maven 저장소 추가 (`settings.gradle.kts`)

SDK 는 Maven 저장소로 배포됩니다. 저장소 주소를 `settings.gradle.kts` 에 더합니다.

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://haiicorp.github.io/vultus-android-sdk/")
    }
}
```

저장소는 GitHub Pages(`*.github.io`)에서 열립니다. 사내 프록시나 방화벽이 그 주소를 막고 있으면 의존성 해석이 「찾을 수 없다」 로 끝납니다 — 그 주소를 허용 목록에 넣거나, 열 수 없는 환경이라면 HAII 에 문의해 주세요.

### 2.2 의존성 추가 (`app/build.gradle.kts`)

좌표 한 줄이면 됩니다. SDK 가 쓰는 라이브러리(CameraX · ML Kit 등)는 SDK 의 POM 을 따라 함께 들어옵니다.

```kotlin
dependencies {
    implementation("io.haii:vultus-aos:1.0.0")
}
```

### 2.3 권한 설정 (`AndroidManifest.xml`)

카메라 사용 및 서버 데이터 전송을 위해 권한을 추가합니다.

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
```

SDK 가 서버로 보내는 데이터는 앱이 Google Play 의 데이터 보안 양식에 신고해야 합니다 — 항목은 [14절](#14-스토어-제출--데이터-보안-신고)에 있습니다.

### 2.4 의존성 충돌 주의사항 및 환경 호환성 가이드 (Troubleshooting)

외부 앱에 Vultus SDK를 연동할 때 발생할 수 있는 주요 환경/의존성 충돌 이슈 및 해결 방법입니다.

#### 1) AGP (Android Gradle Plugin) 및 Java / Kotlin 버전 호환성
- **최소 SDK**: **`minSdk 24`** (Android 7.0) 이상. 동봉된 샘플앱은 `minSdk 25` 로 잡혀 있지만 SDK 자체의 하한은 24 입니다.
- **컴파일 SDK**: **`compileSdk 36`** 이상.
  - *증상*: `Dependency ... requires libraries and applications that depend on it to compile against version 36 or later of the Android APIs` 빌드 오류
  - *이유*: SDK 가 요구하는 `androidx.camera 1.6.1` 이 `compileSdk 36` 을 요구합니다. `compileSdk` 는 컴파일 시점 API 만 정하며 `minSdk`·`targetSdk` 에는 영향이 없습니다.
- **AGP 버전**: **AGP 8.9.1 이상** (권장: `8.13.x`) 및 **Gradle 8.11.1 이상** 필요.
  - *증상*: `Dependency 'androidx.camera:camera-core:1.6.1' requires Android Gradle plugin 8.9.1 or higher` 빌드 오류(`checkDebugAarMetadata`)
  - *이유*: SDK 가 요구하는 `androidx.camera 1.6.1` 이 AGP 8.9.1 이상을 요구하고, AGP 8.9 는 Gradle 8.11.1 이상을 요구합니다.
- **Java / JVM Target**: Vultus SDK는 **Java 17 (JVM Target 17)** 기반으로 빌드되었습니다.
  - *증상*: `Cannot inline bytecode built with JVM target 17 into bytecode built with JVM target 1.8` 빌드 오류 발생
  - *해결*: 연동 앱의 `build.gradle.kts`에 Java 17 설정 추가:
    ```kotlin
    android {
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        kotlinOptions {
            jvmTarget = "17"
        }
    }
    ```
- **Kotlin 버전**: **Kotlin 2.0.0 이상** (권장: `2.0.x`)
  - *증상*: Kotlin 메타데이터 버전 불일치 오류 (`incompatible version of Kotlin. The binary version of its metadata is 2.1.0, expected version is 1.9.0`)
  - *이유*: `androidx.camera 1.6.1` 과 그것이 가져오는 Kotlin 표준 라이브러리가 메타데이터 2.1 로 빌드되어 있어, 2.1 을 읽을 수 있는 Kotlin 2.0 이상 컴파일러가 필요합니다.
  - *해결*: 연동 프로젝트 루트 `build.gradle.kts` 내 Kotlin 플러그인 버전을 `2.0.0` 이상으로 업그레이드합니다.

#### 2) Native (NDK / `.so`) 및 ABI 충돌 해결
- **지원 ABI (64비트)**: **`arm64-v8a`**
  - *주의*: 연동 앱의 `ndk.abiFilters`에 `arm64-v8a`가 포함되어 있어야 합니다. 32비트 전용(`armeabi-v7a`)으로만 제한된 환경에서는 NDK 네이티브 모듈 로딩 실패(`UnsatisfiedLinkError`)가 발생합니다.
  - *해결*:
    ```kotlin
    android {
        defaultConfig {
            ndk {
                abiFilters.add("arm64-v8a")
            }
        }
    }
    ```
- **`libc++_shared.so` 중복 충돌 (Duplicate Native Libraries)**
  - *증상*: OpenCV, TensorFlow Lite 등 NDK C++ STL(`libc++_shared.so`)을 사용하는 타사 라이브러리와 함께 빌드 시 `2 files found with path 'lib/arm64-v8a/libc++_shared.so'` 패키징 오류 발생
  - *해결*: 연동 앱의 `build.gradle.kts` 내 `packaging` 블록에 `pickFirsts` 설정 추가:
    ```kotlin
    android {
        packaging {
            resources {
                pickFirsts += "**/libc++_shared.so"
            }
        }
    }
    ```

#### 3) 주요 라이브러리 버전 충돌 (CameraX / ML Kit)
- **ML Kit Face Detection (`com.google.mlkit:face-detection`)**:
  - Vultus SDK는 ML Kit `16.1.7`을 사용합니다. 연동 앱에서 구버전 ML Kit을 이미 참조 중인 경우 클래스/메서드 서명 충돌이 발생할 수 있으므로 ML Kit 버전을 `16.1.x` 이상으로 맞추는 것을 권장합니다.
- **CameraX (`androidx.camera:camera-*`)**:
  - Vultus SDK는 CameraX `1.6.1` (1.4.x 이상 호환)을 사용합니다. `1.3.x` 이하 구버전 CameraX 사용 시 API 불일치가 발생할 수 있으므로 CameraX를 `1.4.0` 이상으로 업그레이드합니다.

### 2.5 이용 조건 · 오픈소스 고지

| 파일 | 내용 |
| :--- | :--- |
| `LICENSE` | SDK 이용 조건. SDK 는 HAII 와 체결한 계약에 따라 사용합니다 |
| `THIRD-PARTY-NOTICES.txt` | AAR 에 포함된 오픈소스 소프트웨어(OpenSSL · curl · OpenCV · ONNX Runtime · libc++ 등)의 고지 |

같은 두 파일이 AAR 안의 `META-INF/vultus/` 에도 들어 있어 앱 APK 에 함께 실립니다. Maven 으로 받는 경우에도 같습니다.

SDK 를 앱에 넣어 배포하면 이 오픈소스들도 앱과 함께 배포됩니다. **앱의 오픈소스 고지(설정 화면의 「오픈소스 라이선스」 등)에 `THIRD-PARTY-NOTICES.txt` 의 내용을 포함해야 합니다.** 내용은 SDK 버전마다 바뀔 수 있으니 판올림할 때 함께 갱신하세요.

CameraX · ML Kit 처럼 앱이 Gradle 로 직접 받는 의존성은 이 파일에 없습니다 — 앱의 다른 의존성과 같은 방식으로 고지하세요.

---

## 3. SDK 초기화 (Initialization)

`Vultus.Initializer` 빌더 클래스를 통해 라이선스 키 및 옵션을 설정하고 초기화합니다.
`licenseCode`, `holderKey`, `clientKey` 3가지 필수 인증 정보를 제외한 **모든 세부 설정 메서드는 기본값(Default Value)을 가지고 있으므로 전부 선택 사항(Optional)** 입니다. 기본 설정만으로도 즉시 정상 동작합니다.

### 3.1 초기화 코드 예시

```kotlin
import android.view.Surface
import io.haii.vultus.Vultus

// 1. 필수 값만으로 최소 초기화 (모든 옵션 권장 기본값 적용)
Vultus.Initializer(
    licenseCode = "YOUR_LICENSE_CODE",
    holderKey = "YOUR_HOLDER_KEY",
    clientKey = "YOUR_CLIENT_KEY"
).initialize(context, lifecycleOwner)

// 2. 필요한 옵션만 선택적으로 재정의하여 초기화
Vultus.Initializer(
    licenseCode = "YOUR_LICENSE_CODE",
    holderKey = "YOUR_HOLDER_KEY",
    clientKey = "YOUR_CLIENT_KEY"
)
    .minFaceSize(0.3f)                             // (Optional) 최소 얼굴 크기 비율
    .alwaysDetect(true)                            // (Optional) 측정 전 프리뷰 얼굴 탐지 여부
    .enableFER(enable = true, ferInterval = 250L) // (Optional) FER 감정 분석 활성화
    .initialize(context, lifecycleOwner)
```

> [!NOTE]
> `context` 에는 측정 화면의 Activity 를 넘기세요. Fragment 에서 초기화한다면 `requireContext()` 를 넘기면 됩니다. Activity 를 찾을 수 없는 context(Application context 등)를 넘기면 화면 밝기가 측정 데이터에 기록되지 않습니다.

### 3.2 Initializer 주요 설정 옵션 목록 (모두 Optional)

| 설정 메서드 | 파라미터 타입 | 기본값 (Default) | 설명 | 비고 |
| :--- | :--- | :--- | :--- | :--- |
| **`minFaceSize`** | `Float` | `0.3f` | 전체 카메라 화면 대비 탐지할 최소 얼굴 비율 (0.1 ~ 1.0) | Optional |
| **`alwaysDetect`** | `Boolean` | `false` | 측정 중이 아닐 때도 프리뷰 상에서 얼굴 위치를 지속 탐지할지 여부. 켜면 **측정 전에도 촬영 조건 안내**([6](#6-상태-구독))가 갱신됩니다 | Optional |
| **`useAccurateMode`** | `Boolean` | `false` | MLKit 얼굴 탐지 정확도 모드 (`false`: FAST 모드 성능 권장) | Optional |
| **`detectContour`** | `Boolean` | `false` | 얼굴 윤곽선(Contour) 세부 좌표 탐지 여부 | Optional |
| **`enableFER`** | `Boolean`, `Long` | `enable = false`<br>`ferInterval = 250L` | FER(감정 분석) 활성화 여부 및 추론 사이의 최소 간격(ms). 0 ~ 60000 — 밖이면 그 끝으로 자릅니다. `ferInterval` 은 표정 추론 사이의 **최소 간격**입니다. 기기가 그 안에 추론을 마치면 이 간격으로 돌고, 추론이 더 오래 걸리는 기기에서는 그사이의 프레임을 건너뛰어 실제 간격이 추론 시간만큼 늘어납니다 — 설정한 간격을 보장하지는 않습니다. 결과는 늘 그 시점에 가장 최근 프레임의 표정입니다. `0` 이면 추론이 감당하는 만큼 자주 돕니다 — 측정 내내 추론이 쉬지 않아 저사양 기기에서는 심박 측정의 프레임 손실 · 발열로 이어질 수 있으니, 측정 품질이 먼저면 기본값을 권합니다 | Optional |
| **`lockCameraOnStartMeasure`** | `Boolean` | `true` | 측정을 시작하기 전(`ADJUSTING_CAMERA`)에 초점 · AE · AWB 를 (지원하는 기기에서) 고정합니다. 신호 안정성에 크게 기여합니다. 끄면 초점 고정도 함께 꺼집니다. 측정마다 바꾸려면 `overrideCameraLock` | Optional |
| **`defaultTimeout`** | `Int` | `30` | 측정 결과 전송 요청의 타임아웃(초). 최대 `60`, 최소 `1` | Optional |
| **`extCamConfiguration`** | `ExtCamConfiguration` | 적지 않음 | **적으면 외장(USB) 카메라를 우선합니다.** 값은 외장 카메라의 표시 회전 **각도**와 미러링 모드입니다(적지 않았을 때 외장을 열면 `0` · 미러) — [8.1](#81-카메라-선택과-표시--extcamconfiguration) | **Optional**<br>*(외장 카메라를 쓰는 기기만)* |
| **`useDevServer`** | `Boolean` | `false` | 개발 서버로 보냅니다. 통합 개발 · 시험용이며 **개발 서버는 정상 동작을 보장하지 않습니다**(예고 없이 멈추거나 바뀔 수 있습니다). **릴리스 빌드에서 부르면 `initialize` 가 예외를 던집니다**([10.1](#101-초기화-예외)). 서버 주소는 SDK 안에 고정돼 있어 바꿀 수 없습니다 | **Optional** |

### 3.3 네이티브 워밍업 및 준비 상태 (`prepareState`)

`initialize(...)` 호출 시 SDK는 **네이티브 콜드 스타트 비용을 백그라운드에서 미리 지불(warm-up)** 합니다. 이 워밍업이 없으면 사용자가 앱 기동 후 **처음** `startMeasure()`를 호출할 때 다음과 같은 프로세스 1회성 비용이 그 자리에서 발생해 **5초 이상 지연**될 수 있습니다.

- 네이티브 라이브러리(`libvultus.so`)의 최초 로드(`dlopen`)
- 추론 엔진 준비와 FER 모델 데이터 적재
- 라이선스 서버로의 DNS/TLS 핸드셰이크

워밍업은 `initialize(...)` 직후 자동으로 백그라운드 스레드에서 진행되며, 그 진행 상황은 `Vultus.prepareState` (`StateFlow<Vultus.PrepareState>`)로 노출됩니다. **앱은 이 상태가 `PREPARING`인 동안 측정 UI 대신 "준비 중" 화면을 노출**해야 합니다. (`initialize()` 호출 자체는 블로킹되지 않고 즉시 반환됩니다.)

| `PrepareState` | 의미 | 권장 UI 처리 |
| :--- | :--- | :--- |
| **`PREPARING`** | 워밍업 진행 중 (초기 상태) | 스피너 + "준비 중" 안내, 측정 버튼 비활성 |
| **`READY`** | 워밍업 완료. 첫 측정도 지연 없이 시작 가능 | 정상 측정 화면 노출 |
| **`LICENSE_INVALID`** | 워밍업은 끝났으나 라이선스 검증 실패 | **측정 화면을 그대로 노출** + 안내 배너 |
| **`FAILED`** | 네이티브 객체 생성 자체가 실패한 치명적 상태 | 오류 안내 후 앱 재실행 유도 |

> [!IMPORTANT]
> **`LICENSE_INVALID` 에서 측정 UI를 막지 마세요.** 대부분은 앱 기동 시점의 일시적 네트워크 단절이고, `startMeasure()` 가 진입할 때 라이선스를 한 번 더 검사합니다. 그때 통과하면 상태는 `READY`로 되돌아갑니다. 측정 버튼을 숨기면 이 복구 경로가 아예 실행되지 않습니다.
>
> 사유는 `Vultus.license.error` 로 읽어 안내 문구에 씁니다. 여기서 끝내 실패하면 `startMeasure()` 가 `Refused(VultusError.LICENSE_INVALID)` 를 돌려주므로([5절](#5-측정-라이프사이클)), 그때 사용자에게 알리면 됩니다.
>
> 네트워크가 아닌 사유는 다시 시도해도 같습니다. client key 가 받아들여지지 않으면 워밍업과 `startMeasure()` 모두 `LICENSE_INVALID` 이고, `Vultus.license.error` 는 「라이선스 설정을 받아들이지 않았다 — client key 를 확인하라」 는 사유입니다. 발급받은 client key 를 그대로 복사해 붙여넣으세요.

라이선스 검증은 워밍업에서 **백오프 재시도**(최대 3회)를 거칩니다. 서버가 라이선스를 거절한 경우(HTTP 4xx)나 앱 식별자가 어긋난 경우는 다시 물어도 답이 같으므로 재시도하지 않습니다.

```kotlin
import io.haii.vultus.Vultus

// initialize() 는 즉시 반환되고, 워밍업은 백그라운드에서 진행된다.
Vultus.Initializer(licenseCode, holderKey, clientKey)
    .enableFER(true)
    .initialize(context, lifecycleOwner)

// Jetpack Compose: 준비 상태에 따라 화면을 분기한다.
val prepareState by Vultus.prepareState.collectAsState()

when (prepareState) {
    // 라이선스 실패에서도 측정 화면을 막지 않는다 — startMeasure 가 다시 검사한다.
    Vultus.PrepareState.READY,
    Vultus.PrepareState.LICENSE_INVALID -> MeasureScreen()   // 정상 측정 화면
    Vultus.PrepareState.FAILED    -> ErrorScreen()           // 초기화 실패 안내
    Vultus.PrepareState.PREPARING -> PreparingScreen()       // "준비 중..." 화면
}
```

**시작 버튼은 `prepareState` 를 구독해 켜고 끕니다.** 버튼에는 「지금 준비됐는가」 가 아니라 **바뀌는 순간**이 필요하고, 꺼 둘 이유(준비 중 · 사용할 수 없음)를
가려 보여 줘야 하기 때문입니다. `LICENSE_INVALID` 에서는 켭니다 — 시작이 라이선스를 다시 검사하는 복구 경로입니다(위 IMPORTANT). 준비 전에 시작을 불러도
오류는 아닙니다. SDK 가 워밍업이 끝나기를 기다렸다가 측정을 시작하고, 그동안 `cancelMeasure()` 로 취소할 수 있습니다.

```kotlin
// View 기반 화면 — 화면이 보이는 동안만 구독한다.
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        Vultus.prepareState.collect { state ->
            startButton.isEnabled = state == Vultus.PrepareState.READY || state == Vultus.PrepareState.LICENSE_INVALID
            startButton.text = when (state) {
                Vultus.PrepareState.PREPARING -> "준비 중…"
                Vultus.PrepareState.FAILED -> "사용할 수 없음"
                Vultus.PrepareState.READY, Vultus.PrepareState.LICENSE_INVALID -> "측정 시작"
            }
            // 라이선스 실패는 버튼을 막지 않고 배너로 알린다 — 사유는 Vultus.license.error.
            licenseBanner.isVisible = state == Vultus.PrepareState.LICENSE_INVALID
        }
    }
}
```

> [!CAUTION]
> **`initialize(...)` 를 두 번 호출하면 `Vultus.InitializationError.AlreadyInitialized` 가 발생합니다.**
>
> `Activity.onCreate` 에 두었다면 반드시 가드하세요 — 화면 회전뿐 아니라 **다크모드 전환·폰트 크기 변경·다중 창 크기 조절**만으로도 Activity 가 재생성되어 `onCreate` 가 다시 돕니다.
>
> ```kotlin
> if (!Vultus.isInitialized()) {
>     Vultus.Initializer(licenseCode, holderKey, clientKey).initialize(this, this)
> }
> ```
>
> **`Application.onCreate` 에 두는 편이 낫습니다.** 프로세스당 한 번만 돌므로 가드가 필요 없고, SDK 의 수명(프로세스 단위)과도 맞습니다.
>
> **초기화에 넘긴 `lifecycleOwner` 는 측정 화면이 아니어도 됩니다.** 조도 센서와 화면 밝기는 **측정하는 화면**을 따릅니다 — Default 길은 `Vultus.openDefaultSession` 에 넘긴 화면, Custom 길은 `attachPreview` 로 붙인 프리뷰 뷰가 놓인 Activity 입니다. 초기화 화면은 그런 화면이 없을 때의 대신일 뿐입니다. 그래서 선택 화면에서 초기화하고 다른 Activity 에서 측정해도 됩니다. 단, Application 에서 초기화하고 Custom 길에서 SDK 프리뷰 뷰를 쓰지 않으면 밝기를 읽을 창이 없어 화면 밝기가 기록되지 않습니다(음수).
>
> 예외를 던지는 이유는, 조용히 무시하면 앱이 다른 `useDevServer` 나 `enableFER` 로 다시 초기화했을 때 **처음 설정이 그대로 유지되면서 아무 신호도 나오지 않기** 때문입니다.

### 3.4 전역 접근자

초기화 뒤에는 `Vultus` 의 정적 메서드로 SDK 에 접근합니다. 이 문서의 모든 예제가 이것들을 씁니다.

| 접근자 | 타입 | 설명 | **초기화 전 접근** |
| :--- | :--- | :--- | :--- |
| `Vultus.sdkVersion` | `String` | SDK 버전(SemVer). 원격 지원 때 함께 알려 주세요 | 정상 동작 |
| `Vultus.isInitialized()` | `Boolean` | `initialize(...)` 가 성공한 적이 있는지 | `false` |
| `Vultus.prepareState` | `StateFlow<PrepareState>` | 워밍업 진행 상태 ([3.3](#33-네이티브-워밍업-및-준비-상태-preparestate)) | `PREPARING` |
| `Vultus.DEFAULT_ANALYSIS_WINDOW_SECONDS` | `Int` | 분석 창 기본값(유효 초) — `startMeasure` 의 `analysisWindow` | 정상 동작 |
| `Vultus.getConfiguration()` | `Vultus.Config` | 확정된 설정을 **읽기 전용**으로. 앱이 만들어 넘길 수는 없습니다 | **앱이 멈춥니다** |
| `Vultus.license` | `VultusLicense` | 라이선스 — 유효 여부(`isValid`) · 허용한 기능(`grantedFeatures`) · 실패 사유(`error`)를 한 값으로 ([10.2](#102-라이선스-상태-확인-api)) | `isValid = false`, 사유는 미초기화 안내 |
| `Vultus.openDefaultSession(…)` · `openCustomSession()` | `VultusOpenResult` | 측정 세션을 엽니다 ([4절](#4-카메라와-프리뷰--두-길-default--custom)) | `Failure(NOT_INITIALIZED)` |

> [!NOTE]
> **측정 · 상태 · 검출 결과는 모두 세션에서 받습니다**([5절](#5-측정-라이프사이클) · [6절](#6-상태-구독)). `Vultus` 에는 세션을 여는 것 말고 측정을 다루는 정적 호출이 없습니다.

> [!IMPORTANT]
> **인스턴스를 꺼내는 접근자(`getConfiguration()`)는 초기화 전에 부르면 `UninitializedPropertyAccessException` 으로 앱이 멈춥니다.** `Vultus.isInitialized()` 로 가드하거나, 초기화 성공을 확인한 뒤에만 그 화면을 그리세요([10.1](#101-초기화-예외)).
>
> 상태를 묻는 접근자(`isInitialized()` · `prepareState` · `license`)와 세션 열기는 초기화 전에 불러도 안전합니다.

---

## 4. 카메라와 프리뷰 — 두 길 (Default / Custom)

Vultus 는 촬영을 다루는 길을 둘로 나눠 냅니다. 길마다 세션 타입이 다르고, 측정 · 상태는 두 세션이 같은 인터페이스(`VultusSession`)로 냅니다.

| | Default 길 | Custom 길 |
|---|---|---|
| 한 줄 요약 | **SDK 가 대부분을 대신합니다** | **직접 구현합니다(제약 최소)** |
| 카메라 소유 | SDK | 앱 |
| 진입점 | `Vultus.openDefaultSession(preview, lifecycleOwner)` → `VultusDefaultSession` | `Vultus.openCustomSession()` → `VultusCustomSession` |
| 프리뷰 | `VultusPreviewView` 를 놓기만 합니다 | 앱이 그리거나 `VultusPreviewView` 를 씁니다 |
| 해상도 · fps · 톤 | SDK 가 걸고, override 로 바꿉니다 | 앱이 자기 use case 에 겁니다 |
| 카메라 메타 | SDK 가 늘 모읍니다 | `prepareImageAnalysis` · `attachCamera` 로 SDK 가 모읍니다 |

**길 전용 호출은 그 세션 타입에만 있습니다** — 예를 들어 `setFaceGuide` 는 `VultusDefaultSession` 에, `attachCamera` 는 `VultusCustomSession` 에만 있으므로 다른 길의 호출은 컴파일되지 않습니다.

세션 열기는 예외를 던지지 않고 `VultusOpenResult` 를 돌려줍니다. `getOrElse { error -> … }` 나 `getOrNull()` 로 꺼내세요.

> [!IMPORTANT]
> **열린 세션은 언제나 하나입니다.** 세션이 열려 있는 동안 다른 세션을 열면 `VultusError.OTHER_WAY_ACTIVE` 가 돌아옵니다. 예외는 Default 세션을 다시 여는 경우로, 열린 Default 세션을 넘겨받습니다([4.2](#42-default-길--vultusopendefaultsession)). 다 쓴 세션은 `close()` 하세요.

### 4.1 카메라 권한 — 앱이 요청합니다

Default 길에서 SDK 는 권한을 **확인만** 하고, 없으면 `VultusError.CAMERA_PERMISSION_DENIED` 를 돌려줍니다. 요청은 앱이 합니다. Custom 길은 카메라를 앱이 열므로 열 때는 확인하지 않고 측정 시작에서 확인합니다 — 권한이 없으면 `startMeasure` 가 `Refused(CAMERA_PERMISSION_DENIED)` 를 돌려줍니다. 그래서 Custom 세션은 권한을 묻기 전에 열어 둘 수 있습니다.

> [!WARNING]
> 권한을 묻기 **전에** `Vultus.openDefaultSession` 을 부르면 그 호출은 거절로 끝나고, 나중에 사용자가 허용해도 **다시 부르는 주체가 없습니다.** 허용이 확정된 뒤에 프리뷰를 여세요.

```kotlin
private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    cameraAuthorized = granted
}

if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
    cameraAuthorized = true
} else {
    requestCamera.launch(Manifest.permission.CAMERA)
}
```

### 4.2 Default 길 — `Vultus.openDefaultSession`

프리뷰 뷰를 레이아웃에 놓고 `openDefaultSession` 에 넘기면 끝입니다. 카메라 선택 · 바인딩 · 표시 미러 · 표시 회전 · 기본 얼굴 가이드는 SDK 가 정합니다.

Default 길에서 SDK 는 카메라를 바인딩할 때 `ProcessCameraProvider` 에 묶인 것을 **모두 풉니다** — 앱이 같은 카메라에 다른 use case(`ImageCapture` 등)를 함께 묶어야 하면 Custom 길을 쓰세요.

```kotlin
val preview = VultusPreviewView(context).apply {
    fill = VultusPreviewFill.FILL          // 덮기(기본) 또는 FIT(레터박스)
}

val session = Vultus.openDefaultSession(
    preview = preview,
    lifecycleOwner = this,                 // **화면의 수명**입니다(초기화 때 넘긴 것과 별개)
    // selection 생략 — extCamConfiguration 을 적었으면 외장 우선, 아니면 전면
).getOrElse { error ->
    return                                 // error.code 로 사유를 판단합니다
}

// 화면을 떠날 때
session.close()
```

- **열린 Default 세션을 넘겨받습니다.** 새 화면의 `openDefaultSession` 이 옛 화면의 `close` 보다 먼저 불려도 거절되지 않습니다 — 옛 세션은 그때 닫힙니다.
- `selection` 은 `FRONT` · `BACK` · `EXTERNAL` · `PREFER_EXTERNAL` 중 하나입니다. `PREFER_EXTERNAL` 은 외장(USB) 카메라가 있으면 그것을, 없으면 전면을 엽니다 — 키오스크와 폰을 같은 코드로 다룰 때 씁니다. 외장 카메라는 [8](#8-외장usb-카메라--거치형-기기) 에 모았습니다.
- **`selection` 을 생략하면 초기화 설정이 정합니다.** `extCamConfiguration` 을 적었으면 `PREFER_EXTERNAL`, 적지 않았으면 `FRONT` 입니다. 명시한 `selection` 은 설정보다 우선합니다.
- **화면이 없어지면(`ON_DESTROY`) 세션이 스스로 닫힙니다.** 화면이 잠깐 멈췄다 돌아오는 것(`ON_STOP` → `ON_START`)은 CameraX 가 카메라를 놓았다가 다시 붙이므로 앱이 할 일이 없습니다 — `ON_STOP` 에서 닫지 마세요.
- 카메라를 곧바로 놓으려면 화면을 떠날 때(`onPause` 에서 `isFinishing` 일 때) `close()` 하세요. `ON_DESTROY` 는 뒤로 간 한참 뒤일 수 있고, 그 사이 다른 화면이 Custom 세션을 열면 `OTHER_WAY_ACTIVE` 로 거절됩니다.
- ⚠️ **이미 끝난 수명을 넘기면 `UNSUPPORTED_VALUE` 로 거절합니다.** `DESTROYED` 상태의 `lifecycleOwner` 를 받으면 카메라가 열릴 수 없으므로 성공을 돌려주지 않습니다 — 화면이 이미 정리된 뒤에 도착한 호출이 조용히 실패하지 않도록 한 것입니다.
- ⚠️ **`openDefaultSession` 은 메인 스레드에서 부르세요**(`@MainThread`) — 프리뷰 뷰와 카메라 바인딩을 만집니다. Default 세션의 `close` 는 어느 스레드에서 불러도 돌아오면 닫힌 세션입니다(곧바로 다른 세션을 열 수 있습니다) — 카메라 바인딩만 메인으로 넘겨 놓습니다.

#### Jetpack Compose 래퍼 (예제)

> [!NOTE]
> **SDK 패키지에는 Compose 의존성이 없습니다.** 아래 래퍼를 복사해 쓰세요. 배포 패키지의 `sample/` 에는 같은 코드가 측정 화면 안에 있습니다(`app/src/main/java/com/example/vultus/presentation/defaultway/DefaultMeasureView.kt` 의 `AndroidView`).

```kotlin
@Composable
fun CameraPreviewBox(
    onSession: (VultusDefaultSession?) -> Unit,   // 열린 세션(닫히면 null) — 측정 · 상태 구독에 씁니다
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var session by remember { mutableStateOf<VultusDefaultSession?>(null) }

    AndroidView(
        factory = { ctx ->
            VultusPreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                fill = VultusPreviewFill.FILL
                onOverlayTransformChanged = { transform -> /* 오버레이에 넘깁니다 — §7 */ }
            }.also { view ->
                session = Vultus.openDefaultSession(view, lifecycleOwner, VultusCameraSelection.FRONT).getOrNull()
                onSession(session)
            }
        },
        onRelease = {
            session?.close()
            session = null
            onSession(null)
        },
        modifier = modifier,
    )
}
```

> [!CAUTION]
> **`DisposableEffect` 로 열지 마세요.** 재구성마다 키가 흔들려 `close` → `openDefaultSession` 이 왕복하고, 그 사이 카메라가 한 번 닫힙니다. `factory` 에서 열고 `onRelease` 에서 닫으면 한 번만 불립니다.

### 4.3 Custom 길 — `Vultus.openCustomSession`

앱이 카메라를 쥡니다. SDK 는 프레임을 받아 측정만 합니다. 남는 제약은 넷입니다 — 픽셀 포맷(`YUV_420_888`) · 프레임은 `imageAnalyzer` 로 · 측정 중 해상도 · 회전 변경 금지 · 3A 락은 SDK 가 기본으로 겁니다(끄는 길은 `overrideCameraLock`). 카메라 메타는 `prepareImageAnalysis` · `attachCamera` 로 SDK 가 모읍니다.

CameraX 는 순서가 정해져 있어 두 걸음이 필요합니다. `ImageAnalysis.Builder` 는 **빌드 전에만**, `Camera` 는 **바인딩 뒤에만** 있기 때문입니다.

```kotlin
val session = Vultus.openCustomSession().getOrElse { error ->
    return                                                  // error.code 로 사유를 판단합니다
}

val analysis = ImageAnalysis.Builder()
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .also { session.prepareImageAnalysis(it) }              // ① 빌드 **전** — 카메라 메타 콜백(빼면 측정 전 카메라 고정이 자리 잡기를 기다리지 못합니다)
    .also { Vultus.applyRecommendedCapture(it, cameraInfo) } // ② 빌드 **전** — 권고 캡처 설정(톤 처리 · 필요한 기기에서 영상 안정화)
    .build()
    .also { it.setAnalyzer(session.analysisExecutor, session.imageAnalyzer) } // SDK 의 프레임 줄 — 사본 없이 곧바로 처리됩니다

val camera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
session.attachCamera(camera)                                // ③ 바인딩 **뒤** — 3A 락 · 렌즈 메타

// 화면을 떠날 때 — 카메라는 앱이 놓습니다
session.close()
cameraProvider.unbindAll()
```

- **use case 는 그것을 준비한 세션의 것입니다.** `prepareImageAnalysis` 가 끼운 수집은 그 세션에 매이므로, 세션을 새로 열면 use case 도 새로 만들어 다시 바인딩하세요.
- **`applyRecommendedCapture` 는 세션을 연 뒤에 부르세요.** 실제로 건 항목이 그때 열려 있는 세션의 측정에 기록됩니다.
- `attachCamera` 를 부르지 않으면 `startMeasure` 가 `Refused(VultusError.CAMERA_NOT_ATTACHED)` 를 돌려줍니다. 다시 바인딩했으면 새 `Camera` 로 다시 부르세요. 측정을 시작한 뒤(시작을 기다리는 동안부터 남은 프레임을 다 처리할 때까지)에는 `NOT_ALLOWED_WHILE_MEASURING` 으로 거절합니다 — 카메라는 측정 전이나 끝난 뒤에 바꾸세요.
- **프레임률은 앱이 겁니다. 30 fps 고정을 권장합니다** — 기기가 신고한 `CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES` 에 `[30,30]` 이 있으면 `Camera2Interop.Extender(builder).setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(30, 30))` 로 겁니다. 목록에 없는 값을 걸면 기기가 조용히 화각을 바꿀 수 있습니다.
- 프리뷰는 앱이 그려도 되고, `VultusPreviewView` 를 그대로 써도 됩니다(§7). 쓴다면 `session.attachPreview(view)` 로 붙이세요 — 자동 맞춤의 표시 맞춤이 걸리고(§4.5), 조도 · 화면 밝기를 그 화면에서 읽습니다.
- **닫은 세션은 다시 쓰지 않습니다**(두 길 모두). 닫은 세션에서 `startMeasure` 를 부르면 `Refused(SESSION_CLOSED)`, `attachCamera` · `override…`(촬영 · 회전 · 락 · 맞춤) · `setFaceGuide` · `attachExternalCameraAdapters` 를 부르면 `SESSION_CLOSED` 가 돌아옵니다. 측정 시작을 기다리는 사이 세션을 닫아도 `Refused(SESSION_CLOSED)` 입니다(라이선스가 무효여도 닫힘이 먼저입니다). Default 길에서 시작을 기다리는 사이 새 Default 세션을 열어 넘겨받아도 같습니다. 수집 중(`STARTED`)에 닫으면 측정을 버리고 `Canceled` 로 끝납니다. 멈춘 측정(`stopMeasure()` · 자동 정지 · 하드 종료 뒤 — `STOPPED` · `FINISHED` · `SUBMITTING`)은 닫아도 버리지 않고 끝까지 보내 결말을 돌려줍니다(그 사이 `beforeSubmit` 도 불립니다). 닫은 뒤 그 측정을 버리려면 닫은 핸들의 `cancelMeasure()` 를 부르거나 `startMeasure` 를 부른 코루틴을 취소하세요. 그 밖의 호출은 닫은 세션에서 아무것도 바꾸지 않으며, 돌려주는 값은 메서드마다 다릅니다.
- **측정을 시작할 수 있는지는 `session.state` 하나로 봅니다.** `VultusMeasureState.IDLE` 이면 `startMeasure` 를 부를 수 있습니다 — 앞 측정은 결과(전송 응답)까지 끝났습니다. `IDLE` 이 아닐 때 부르면 수집 중(`STARTING` · `ADJUSTING_CAMERA` · `STARTED`)이면 `Refused(NOT_ALLOWED_WHILE_MEASURING)`, 정리 · 전송 중(`STOPPED` · `FINISHED` · `SUBMITTING` · `CANCELED`)이면 `Refused(PROCESSING_PREVIOUS_MEASUREMENT)` 입니다.
- **예외는 세션을 닫고 곧바로 새 세션을 연 때 하나입니다.** 새 세션은 처음부터 `IDLE` 이지만, 닫은 세션의 측정이 아직 정리 · 전송 중이면 `Refused(PROCESSING_PREVIOUS_MEASUREMENT)` 가 돌아옵니다 — 결과를 보내는 중이면 전송이 끝날 때까지(전송 타임아웃 `defaultTimeout`, 기본 30초 — 서버가 세션을 다시 요구한 드문 경우는 라이선스 재확인 약 5 초와 두 번째 전송만큼 더) 이어질 수 있습니다. 측정 화면은 결과를 받은 뒤에 닫는 것을 권합니다. 곧바로 다시 시작해야 한다면 그 시간을 덮게 재시도하세요.

  ```kotlin
  var outcome = session.startMeasure(analysisWindow = 60)
  var retries = 0
  while (outcome is VultusMeasureOutcome.Refused &&
      outcome.error == VultusError.PROCESSING_PREVIOUS_MEASUREMENT && retries++ < 60
  ) {
      delay(500)
      outcome = session.startMeasure(analysisWindow = 60)
  }
  ```

### 4.4 촬영 설정 override

권장값을 벗어나야 할 때 씁니다. **메서드 이름에 `override` 가 들어갑니다** — 부를 때마다 눈에 띄도록 한 것입니다.

```kotlin
// 두 길 공통 — VultusSession
session.overrideCameraLock(false)                    // null 이면 권장값으로 되돌립니다

// Default 길만 — 카메라를 SDK 가 쥐는 VultusDefaultSession
defaultSession.overrideResolution(Size(1280, 720))
defaultSession.overrideFrameRate(30)
defaultSession.overrideToneAndNoiseProcessing(false)
```

| override | 세션 | 바꾸는 것 |
| :--- | :--- | :--- |
| `overrideCameraLock` | 두 길 | 측정 시작 전(`ADJUSTING_CAMERA`)의 3A 락 |
| `overrideOrientationPolicy` | 두 길 | 방향을 정하는 방식([7.2](#72-좌표-변환)) |
| `overrideResolution` · `overrideFrameRate` · `overrideToneAndNoiseProcessing` | Default 길 | SDK 가 쥔 카메라의 촬영 구성 |
| `overrideAutoFraming` | Custom 길 | 자동 맞춤의 권고([4.5](#45-자동-맞춤--사용자는-편한-자리에-카메라가-맞춥니다)) |

> [!WARNING]
> **신호 품질이 떨어질 수 있습니다.** 권장값은 측정 신호를 기준으로 정한 것이고, 바꾸면 그만큼 결과가 나빠질 수 있습니다. 그래서 **어느 축을 바꿨는지와 실제로 적용된 값이 측정에 함께 기록됩니다.**

- **측정을 시작한 뒤에는 거절합니다**(`NOT_ALLOWED_WHILE_MEASURING`) — 시작을 기다리는 동안부터 남은 프레임을 다 처리할 때까지입니다. 해상도나 fps 가 바뀌면 한 측정 안에서 좌표계와 시계가 두 벌이 됩니다.
- **기본 프레임률은 30 fps 고정입니다.** 어두워도 fps 를 낮추지 않습니다 — 조도가 부족하면 촬영 조건이 `TOO_DARK` 를 내므로 사용자를 밝은 곳으로 안내하세요. 기기가 `[30,30]` 을 지원하지 않으면 걸지 않습니다.
- ⚠️ **메인 스레드에서 부르세요.** `overrideResolution` · `overrideFrameRate` · `overrideToneAndNoiseProcessing` 은 카메라를 다시 바인딩하므로 메인 스레드 계약을 따릅니다(`@MainThread` 가 붙어 있어 Lint 가 잡아 줍니다). 다른 스레드에서 부르면 `CAMERA_UNAVAILABLE` 이 돌아옵니다. `Vultus.openDefaultSession` 도 같습니다. `setFaceGuide` 는 메인 밖에서 불러도 거절하지 않고 뷰를 그대로 만지니(오류가 아니라 예외나 화면 깨짐이 될 수 있습니다) 메인에서 부르세요. `close` 는 어느 스레드에서 불러도 됩니다. 나머지(`overrideCameraLock` · 회전 입력)는 스레드를 가리지 않습니다.
- ⚠️ **기기가 지원하지 않는 프레임률은 `UNSUPPORTED_VALUE` 로 거절하고 권장 기본값을 그대로 씁니다.** 기기가 신고한 목표 프레임률 목록에 `[값, 값]` 한 점이 있어야 못 박을 수 있습니다 — 열린 범위(예: 7~30)가 그 값을 포함하더라도 한 점이 없으면 거절합니다. **거절된 호출은 아무것도 바꾸지 않습니다.** 기본값으로 되돌리려면 `null` 을 넘기세요.
- **override 는 세션의 것입니다.** 새로 연 세션은 권장값에서 시작합니다.
- **해상도 · fps · 톤/노이즈 override 는 `VultusDefaultSession` 에만 있습니다.** Custom 길은 카메라를 앱이 쥐고 있어 SDK 가 그 축에 손댈 수 없으므로, 앱이 자기 use case 에 직접 겁니다(권고 캡처 설정은 `Vultus.applyRecommendedCapture`, 프레임률은 [4.3](#43-custom-길--vultusopencustomsession)).

### 4.5 자동 맞춤 — 사용자는 편한 자리에, 카메라가 맞춥니다

측정 전 프리뷰에서 SDK 가 두 가지를 맞춥니다. **분석 프레임의 얼굴**이 측정에 필요한 크기(얼굴 긴 변 px) 이상이 되도록 카메라 줌을, **화면의 얼굴**이 앱이 정한 자리(가이드)에 보이도록 프리뷰의 배율 · 이동을 맞춥니다. 사용자에게는 편한 거리에서 카메라를 바라보라고만 안내하면 되고, 카메라로 더 맞출 수 없을 때만 SDK 가 요청할 것을 알려 줍니다. 사람이 움직이는 동안에는 확대하지 않고 멈추면 맞춥니다. 다가와 얼굴이 커지면 멈추기를 기다리지 않고 줌을 풉니다. 맞춤이 언제 멈추는지는 측정을 시작하는 방식이 정합니다([5](#5-측정-라이프사이클) 「시작 방식」).

> [!IMPORTANT]
> 자동 맞춤은 측정 전 프리뷰의 얼굴 검출로 돕니다 — **`alwaysDetect(true)` 를 켜야 합니다.** 끄면 측정 전에는 검출이 돌지 않아 맞춤 상태가 `Searching` 에 머물고 권고도 오지 않습니다(비대화형 시작이 맞춤을 기다리는 동안은 예외로 검출이 돕니다).

**Default 길 — 가이드만 정합니다.**

```kotlin
val session = Vultus.openDefaultSession(previewView, lifecycleOwner).getOrElse { return }
session.setFaceGuide(VultusFaceGuide(widthFraction = 0.6f))   // 프리뷰 폭의 60 %, 가운데

lifecycleScope.launch {
    session.framing.collect { state ->
        when (state) {
            is VultusFramingState.Framed -> hideHint()   // 시작 버튼은 막지 않습니다 — 누르는 순간 맞춤이 멈춥니다
            is VultusFramingState.Limited -> when (state.limit) {
                VultusFramingLimit.TOO_FAR -> showHint("조금 더 가까이 와 주세요")
                VultusFramingLimit.TOO_CLOSE -> showHint("조금 더 멀리 떨어져 주세요")
                VultusFramingLimit.OFF_CENTER -> showHint("화면 가운데로 와 주세요")
            }
            else -> Unit   // Searching(얼굴을 기다림) · Adjusting(맞추는 중 — 사용자에게 할 말이 없음) · Countdown(비대화형 시작, §5)
        }
    }
}
```

- 가이드는 **프리뷰 뷰 기준 비율**입니다(화면 기준이 아닙니다). 부르지 않으면 가로 60 %, 가운데에서 조금 아래입니다. `VultusFaceGuide` 의 기본 중심은 가운데라, 폭만 넘기면 중심도 가운데로 바뀝니다. 폭은 0 초과 1 이하, 중심은 0 ~ 1 이고 밖이면 `UNSUPPORTED_VALUE` 입니다. 지금 가이드는 `session.faceGuide` 로 읽습니다.
- `framing` 은 **상태의 종류가 바뀔 때만** 냅니다(`Limited` 는 이유가, `Countdown` 은 남은 초가 바뀔 때도 냅니다. `Framed` 의 값만 바뀐 것은 다시 내지 않습니다) — Compose 에서 최상위로 관찰해도 매 프레임 재구성되지 않습니다.
- 맞추는 동안 촬영 조건의 원인은 `VultusCaptureCondition.Cause.FRAMING` 입니다 — 사용자에게 알릴 것이 없다는 뜻입니다. `TOO_FAR` 는 줌으로 더 맞출 수 없을 때만 나옵니다.
- 프리뷰를 늘리고 옮기므로, 오버레이는 반드시 `onOverlayTransformChanged` 로 받은 변환을 쓰세요(§7.2).
- **프리뷰가 얼굴을 따라가는 데는 한계가 있습니다.** 얼굴이 렌즈 중심(분석 프레임 가운데)에서 멀면 화면은 일부만 옮겨, 얼굴이 가이드에서 비켜 보입니다 — 사용자가 가운데로 오게 하는 단서입니다. 맞춘 뒤 사용자가 자리를 옮기면, 멈춘 뒤 프리뷰가 다시 따라갑니다(움직이는 동안은 따라가지 않습니다). 렌즈 중심을 화면에 보이려면 맞춤 crosshair 를 켜세요(§7.4).
- **가이드가 프리뷰 모양에 비해 작으면 프리뷰 가장자리가 빕니다(레터박스).** 가이드는 프리뷰 뷰 기준이고 얼굴의 최소 크기는 분석 프레임 기준이라, 프레임 일부가 잘려 보이는 프리뷰(세로로 긴 전체 화면 등)에서는 얼굴을 가이드에 얹으려고 영상을 덮기보다 작게 그립니다. 빈 가장자리가 싫으면 프리뷰 뷰를 분석 프레임에 가까운 비율로 두거나 가이드를 키우세요.
- **Default 길에서는 늘 켜져 있습니다** — 카메라를 SDK 가 쥐므로 끄는 메서드가 없습니다. 맞춤 없이 카메라를 다루려면 Custom 길을 쓰세요.

**Custom 길 — SDK 는 앱의 카메라를 바꾸지 않습니다.** 프리뷰 표시는 `VultusPreviewView` 를 쓰면 SDK 가 맞추고, 줌은 SDK 의 권고를 받아 앱이 겁니다.

```kotlin
previewView.faceGuide = VultusFaceGuide(widthFraction = 0.6f)
session.attachPreview(previewView)            // 가이드 · 표시 맞춤 · 오버레이 변환을 세션에 잇는다

lifecycleScope.launch {
    // 권고는 줌을 바꿔도 되는 때(측정 전 — 비대화형 시작이 맞춤을 기다리는 동안 포함)에만 옵니다 — 오는 대로 겁니다.
    session.framingAdvice.filterNotNull().collect { advice ->
        session.applyFramingZoom(camera, advice)  // 선택 — 줌을 바꾸면 안 되는 때면 걸지 않고 false
        advice.limit?.let { showHint(it) }        // 줌으로 더 맞출 수 없을 때 사용자에게 요청할 것
    }
}

// 화면을 떠날 때 — close() 도 프리뷰를 뗍니다
session.attachPreview(null)
```

- 권고 줌(`recommendedZoomRatio`)은 **센서의 실제 화소를 더 쓰는 범위까지만** 나옵니다 — 그 이상 확대해도 측정 품질은 좋아지지 않습니다. 맞춤이 겨냥하는 얼굴 크기는 권고의 `targetFaceBoxPx`, 지금 크기는 `currentFaceBoxPx` 입니다.
- **줌은 권고가 올 때만 거세요.** 권고는 측정 중에는 `null` 이고, `applyFramingZoom` 도 그때는 걸지 않습니다. 실제 줌은 프레임마다 측정에 기록됩니다. **측정 중에는 해상도를 바꾸지 마세요.**
- **권고를 따를지는 앱이 정합니다.** 따르지 않아도 SDK 는 그대로 동작합니다 — 측정 · 촬영 조건 판정은 권고와 무관합니다.
- Custom 길의 촬영 조건은 앱이 권고를 따를지 알 수 없으므로 `FRAMING` 대신 `TOO_FAR` 를 그대로 냅니다.
- 권고 줌을 걸었는데 카메라가 그 줌에 닿지 않으면, SDK 는 닿은 줌을 그 카메라의 한도로 보고 같은 줌을 거듭 권하는 대신 `limit` 로 사용자에게 요청합니다. `applyFramingZoom` 을 쓰지 않고 앱이 직접 걸어도, 줌이 권고 쪽으로 움직였으면 같게 봅니다. 한도는 맞춤을 처음부터 다시 할 때(카메라가 다시 열림 · 프리뷰를 다시 붙임 · 맞춤을 다시 켬)와 줌이 그 한도보다 올라갔을 때 풀립니다.
- 끄려면 `session.overrideAutoFraming(false)` — 권고를 멈춥니다.
- 자체 프리뷰를 쓰는 앱은 표시 맞춤 없이 권고만 받아 쓰면 됩니다.

---

## 5. 측정 라이프사이클

두 길이 같은 것을 냅니다 — 측정은 열린 세션(`VultusSession`)에서 부릅니다. **`startMeasure` 는 측정이 끝날 때까지 기다렸다가 결말을 돌려줍니다.** 측정 하나에 결말이 하나입니다.

```kotlin
// 측정 중 계속 오는 값은 흐름으로 읽습니다. 표정은 측정을 시작하면 null 로 돌아갑니다.
lifecycleScope.launch {
    session.emotion.filterNotNull().collect { emotion -> /* ... */ }
}

lifecycleScope.launch {
    when (val outcome = session.startMeasure(
        analysisWindow = 60,                    // 서버가 분석할 유효 초 — 기본 60
        appendix = mapOf("userKey" to "..."),   // 측정에 그대로 실려 서버로 갑니다
    )) {
        is VultusMeasureOutcome.Finished -> { /* outcome.responseJson — VultusAnalysisResult.fromJson 으로 읽습니다 */ }
        VultusMeasureOutcome.Canceled -> { /* 전송 없이 끝났습니다 */ }
        is VultusMeasureOutcome.Failed -> { /* outcome.reason · outcome.progress */ }
        is VultusMeasureOutcome.Refused -> { /* 시작하지 않았습니다 — outcome.error */ }
    }
}

session.stopMeasure()    // 지금까지 모은 것으로 끝냅니다
session.cancelMeasure()  // 버립니다 — 전송하지 않습니다
```

`cancelMeasure()` 는 측정 취소입니다 — 결말 전이면 어느 단계에서든 측정을 버리고 `IDLE` 로 돌아갑니다. 코어 측정 객체를 만들고 라이선스를 확인하는 동안(`STARTING` 의 첫머리)은 끊지 않고, 그것이 끝나는 대로 버립니다. 보내는 중(`SUBMITTING`)이면 전송을 끊고 `Canceled` 로 끝냅니다. `startMeasure` 를 부른 코루틴을 취소해도 전송은 끊기지만, 그때는 결말이 오지 않습니다. 예외가 하나 있습니다 — 보내는 중 서버가 세션을 다시 요구하면(HTTP 401) SDK 가 라이선스를 다시 확인하는데(약 5 초), 이때 취소하면 상태는 곧바로 `CANCELED` 가 되고, 그 확인이 끝나면 다시 보내지 않고 `Canceled` 결말과 함께 `IDLE` 로 갑니다. 마지막 라이선스 확인 뒤 서버에서 라이선스(권한 등)가 바뀐 드문 경우입니다.

| 결말 | 뜻 |
| :--- | :--- |
| `Finished(responseJson)` | 전송 단계를 끝냈습니다. `responseJson` 은 서버에 닿았으면 서버 응답, 보내지 못했거나 응답을 믿을 수 없으면 SDK 가 만든 오류 JSON 입니다 — 형태와 `error_code` 는 [10.5](#105-오류-응답-json-스키마) |
| `Canceled` | 버렸습니다 — `cancelMeasure()`(보내는 중이면 전송을 끊음) · 수집 중 세션 닫기 · `beforeSubmit` 이 `null` 을 돌려준 경우 |
| `Failed(reason, progress)` | 측정이 결과를 낼 수 없어 끝났습니다. `progress` 는 끝난 측정의 마지막 진행입니다 |
| `Refused(error)` | 시작하지 않았습니다 — 사유는 `error` 입니다 |

`VultusMeasureFailureReason` 은 앞으로 늘어날 수 있으니 `outcome.reason` 을 나눌 때는 `else` 로 모르는 값을 받으세요.

**전송 직전 훅 — `beforeSubmit`.** 측정 뒤에야 정해지는 값(업로드 동의 등)은 `startMeasure(beforeSubmit = { … })` 로 넣습니다. 전송 직전에 한 번 불리고, 돌려준 맵은 `appendix` 에 합쳐집니다(키가 겹치면 이쪽이 이깁니다). `null` 을 돌려주면 보내지 않고 `Canceled` 로 끝납니다. 이 훅이 돌아올 때까지 측정이 끝나지 않습니다. 다만 그사이 측정을 버리면(`cancelMeasure()` · 부른 코루틴의 취소) SDK 는 훅을 기다리지 않고 곧바로 `Canceled` 로 끝내고, 훅의 코루틴에 취소를 건넵니다(그 뒤 돌려준 값은 버립니다) — 훅에서 띄운 동의 창은 `Canceled` 를 받으면 닫으세요.

> [!IMPORTANT]
> **`analysisWindow` 는 서버가 분석할 길이입니다.** 서버는 받은 측정에서 정확히 이 길이(유효 초)의 구간 하나를 골라 HRV 를 계산합니다 — 세션마다 분석 길이가 같아야 SDNN 처럼 길이에 따라 달라지는 지표를 비교할 수 있습니다. 기본 60, 20 초 이상이고 상한은 174 초입니다(서버 설정에 따라 더 길 수 있습니다). 범위 밖이면 시작이 `UNSUPPORTED_VALUE` 로 거절됩니다.
>
> **창을 채우면 SDK 가 스스로 멈춥니다**(`extraSeconds` 기본 0). 앱이 타이머를 들 필요가 없습니다.
>
> | `extraSeconds` | 동작 |
> | :--- | :--- |
> | `0`(기본) | 창을 채우는 즉시 멈춥니다 |
> | `N` | 창을 채운 뒤 유효 신호 N 초를 더 모으고 멈춥니다 — 서버가 그 여유 안에서 분석할 구간을 고릅니다 |
> | `null` | 자동으로 멈추지 않습니다 — 앱이 `stopMeasure()` 로 멈춥니다 |
>
> 어느 경우든 **측정 시작부터 정해진 시간(기본 180 초)에서 SDK 가 멈추고 모은 것을 보냅니다**(하드 안전 종료 — 한 측정을 한 요청으로 보내는 전송 한계입니다). 이 시간은 서버 설정에 따라 더 길 수 있고, 받을 수 있는 분석 창(기본 상한 174 초)을 채우기에 늘 충분합니다.
>
> 길이는 **벽시계가 아니라 유효 신호가 모인 시간**으로 셉니다. 얼굴을 놓친 구간은 진행에 들어가지 않습니다. **측정은 분석 창보다 몇 초 더 걸립니다** — 실제로 모으는 길이는 `progress` 의 `targetSeconds` 입니다.
>
> 결과의 `analysis.analyzedSeconds` 는 박동이 실제로 걸친 길이입니다. 신호가 깨끗하면 `analysisWindow` 와 같고, 신호가 나쁜 구간이 있으면 그만큼 짧게 나옵니다. 창을 담을 만큼 이어진 구간이 없었으면 `analysis.windowSelection` 이 `longestSegment`(가장 긴 구간 전체)로 옵니다.

> [!NOTE]
> **`FULFILLED` 는 「창을 채웠다」이지 「멈췄다」가 아닙니다.** 여기서 멈춰도 요청한 길이의 분석이 나오므로 강한 안내를 거둘 수 있는 지점입니다. 수집은 `targetSeconds` 까지 이어지므로 `progress` 의 `collectedSeconds` 는 `analysisWindow` 를 **넘습니다** — 진행바는 `percent`(수집 ÷ `targetSeconds`)로 그리세요. `targetSeconds` 는 SDK 가 스스로 멈추는 길이이고, `elapsedSeconds` 는 벽시계 시간입니다(진행은 측정 프레임을 처리할 때마다 갱신되므로 얼굴을 놓친 동안은 멈춰 있다가 따라잡습니다).

> [!NOTE]
> **HRV 가 비어 오면 `analysis.hrvUnavailable` 이 이유를 말합니다.** 서버가 박동을 세우지 못하면 `hrv` 의 세 블록이 빈 채로 정상 응답이 옵니다 — 오류가 아니라 「계산하지 못했다」는 결과입니다. 블록이 차 있으면 이 값은 `null` 입니다.
>
> | 값 | 뜻 | 안내 예 |
> | :--- | :--- | :--- |
> | `tooShort` | 분석할 수 있는 길이가 20 초에 못 미쳤습니다 | 측정이 너무 짧습니다 |
> | `noContinuousSegment` | 길이는 됐지만 20 초 넘게 이어진 구간이 없었습니다 | 얼굴이 자주 화면을 벗어났습니다 |
> | `noPulse` | 이어진 신호는 있었는데 맥박을 찾지 못했습니다 | 조명과 자세를 확인해 주세요 |
>
> 상수는 `VultusAnalysisResult.Report.HRV_TOO_SHORT` 등입니다. **값은 앞으로 늘어날 수 있으니** 모르는 값은 일반 안내로 받으세요.
>
> 측정 **중에** 앵커(연속 10 초)가 끝내 서지 않은 것은 이것과 다른 자리에서 알립니다 — `submitUnfulfilledMeasurement = false` 면 `Failed(MEASUREMENT_TOO_SHORT)` 로 전송 없이 끝나고, 그때 `outcome.progress.state` 가 `ANCHORING` 입니다. 보냈다면 서버가 `tooShort` · `noContinuousSegment` 로 말합니다.

> [!NOTE]
> **`Failed` 의 `progress` 는 끝난 측정의 마지막 진행입니다.** `Failed(MEASUREMENT_TOO_SHORT)` 에서 그 `state` 가 `ANCHORING` 이면 연속 10초(앵커)가 끝내 서지 않은 것이고, `ANCHORED` 면 앵커는 섰지만 목표 전에 멈춘 것입니다. 사용자에게 줄 안내가 다릅니다(얼굴을 화면 안에 두라 · 끝까지 기다리라).
>
> **`startMeasure` 는 측정을 정리한 뒤에 돌아옵니다** — 결말을 받은 자리에서 곧바로 다음 측정을 시작해도 됩니다(공개 `state` 는 메인 스레드에서 갱신되므로, 메인 밖에서 불렀다면 돌아온 직후 잠깐 앞 값으로 보일 수 있습니다). 다른 곳(버튼 등)에서 시작한다면 `state` 가 `IDLE` 일 때만 시작하세요.
>
> **결과가 돌아오기 전에 `startMeasure` 를 부른 코루틴이 취소되면 서버 전송도 취소되며 분석 결과를 받을 수 없습니다.** 결과가 필요한 앱은 화면을 닫아도 취소되지 않는 범위에서 부르세요(`lifecycleScope` · `viewModelScope` 는 화면을 닫으면 취소됩니다).

시작이 거절되면 `startMeasure` 는 측정 없이 `Refused(error)` 를 돌려줍니다. 진행률은 `progress` 로 받습니다(§6) — 앱이 벽시계로 세면 얼굴을 놓친 구간까지 진행으로 쳐서, 서버가 계산하지 못하는 측정을 "다 모았다" 고 표시하게 됩니다.

**시작 방식 — 대화형 · 비대화형.** `startMeasure` 의 `mode` 가 측정을 언제 시작하는지를 정합니다. **시작한 뒤의 측정은 둘이 같습니다** — 시작 순간 자동 맞춤을 멈추고 걸려 있던 줌이 닿으면 노출 · 화이트밸런스를 잠그고 수집합니다.

| | `VultusStartMode.Interactive`(기본) | `VultusStartMode.NonInteractive(timeoutSeconds, countdownSeconds)` |
| :--- | :--- | :--- |
| 쓰는 곳 | 사용자가 시작 버튼을 누른다 | 버튼 없이 시작한다(화면이 열리자마자 등) |
| 시작 순간 | 누른 순간 | 얼굴이 맞춰지고 줌이 닿고 사람이 멈추고 카메라 노출이 자리 잡은 뒤 카운트다운을 다 센 순간 |
| 맞춰지지 않으면 | 그대로 시작한다(얼굴 기준 방향 맞춤은 예외 — [7.2](#72-좌표-변환)) | 제한 시간 안에 카운트다운이 시작되지 않으면 시작하지 않는다(`Refused(FRAMING_FAILED)`) |

```kotlin
// 화면이 열리자마자 — 맞춰지고 카운트다운(기본 3 초)을 센 뒤 시작합니다. 제한 시간(기본 10 초) 안에 맞춰지지 않으면 Refused(FRAMING_FAILED) 입니다.
// 화면을 떠나면 코루틴과 함께 측정도 취소됩니다
lifecycleScope.launch {
    val outcome = session.startMeasure(analysisWindow = 60, mode = VultusStartMode.NonInteractive())
    /* outcome 을 위와 같이 나눕니다 */
}
```

- `timeoutSeconds` 는 첫 카운트다운이 시작되기까지 기다릴 최대 시간입니다. 주지 않으면 기본 10 초, `0` 이면 무제한, 상한은 10 초입니다.
- `countdownSeconds` 는 맞춰진 뒤 시작까지 셀 시간입니다. 주지 않으면 기본 3 초, `0` 이면 맞춰지는 즉시 시작, 상한은 5 초입니다.
- 주지 않은 값의 기본은 서버 설정에 따라 다를 수 있습니다 — 정해진 시간이 필요하면 값을 넘기세요.
- 음수이거나 상한을 넘기면 `UNSUPPORTED_VALUE` 로 거절합니다.
- **카운트다운은 `framing` 에 `Countdown(secondsLeft)` 로 1 초마다 옵니다.** 세는 중 맞춤이 깨지면(움직였거나 얼굴을 놓침) 다른 상태로 바뀌고, 다시 맞춰지면 처음부터 셉니다. 제한 시간이 지난 뒤 깨지면 그때 `FRAMING_FAILED` 로 거절합니다.
- 맞춤을 기다리는 동안은 라이선스 확인 · 맞춤과 겹쳐 진행되고(`alwaysDetect` 를 끈 세션은 라이선스 확인 뒤에 맞춤을 시작합니다 — 제한 시간도 그때부터 잽니다), 이 시간은 측정 길이에 들어가지 않습니다(수집은 시작 순간부터 합니다).
- 자동 맞춤을 끈 Custom 세션(`overrideAutoFraming(false)`)에는 맞출 것이 없어, 비대화형도 곧바로 시작합니다.
- `alwaysDetect` 를 켜지 않은 세션도 비대화형 시작이 맞춤을 기다리는 동안에는 SDK 가 얼굴 검출을 돌립니다.
- 시작 방식과 상관없이, 외장 카메라 어댑터가 3A 를 잠그는 「카메라 조정 중」 에도 화면 색을 재려고 검출이 돕니다. 그 밖의 측정 전에는 `alwaysDetect` 를 켜지 않으면 돌지 않습니다.
- **줌이 걸리는 중이면 다 걸릴 때까지 기다립니다.** 줌은 한 번에 걸지 않고 짧게 나눠 겁니다. 시작 순간 걸리던 줌이 다 걸린 뒤에 수집을 시작합니다(카메라가 줌에 닿지 않으면 잠시 기다린 뒤 시작합니다). 그래도 닿지 않은 채 시작했으면 시작 뒤에 닿을 때까지 줌이 마저 움직입니다.
- **측정 중 `framing` 은 거리 요청만 냅니다** — 멀면 `Limited(TOO_FAR)`, 가까우면 `Limited(TOO_CLOSE)`, 문제가 없으면 `Framed` 입니다. 그 뒤로 `Adjusting` · `OFF_CENTER` 를 새로 내지 않고, 맞춤이 멈춘 순간의 상태는 다음 거리 판정까지 남습니다.


**스레드 — 무엇이 어디서 도는가.** 측정은 SDK 가 백그라운드에서 돌리고, 앱이 받는 결말은 메인으로 옵니다. 아래와 다르게 가정하지 마세요.

| 동작 | 스레드 | 권고 |
| :--- | :--- | :--- |
| `startMeasure(...)` 호출 | **어느 디스패처든** | `lifecycleScope.launch { }` 그대로 부르세요. `Dispatchers.Default` 로 옮겨 부를 필요가 없습니다. 측정이 끝날 때까지 돌아오지 않고, 결말(`VultusMeasureOutcome`)은 부른 디스패처로 옵니다 — `lifecycleScope` 면 메인이라 화면을 바로 갱신해도 됩니다. 결과 파싱 · 저장처럼 오래 걸리는 일은 다른 디스패처로 넘기세요 — 메인을 막으면 화면이 멈춥니다 |
| 측정 본체(프레임 처리) | SDK 백그라운드(`Dispatchers.Default`) | 앱이 스레드를 따로 잡지 않습니다 |
| 라이선스 확인 · 네이티브 준비 · 결과 전송 | SDK I/O(`Dispatchers.IO`) | — |
| `beforeSubmit` 훅 | **메인** | 동의 창 같은 화면을 띄울 수 있습니다. 네트워크 · 디스크처럼 막는 호출은 훅 안에서 `withContext(Dispatchers.IO)` 로 감싸세요 |
| 세션의 `StateFlow`(`snapshot` · `state` · `progress` · `displayBpm` · `guidance` · `emotion` · `signalTrace` · `detectedFace` · `framingAdvice` …) | **메인에서 갱신** | SDK 는 값을 늘 메인의 다음 차례에 냅니다 — 수집 안에서 `close()` 같은 입구를 불러도 됩니다. `.value` 는 어느 스레드에서 읽어도 되고, 연달아 바뀌면 마지막 값만 올 수 있습니다 |
| `Vultus.openDefaultSession` · Default 촬영 override · `setFaceGuide` | **메인**(`@MainThread`) | 메인 밖이면 열기 · override 는 `CAMERA_UNAVAILABLE` 로 거절합니다 |
| Custom 길 `attachPreview` | **메인**(`@MainThread`) | 거절하지 않고 뷰 · 화면 수명을 만지므로 메인 밖에서는 예외가 날 수 있습니다 |
| `Vultus.openCustomSession` · 세션 `close()` | 어느 스레드든 | `close()` 는 돌아오면 닫힌 세션입니다 — Default 의 카메라 바인딩만 메인으로 넘겨 놓습니다 |
| `stopMeasure` · `cancelMeasure` | 어느 스레드든 | — |
| Custom 길 `session.imageAnalyzer` | **`session.analysisExecutor`** 를 `setAnalyzer` 에 넘기세요 | 세션의 프레임 줄이라 프레임이 사본 없이 처리되고 `KEEP_ONLY_LATEST` 가 그대로 배압이 됩니다. 앱의 executor 를 넘겨도 되지만 SDK 가 프레임마다 사본을 떠 줄로 넘기고, 줄이 밀리면 프레임을 버립니다(측정 중이면 간극으로 기록). 메인 executor 는 넘기지 마세요 — 사본 뜨기가 화면을 멈춥니다. 세션을 새로 열면 새 `analysisExecutor` 를 넘깁니다 |

---

## 6. 상태 구독

상태는 세션이 냅니다. **`session.snapshot`**(`StateFlow<VultusSnapshot>`)은 한 시점의 세션 상태 — `state` · `progress` · `signalClarity` · `stability` · `captureCondition` · `guidance` · `displayBpm` · `framing` · `effectiveOrientation` · `externalCameraStatus` — 를 한 값으로 묶은 작은 불변 값이고, **필드 하나라도 바뀔 때만** 새 값이 흐릅니다. 여러 값을 함께 보는 판단도 같은 순간의 값으로 하게 됩니다. 선언형 UI(Compose)는 이것 하나를 구독하세요. 세션을 열기 전에는 초기값 `VultusSnapshot()` 으로 그리면 됩니다.

필드마다 같은 이름의 투영(`session.state` · `session.progress` · `session.displayBpm` …)도 `StateFlow` 로 있습니다. 그 필드가 바뀔 때만 흐르므로 위젯 단위로 갱신하는 명령형(View) UI 에 씁니다. 신호 그래프(`signalTrace`) · 검출된 얼굴(`detectedFace`) · 표정(`emotion`)은 크거나 자주 바뀌어 스냅숏에 넣지 않았습니다 — 필요한 곳에서만 따로 구독하세요. 그래프 · 얼굴은 측정 중 프레임마다(30Hz) 바뀌므로 화면 최상위에서 받으면 화면 전체가 그만큼 다시 구성됩니다. 그리는 컴포저블 안에서 받으세요(샘플의 `MeasureSignalBox`).

```kotlin
// 선언형(Compose) — 스냅숏 하나를 구독합니다
val snapshot by session.snapshot.collectAsState()
Text("${snapshot.state} · ${snapshot.progress.percent}% · ${snapshot.displayBpm.bpm} bpm")

// 크거나 빠른 값은 그리는 컴포저블 안에서 따로 구독합니다
@Composable
fun SignalGraph(signalTrace: StateFlow<FloatArray>) {
    val trace = signalTrace.collectAsState()          // 그래프용 파형
    Canvas(Modifier.fillMaxWidth().height(80.dp)) {
        val samples = trace.value                     // 그리는 자리에서 읽으면 다시 그리기만 일어납니다
        // …
    }
}

// 명령형(View) — 필요한 필드의 투영만
lifecycleScope.launch {
    session.displayBpm.collect { bpmView.text = it.bpm.toString() }
}
```

**`VultusMeasureState`** 는 `IDLE → STARTING → ADJUSTING_CAMERA → STARTED → STOPPED → FINISHED → SUBMITTING → IDLE` 로 흐릅니다.

| 상태 | 뜻 | 측정 버튼 |
| :--- | :--- | :--- |
| `IDLE` | 측정이 없다. **다음 측정은 여기서만 시작합니다** | 시작 |
| `STARTING` | 시작이 라이선스 확인 · 준비 · 걸리던 줌 · 비대화형 시작의 맞춤과 카운트다운을 기다린다(콜드 스타트면 수 초). 거절되면 `IDLE` 로 돌아갑니다 | 막아 두기(「준비 중」) |
| `ADJUSTING_CAMERA` | 측정을 시작하기 전에 카메라를 고정합니다 — 초점 → 노출 → 화이트밸런스 순서로, 각각 자리를 잡은 뒤 잠급니다. 3A 락이 꺼져 있으면 거치지 않습니다 | 막아 두기(「카메라 조정 중」) |
| `STARTED` | 프레임을 모은다 | 멈춤 |
| `STOPPED` | 새 프레임은 받지 않고, 멈추기 전에 받은 프레임을 모두 처리한다 | 막아 두기 |
| `FINISHED` | 보낼 결과를 만든다(`beforeSubmit` 포함) | 막아 두기 |
| `SUBMITTING` | 결과를 서버로 보내고 응답을 기다린다. 응답이 오면 `IDLE` 로 갑니다. 전송하지 않고 끝나면 거치지 않습니다 | 막아 두기(「결과 전송 중」) |
| `CANCELED` | 버리고 정리한다. 결말 전 어느 단계에서든(보내는 중 `SUBMITTING` 포함) 옵니다 | 막아 두기 |

`STARTING` · `ADJUSTING_CAMERA` 에서 `stopMeasure()` 를 부르면 모인 것이 없으므로 `cancelMeasure()` 와 같게 버립니다. 카메라는 측정 구간이 시작되기 **전에** 고정되므로 고정하는 동안의 변화가 측정에 섞이지 않습니다. 상태를 `when` 으로 빠짐없이 나누는 앱은 `ADJUSTING_CAMERA` · `SUBMITTING` 도 다뤄야 합니다(각각 「시작 중」 · 「전송 중」 과 같이 막아 두면 됩니다).

---

## 7. 프리뷰와 오버레이

### 7.1 `VultusPreviewView`

프리뷰 표시는 SDK 뷰가 맡습니다. 표시 미러 · 표시 회전 · 종횡비 · 얼굴 가이드가 이 안에서 끝납니다.

```kotlin
preview.fill = VultusPreviewFill.FILL       // 덮기 / FIT = 레터박스
preview.guideOverlayEnabled = true          // 얼굴 가이드 점선
preview.dimsOutsideTargetRegion = false     // 가이드 밖을 어둡게
```

기기에 따라 표시 축이 어긋나면 통합자가 못 박습니다. **표시 전용이고 검출 좌표를 바꾸지 않습니다.**

```kotlin
preview.overridePreviewRotation(270)        // null 이면 장치 값
preview.overrideStreamMirrored(true)        // CameraX 가 미러 여부를 모르는 외장 카메라용 — [8.1](#81-카메라-선택과-표시--extcamconfiguration)
```

### 7.2 좌표 변환

검출 좌표(`VultusDetectedFace.boundingBox` · 랜드마크)는 **회전 보정된 분석 프레임의 픽셀 좌표**입니다. 화면에 얹으려면 배율 · 오프셋 · 미러를 맞춰야 하는데, 그 값을 **프리뷰 뷰가 알려 줍니다.**

```kotlin
preview.onOverlayTransformChanged = { transform ->
    // 뷰 크기 · 미러 · 프레임 크기가 달라질 때마다 옵니다.
    overlayTransform = transform
}

// 그릴 때
val box = overlayTransform.rect(face.boundingBox)
val point = overlayTransform.point(landmark.x, landmark.y)
```

> [!CAUTION]
> **미러를 앱이 정하지 말고 받으세요.** 스트림이 이미 뒤집혀 오는지를 CameraX 가 **나중에** 알려주기 때문에, 뷰를 붙이자마자 읽으면 아직 모르는 값을 읽습니다. `onOverlayMirroredChanged` 나 위 변환을 쓰면 그 시점 문제가 사라집니다.

오버레이를 다른 뷰에 그린다면 **그 뷰의 크기가 프리뷰 뷰와 같아야 합니다.** 다르면 배율과 오프셋이 갈려 조용히 어긋납니다.

**방향은 정하는 방식을 고릅니다 — 대부분의 앱은 아무것도 부르지 않습니다.** 분석 프레임은 얼굴이 바로 서는 좌표계로 세워야 하고, 그 좌표계를 무엇으로 정할지가 `overrideOrientationPolicy` 입니다.

| 대상 | 부르는 것 | 방향을 정하는 것 |
|---|---|---|
| 범용 기기(스마트폰 · 태블릿) | 없음(`VultusOrientationPolicy.Default`) | 카메라 · 화면이 신고한 값(카메라 장착 + 화면 회전) |
| 신고가 맞지 않는 전용 기기 · 외장 카메라 | `VultusOrientationPolicy.FaceProbe` | 얼굴 — 얼굴이 바로 서는 회전을 프레임에서 찾습니다 |
| 〃 | `VultusOrientationPolicy.Fixed` | 앱이 못 박은 장착 · 화면 방향 |

- **기본(`VultusOrientationPolicy.Default`)** — 측정 밖에서는 화면 회전을 프레임마다 따릅니다. 가로 · 세로 UI 를 모두 내는 앱도 저절로 맞고, 화면을 돌려도 세션을 다시 열 필요가 없습니다. UI 를 한 방향으로 고정한 앱에서 기기를 눕히면 프레임 속 얼굴도 누워 검출되지 않습니다 — 설계가 그렇습니다. UI 방향대로 들고 측정하세요.
- **얼굴 기준(`VultusOrientationPolicy.FaceProbe`)** — 얼굴이 바로 서는 회전을 프레임에서 찾아 맞춥니다. 찾을 때까지 주기적으로 다시 봅니다(시도마다 프레임 처리가 잠시 멈춥니다). 맞추기 전에는 `effectiveOrientation` · `detectedFace` 가 `null` 이고, 측정 시작은 맞추기를 기다립니다 — 상한(비대화형은 시작 관문의 제한 시간, 대화형은 그 기본값)을 넘기면 `FRAMING_FAILED` 로 거절합니다. 화면이 돌면 다시 맞춥니다. 좌우 반전은 얼굴로 알 수 없으니, 프레임이 반전으로 들어오는 카메라는 `VultusOrientationPolicy.Fixed` 를 쓰세요.
- **고정(`VultusOrientationPolicy.Fixed`)** — 장착(`VultusCameraMounting` — 각도 · 좌우 반전)과 화면 방향을 못 박습니다. `null` 인 것은 신고한 값을 씁니다 — 화면 방향을 OS 로 고정한 기기는 장착만 넘기면 됩니다. 분석 좌표계만 고칩니다 — 프리뷰는 플랫폼이 그리므로 표시 회전은 따로 맞춥니다(`VultusPreviewView.overridePreviewRotation`).

> [!TIP]
> **고정 거치 기기는 `VultusOrientationPolicy.FaceProbe` 로 얻은 값으로 `VultusOrientationPolicy.Fixed` 로 바꾸기를 권합니다.** `VultusOrientationPolicy.FaceProbe` 는 세션마다 얼굴이 보여야 방향이 정해지고, 그때까지 측정을 시작할 수 없으며, 시도마다 검출 비용이 커 사용성이 떨어질 수 있습니다. 한 번 `VultusOrientationPolicy.FaceProbe` 로 돌려 `effectiveOrientation.cameraMounting`(얼굴로 맞춘 회전을 내는 장착 방향)을 얻고, 그 값을 `VultusOrientationPolicy.Fixed` 에 하드코딩하세요. 그 장착은 **탐색할 때의 화면 방향을 기준으로** 낸 값입니다 — 장착만 넘기고 화면 방향은 신고 값에 맡기세요(화면 방향까지 못 박는다면 탐색할 때와 같은 방향이어야 합니다). 프리뷰를 세우려고 준 표시 회전(`overridePreviewRotation` · `extCamConfiguration` 의 회전)을 장착으로 옮기지 마세요 — 다른 축입니다.

범용 기기와 전용 기기를 함께 대상으로 하는 앱은 기기를 판별해(모델명 등 — 기준은 앱이 정합니다) 전용 기기에서만 부르세요. 같은 모델에 카메라 배치가 여럿이라 모델로 장착이 갈리지 않는 기기는 `VultusOrientationPolicy.FaceProbe` 를 쓰세요.

```kotlin
// 범용 앱이 전용 키오스크에서도 돈다 — 기기를 판별하는 기준(isOurKiosk)은 앱이 정합니다.
if (isOurKiosk()) {
    // 처음 한 번은 FaceProbe 로 돌려 effectiveOrientation.cameraMounting 을 얻고, 그 값을 하드코딩합니다.
    // 화면 방향은 넘기지 않습니다(신고 값) — 얻은 장착이 그 화면 방향을 기준으로 한 값입니다.
    session.overrideOrientationPolicy(
        VultusOrientationPolicy.Fixed(mounting = VultusCameraMounting(degrees = 270)),
    )
}
```

세션을 연 직후, 측정을 시작하기 전에 고르세요. 시작을 기다리는 동안부터 남은 프레임을 다 처리할 때까지는 거절하고(`NOT_ALLOWED_WHILE_MEASURING`), 바꾸면 방향을 처음부터 다시 정합니다 — 그동안 `effectiveOrientation` 은 `null` 입니다(측정이 끝난 뒤 · 전송 전 훅 동안 고른 것은 측정 정리 때 반영됩니다).

**측정 중에는 방향이 바뀌지 않습니다.** 측정이 끝날 때까지 방향을 얼리므로 측정 중에 기기 · 화면을 돌려도 분석 방향은 그대로입니다. 기본 · 고정은 시작이 방향을 기다리지 않습니다 — 아직 정하지 못했으면(첫 프레임 전) 측정의 첫 프레임에서 정하고, 그때는 `effectiveOrientation` 이 측정 중에 한 번 값을 얻지만 측정의 좌표계는 처음부터 하나입니다.

분석이 화면과 다르게 섰으면(얼굴로 맞췄거나 화면 방향을 못 박았을 때) 분석 프레임과 프리뷰의 방향이 달라지는데, 위 변환은 그 차이를 이미 반영합니다. 변환을 직접 만든다면 `effectiveOrientation` 의 `overlayRotationDegrees` 를 넘기세요(정해진 방향만 냅니다 — 정하기 전 · 다시 정하는 동안은 `null`).

```kotlin
val transform = VultusPreviewTransform.of(
    imageWidth, imageHeight, viewWidth, viewHeight,
    rotationDegrees = orientation.overlayRotationDegrees,
)
```

Custom 길에서 `VultusPreviewView` 를 쓴다면 세션의 방향을 뷰에 넘겨야 변환이 맞습니다.

```kotlin
session.effectiveOrientation.collect { preview.setEffectiveOrientation(it) }  // 메인 스레드에서
```

> [!IMPORTANT]
> **방향은 측정 동안만 고정됩니다.** 측정 밖에서는 기본 방식이 화면 회전을 따르므로 UI 가 돌면 다음 프레임부터 새 방향입니다 — 세션을 다시 열 필요가 없습니다. 방향이 정해지기 전(첫 프레임 전, 얼굴 기준이면 맞추기 전)에는 `detectedFace` 가 `null` 입니다.

### 7.3 얼굴 가이드

얼굴을 보일 자리는 가이드 하나로 정합니다 — Default 길은 `session.setFaceGuide`, Custom 길은 `preview.faceGuide` 입니다([4.5](#45-자동-맞춤--사용자는-편한-자리에-카메라가-맞춥니다)). 자동 맞춤은 그리기와 상관없이 이 자리에 얼굴을 얹습니다.

가이드를 SDK 가 그릴지는 앱이 정합니다. 직접 그리려면 끄고, 뷰 좌표의 가이드 사각형을 읽어 쓰세요.

```kotlin
preview.guideOverlayEnabled = false
val rect = preview.faceGuideRect   // 뷰 좌표 — 변환 없이 그대로 그립니다
```

### 7.4 맞춤 crosshair

자동 맞춤이 도는 동안(측정 전) 얼굴이 **렌즈 중심**에서 벗어나면 표시 둘을 띄울 수 있습니다 — 얼굴을 옮길 **목적지점**(렌즈 중심, 분석 프레임의 가운데)과 **현재 지점**(얼굴 중심)입니다. 렌즈 중심 가까이에서는 숨고, 프리뷰가 얼굴을 따라가는 한계에 가까워질 만큼 벗어났을 때만 보입니다(경계에서 깜빡이지 않게 사라지는 데는 여유가 있습니다). 프리뷰가 얼굴을 따라 옮겨지면 두 표시도 함께 옮겨집니다. 측정이 시작돼 맞춤이 멈추면(대화형 시작을 누른 순간 · 비대화형 카운트다운이 끝난 순간) 숨고, 얼굴이 없거나 `guideOverlayEnabled` 가 꺼져 있어도 숨습니다.

```kotlin
preview.showsFramingCrosshair = true                    // 기본은 끔
preview.framingTargetCrosshairView = myTargetView       // 목적지점(렌즈 중심) 모양 — null 이면 기본 +
preview.framingCurrentCrosshairView = myCurrentView     // 현재 지점(얼굴 중심) 모양 — null 이면 기본 ×
// Compose 모양: ComposeView(context).apply { layoutParams = FrameLayout.LayoutParams(sizePx, sizePx); setContent { ... } }
```

**모양은 앱이 정합니다.** SDK 는 넘긴 뷰의 중심을 그 점에 맞추고 보이거나 숨기기만 합니다. 크기는 넘긴 뷰의 `layoutParams` 를 따릅니다(없으면 `WRAP_CONTENT`). 넘긴 뷰는 프리뷰 뷰의 하위 뷰로 옮겨 붙습니다 — 터치를 받지 않는 뷰를 넘기세요.

---

### 7.5 검출된 얼굴 (`detectedFace`)

얼굴 검출은 **ML Kit Face Detection** 으로 SDK 안에서 처리됩니다(교체할 수 없습니다). 결과는 `session.detectedFace`(`VultusDetectedFace?`)로 흐릅니다.

| 필드 | 뜻 |
| :--- | :--- |
| `boundingBox` · `landmarks`(8 점) · `imageWidth` · `imageHeight` | SDK 가 측정 규격으로 맞춘 값 — 회전 보정된 분석 프레임의 픽셀 좌표(원점 왼쪽 위, 미러 없음). 상자는 프레임 안으로 자른 값입니다. **두 플랫폼이 같은 규격입니다** |
| `faceInfo` | ML Kit 의 검출 결과 원본(`com.google.mlkit.vision.face.Face`) — 머리 각(`headEulerAngleX/Y/Z`, 도) · 랜드마크 · 윤곽 등 |

**`faceInfo` 는 일부러 열어 둔 통로입니다.** 얼굴 검출은 비싼 연산이라, SDK 가 쓰고 버리면 다른 용도가 필요한 앱이 같은 프레임에 검출을 다시 돌려야 합니다. 원본을 그대로 넘기고, SDK 는 측정에 쓸 값만 따로 복사해 둡니다 — 앱이 원본을 어떻게 다뤄도 측정은 영향받지 않습니다.

#### 두 플랫폼의 검출 결과 비교 (Android · iOS 공통)

Android 와 iOS 는 얼굴 검출기가 다릅니다 — Android 는 ML Kit, iOS 는 Vision 입니다(둘 다 SDK 안에 고정). SDK 가 두 플랫폼에서 같은 규격으로 맞춰 주는 값은 `boundingBox` · `landmarks`(8 점) · `imageWidth` · `imageHeight` 뿐이고, 검출 결과 원본과 머리 각은 각 검출기의 값입니다. **두 플랫폼의 값을 함께 모으거나 비교하는 앱은 아래 차이를 직접 맞추세요.**

| | Android — `faceInfo`(ML Kit `Face`) | iOS — `faceObservation`(Vision `VNFaceObservation`) |
| :--- | :--- | :--- |
| 좌표 | 회전 보정된 분석 프레임의 픽셀, 원점 왼쪽 위 | 0~1 정규화, 원점 **왼쪽 아래** — 상자는 회전 보정된 분석 프레임에 대해, 랜드마크 점은 **얼굴 상자에 대해** 정규화(`pointsInImage(imageSize:)` 에 `imageWidth` · `imageHeight` 를 넘겨 프레임 좌표로 옮긴다 — 원점은 여전히 왼쪽 아래) |
| 상자 | ML Kit 의 상자 — SDK `boundingBox` 는 이것을 프레임 안으로 자른 것(얼굴이 프레임 가장자리에 걸치면 더 작다) | Vision 의 상자 — SDK `boundingBox`(측정 규격에 맞춘 상자)와 **다른 상자** |
| 랜드마크 | 점 몇 개(`detectContour` 를 켜면 윤곽선도) | 영역별 점 묶음(`VNFaceLandmarks2D`) |
| 머리 각 | `faceInfo.headEulerAngleX/Y/Z` — 도. **pitch 는 고개를 들면 양수** | `headEulerAngleX/Y/Z` — 도(원본은 라디안). **pitch 는 고개를 들면 음수** |
| 머리 각의 크기 | 같은 자세라도 두 검출기의 각도 크기가 다릅니다(축마다 다름) — 부호만 뒤집어서는 맞지 않습니다 | 〃 |
| 검출기 옵션 | `Initializer` 의 `minFaceSize` · `useAccurateMode` · `detectContour` | 없음 |

얼굴이 너무 멀거나 가깝거나 화면 밖에 걸렸는지는 상자의 크기 · 위치로 직접 판단하지 말고 SDK 의 촬영 조건(`captureCondition`)으로 보세요 — 두 플랫폼이 같은 기준으로 냅니다.

---

## 8. 외장(USB) 카메라 — 거치형 기기

키오스크처럼 USB(UVC) 웹캠을 단 기기에서 필요한 것을 이 장에 모았습니다. 휴대폰의 내장 카메라만 쓴다면 건너뛰어도 됩니다.

### 8.1 카메라 선택과 표시 — `extCamConfiguration`

CameraX 는 외장 카메라의 축도 미러 여부도 알려 주지 못합니다(언제나 회전 0 · 미러 아님으로 보고됩니다). 그래서 **기기에 단 방향을 통합자가 초기화 때 적습니다.**

```kotlin
Vultus.Initializer(licenseCode, holderKey, clientKey)
    .extCamConfiguration(Vultus.ExtCamConfiguration(rotationDegrees = 270, useMirrorMode = true))
    .initialize(context, lifecycleOwner)
```

- `rotationDegrees` 는 외장 카메라 영상을 화면에 세우는 **표시 회전 각도**(`0` · `90` · `180` · `270`)입니다. ⚠️ `Surface.ROTATION_*` 상수는 사분면 인덱스이므로 넘기지 마세요. 90 의 배수가 아닌 값은 오류 로그를 남기고 `0` 으로 둡니다.
- `useMirrorMode` 는 거울처럼 좌우를 뒤집어 보일지입니다. **표시 전용이고 검출 좌표를 바꾸지 않습니다.**
- 적은 값은 측정에 함께 기록됩니다.
- **적으면 외장 카메라를 우선합니다.** Default 길에서 `selection` 을 생략하면 `PREFER_EXTERNAL` — 외장 카메라가 있으면 그것을, 없으면 전면을 엽니다. 키오스크와 폰을 같은 코드로 다룰 수 있습니다([4.2](#42-default-길--vultusopendefaultsession)).
- **Default 길은 SDK 가 이 값을 프리뷰 뷰에 겁니다.** Custom 길은 카메라를 앱이 쥐므로 앱이 프리뷰 뷰에 같은 값을 겁니다. 프리뷰 use case 에는 `targetRotation` 을 걸지 마세요 — 회전은 프리뷰 뷰가 한 곳에서 정합니다.

```kotlin
// Custom 길 — 외장 카메라를 바인딩할 때
val ext = Vultus.getConfiguration().extCamConfiguration
preview.showsMirrored = ext?.useMirrorMode ?: true
preview.overridePreviewRotation(ext?.rotationDegrees)
session.effectiveOrientation.collect { preview.setEffectiveOrientation(it) }  // 메인 스레드에서
```

영상이 거꾸로 서거나 옆으로 누워 보이면 `rotationDegrees` 를 기기에 맞게 바꾸세요. 검출 박스는 맞는데 영상만 돌아가 보이는 것이 이 값이 틀렸을 때의 모습입니다.

### 8.2 카메라 설정 — 어댑터

USB(UVC) 웹캠의 카메라 쪽 설정(화각 · 역광/저조도 보정 · 노출 · 화이트밸런스 · 줌)은 Camera2 · CameraX 로 걸리지 않거나, 걸린 것처럼 보고만 하고 적용되지 않습니다. 앱이 카메라 모델에 맞는 **어댑터**를 넘기면 SDK 가 USB 로 직접 겁니다. 두 길 모두 같습니다 — 카메라를 앱이 쥔 Custom 길에서도 SDK 가 USB 로 겁니다. 어댑터가 없어도 외장 카메라로 측정할 수 있습니다 — 카메라는 자기 기본 설정으로 동작합니다.

```kotlin
class MyWebcamAdapter : VultusExternalCameraAdapter {
    override val name = "my-webcam"

    // 확인한 제품 번호만 맞춘다 — 같은 이름이라도 펌웨어가 다르면 동작이 다르다
    override fun matches(vendorId: Int, productId: Int) = vendorId == 0x1234 && productId == 0x5678

    // 화각 · 역광 보정 같은 정적 설정. 이미 같은 값인 컨트롤은 쓰지 않는다(ensureInt)
    override fun applySettings(control: VultusUvcControl): Boolean {
        val pu = control.processingUnitId ?: return false
        val backlight = VultusUvcControl.Standard.BACKLIGHT_COMPENSATION
        return control.ensureInt(pu, backlight.selector, backlight.length, 0)
    }

    // 카메라 쪽 줌을 맡을 때만 — 자동 맞춤이 이 범위 안에서 건다
    override val maxZoom = 1.5f
    override fun setZoom(control: VultusUvcControl, ratio: Float): Boolean {
        val ct = control.cameraTerminalId ?: return false
        val zoom = VultusUvcControl.Standard.ZOOM
        return control.ensureInt(ct, zoom.selector, zoom.length, (ratio * 100).roundToInt())
    }
}

// 두 길 모두 — 세션을 연 뒤 붙입니다
session.attachExternalCameraAdapters(listOf(MyWebcamAdapter()))
session.externalCameraStatus.collect { status -> /* APPLIED · NO_PERMISSION · … */ }
```

- **어댑터는 세션에 붙습니다.** 세션을 닫으면 떨어지므로, 세션을 새로 열 때마다 다시 붙이세요.
- **시작을 기다리는 동안부터 남은 프레임을 다 처리할 때까지는 붙이거나 바꿀 수 없습니다**(`NOT_ALLOWED_WHILE_MEASURING`).
- **외장 카메라가 의도대로 조정 · 잠겼는지는 `externalCameraStatus` 로 확인하세요.** SDK 는 배포 빌드에서 외장 카메라의 설정 · 락 · 줌의 결과와 값을 로그로 남기지 않습니다(실패 · 예외만 `VultusExternal` 태그에 경고로 남습니다). 현장(키오스크 등)에서 확인이 필요하면 `state` · `adapter` · `values` 와, 측정 중의 `locked` · `lockColorShift` 를 앱 화면이나 앱의 로그에 띄우세요.

- **USB 권한은 앱이 요청합니다.** 권한이 없으면 SDK 는 어댑터를 부르지 않고 `NO_PERMISSION` 으로 남습니다. 권한 결과가 돌아오지 않는 경우가 있으니(다른 USB 장치의 권한 창과 겹칠 때 등) 이 상태가 이어지면 다시 요청하세요. 허용된 뒤 어댑터를 다시 붙이면 곧바로 설정이 걸립니다.
- **언제 부르나**: 붙일 때 · 카메라를 꽂을 때 `applySettings`(어느 카메라로 측정하든 — 줌을 맡는 어댑터(`maxZoom` > 1)면 고를 때 `setZoom` 으로 1 배로도 맞춥니다). 측정 카메라가 그 외장 카메라이면 측정을 시작하기 전 `applySettings` 를 다시 걸고, 측정을 시작하기 전(`ADJUSTING_CAMERA`) `lockExposure` → `lock3A` → (필요하면) `adjustBrightness`, 측정 종료 때 `unlock3A`(`lockExposure` 를 불렀으면 결과와 상관없이, 시작을 버린 경우에도 — 걸지 않은 축이 있어도 안전해야 합니다. 3A 락은 [4.4](#44-촬영-설정-override)), 자동 맞춤이 줌을 바꿀 때 `setZoom`. 모두 SDK 의 전용 스레드에서 오므로 USB 전송을 해도 됩니다. 외장 카메라로 측정하는 중에 다른 USB 장치가 꽂히거나 빠지면 그때는 아무것도 다시 걸지 않고, 측정이 끝난 뒤 한 번 다시 봅니다(이때도 `applySettings` 가 불릴 수 있습니다). 내장 카메라로 측정하는 중이면 곧바로 다시 겁니다.
- **측정에 걸리는 호출은 측정 카메라가 그 외장 카메라일 때만 부릅니다.** 어댑터를 붙여 둔 채 내장 카메라(전면 등)로 측정하면 측정 전 `applySettings` · `lockExposure` · `lock3A` · `adjustBrightness` · `unlock3A` 와 자동 맞춤의 `setZoom` 은 불리지 않고, 내장 카메라의 3A 락과 줌이 걸립니다. 붙일 때 · 꽂을 때의 `applySettings` 와 어댑터를 고를 때의 1 배 맞춤(줌을 맡는 어댑터)은 그대로입니다. 외장 상태(`externalCameraStatus`)의 `locked` · `lockColorShift` 는 외장 카메라로 측정할 때 락 단계가 끝난 뒤부터 수집이 끝나 락을 풀 때까지만 값이 있습니다(락이 실패한 `false` 도 같습니다) — 내장 카메라 측정에서는 비어 있습니다.
- **설정은 카메라 안에 남습니다.** 다른 앱이나 도구가 바꾼 값이 그대로 올 수 있어 SDK 가 매번 다시 겁니다. 줌을 맡는 어댑터(`maxZoom` > 1)면 고를 때 줌을 1 배로 맞춥니다.
- **줌은 `applySettings` 에 넣지 마세요.** 측정 시작 전에 다시 걸리면 자동 맞춤이 건 줌을 덮습니다.
- ⚠️ **`maxZoom` 은 카메라가 신고한 범위가 아니라 프레임으로 잰 값을 주세요.** 범위 안의 값을 받고 되읽기에도 그 값을 그대로 돌려주면서 실제 배율은 늘지 않는 카메라가 있습니다. 같은 화각에서 줌 프레임과 1 배 프레임 가운데를 늘린 것을 비교해, 배율이 따라오고 더 선명한 범위까지만 줍니다. 카메라가 받는 해상도가 바뀌면 다시 잽니다.
- **락은 두 번에 나눠 겁니다** — `lockExposure` 가 초점 · 노출을 잠그고, SDK 가 그 밝기에서 화면 색이 자리 잡기를 기다린 뒤(`wbSettleWindowMs` 동안 `wbSettleTolerance` 안, 상한 `wbSettleMaxMs`) `lock3A` 가 화이트밸런스를 잠급니다. 노출이 바뀌면 카메라의 색 처리가 따라 바뀌기 때문입니다. 자동 노출이 아닌 값으로 잠그려면 `lockExposure` 에서 `control.lockStandardFocusAndExposure(exposure, gain)` 을 부르세요. 기본 구현은 둘 다 표준 UVC 컨트롤로 지금 값을 잠급니다. SDK 는 `wbSettleWindowMs` 를 50 ~ 5000 ms, `wbSettleMaxMs` 를 0 ~ 5000 ms 로 자르고 창을 상한보다 짧게 줄이며(창이 상한에 가까우면 판정할 틈이 없어 상한으로 끝납니다), 상한이 0 이거나 허용 폭이 0 이하이면 기다리지 않습니다. `unlock3A` 를 덮어쓰면 `control.unlockStandard3A()` 도 함께 부르세요 — 기본 `lockExposure` 가 표준 컨트롤로 잠근 초점 · 노출은 그것이 되돌립니다. 외장 카메라에서 3A 락을 쓰지 않으려면 `overrideCameraLock(false)` 를 거세요 — 어댑터의 `lock3A` · `lockExposure` 가 `false` 를 돌려주게 덮어쓰면 락은 걸리지 않지만 그 측정은 「잠그려 했으나 잠기지 않음」 으로 기록됩니다(기본 `lockExposure` 는 초점 · 노출을 잠급니다). `lockExposure` 가 `false` 이면 SDK 는 색이 자리 잡기를 기다리지 않습니다.
- **외장 카메라의 3A 락은 어댑터가 겁니다** — 카메라 HAL 의 3A 고정은 하지 않습니다. `lockExposure` · `lock3A` 가운데 하나라도 `true` 를 돌려주면(축 하나라도 잠갔으면) 그 측정은 잠긴 것으로 기록되고, 맞는 어댑터가 없거나(요청에 정상으로 답하지 않아 어댑터를 쓰지 않는 카메라, 어댑터의 `matches` · `name` 이 예외를 던져 쓰지 않는 어댑터 포함), 연결된 외장 카메라를 찾지 못하거나, USB 권한이 없거나 장치를 열어 제어할 수 없거나, 두 호출이 모두 `false` 이면 잠기지 않은 것으로 기록됩니다. `lockExposure` · `lock3A` 가 예외를 던지거나 시간 안에 끝나지 않으면 그 호출은 `false` 로 봅니다 — 다른 쪽이 `true` 면 잠긴 것으로 기록됩니다. 락 뒤 밝기 되맞추기(`adjustBrightness`)는 이 기록에 들어가지 않습니다(락이 화면을 바꾼 정도는 `lockColorShift`). 이번 측정에서 잠갔는지는 측정 중에 `externalCameraStatus.locked` 로 봅니다 — 지금 상태라 수집이 끝나 락을 풀면 `null` 로 돌아갑니다(`beforeSubmit` 에서는 이미 `null`). 그 측정의 락 결과 · 색 이동 · 락 단계 끝의 카메라 값은 SDK 가 측정 기록에 함께 싣습니다.
- **외장 카메라 어댑터의 조정 단계는 측정을 시작하기 전에 끝납니다.** SDK 는 그동안(`ADJUSTING_CAMERA`) 기다렸다가 측정을 시작하므로, 어댑터의 기다림 · 되맞추기 값(`wbSettleMaxMs` + `lockSettleMs` × (`lockLumaSteps` + 1))에 락 직전 화면 색을 재는 시간(최대 1 초)과 설정 전송 시간을 더한 만큼 시작이 늦어집니다. SDK 는 `lockSettleMs` 를 0 ~ 5000 ms, `lockLumaSteps` 를 0 ~ 10 으로 자르고, `lockLumaTolerance` · `lockLumaCeiling` 가 유한한 수가 아니면 되맞추지 않습니다. 어댑터 호출 하나가 SDK 가 정한 시간(10 초) 안에 끝나지 않으면 그 호출은 실패로 보고, 그 호출이 아직 도는 동안 온 조정 단계의 다른 호출은 기다리지 않고 건너뜁니다(실패로 봅니다). 끝나지 않은 호출은 그 뒤에도 끝까지 돌 수 있습니다.
- ⚠️ **3A 락이 화면 색을 바꾸는 카메라가 있습니다.** 기본 락(`lockStandard3A`)은 자동 중에 읽은 화이트밸런스 온도를 그대로 수동으로 잠그는데, 자동이 실제로 쓰는 온도와 보고하는 온도가 다른 카메라는 그 순간 색이 옮습니다. 그런 카메라는 어댑터의 `lock3A` 에서 `control.lockStandard3A { reported -> /* 같은 색을 내는 온도 */ }` 로 번역해 잠그세요. 락이 화면을 바꾼 정도는 측정 중에 `externalCameraStatus.lockColorShift` 로 확인할 수 있습니다(측정 기록에도 실립니다).
- **락 뒤 밝기 보정이 필요한 카메라는 어댑터의 `adjustBrightness(control, currentY, deltaY)` 를 구현하세요.** 자동 노출 중에 되읽은 값을 그대로 잠가도 같은 밝기가 나오지 않는 카메라가 있습니다. SDK 가 필요할 때 부르며, 어떤 컨트롤로 맞출지는 카메라마다 달라 어댑터가 정합니다. 기본 구현은 아무것도 하지 않습니다.
- ⚠️ **되읽기로 적용을 확인할 수 없는 카메라가 있습니다.** 요청한 값을 그대로 돌려주면서 실제로는 다른 값으로 동작하는 컨트롤이 있으니, 새 컨트롤을 쓸 때는 영상(밝기 · 배율)으로 적용을 확인하세요.
- **`applySettings` 가 `false` 를 돌려주면 상태는 `APPLY_FAILED` 입니다.** 이때도 3A 락은 따로 걸리므로 `state` 와 `locked` 는 함께 오지 않을 수 있습니다 — 측정이 의도한 조건에서 돌았는지는 측정 중에 둘을 함께 보세요(`locked` 는 락을 풀면 `null` 로 돌아갑니다).
- ⚠️ **모든 UVC 웹캠이 앱에서 설정을 받지는 않습니다.** 펌웨어에 따라 앱이 보내는 요청을 거절하거나 모든 값을 0 으로 돌려주는 카메라가 있고, 같은 이름의 제품도 펌웨어가 다르면 다르게 동작합니다. 어댑터는 그 제품 번호의 카메라를 실제 기기에 꽂아 `APPLIED` 와 영상으로 적용을 확인한 뒤에 맞추세요.
- **요청에 정상으로 답하지 않는 카메라에는 어댑터를 쓰지 않습니다.** SDK 가 먼저 표준 컨트롤의 응답을 보고, 거절하거나 0 만 돌려주면 어댑터를 부르지 않고 `APPLY_FAILED` 로 둡니다. 그 카메라는 기본 설정으로 동작합니다.
- 맞는 어댑터가 없는 카메라에는 USB 요청을 보내지 않습니다(`NO_ADAPTER`) — 그 카메라는 기본 설정으로 동작합니다.

### 8.3 설치 조건

- ⚠️ **외장 카메라는 압축(MJPEG) 포맷으로 받도록 두세요.** 일부 기기의 외장 카메라 경로는 비압축(YUYV)으로 받을 때 영상 범위(밝기 16~235) 값을 표시 없이 넘깁니다. `YUV_420_888` 에는 범위 정보가 없어 SDK 가 알아챌 수 없고, 오류 없이 측정 품질이 떨어집니다. 기기 설정으로 웹캠 포맷을 바꿀 수 있다면 기본값(MJPEG)을 유지하세요.
- ⚠️ **외장 카메라는 하나만 꽂으세요.** 영상 장치가 둘 이상이면 SDK 가 제어하는 카메라와 촬영 중인 카메라가 다를 수 있고, 그래도 오류는 나지 않습니다. 이때 측정에 기록되는 3A 락 결과도 SDK 가 제어한 카메라의 것입니다.

---

## 9. ProGuard / R8 설정

**따로 추가할 규칙은 없습니다.** 필요한 킵(Keep) 규칙은 AAR 안의 `consumer-rules.pro`에 담겨 있어 앱 빌드에 자동으로 적용됩니다. 네이티브 함수를 이름으로 찾는 JNI 경로가 여기서 보호됩니다.

> [!CAUTION]
> **`-keep class io.haii.vultus.** { *; }` 같은 광범위한 규칙을 넣지 마세요.** AAR은 이미 최소화된 상태로 배포됩니다. 패키지 전체를 keep하면 앱 쪽 R8이 SDK 코드를 더 줄이지 못해 APK만 커지고, 얻는 것이 없습니다. 공개 API는 앱 코드가 참조하는 것만으로 이미 유지됩니다.

---

## 10. 라이선스 검증 및 예외 처리 (License & Error Handling)

Vultus SDK는 라이선스 검증이 실패하거나 서버 API 요청 중 오류가 발생하더라도 **연동 앱의 비정상 종료(Crash)를 유발하지 않도록 안전하게 예외를 격리**하도록 설계되어 있습니다.

### 10.1 초기화 예외

`Initializer.initialize(...)` 는 `Vultus.InitializationError` 를 던집니다.

| 케이스 | 의미 |
| :--- | :--- |
| `AlreadyInitialized` | 이미 초기화됨 ([3.3](#33-네이티브-워밍업-및-준비-상태-preparestate) 의 가드 참고) |
| `DevServerInReleaseBuild` | 릴리스 빌드에서 `useDevServer` 를 불렀음 |

**SDK 가 예외를 던지는 자리는 `initialize` 하나뿐이고, 던지는 것은 위 둘뿐입니다.** 그 밖의 실패는 상태(`PrepareState` · `Vultus.license`)나 반환값(`VultusOpenResult` · `VultusMeasureOutcome`)으로 전달됩니다.

> [!WARNING]
> **`useDevServer` 는 개발 빌드에서만 허용되며, 개발 서버는 정상 동작을 보장하지 않습니다**(가용성 · 응답 형태가 예고 없이 바뀔 수 있습니다 — 출시 판단은 운영 서버로 하세요). 앱이 `debuggable` 이 꺼진 빌드(보통 `release` 빌드 타입)라면 `initialize` 가 `DevServerInReleaseBuild` 를 던집니다. 판정은 SDK 가 기기에서 앱의 `debuggable` 플래그를 직접 읽어서 하며, 그 경로를 피해 가도 SDK 는 개발 서버에 접속하지 않습니다.
>
> 디버그 빌드에서만 켜려면 빌드 타입으로 가르세요.
>
> ```kotlin
> Vultus.Initializer(licenseCode, holderKey, clientKey)
>     .useDevServer(BuildConfig.DEBUG)
>     .initialize(this, this)
> ```

네이티브 생성 실패는 여기로 오지 않고 `PrepareState.FAILED` 로 나타납니다 — 생성이 워밍업 스레드에서 일어나 던질 자리가 없기 때문입니다.

> [!CAUTION]
> **초기화 실패는 앱이 반드시 다뤄야 하는 지점입니다.**
>
> 초기화에 실패하면 SDK 는 아무것도 하지 않습니다. 그 상태에서 인스턴스를 꺼내는 접근자(`getConfiguration()`)를 부르면 **`UninitializedPropertyAccessException` 으로 앱이 멈춥니다.** SDK 가 대신 판단해 줄 수 있는 것이 없습니다 — 측정 화면을 띄울지, 재시도할지, 안내하고 종료할지는 앱의 결정입니다.
>
> `InitializationError` 는 `IllegalStateException` 을 상속하는 **unchecked 예외라 컴파일러가 처리를 강제하지 않습니다.** 잡지 않으면 그대로 앱이 종료됩니다.
>
> ```kotlin
> private var initializationError by mutableStateOf<String?>(null)
>
> private fun setupVultus() {
>     if (!Vultus.isInitialized()) {
>         try {
>             Vultus.Initializer(licenseCode, holderKey, clientKey).initialize(this, this)
>         } catch (e: Vultus.InitializationError) {
>             initializationError = e.message
>             return   // 여기서 멈추지 않고 넘어가면 초기화되지 않은 SDK 를 또 건드린다
>         }
>     }
> }
> ```
>
> 그리고 **화면을 그리기 전에 갈라야 합니다.** 로그만 남기고 넘어가면 UI 가 SDK 인스턴스에 접근하는 순간 죽습니다.
>
> ```kotlin
> setContent {
>     val error = initializationError
>     if (error != null)            ErrorScreen(error)      // 실패 안내
>     else if (Vultus.isInitialized()) MeasureFlow()         // 정상 경로
> }
> ```
>
> 샘플앱(`app/src/main/java/com/example/vultus/MainActivity.kt`)이 이 형태로 되어 있습니다.

### 10.2 라이선스 상태 확인 API

```kotlin
// 라이선스 상태 — 유효 여부 · 허용한 기능 · 실패 사유를 한 값으로
val license: VultusLicense = Vultus.license

if (!license.isValid) {
    Log.e("VultusSDK", "라이선스 검증 실패: ${license.error}")
}
```

이 값은 **마지막 검사 결과**이고, 읽는 순간의 값입니다 — 붙들어 두지 말고 필요할 때 다시 읽으세요. 워밍업이 끝나기 전에는 `isValid` 가 `false`이며, 그 뒤로는 워밍업과 각 `startMeasure()` 시점의 검사 결과를 반영합니다. 측정 결과 제출 중 서버가 세션을 거절해 SDK 가 다시 받은 결과도 반영합니다. 유효 시간 뒤의 재확인이 경로 문제(네트워크 · 프록시 · 인증서)로 실패해 지금 세션으로 측정한 경우는 오류로 남지 않습니다.

### 10.3 검사 시점과 복구

라이선스는 한 번만 검사하지 않습니다. 검사가 실패한 채로 굳으면 코어가 입구에서 모든 처리를 반환해 **BPM·신호·FER 이 전부 나오지 않으면서도 예외는 발생하지 않는** 상태가 되기 때문입니다.

| 시점 | 재시도 | 실패했을 때 |
| :--- | :--- | :--- |
| 워밍업 (`initialize` 직후, 백그라운드) | 백오프 3회 | `prepareState` 가 `LICENSE_INVALID` |
| `startMeasure()` 진입 | 1회(워밍업 중이면 워밍업이 끝나기를 기다린 뒤) | `startMeasure()` 가 `Refused(LICENSE_INVALID)` |
| 유효 시간(서버가 정한다, 기본 3 시간)이 지난 뒤의 `startMeasure()` | 1회 — 다시 확인한다 | 서버가 명시적으로 거절하면 `Refused(LICENSE_INVALID)`, 그 밖의 실패(네트워크 · 프록시 · 인증서)는 지금 세션으로 측정하고 다음 시작에서 다시 확인합니다 |
| 측정 결과 제출을 서버가 세션 문제로 거절(HTTP 401) | 라이선스를 다시 받아 한 번 다시 보냄 | 다시 받지 못했거나 또 거절되면 결말은 `Finished` 이고 응답의 `error_code` 가 `API_HTTP_ERROR`([10.5](#105-오류-응답-json-스키마)) |

재시도 대상은 **시간이 지나면 풀릴 수 있는 실패**뿐입니다 — 연결 실패·타임아웃·서버 5xx·408·429. 서버가 라이선스를 거절했거나(4xx) 앱 식별자가 어긋난 경우(`PKG_NAME_MISMATCH`), 서버 인증서를 믿을 수 없는 경우(`TLS_UNTRUSTED`, [10.6](#106-서버-인증서-검증))는 다시 물어도 답이 같으므로 재시도하지 않습니다.

### 10.4 라이선스 기능 플래그 (Feature Flags)

라이선스마다 **허용된 기능이 다릅니다.** 요청한 기능이 허용되지 않으면 그 기능만 동작하지 않고, 측정 자체는 계속되며 예외도 발생하지 않습니다.

```kotlin
val granted: Set<String> = Vultus.license.grantedFeatures

if (Vultus.getConfiguration().enableFER && "fer" !in granted) {
    // 표정 UI 를 숨기거나 안내 문구를 띄운다
}
```

초기화 전이거나 라이선스 검증 전·실패 후에는 **빈 집합**입니다.

| 토큰 | 기능 | 결과에서 받는 자리 |
| :--- | :--- | :--- |
| `fer` | 표정 인식(FER) | 측정 중 `emotion` · `VultusAnalysisResult.Entry``.fer` |
| `env` | 측정 환경 — 조도 · 화면 밝기 · 프레임당 카메라 상태 | `VultusAnalysisResult.Entry``.env` · `.camera` |
| `chroma` | 크로마 신호 | `VultusAnalysisResult.Entry``.chroma` |
| `rawppg` | 원신호 | `VultusAnalysisResult.Entry``.ppgRawJson`(JSON 문자열) |
| `rawface` | 얼굴 검출 결과 | `VultusAnalysisResult.Entry``.faceJson`(JSON 문자열) |
| `imu` | 측정 구간의 가속도 · 자이로 스트림 | `VultusAnalysisResult.imuJson`(JSON 문자열) |
| `hrv_das` | 스트레스·우울·불안 지표 | (준비 중) |

`rawppg` · `rawface` · `imu` 의 JSON 문자열 안 필드는 해당 기능을 받은 라이선스와 함께 제공하는 문서를 따릅니다. 세 JSON 문자열은 응답 원문을 그대로 잘라 담은 것입니다 — 파서가 풀었다가 다시 만들지 않으므로 이름 순서 · 숫자 표기가 응답과 같고, 필요한 앱만 풀면 됩니다.

`chroma` · `rawppg` · `rawface` 중 하나라도 있으면 빠진 프레임도 함께 받습니다 — `VultusAnalysisResult.Entry.gap`(`VultusAnalysisResult.Gap` — `frames` · `skipped` · `reasons` 비트, `hasReason(Gap.REASON_NO_FACE)` 등). 그 엔트리가 간극의 끝이고 시작은 직전 엔트리이며, 빠진 것이 없으면 `null` 입니다.

#### FER 이 허용되지 않으면

`enableFER(true)` 로 요청했더라도 라이선스가 `fer` 를 주지 않으면:

* **추론 모델을 로드하지 않습니다.** 메모리와 초기화 비용이 들지 않습니다.
* `emotion` 은 **계속 값을 내지만 비어 있습니다** — `emotionIndex = -1`, `label` 은 `"Unknown"`. 흐름을 아예 멈추지 않는 이유는, 멈추면 앱이 "카메라가 얼굴을 못 잡나"를 먼저 의심하게 되고 그쪽을 아무리 고쳐도 달라지지 않기 때문입니다.
* 측정 시작 시 `Log.e` 로 사유가 **한 번** 기록됩니다.

`emotionIndex` 가 음수인 경우를 값 없음으로 처리하면 별도 분기 없이 안전합니다.

```kotlin
val label = if (result.emotionIndex >= 0) result.label else "--"
```

#### ⚠️ 권한 변경은 늦게 반영될 수 있습니다

라이선스 세션은 **유효 시간(서버가 정한다, 기본 3 시간) 동안 유지**됩니다. 그동안 측정은 라이선스 확인에 네트워크를 타지 않으므로(그래서 측정 시작이 빠릅니다), **앱이 실행 중일 때 서버에서 권한을 바꿔도 곧바로 반영되지는 않습니다.**

* 서버 응답에 실리는 데이터(`env` · `chroma` · `rawppg` · `rawface` · `imu` · `hrv_das`)는 매 측정 제출마다 서버가 다시 확인하므로 즉시 반영됩니다.
* **클라이언트에서 동작하는 기능(FER)** 은 SDK 가 라이선스를 다시 확인한 뒤의 측정부터 반영됩니다 — 유효 시간이 지난 뒤의 `startMeasure()`, 또는 제출 중 서버가 세션을 거절했을 때(권한이 바뀐 경우 포함) 다시 확인합니다. 새로 주어진 권한은 그때부터 동작하고, 거둔 권한은 그때부터 FER 결과가 비어 옵니다(`emotionIndex = -1`).
* 구매 직후 곧바로 쓰게 하려면 `Vultus.license.grantedFeatures` 로 현재 권한을 확인해 "앱을 다시 시작해 주세요"를 띄우는 것이 확실합니다.

### 10.5 오류 응답 JSON 스키마

`VultusMeasureOutcome.Finished` 의 `responseJson` 으로 수신되는 JSON 응답에서 오류 발생 시 다음과 같은 형태로 전달됩니다:

```json
{
  "status": "error",
  "error_code": "API_HTTP_ERROR",
  "message": "Measurement API failed with HTTP 401"
}
```

측정 결과 제출은 **SDK 가 다시 보내지 않습니다** — 서버가 세션을 다시 요구한(HTTP 401) 때만 라이선스를 다시 받아 한 번 더 보냅니다. 오류로 끝나면 측정 데이터는 남지 않으므로 다시 측정하도록 안내하세요.

| `error_code` | 뜻 |
|---|---|
| `API_NETWORK_ERROR` | 서버에 닿지 못했거나 응답을 받지 못했습니다(네트워크 끊김 · 시간 초과) |
| `API_HTTP_ERROR` | 서버가 오류 상태(HTTP 4xx · 5xx)로 답했습니다. 401 이면 마지막 라이선스 확인 뒤 서버에서 라이선스(권한 등)가 바뀌어 세션이 무효해졌고, SDK 가 라이선스를 다시 받지 못했거나 다시 보냈는데도 거절된 경우입니다 |
| `API_TLS_UNTRUSTED` | 서버 인증서를 믿을 수 없습니다(아래 「서버 인증서 검증」) |
| `API_SIGNATURE_VERIFY_FAILED` | 응답의 서명을 확인하지 못했습니다(아래) |
| `HTTP_INIT_FAILED` | 기기에서 통신을 준비하지 못했습니다 |
| `SUBMISSION_BUILD_FAILED` | 기기에서 측정 데이터를 보낼 형태로 만들지 못했습니다(보내지 않았습니다) |
| 라이선스 검사의 코드(`LICENSE_*` · `PKG_NAME_MISMATCH` · `NETWORK_ERROR` · `TLS_UNTRUSTED` 등) | 보내기 전에 라이선스가 유효하지 않아 보내지 않았습니다 |

측정 결과 제출에서 서버 인증서를 믿을 수 없으면 `error_code` 가 `API_TLS_UNTRUSTED` 입니다([10.6](#106-서버-인증서-검증)).

측정 결과는 서버가 서명해 보내고 SDK 가 확인한 뒤에만 넘깁니다. 확인에 실패하면 `error_code` 가 `API_SIGNATURE_VERIFY_FAILED` 입니다 — 통신 중간에서 응답이 바뀌었다는 뜻이므로 결과로 쓰지 말고, 네트워크(프록시 · VPN · 보안 앱)를 확인한 뒤 다시 측정하도록 안내하세요.

### 10.6 서버 인증서 검증

SDK 는 Vultus 서버와 통신할 때 서버 인증서를 **기기의 시스템 인증 기관(CA)** 으로 검증합니다. 앱의 `network_security_config` 나 사용자가 설치한 인증서로 추가한 CA 는 SDK 통신에 쓰이지 않습니다. 그래서 사용자 인증서를 설치하는 프록시 도구로는 SDK 통신이 성립하지 않습니다.

> [!IMPORTANT]
> **앱의 `network_security_config` 가 시스템 CA 를 빼면 SDK 통신이 실패합니다.** `base-config` 의 `<trust-anchors>` 에 `<certificates src="system" />` 이 없다면, SDK 서버 도메인에 시스템 CA 를 열어 두세요.
>
> ```xml
> <network-security-config>
>     <domain-config>
>         <domain includeSubdomains="false">api.haii.io</domain>
>         <trust-anchors>
>             <certificates src="system" />
>         </trust-anchors>
>     </domain-config>
> </network-security-config>
> ```

검증에 실패하면 라이선스 검사는 `TLS_UNTRUSTED`, 측정 결과 제출은 `API_TLS_UNTRUSTED` 로 끝나며 재시도하지 않습니다. 공용 Wi-Fi 의 로그인 페이지나 통신을 가로채는 프록시를 거치는 네트워크에서 주로 일어납니다.

오류 메시지 끝에 원인이 붙습니다. 원인별 대응은 다음과 같습니다.

| 메시지의 원인 | 뜻 | 대응 |
|---|---|---|
| `not issued by an authority the system trusts` | 인증서가 시스템이 믿는 인증 기관으로 이어지지 않습니다 | 프록시 · VPN · 보안 앱 · 네트워크 로그인 페이지를 거치는지 확인하고, 다른 네트워크에서 다시 시도하도록 안내합니다 |
| `expired or not yet valid` | 인증서의 유효 기간 밖입니다 | 기기의 날짜 · 시간 설정(자동 설정)을 확인하도록 안내합니다 |
| `does not match the server name` | 다른 서버의 인증서가 왔습니다 | 네트워크가 연결을 다른 곳으로 돌리는 경우입니다(로그인 페이지 등). 네트워크 로그인을 마치거나 다른 네트워크를 쓰도록 안내합니다 |
| `was rejected` | 그 밖의 이유로 인증서를 받아들이지 않았습니다 | 다른 네트워크에서도 반복되면 문의해 주세요 |

인증서를 확인하는 과정 자체가 이 기기에서 실패한 경우(인증서 탓이 아닌 일시적 오류)는 인증서 오류가 아니라 `NETWORK_ERROR` · `API_NETWORK_ERROR` 로 옵니다 — 라이선스 검사의 `NETWORK_ERROR` 는 다른 네트워크 오류처럼 다시 시도되고, 측정 결과 제출의 `API_NETWORK_ERROR` 는 다시 보내지 않으므로 다시 측정합니다.

---

## 11. 샘플 앱 구동 방법 (Sample App Guide)

배포 저장소의 루트에 단독 샘플 앱(`sample/`) 프로젝트가 있습니다. 샘플은 SDK 를 Maven 저장소에서 받습니다(`sample/gradle.properties` 의 `vultusMavenVersion`).

빌드 전에 `sample/app/build.gradle.kts` 의 `VULTUS_LICENSE_CODE` · `VULTUS_HOLDER_KEY` · `VULTUS_CLIENT_KEY` 에 발급받은 값을 넣으세요. 라이선스는 앱 식별자를 함께 확인하므로 샘플의 `applicationId`(`com.example.vultus`)가 등록된 라이선스 값이어야 합니다([10](#10-라이선스-검증-및-예외-처리-license--error-handling)).

> [!NOTE]
> 샘플은 외장(USB) 카메라를 거치형 기기에 단 것으로 보고 `extCamConfiguration(270, useMirrorMode = true)` 를 싣습니다. USB 웹캠을 꽂은 기기에서 Default 길 프리뷰가 돌아가 보이면 `MainActivity` 의 이 값을 기기에 맞게 바꾸거나 지우세요([8.1](#81-카메라-선택과-표시--extcamconfiguration)). 외장 카메라 어댑터는 싣지 않습니다 — 카메라 제어(3A 락 · 줌)가 필요하면 `camera/ExternalCameraAdapters.kt` 의 목록에 기기 카메라의 어댑터를 넣으세요([8.2](#82-카메라-설정--어댑터)).

### Option A. Android Studio 이용
1. Android Studio를 실행하고 **Open**을 클릭합니다.
2. 배포 패키지 디렉터리 내의 `sample/` 폴더를 선택합니다.
3. Gradle Sync가 완료되면 Android 7.1 (API Level 25) 이상의 실기기를 연결합니다.
4. 상단 **Run 'app'** (Shift+F10) 버튼을 눌러 실행합니다.

### Option B. CLI (터미널) 이용
```shell
# 배포 패키지 루트에서 sample 디렉터리로 이동
cd sample

# 디버그 APK 빌드
./gradlew assembleDebug

# 연결된 실기기에 설치 및 실행
./gradlew installDebug
```

---

## 12. 모범 사례 및 트러블슈팅

1. **카메라 가로/세로 회전 대응**
   - 전면 카메라를 사용하는 경우 `mirrorMode = true`로 설정하여 사용자가 거울을 보듯 조작할 수 있게 구성합니다.
2. **조도(Lux) 환경**
   - 어두운 환경(조도 낮음)에서는 rPPG 신호의 SNR(신호 대 잡음비)이 저하될 수 있으므로 주변 조도 환경을 확인하는 것을 권장합니다.
   - 조명이 **한쪽으로 치우치면** 얼굴 한쪽에 그늘이 져 신호가 약해집니다. 정면광이나 고른 확산광이 가장 좋습니다.
3. **얼굴 크기가 곧 신호 품질입니다**
   - 얼굴이 작을수록 신호에 쓰이는 화소가 줄고, 일정 크기 아래에서는 화질 손실이 더해집니다. 크기는 **자동 맞춤이 맞춥니다**(§4.5) — 앱은 맞춤 상태(`Limited`)가 요청할 때만 사용자에게 다가오거나 물러나라고 안내하면 됩니다.
   - 위쪽에도 한계가 있습니다. **얼굴이 너무 크면** 고개를 조금만 돌려도 얼굴이 프레임을 벗어나 **그 구간을 측정에 쓰지 못합니다** — 촬영 조건의 `TOO_CLOSE` 를 따르세요. 샘플앱은 SDK 의 촬영 조건을 따라 쓸 만하면 초록, 작으면 노랑, 너무 가깝거나 화면 밖에 걸리면 주황으로 표시합니다 — 두 색만 쓰면 이미 너무 가까운 사용자가 "더 가까이"로 읽고 상황을 악화시킵니다.
4. **분석 길이는 `startMeasure(analysisWindow = …)` 로 넘깁니다**
   - **창을 채우면 SDK 가 스스로 멈춥니다.** 앱이 타이머를 들 필요가 없습니다. 더 모으려면 `extraSeconds` 를, 앱이 직접 멈추려면 `extraSeconds = null` 을 넘깁니다(어느 쪽이든 하드 안전 종료 — 기본 180 초 — 에서 SDK 가 멈춥니다, §5).
   - 길이는 **유효 신호가 모인 시간**으로 셉니다 — 얼굴을 놓친 구간은 진행에 들어가지 않으므로 벽시계보다 오래 걸릴 수 있습니다. 남은 시간을 보여 주려면 `progress` 를 쓰세요.
5. **메모리 관리**
   - `Vultus.Initializer`에 전달하는 `LifecycleOwner`를 정확히 바인딩하면, Activity/Fragment의 `onPause`, `onResume` 시점에 조도 센서 및 바운딩 박스 감지가 자동으로 일시정지 및 재개되어 전력 소비를 줄일 수 있습니다.
6. **표정 결과는 시선에 크게 좌우됩니다**
   - 결과를 모아 평균 내도 보정되지 않으므로, 표정 결과를 쓰신다면 **사용자가 렌즈를 보도록 유도**하세요.
7. **검출이 크게 밀리면 실시간 BPM 과 그래프가 잠시 흔들릴 수 있습니다**
   - 저사양 기기나 발열로 얼굴 검출이 프레임 간격보다 한참 오래 걸리면 일부 프레임의 처리 순서가 바뀔 수 있습니다(`[FRAME_STATS]` 의 `outOfOrder` 가 0 이 아닌 측정). 화면의 표시용 BPM · 신호 그래프만 잠시 흔들리고, 서버로 보내는 결과는 영향을 받지 않습니다.
8. **촬영 중에 기기를 가로 ↔ 세로로 돌리면 얼굴 상자가 어긋날 수 있습니다**
   - 방향은 측정 동안 고정되어, 측정 중에 돌린 뒤의 영상은 얼굴이 누운 채 분석됩니다. 얼굴 상자가 얼굴에서 밀리고 분석 품질이 떨어질 수 있습니다. 측정 중에 기기를 돌렸다면 그 측정을 취소하고 다시 시작하세요 — 측정 밖에서는 기본 방식이 화면 회전을 따라 다시 맞춥니다. UI 를 한 방향으로 고정한 앱은 기기를 돌려도 화면 회전이 바뀌지 않으므로 얼굴이 누운 채로 남습니다(§7.2 의 방향 안내).

### SDK 로그의 태그

SDK 는 logcat 에 다섯 태그로 남깁니다 — `Vultus`(코어) · `VultusSession`(측정의 흐름 — 시작 · 카메라 락 · 분석 · 움직임 센서 · 전송) · `VultusIntake`(프레임 · 얼굴 검출 · 프레임의 카메라 정보) · `VultusLifecycle`(초기화 · 카메라 바인딩 · 촬영 설정 · 화면 · 조도 센서) · `VultusExternal`(외장 카메라). 한꺼번에 보려면 `adb logcat -s Vultus VultusSession VultusIntake VultusLifecycle VultusExternal` 입니다.

### 프레임 흐름 진단 (`[FRAME_STATS]`)

rPPG 는 **균일한 프레임 간격을 전제**합니다. BPM 이 튀거나 선명도(`signalClarity`)가 낮게 나오는데 조명·자세에 문제가 없다면, 프레임이 제대로 흘렀는지부터 확인하세요.

측정이 끝날 때마다 한 줄이 남고, 같은 값이 측정 데이터와 함께 서버로도 전송됩니다.

```
[FRAME_STATS] enqueued=502 outOfOrder=0 maxGapMs=0
```

| 로그 키 | 읽는 법 |
| :--- | :--- |
| `enqueued` | 측정 동안 실제로 처리된 프레임 수. **측정 시간 × 30 에 크게 못 미치면 프레임이 버려지고 있다는 뜻**입니다 |
| `outOfOrder` | 신호 처리에 들어간 순서가 뒤집힌 횟수 |
| `maxGapMs` | 가장 크게 뒤집힌 폭(ms) |

`outOfOrder` 는 **보통 0 입니다.** 얼굴 검출이 프레임 간격보다 한참 오래 걸리면(저사양 기기 · 발열) 일부 프레임의 처리 순서가 바뀔 수 있습니다.

`maxGapMs` 로 심각도를 판단하세요 — 0 이 아니면 검출이 프레임 간격을 크게 넘은 구간이 있었다는 뜻이고(저사양 기기 · 발열), 폭이 클수록 실시간 BPM 이 튈 수 있습니다. 서버로 보내는 데이터는 영향을 받지 않습니다.

`enqueued` 가 낮다면 처리가 카메라 프레임률을 못 따라가는 것입니다. `useAccurateMode` 를 끄거나 `detectContour` 를 끄는 쪽이 먼저입니다.

### 측정 품질 진단

BPM 이 튀거나 표정이 이상할 때, **먼저 확인할 것은 거리와 자세**입니다. 배포 AAR 은 릴리스 빌드라 프레임 단위 진단 로그를 남기지 않으므로, 같은 확인은 공개 흐름으로 합니다.

1. **머리 각도(pitch)** — 촬영 조건(`captureCondition`)의 원인 `HEAD_TILTED` · `HEAD_TURNED` 가 뜨지 않는 자세를 기준으로 삼으세요(각도 값은 `detectedFace` 의 `faceInfo.headEulerAngleX` — iOS 와 부호 · 크기가 달라 플랫폼마다 같은 숫자로 판단할 수 없습니다, [7.5](#75-검출된-얼굴-detectedface)). rPPG 품질에 크게 영향을 줍니다 — 기기를 아래에서 올려 든 자세는 BPM 을 크게 흩뜨립니다. 기기를 눈높이로 들어 정면을 보게 하세요
2. **얼굴 크기(거리)** — 촬영 조건(`captureCondition`)의 원인 `TOO_FAR` · `TOO_CLOSE` 로 판단하세요. 너무 작으면 신호가 약하고, 너무 크면 고개를 돌릴 때 얼굴이 프레임을 벗어납니다. `detectedFace` 의 `boundingBox` 크기는 참고값입니다 — 얼굴이 프레임 가장자리에 걸치면 잘려 작아집니다
3. **화면용 심박수의 상태** — `displayBpm` 의 `state`. 워밍업인 동안은 아직 값이 나오기 전이고, 추정 중은 값이 흔들리는 구간입니다. 표시 전용 값이므로 측정 결과로 모으지 마세요([6](#6-상태-구독))
4. **빠진 프레임** — 측정 결과의 `VultusAnalysisResult.Entry.gap`(`VultusAnalysisResult.Gap.reasons`)이 얼굴을 놓친 구간과 사유를 알려 줍니다(해당 라이선스 기능이 있을 때)

프레임 간격 자체가 의심되면 [프레임 흐름 진단](#프레임-흐름-진단-frame_stats) 을 보세요.

---

## 13. 스토어 제출 — 수출 규정 신고

Google Play 에 올리는 앱은 **미국 수출법 준수(US export laws) 항목에 동의**해야 하며,
암호화를 포함한 앱을 배포할 때 해당 규정을 따르는 책임은 배포자에게 있습니다.
Vultus SDK 는 암호화를 사용하므로, SDK 를 넣은 앱은 "암호화를 사용하지 않음" 에
해당하지 않습니다. 판단에 필요한 사실을 아래에 밝힙니다.

### 13.1 SDK 가 사용하는 암호화

| 무엇 | 알고리즘 | 쓰이는 곳 |
| :--- | :--- | :--- |
| 전송 채널 보호 | TLS | 라이선스 검증 · 측정 결과 전송 |
| 전송 데이터 암호화 | **X25519 ECDH + AES-256-GCM** | 측정 페이로드와 메타데이터 |
| 라이선스 응답 · 측정 결과 응답 · 서버 조정값 검증 | **Ed25519 서명 검증** | 세션 수립 · 결과 수신 |

- **암호화 구현은 SDK 의 네이티브 라이브러리에 정적으로 포함**됩니다(OpenSSL).
  Android 플랫폼이 제공하는 암호화 API 를 호출하는 방식이 **아닙니다.**
- 알고리즘은 모두 공개된 국제 표준이며 독자 방식은 없습니다.
- 측정 데이터는 **TLS 위에 한 번 더 암호화**되어 전송됩니다.

### 13.2 ⚠️ 판단할 때 주의할 것

**"표준 공개 알고리즘을 쓴다" 는 면제 사유가 아닙니다.** 규제 대상이 되는 것이 오히려
AES-256 같은 표준 강암호입니다.

면제 여부는 **알고리즘이 아니라 용도**로 정해집니다. 인증·전자서명에만 쓰거나
운영체제가 제공하는 암호화만 호출하는 경우가 면제 항목에 해당하는데,
**Vultus SDK 는 사용자 측정 데이터 자체를 암호화하고 암호화 구현을 직접 포함**하므로
그 두 항목으로는 설명되지 않습니다.

어떤 면제 항목에 해당하는지, 해당하지 않아 신고가 필요한지는 **앱마다 다르며 배포자가
판단할 사항**입니다. 이 문서는 판단에 필요한 사실만 제공합니다.

> iOS 판에는 App Store Connect 의 `ITSAppUsesNonExemptEncryption` 신고 방법이 함께
> 정리돼 있습니다. 같은 앱을 양 플랫폼에 낸다면 **두 스토어의 답이 같아야** 합니다 —
> 같은 SDK 가 같은 암호화를 하기 때문입니다.

---

## 14. 스토어 제출 — 데이터 보안 신고

Google Play 는 앱이 수집 · 전송하는 데이터를 Play Console 의 **앱 콘텐츠 → 데이터 보안** 양식으로 신고하게 하며, **앱에 넣은 SDK 가 수집하는 데이터도 앱이 신고**해야 합니다. iOS 처럼 SDK 가 신고 파일을 함께 실어 보낼 방법이 없으므로, 아래 사실을 보고 양식을 채우세요. `appendix` 로 넘기는 값(사용자 ID 등)은 SDK 와 별개로 앱의 신고 대상입니다.

### 14.1 SDK 가 보내는 데이터

카메라 영상 · 얼굴 이미지는 기기를 떠나지 않습니다. 나가는 것은 측정 중 영상과 센서에서 뽑은 아래 값이며, Vultus 서버 한 곳으로만 암호화해 전송되고 서버에 저장되지 않습니다.

| 보내는 것 | 가장 가까운 양식 항목 |
| :--- | :--- |
| 얼굴 PPG 신호 | 건강 및 피트니스 → 건강 정보 |
| 머리 자세(3 축 각도 1 개) · 얼굴 위치 사각형 1 개 · 얼굴 특징점 좌표 · 표정 추론 결과(각성도 · 정서가 · 표정 범주 1 개) | 개인 정보 → 기타 정보 |
| 측정 중 기기 가속도 · 자이로(IMU) | 개인 정보 → 기타 정보 |
| 카메라 설정 · 상태, 주변 밝기, 화면 밝기, 기기 모델명 · OS 판본 · 칩셋, 프레임 수 통계 | 앱 정보 및 성능 → 진단 |

표정 추론 결과는 `enableFER(true)` 일 때만 보냅니다. 기기를 식별하는 값(광고 ID · Android ID · 시리얼)은 보내지 않습니다.

### 14.2 양식의 다른 질문에 필요한 사실

| 질문 | SDK 의 사실 |
| :--- | :--- |
| 수집하는가 | 예 — 기기 밖으로 전송됩니다 |
| 일시적으로 처리되는가 | 예 — 측정 결과를 산출하는 요청 안에서만 쓰이고 서버에 저장되지 않습니다. 일시적으로 처리되는 데이터는 양식에는 답하지만 스토어의 데이터 보안 섹션에는 표시되지 않습니다 |
| 전송 중 암호화 | 예 — 모두 TLS 로 보내고, 측정 데이터는 그 위에 한 번 더 암호화합니다([13.1](#131-sdk-가-사용하는-암호화)) |
| 용도 | 앱 기능(측정 결과 산출) — 광고 · 추적에 쓰지 않습니다 |
| 선택 여부 | 측정을 하면 항상 보냅니다(표정 추론 결과만 앱이 끌 수 있습니다) |

**공유(제3자 전송) · 삭제 요청 문항은 앱과 Vultus 의 계약 관계에 따라 답이 달라집니다** — 판단은 배포자의 몫이고, 필요한 정보는 HAII에 문의해 주세요.

측정 데이터를 서버에 저장해야 한다면 위 답이 달라집니다. 저장은 별도 협의 대상이며, HAII 에 문의하면 그에 맞는 신고 · 개인정보 보호 안내를 따로 드립니다.

> iOS 판은 SDK 의 개인정보 보호 매니페스트(`PrivacyInfo.xcprivacy`)가 같은 사실을 따릅니다. Apple 은 요청 처리에만 쓰고 저장하지 않는 데이터를 「수집」으로 보지 않으므로 매니페스트의 수집 항목은 비어 있습니다. Google Play 는 같은 데이터를 수집으로 답하되 일시적 처리로 표시하게 합니다 — 기준이 다를 뿐 **두 스토어의 답은 같은 사실(서버에 저장하지 않음)을 가리킵니다.**
