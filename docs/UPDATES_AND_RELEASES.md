# AgentDock — Trung tâm cập nhật và phát hành

**Trạng thái:** Đặc tả thiết kế; chưa triển khai trong ứng dụng.

## 1. Trải nghiệm

Trong Cài đặt → Giới thiệu → Cập nhật, người dùng xem versionName/versionCode, build, kênh phát hành, ngày phát hành; kiểm tra ngay; đọc changelog; xem dung lượng, yêu cầu Android và mức độ quan trọng; tải, theo dõi tiến trình, xác minh gói và chuyển sang trình cài đặt Android/Google Play.

Changelog chia nhóm: tính năng mới, sửa lỗi, bảo mật, tối ưu hiệu năng, tương thích. Không tự tải liên tục, không tự cài âm thầm và không tạo áp lực giả.

## 2. Nguồn phân phối

### Google Play
Nếu phát hành qua Google Play, ưu tiên Play In-App Updates:
- Flexible flow cho cập nhật thông thường.
- Immediate flow chỉ cho bản vá bảo mật hoặc thay đổi bắt buộc có lý do rõ.
- Google Play quản lý tải/cài và xác minh chữ ký.

### GitHub Releases / APK trực tiếp
Nếu phát hành ngoài Play:
- Tạo GitHub Release chính thức; không dùng CI artifact tạm thời làm nguồn production.
- Phát hành manifest metadata, changelog và APK.
- Manifest ký số bằng khóa riêng; app ghim khóa công khai.
- Manifest gồm versionCode/versionName, channel, ngày phát hành, minSdk, ABI/variant, kích thước, SHA-256, URL asset chính thức, mức độ bắt buộc và changelog.
- HTTPS và SHA-256 là cần thiết nhưng không đủ nếu manifest và APK cùng bị thay. Chữ ký manifest và xác minh danh tính ứng dụng là bắt buộc.
- Android Package Installer phải kiểm tra package ID và chứng thư ký tương thích. Không bỏ qua kiểm tra chữ ký.
- Chỉ nhận asset từ nguồn phát hành allowlist. Người dùng xác nhận cài theo cơ chế Android; giải thích quyền cài từ nguồn ngoài nếu Android yêu cầu.

Không trộn luồng Play và sideload trong cùng bản cài nếu chưa có thiết kế kênh rõ.

## 3. Luồng cập nhật

1. Đọc version hiện tại và channel.
2. Lấy metadata HTTPS với timeout/cache.
3. Xác minh chữ ký manifest bằng khóa tin cậy.
4. Kiểm tra versionCode tăng đơn điệu, channel, minSdk, ABI và tương thích.
5. Hiển thị changelog, lựa chọn tải/cài.
6. Tải vào vùng riêng; hỗ trợ tiếp tục/tải lại có giới hạn.
7. Kiểm tra SHA-256 và kích thước.
8. Chuyển sang Google Play hoặc Android Package Installer.
9. Chỉ báo thành công sau khi xác minh phiên bản mới đã chạy.
10. Telemetry kết quả chỉ ở mức tối thiểu và theo lựa chọn quyền riêng tư.

## 4. Chính sách

- **Thông thường:** cập nhật ngay hoặc nhắc lại sau.
- **Khuyến nghị:** giải thích lợi ích, không khóa app.
- **Bảo mật quan trọng:** nêu mức độ và khuyến nghị sớm.
- **Bắt buộc:** chỉ khi phiên bản cũ có lỗ hổng nghiêm trọng hoặc không còn tương thích an toàn với backend; nêu lý do, thời hạn và hỗ trợ.
- Không dùng minimumSupportedVersion để ép cập nhật tính năng thông thường.
- Không tự hạ cấp APK. Rollback cần phát hành bản mới có versionCode cao hơn hoặc phương án phục hồi tương thích được thiết kế trước.

## 5. Chuỗi phát hành an toàn

- Khóa ký APK và khóa ký manifest tách biệt; lưu trong secret manager/HSM phù hợp; không commit Git hoặc nhúng private key trong APK.
- Giới hạn quyền workflow phát hành và yêu cầu review cho production.
- Duy trì continuity của chứng thư ký Android.
- Kiểm tra dependency, secret, chữ ký và tạo SBOM trước phát hành.
- Manifest có issuedAt, expiresAt, channel, versionCode, hash, URL asset, minSdk và chữ ký.
- Chống replay/rollback bằng versionCode đơn điệu, thời hạn manifest và channel được ghim.
- Không tin redirect tùy ý, URL lấy từ hội thoại hoặc server không allowlist.
- Không tải và thực thi script tùy ý như một phần cập nhật.

## 6. Kênh và tương thích

Tối thiểu stable và preview; preview phải tự nguyện, cảnh báo rõ và không trộn với stable. Mỗi release khai báo versionCode tăng đơn điệu, versionName, channel, publishedAt, minimumSupportedVersionCode (chỉ khi cần), minSdk, ABI/variant, artifact, changelog, SHA-256, kích thước, chữ ký manifest và phạm vi tương thích backend.

Không phát hành APK không tương thích minSdk/ABI; cân nhắc split APK/App Bundle thay vì gói universal quá lớn khi phù hợp.

## 7. UI states

- Đang dùng bản mới nhất / Có bản mới.
- Đang tải / tạm dừng / tiếp tục / lỗi tải.
- Đang xác minh chữ ký/hash.
- Không tương thích thiết bị/Android.
- Không đủ dung lượng / mất mạng.
- Chờ xác nhận installer.
- Cài thành công (chỉ sau xác minh) / người dùng hủy.
- Manifest sai chữ ký, hết hạn hoặc không hợp lệ: dừng, không cài.
- Server lỗi: giữ bản hiện tại hoạt động nếu không có chính sách bảo mật bắt buộc.

## 8. Trạng thái repository

- app/build.gradle.kts đã có versionCode/versionName từ Gradle properties.
- .github/workflows/release-apk.yml build APK ký số, kiểm tra chữ ký và upload GitHub Actions artifact giữ 30 ngày.
- Workflow chưa tạo GitHub Release, chưa publish manifest/changelog có cấu trúc và chưa có updater.
- AndroidManifest.xml hiện chưa khai báo INTERNET.
- Chưa xác nhận có module kiểm tra cập nhật hoặc kiểm thử cài đè thực tế.

Đây là tính năng mới, cần triển khai theo lát cắt; không được mô tả là đã có.

## 9. Acceptance criteria

- Manifest sai chữ ký, hết hạn, sai channel hoặc versionCode thấp đều bị từ chối.
- APK sai hash, package ID hoặc chứng thư không tương thích không được cài.
- Bản hợp lệ cài đè không mất dữ liệu.
- Mạng lỗi, thiếu dung lượng, app bị đóng hoặc người dùng hủy không làm hỏng bản hiện tại.
- Changelog đúng versionCode/versionName.
- Không có private key/token phát hành trong APK, log hoặc Git.
- Test stable/preview, minSdk, ABI, nâng cấp từ bản cũ và phục hồi sau lỗi.
- Người dùng xem được thông tin mà không bị ép tải/cài.
