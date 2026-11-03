# AgentDock — Tối ưu hiệu năng đa thiết bị

**Trạng thái:** Đặc tả hiệu năng và kiểm thử; chưa phải kết quả benchmark.

## 1. Mục tiêu

Tối ưu trải nghiệm trên nhiều thế hệ Android, từ máy cấu hình thấp đến cao, tablet và thiết bị gập. “Tối ưu cho mọi máy” nghĩa là thích ứng với khả năng thực tế, không phải cam kết mọi máy có cùng tốc độ/FPS. Không khóa tính năng cốt lõi theo thương hiệu.

## 2. Capability profile

Tạo DeviceCapabilityProfile từ tín hiệu có thể đọc hợp pháp:
- Android API, memory class/áp lực bộ nhớ.
- Thermal status và battery saver khi nền tảng cung cấp.
- Kích thước cửa sổ, density, refresh rate hỗ trợ.
- Mạng, dung lượng lưu trữ còn trống và benchmark runtime.

Có thể phân loại Resource-constrained, Balanced và High-capability. Profile thay đổi theo tình trạng thực tế; một thiết bị có thể chuyển sang tiết kiệm khi nóng hoặc pin yếu. Không dùng tên model làm điều kiện cứng; không thu IMEI, serial hoặc định danh phần cứng lâu dài.

## 3. Quy tắc thích ứng

### Mọi thiết bị
- Bảo đảm nội dung, bảo mật, quyền riêng tư và chức năng chính nhất quán.
- Compose state ổn định, lazy layouts, stable keys và pagination.
- Không chặn main thread bằng I/O, parsing, mã hóa hoặc xử lý tệp.
- Giới hạn cache, ảnh, lịch sử hiển thị và concurrency.
- Batch streaming; không render lại toàn bộ Markdown mỗi token.
- Tránh polling liên tục; backoff khi mất mạng.
- Khởi tạo thành phần khi cần; trì hoãn việc không thiết yếu sau first frame.
- Tôn trọng font scale, TalkBack, reduced motion, theme và cài đặt hệ thống.

### Máy tài nguyên thấp
- Giảm blur, parallax, particle, animation trang trí và preview tự động.
- Giảm item render trước, ảnh tải đồng thời và cache.
- Pagination, tải theo yêu cầu, giới hạn preview tệp lớn.
- Dừng animation ngoài màn hình, giảm refresh khi app ở nền.
- Không giữ tác vụ nền liên tục.
- Khi tính năng nâng cao không khả dụng, nêu rõ và cung cấp phương án cơ bản.

### Máy mạnh
- Chỉ tăng prefetch/cache/hiệu ứng khi benchmark chứng minh lợi ích và không gây tăng nhiệt/pin đáng kể.
- Không ép refresh rate cao; hệ điều hành và thiết bị quyết định refresh thực tế.

## 4. Cài đặt người dùng

Trong Cài đặt → Hiệu năng:
- Tự động (khuyến nghị).
- Tiết kiệm tài nguyên.
- Ưu tiên trải nghiệm.
- Giảm chuyển động.

Không lựa chọn nào được tắt bảo mật, quyền riêng tư, xác minh hoặc phê duyệt hành động.

## 5. Kiến trúc Android

- DeviceCapabilityProvider: đọc khả năng/trạng thái.
- PerformancePolicy: chuyển profile thành giới hạn UI/cache/concurrency.
- PerformanceSettingsRepository: lưu lựa chọn bằng DataStore.
- PerformanceState: state bất biến cho UI.
- Feature nhận policy qua DI; tránh truy cập system service rải rác.

Không tạo fingerprint thiết bị. Chỉ giữ thuộc tính cần thiết cho phiên hoặc profile tổng quát.

## 6. Đo lường

Đo baseline và trước/sau:
- Cold/warm startup, time to first frame.
- Frame timing, jank, scroll.
- P50/P90/P95 thao tác chính.
- Peak/steady memory, allocations, cache.
- CPU, network bytes, pin/nhiệt.
- Crash và ANR.

Dùng Macrobenchmark cho hành trình quan trọng; Baseline Profiles/Startup Profiles khi build hỗ trợ; đọc trace trước khi tối ưu. Không ghi hội thoại, prompt, tệp hoặc dữ liệu cá nhân vào telemetry hiệu năng.

## 7. Ma trận kiểm thử

- Android API 23 (minSdk) và phiên bản Android đang hỗ trợ phổ biến.
- Thiết bị RAM thấp/CPU yếu.
- Thiết bị tầm trung.
- Thiết bị mạnh/tần số quét cao.
- Tablet, foldable, resize/split-screen.
- Mạng chậm/mất mạng, pin yếu, trạng thái nhiệt cao.

Emulator là bổ sung, không thay thế đo pin, nhiệt và hiệu năng trên thiết bị thật.

## 8. Acceptance criteria

- Không crash/ANR do danh sách lớn, stream dài hoặc preview file trong giới hạn công bố.
- Màn hình chính theo baseline đo được, không làm tác vụ không cần thiết trước first frame.
- UI vẫn phản hồi khi mạng chậm/backend lỗi.
- Cache và concurrency có giới hạn.
- Tiết kiệm/reduced motion giảm hiệu ứng đúng.
- Layout hoạt động portrait, landscape, split-screen và cửa sổ rộng.
- Không cam kết FPS chung; báo kết quả theo thiết bị và phép đo.
- Mỗi release có benchmark/regression summary; changelog chỉ ghi tối ưu đã được xác minh.
