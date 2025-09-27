# 🚀 HƯỚNG DẪN SỬ DỤNG GOOGLE CONSOLE CHO GEMINI API

## Bạn đã bật Generative Language API ✅

Vì bạn đã bật API trong Google Console, có 2 cách lấy key:

### Cách 1: Từ Google Console (Khuyến nghị)
1. Vào: https://console.cloud.google.com/apis/credentials
2. Chọn project có Generative Language API đã bật
3. Click "Create Credentials" → "API Key"
4. Copy API key được tạo

### Cách 2: Từ AI Studio (Dễ hơn)
1. Vào: https://makersuite.google.com/app/apikey
2. Chọn cùng project đã bật API
3. Create API key

## 🔧 Cấu hình trong .env:

```env
# Thay thế dòng này:
GEMINI_API_KEY=your-actual-gemini-key-here

# Bằng key thật từ Google Console hoặc AI Studio
GEMINI_API_KEY=AIzaSyD...
```

## 🎯 Lợi ích khi dùng Google Console:

### 1. Tích hợp OAuth2 + Gemini:
- User đăng nhập bằng Google OAuth2
- AI Agent sử dụng Gemini API cùng project
- Centralized billing và monitoring

### 2. Enterprise features:
- Quota management
- Usage analytics 
- Security policies
- Team access control

### 3. Production ready:
- Service accounts
- Environment-specific keys
- Advanced monitoring

## ✅ Setup hoàn chỉnh bạn đã có:

1. **Google OAuth2** ✅ - User authentication
2. **Generative Language API** ✅ - Gemini AI 
3. **Redis** ✅ - Memory layer
4. **Database** ✅ - User context
5. **JWT** ✅ - Session management

## 📋 Chỉ cần làm:

1. **Lấy Gemini API Key** từ Console hoặc AI Studio
2. **Cập nhật .env** với key thật
3. **Restart app** 
4. **Test AI Agent** với user đã login Google OAuth2

Bạn sẽ có hệ thống AI Agent hoàn chỉnh với Google ecosystem integration!
