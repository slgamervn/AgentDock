# AgentDock — Chính sách quyền riêng tư

**Trạng thái:** Bản dự thảo thiết kế; chưa được phê duyệt pháp lý và chưa mô tả hoạt động production đã xác minh.  
**Ngày hiệu lực:** [BỔ SUNG SAU KHI PHÊ DUYỆT]  
**Đơn vị kiểm soát/xử lý dữ liệu:** [BỔ SUNG]  
**Địa chỉ/email quyền riêng tư:** [BỔ SUNG]

> Chỉ công bố sau khi đối chiếu với luồng dữ liệu thực tế, nhà cung cấp, retention, địa điểm xử lý và cơ sở xử lý. Đây không phải tư vấn pháp lý.

## 1. Cam kết

AgentDock áp dụng thu thập tối thiểu, giới hạn mục đích, minh bạch, bảo mật theo thiết kế và quyền kiểm soát của người dùng. Không bán dữ liệu cá nhân. Không dùng hội thoại riêng tư để huấn luyện/tinh chỉnh mô hình theo mặc định.

Cung cấp tính năng AI, lưu bộ nhớ, phân tích vận hành và đóng góp cải thiện AI là các mục đích riêng, phải được mô tả và kiểm soát riêng.

## 2. Nhóm dữ liệu có thể xử lý

| Nhóm | Ví dụ | Nguyên tắc |
|---|---|---|
| Tài khoản | ID nội bộ, phương thức đăng nhập, tenant/membership | Không lưu mật khẩu nhà cung cấp định danh |
| Nội dung | Prompt, hội thoại, tệp, mã nguồn, kết quả | Chỉ dùng theo mục đích đã thông báo |
| Tác vụ | Trạng thái, sự kiện, phê duyệt, kết quả công cụ | Giới hạn theo tenant/quyền |
| Bộ nhớ | Sở thích/thông tin người dùng cho phép lưu | Xem, sửa, tắt, xuất, yêu cầu xóa |
| Tích hợp | Token ủy quyền, cấu hình kết nối | Lưu an toàn phía máy chủ, quyền tối thiểu |
| Kỹ thuật | Phiên bản app, Android API, crash, hiệu năng | Giảm định danh, giới hạn thời hạn |
| Hỗ trợ | Yêu cầu và trao đổi hỗ trợ | Chỉ nhân sự được phân quyền truy cập |

Không mặc định thu thập danh bạ, SMS, vị trí chính xác, micro, camera, màn hình hoặc cảm biến. Tính năng tương lai cần quyền thiết bị phải giải thích trước, chỉ bật khi người dùng chủ động yêu cầu và có cách dừng/rút quyền.

## 3. Mục đích và cơ sở xử lý

Mục đích có thể gồm: xác thực/tài khoản; thực thi và khôi phục tác vụ; phản hồi AI; bộ nhớ do người dùng bật; bảo vệ hệ thống; cải thiện chất lượng bằng feedback được cho phép; và thực hiện nghĩa vụ pháp lý.

Mỗi mục đích phải có cơ sở xử lý phù hợp theo pháp luật. Không gộp các mục đích không liên quan vào một checkbox chung.

## 4. Đóng góp hội thoại cải thiện AI

**Mặc định: TẮT.** Người dùng vẫn sử dụng được các tính năng cốt lõi nếu không đóng góp dữ liệu.

Các lựa chọn phải tách biệt:
1. Gửi phản hồi cho một câu trả lời/hội thoại cụ thể.
2. Đóng góp dữ liệu được chọn để đánh giá chất lượng, an toàn và độ chính xác.
3. Huấn luyện/tinh chỉnh mô hình: lựa chọn riêng, giải thích mô hình/đối tác, phạm vi, mục đích và hệ quả; chỉ bật sau khi quy trình pháp lý/kỹ thuật được phê duyệt.

Yêu cầu:
- Chọn rõ từng hội thoại hoặc chủ động bật phạm vi đóng góp trong tương lai.
- Không hồi tố dùng hội thoại cũ khi chưa có sự cho phép phù hợp.
- Lọc thông tin nhận dạng, bí mật, thông tin xác thực và dữ liệu nhạy cảm; không coi bộ lọc là bảo đảm ẩn danh tuyệt đối.
- Không đưa mật khẩu, OTP, API key, token, thông tin thanh toán hoặc bí mật vào tập cải thiện.
- Lưu consent ledger gồm chủ thể, mục đích, phạm vi, phiên bản thông báo, thời điểm, nguồn và thời điểm rút lại.
- Rút lại phải ngăn sử dụng tiếp và kích hoạt quy trình xử lý dữ liệu còn trong phạm vi kiểm soát. Không hứa xóa khỏi mô hình đã phát hành nếu không có cơ chế chứng minh.
- Không dùng dữ liệu đóng góp để tạo hồ sơ quảng cáo hoặc bán cho bên thứ ba.

## 5. Nhà cung cấp AI và bên xử lý

Trước khi gửi nội dung tới nhà cung cấp bên ngoài, phải xác định tên/ vai trò, nhóm dữ liệu, mục đích, retention, khu vực xử lý, khả năng chuyển dữ liệu xuyên biên giới, điều khoản không dùng dữ liệu cho mục đích riêng (nếu có) và cách tắt/chọn nhà cung cấp.

Danh sách thực tế phải công bố tại [URL DANH SÁCH NHÀ CUNG CẤP]. Không tuyên bố “không lưu dữ liệu” nếu chưa có bằng chứng hợp đồng/cấu hình.

## 6. Lưu trữ và xóa

Xác định thời hạn riêng cho hội thoại/tệp, tác vụ/sự kiện, bộ nhớ, log bảo mật, bản sao lưu, dữ liệu đóng góp và hồ sơ hỗ trợ. Không lưu vô thời hạn chỉ vì dữ liệu có thể hữu ích cho AI. Khi hết mục đích/thời hạn, xóa hoặc khử định danh phù hợp; bản sao lưu phải có lịch hết hạn và quy trình xóa lan truyền. Dữ liệu phải lưu theo luật cần được giới hạn truy cập và phạm vi.

## 7. Bảo mật

Biện pháp mục tiêu:
- TLS khi truyền và mã hóa dữ liệu nhạy cảm khi lưu.
- Tenant isolation, kiểm tra quyền phía máy chủ và PostgreSQL RLS.
- Không nhúng API key, token nhà cung cấp hoặc khóa ký trong APK.
- Kho bí mật phía máy chủ, quyền tối thiểu và thu hồi.
- Redaction dữ liệu nhạy cảm khỏi log/trace.
- Phê duyệt hành động có tác động, audit có kiểm soát.
- Kiểm thử bảo mật, backup/restore và ứng phó sự cố.

Không có biện pháp nào bảo đảm an toàn tuyệt đối. Sự cố phải được xử lý và thông báo theo luật.

## 8. Quyền người dùng

Theo luật áp dụng và ngoại lệ hợp pháp, người dùng có thể có quyền được biết; đồng ý/từ chối/rút lại; truy cập; chỉnh sửa; yêu cầu cung cấp, xóa hoặc hạn chế xử lý; phản đối; khiếu nại/tố cáo/khởi kiện/yêu cầu bồi thường. AgentDock phải có kênh yêu cầu, xác minh tương xứng, theo dõi thời hạn và ghi nhận kết quả. Không yêu cầu thêm dữ liệu định danh không cần thiết.

## 9. Trẻ em và người chưa thành niên

Xác định nhóm tuổi mục tiêu và áp dụng điều kiện đồng ý của cha mẹ/người đại diện khi luật yêu cầu. Không bật đóng góp hội thoại/huấn luyện mặc định; không quảng cáo hành vi hoặc suy luận nhạy cảm; dùng ngôn ngữ dễ hiểu; giới hạn thu thập và retention; có cơ chế báo cáo và hỗ trợ. Không coi một nút “Đồng ý” là đủ trong mọi trường hợp.

## 10. Quyền thiết bị và chia sẻ màn hình

Nếu triển khai màn hình, hình ảnh, âm thanh hoặc thao tác thiết bị:
- Người dùng chủ động bắt đầu; trạng thái hoạt động hiển thị rõ và dễ dừng.
- Xin quyền Android theo nền tảng; không vượt qua FLAG_SECURE hoặc quyền riêng tư ứng dụng khác.
- Không giữ/tải nội dung vượt mục đích/thời hạn.
- Hành động có hậu quả đáng kể cần xác nhận.

## 11. Chuyển dữ liệu xuyên biên giới

Trước khi dùng hạ tầng/nhà cung cấp ngoài Việt Nam, lập bản đồ dữ liệu, xác định vai trò, mục đích, địa điểm xử lý, biện pháp bảo vệ và hồ sơ/đánh giá cần thiết theo Luật 91/2025/QH15, Nghị định 356/2025/NĐ-CP và quy định liên quan. Không giả định đặt máy chủ ở một khu vực tự động đáp ứng mọi nghĩa vụ.

## 12. Analytics và chẩn đoán

Không ghi nội dung hội thoại, prompt, tệp hoặc bí mật vào telemetry hiệu năng mặc định. Phân biệt telemetry cần thiết cho bảo mật/vận hành với analytics tùy chọn; giới hạn dữ liệu, retention và quyền truy cập; hỗ trợ tắt mục đích không thiết yếu khi cần.

## 13. Thay đổi và liên hệ

Mỗi phiên bản có mã, ngày công bố, ngày hiệu lực và lịch sử thay đổi. Thay đổi trọng yếu về mục đích, dữ liệu, nhà cung cấp hoặc chuyển dữ liệu phải được thông báo và xin lại đồng ý khi luật yêu cầu.

- Đơn vị phụ trách: [BỔ SUNG]
- Email quyền riêng tư: [BỔ SUNG]
- Địa chỉ nhận yêu cầu: [BỔ SUNG]
- Kênh thực hiện quyền dữ liệu: [BỔ SUNG]

## 14. Danh sách trước khi công bố

- [ ] Lập data inventory và sơ đồ Android → backend → worker → nhà cung cấp.
- [ ] Xác định vai trò kiểm soát/xử lý cho từng luồng.
- [ ] Xác minh hợp đồng, retention, vùng dữ liệu và chính sách từng nhà cung cấp AI.
- [ ] Xác định cơ sở xử lý và hồ sơ đánh giá tác động/chuyển dữ liệu cần thiết.
- [ ] Thiết lập consent ledger, rút lại đồng ý và propagation xóa.
- [ ] Kiểm thử quyền truy cập, RLS, redaction, retention, backup expiry và xóa.
- [ ] Hoàn tất quy trình dành cho người chưa thành niên.
- [ ] Bổ sung pháp nhân, liên hệ và thời hạn xử lý yêu cầu.
- [ ] Rà soát theo Luật 91/2025/QH15, Nghị định 356/2025/NĐ-CP và văn bản còn hiệu lực.
