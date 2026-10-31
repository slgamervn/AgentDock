# AgentDock

**Autonomous Agent Control Center** — ứng dụng Android để giao việc, theo dõi tiến trình và phê duyệt hành động của các AI Agent.

> Trạng thái hiện tại: Android foundation \`0.1.0\`. Ứng dụng có Home / Tabs / Settings, workspace độc lập, đổi tên tab, ghim tab, khôi phục tối đa 10 tab vừa đóng và lưu bản nháp cục bộ. Backend điều phối Agent chưa được triển khai; ứng dụng chưa gửi hoặc thực thi tác vụ từ xa.

## Nguyên tắc sản phẩm

- Android là **Control Plane**; tiến trình Agent dài hạn phải chạy ở backend/worker độc lập.
- Người dùng mô tả mục tiêu bằng ngôn ngữ tự nhiên; hệ thống tự lập kế hoạch và phân công Agent.
- Những hành động nhạy cảm như merge, deploy production, thay đổi quyền hoặc xóa dữ liệu cần được phê duyệt rõ ràng.
- Quyền thực thi do backend Policy Engine kiểm tra, không do mô hình AI tự cấp.
- API key và khóa ký không được nhúng trong APK hoặc commit vào repository.

## Android stack

- Kotlin (AGP built-in Kotlin), Jetpack Compose, Material 3, Navigation Compose.
- MVVM/UDF; ViewModel phụ thuộc vào \`WorkspaceRepository\`, không truy cập DataStore trực tiếp.
- DataStore Preferences là nguồn lưu cục bộ ở giai đoạn nền tảng.
- \`applicationId\`: \`com.slg.agentdock\` (giữ ổn định để hỗ trợ cập nhật đè).
- \`minSdk\`: 23; \`compileSdk\`: 36; \`targetSdk\`: 36.
- Jetpack Compose BOM \`2026.06.00\` (Compose 1.11) and Lifecycle \`2.10.0\` are pinned to the stable Android 16/API 36 toolchain; Compose 1.12 requires API 37.
- Android Gradle Plugin 9.5.0, Gradle 9.6.1, JDK 17.

Các phiên bản công cụ và thư viện được pin cụ thể; không dùng dynamic version (\`+\`).

## Build và kiểm thử

GitHub Actions cài Gradle 9.6.1 và Android SDK, sau đó chạy:

\`\`\`bash
gradle --no-daemon lintDebug testDebugUnitTest assembleDebug
\`\`\`

Workflow \`Android CI\` chạy khi push lên \`main\`, khi mở pull request và khi dispatch thủ công. Runner được cố định ở Ubuntu 24.04 để tránh thay đổi ngầm khi nhãn \`ubuntu-latest\` chuyển sang Ubuntu 26.04. APK debug được lưu thành Actions artifact trong 14 ngày.

Để mở bằng Android Studio, dùng Android Studio hỗ trợ AGP 9.5.0 và JDK 17 trở lên. Nếu build từ máy cục bộ, cài Gradle 9.6.1 hoặc tạo Gradle Wrapper từ bản Gradle đó trước khi chia sẻ project.

## Ký APK release

Workflow \`Signed Android APK\` chỉ chạy thủ công. Tạo bốn GitHub Actions repository secrets trong \`Settings → Secrets and variables → Actions\`:

| Secret | Giá trị |
|---|---|
| \`AGENTDOCK_KEYSTORE_BASE64\` | Nội dung file JKS đã mã hóa Base64 |
| \`AGENTDOCK_KEYSTORE_PASSWORD\` | Mật khẩu kho khóa |
| \`AGENTDOCK_KEY_ALIAS\` | Alias của entry ký |
| \`AGENTDOCK_KEY_PASSWORD\` | Mật khẩu private key |

Workflow giải mã JKS vào thư mục tạm của runner, đặt quyền file hạn chế, build release và chạy \`apksigner verify\`. File \`.jks\` bị loại khỏi Git bằng \`.gitignore\`.

Không đổi \`applicationId\` hoặc thay khóa ký giữa các bản phát hành. Workflow tự dùng \`github.run_number\` làm \`versionCode\` tăng dần; bạn chỉ nhập \`versionName\`. Chưa phát hành bản cài đặt production cho tới khi xác minh được alias/mật khẩu JKS trong Actions và kiểm tra cập nhật đè trên thiết bị.

## Bố cục repository

\`\`\`text
app/                 Android application
  src/main/          Manifest, resources, Kotlin source
  src/test/          Unit tests
.github/workflows/   Android CI and signed APK workflows
docs/                Architecture and delivery notes
\`\`\`

## Roadmap

1. Android foundation, tab state, local drafts, CI.
2. Backend API, authentication, durable task records and worker lifecycle.
3. LangGraph orchestration + OpenHands runtime in isolated execution sandboxes.
4. Realtime task events, approval workflow, Policy Engine and audit trail.
5. Provider/model routing, GitHub App, MCP, artifacts and project memory.
6. Evaluation, recovery, security, load tests, release and rollback.
