# 🔑 HƯỚNG DẪN LẤY GEMINI API KEY CHO AI AGENT

## Bước 1: Truy cập Google AI Studio
1. Mở trình duyệt và vào: https://makersuite.google.com/app/apikey
2. Đăng nhập bằng Gmail account của bạn

## Bước 2: Tạo API Key
1. Click nút **"Create API key"**
2. Chọn Google Cloud project (hoặc tạo mới)
3. Copy API key được tạo (dạng: AIzaSyD...)

## Bước 3: Thêm vào file .env
1. Mở file `.env` trong project
2. Tìm dòng: `GEMINI_API_KEY=your-gemini-api-key-here`
3. Thay thế bằng: `GEMINI_API_KEY=AIzaSyD...` (key thật của bạn)

## Bước 4: Restart ứng dụng
```bash
# Stop ứng dụng hiện tại (Ctrl+C)
# Sau đó chạy lại:
./gradlew bootRun
```

## Bước 5: Test AI Agent
Sau khi restart, test API endpoints:

### 1. Kiểm tra setup:
```bash
curl http://localhost:8080/api/ai-agent/setup/test
```

### 2. Test Gemini connection:
```bash
curl http://localhost:8080/api/ai-agent/setup/test-gemini
```

### 3. Test chat (cần JWT token):
```bash
curl -X POST http://localhost:8080/api/ai-agent/messages \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{"content": "Xin chào AI!"}'
```

## 🚨 Lưu ý quan trọng:
- API key miễn phí có giới hạn: 60 requests/phút
- Không chia sẻ API key với ai khác
- Không commit API key lên Git

## 📋 Các API endpoints có sẵn:

### User APIs:
- `POST /api/ai-agent/messages` - Gửi tin nhắn
- `GET /api/ai-agent/messages` - Lấy lịch sử chat
- `DELETE /api/ai-agent/messages` - Xóa session
- `GET /api/ai-agent/session/stats` - Thống kê session

### Admin APIs:
- `POST /api/ai-agent/admin/takeover/{userId}` - Admin can thiệp
- `GET /api/ai-agent/admin/active-sessions` - Xem sessions đang hoạt động
- `GET /api/ai-agent/admin/audit/logs` - Xem audit logs

### System APIs:
- `GET /api/ai-agent/system/health` - Kiểm tra health
- `GET /api/ai-agent/setup/test` - Test setup
- `GET /api/ai-agent/setup/guide` - Hướng dẫn setup

## ✅ Sau khi setup xong:
1. User có thể chat với AI Agent
2. Admin có thể monitor và can thiệp
3. Hệ thống ghi log đầy đủ cho audit
4. AI Agent hiểu context của user (role, organization, project)
