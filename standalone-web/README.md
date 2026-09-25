# 📝 Ứng Dụng Chấm Thi Trắc Nghiệm AI - Bản Web App Độc Lập Cho Máy Tính

Bản Web App độc lập này được thiết kế để chạy **trực tiếp 100% trên máy tính (PC / Laptop)** sử dụng **Webcam máy tính** hoặc **Webcam USB cắm rời**, chấm trắc nghiệm bằng mô hình **Gemini AI Vision** và xuất bảng điểm Excel mà **hoàn toàn không cần qua điện thoại**.

---

## 🚀 Hướng Dẫn Khởi Chạy Nhanh

### Cách 1: Chạy tự động (Khuyên dùng - 1 Click)
* **Trên Windows:** Click đúp vào file `run_windows.bat`. Hệ thống sẽ tự khởi chạy máy chủ cục bộ và tự động mở trình duyệt `http://localhost:8000`.
* **Trên macOS / Linux:** Mở terminal trong thư mục này và chạy `./run_mac_linux.sh` (hoặc `python3 server.py`).

### Cách 2: Mở trực tiếp bằng trình duyệt
* Click đúp chuột vào file `index.html` để mở bằng Google Chrome, Microsoft Edge, Cốc Cốc hoặc Brave.
*(Lưu ý: Để trình duyệt cho phép truy cập Webcam đầy đủ, nên dùng Cách 1 hoặc mở qua localhost).*

### Cách 3: Đưa lên mạng Internet (Hosting cá nhân)
* Bạn có thể tải toàn bộ thư mục này lên **GitHub Pages**, **Vercel**, **Netlify**, hoặc bất kỳ Hosting web nào để sử dụng online ở bất cứ đâu.

---

## 🔑 Hướng Dẫn Cấu Hình API Key
1. Bấm vào nút **"Cấu hình API Key"** ở góc trên bên phải màn hình.
2. Dán mã **Gemini API Key** của bạn (Lấy miễn phí tại [Google AI Studio](https://aistudio.google.com/app/apikey)).
3. Bấm **"Lưu Key"**. Khóa API sẽ được lưu an toàn trong trình duyệt của bạn (LocalStorage).

---

## 📹 Sử Dụng Webcam & Chấm Điểm
1. **Chọn Webcam:** Ở thanh chọn thiết bị phía trên khung hình, chọn Webcam USB gắn ngoài hoặc Camera trước của laptop.
2. **Cấu hình Đề thi:**
   - Chọn loại bài thi: *Khoanh tròn trực tiếp trên đề* hoặc *Học sinh điền vào bảng ô đáp án*.
   - Nhập số lượng câu hỏi và hệ số điểm/câu.
   - Nhập đáp án chuẩn (Master Key) cho từng câu (A, B, C, D).
   - Bấm **"Lưu Cấu Hình"**.
3. **Chấm điểm:**
   - Đặt bài thi của học sinh trước Webcam sao cho bài thi nằm trọn vẹn trong khung ngắm.
   - Bấm nút **"📸 Chụp & Chấm Điểm Ngay"** (hoặc chọn **"📤 Tải Ảnh Bài Thi Từ Ổ Đĩa"** nếu đã có sẵn file ảnh).
   - Hệ thống AI sẽ đọc tên học sinh, phân tích vết bút khoanh và tính điểm ngay lập tức.
4. **Xem kết quả & Xuất Excel:**
   - Bấm vào bất kỳ bài đã chấm trong danh sách để xem chi tiết từng câu đúng/sai kèm ảnh bài thi gốc.
   - Bấm nút **"📊 Xuất Excel"** để tải file `.xlsx` thống kê điểm số và đáp án từng câu của cả lớp.
