
# AgentDock

**Autonomous Agent Control Center** — ứng dụng Android để giao việc, theo dõi tiến trình và phê duyệt hành động của các AI Agent.

> Trạng thái hiện tại: Android foundation 0.1.0 và backend 0.2.0 database foundation. Backend đã có schema PostgreSQL, Alembic migration và kiểm soát cách ly tenant bằng RLS; chưa có đăng nhập, API tạo tác vụ từ xa hay Agent thực thi. Android vẫn lưu bản nháp cục bộ.

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

- Python 3.14, FastAPI, Pydantic Settings; dependency được pin trong `backend/requirements.txt`.
- SQLAlchemy async + asyncpg và Alembic; schema đầu tiên lưu tenants, users, memberships, tasks, task events và idempotency records.
- PostgreSQL RLS được bật và FORCE trên toàn bộ bảng tenant-scoped; runtime role tách khỏi migration role và không có `BYPASSRLS`.
- Tenant context sử dụng transaction-local setting; backend phải kiểm tra membership ở server trước khi gắn tenant context.
- Backend CI chạy migration và kiểm tra RLS trên PostgreSQL thật, đồng thời chạy pytest và build Docker image.
- `GET /api/v1/health/live` chỉ xác nhận process đang hoạt động; API chưa kết nối DB khi khởi động, chưa có readiness endpoint, authentication, remote task API hay Agent runtime.

## Ký APK release

Workflow `Signed Android APK` chỉ chạy thủ công. Tạo bốn GitHub Actions repository secrets trong `Settings → Secrets and variables → Actions`:

| Secret | Giá trị |
|---|---|
| `AGENTDOCK_KEYSTORE_BASE64` | Nội dung file JKS đã mã hóa Base64 |
| `AGENTDOCK_KEYSTORE_PASSWORD` | Mật khẩu kho khóa |
| `AGENTDOCK_KEY_ALIAS` | Alias của entry ký |
| `AGENTDOCK_KEY_PASSWORD` | Mật khẩu của private key thuộc alias đã chọn; có thể khác mật khẩu JKS |

Workflow giải mã JKS vào thư mục tạm của runner, đặt quyền file hạn chế, kiểm tra alias và thử mở private key bằng `keytool -certreq` trước khi build release; sau đó chạy `apksigner verify`. Nếu kiểm tra private key thất bại, workflow chỉ báo tên secret cần kiểm tra, không in giá trị. File `.jks` bị loại khỏi Git bằng `.gitignore`.

Không đổi `applicationId` hoặc thay khóa ký giữa các bản phát hành. Workflow tự dùng `github.run_number` làm `versionCode` tăng dần; bạn chỉ nhập `versionName`. Chưa phát hành bản cài đặt production cho tới khi xác minh được keystore mới trong Actions và kiểm tra cập nhật đè trên thiết bị.

## Bố cục repository

```text
app/src/main/java/com/slg/agentdock/
  data/                 Local data store, model and repository
  domain/               Validation
  ui/                   App shell, screens, components and theme
backend/
  app/                  FastAPI, configuration, database models and sessions
  migrations/           Versioned Alembic schema migrations
  tests/                API and PostgreSQL tenant-isolation tests
.github/workflows/      Android CI, backend CI and signed APK workflows
docs/                   Architecture and delivery notes
```
