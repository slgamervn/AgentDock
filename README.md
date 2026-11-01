
# AgentDock

**Autonomous Agent Control Center** — ứng dụng Android để giao việc, theo dõi tiến trình và phê duyệt hành động của các AI Agent.

> Trạng thái hiện tại: Android foundation 0.1.0 và backend bootstrap 0.1.0. Backend hiện chỉ có liveness endpoint; chưa có đăng nhập, database, tạo tác vụ hay thực thi Agent. Android vẫn lưu bản nháp cục bộ và chưa gửi tác vụ từ xa.

## Nguyên tắc sản phẩm

- Android là **Control Plane**; tiến trình Agent dài hạn phải chạy ở backend/worker độc lập.
- Người dùng mô tả mục tiêu bằng ngôn ngữ tự nhiên; hệ thống tự lập kế hoạch và phân công Agent.
- Những hành động nhạy cảm như merge, deploy production, thay đổi quyền hoặc xóa dữ liệu cần được phê duyệt rõ ràng.
- Quyền thực thi do backend Policy Engine kiểm tra, không do mô hình AI tự cấp.
- API key và khóa ký không được nhúng trong APK hoặc commit vào repository.

## Android stack

- Kotlin, Jetpack Compose, Material 3, Navigation Compose.
- Gradle Wrapper 9.6.1 with official distribution SHA-256 verification.
- MVVM/UDF; ViewModel phụ thuộc vào `WorkspaceRepository`, không truy cập DataStore trực tiếp.
- DataStore Preferences là nguồn lưu cục bộ ở giai đoạn nền tảng.
- `applicationId`: `com.slg.agentdock` (giữ ổn định để hỗ trợ cập nhật đè).
- `minSdk`: 23; `compileSdk`: 36; `targetSdk`: 36.
- Jetpack Compose BOM `2026.06.00` and Lifecycle `2.10.0` are pinned to the stable Android 16/API 36 toolchain.
- Android Gradle Plugin 9.5.0, Gradle 9.6.1, JDK 17.

## Build và kiểm thử

Android CI và workflow ký APK đều dùng Gradle Wrapper 9.6.1; `gradle/actions/setup-gradle@v6` tự xác thực Wrapper JAR và quản lý cache. Workflow ký APK chạy lint/unit tests trước khi nạp khóa ký.

Android CI chạy:
- `lintDebug`
- `testDebugUnitTest` (kiểm tra trạng thái ViewModel, vòng đời tab, debounce và lưu địa chỉ máy chủ)
- `assembleDebug`
- `connectedDebugAndroidTest` trên Android 36 Emulator để kiểm tra navigation và luồng lưu bản nháp.

Runner CI được cố định ở Ubuntu 24.04. APK debug được lưu thành Actions artifact trong 14 ngày.

## Backend foundation

- Python 3.14, FastAPI và Pydantic Settings; dependency runtime được pin trong backend/requirements.txt.
- API version đầu tiên nằm dưới /api/v1; hiện có GET /api/v1/health/live để xác nhận process đang hoạt động.
- Docker image chạy bằng user không đặc quyền; không chứa .env, credentials hoặc signing material.
- Backend CI cài dependencies, compile Python, chạy pytest và build Docker image.
- Chưa có database readiness probe vì PostgreSQL chưa được kết nối; không diễn giải liveness thành trạng thái sẵn sàng xử lý tác vụ.

## Ký APK release

Workflow `Signed Android APK` chỉ chạy thủ công. Tạo bốn GitHub Actions repository secrets trong `Settings → Secrets and variables → Actions`:

| Secret | Giá trị |
|---|---|
| `AGENTDOCK_KEYSTORE_BASE64` | Nội dung file JKS đã mã hóa Base64 |
| `AGENTDOCK_KEYSTORE_PASSWORD` | Mật khẩu kho khóa |
| `AGENTDOCK_KEY_ALIAS` | Alias của entry ký |
| `AGENTDOCK_KEY_PASSWORD` | Mật khẩu của private key thuộc alias đã chọn; có thể khác mật khẩu JKS |

Workflow giải mã JKS vào thư mục tạm của runner, đặt quyền file hạn chế, kiểm tra alias và thử mở private key bằng `keytool -certreq` trước khi build release; sau đó chạy `apksigner verify`. Nếu kiểm tra private key thất bại, workflow chỉ báo tên secret cần kiểm tra, không in giá trị. File `.jks` bị loại khỏi Git bằng `.gitignore`.

Không đổi `applicationId` hoặc thay khóa ký giữa các bản phát hành. Workflow tự dùng `github.run_number` làm `versionCode` tăng dần; bạn chỉ nhập `versionName`. Chưa phát hành bản cài đặt production cho tới khi xác minh được alias/mật khẩu JKS trong Actions và kiểm tra cập nhật đè trên thiết bị.

## Bố cục repository

```text
app/src/main/java/com/slg/agentdock/
  data/                 Local data store, model and repository
  domain/               Validation
  ui/
    components/         Shared app header, tab strip, navigation and empty state
    screens/            Home, Tabs, Workspace and Settings
    theme/              AgentDock color scheme
app/src/androidTest/    Compose UI navigation and draft-flow tests
app/src/test/           JVM unit tests
.github/workflows/      Android CI and signed APK workflows
docs/                   Architecture and delivery notes
```
