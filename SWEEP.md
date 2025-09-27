# SWEEP.md - Development Commands & Setup

## 🚀 Running the Application

### Prerequisites
- Java 21+ installed
- MySQL running on localhost:3306
- Environment variables configured in `.env` file

### Start Backend
```bash
# Give execute permission to gradlew (first time only)
chmod +x gradlew

# Run application
./gradlew bootRun
```

### Environment Variables Setup
- Copy `.env.example` to `.env`
- Update values in `.env` file with your actual credentials

#### Dotenv Integration (Recommended)
The project now uses **dotenv-java** library to automatically load `.env` file:
- ✅ Automatically loads `.env` file on startup
- ✅ No manual configuration needed
- ✅ Sensitive data protected (`.env` in `.gitignore`)

#### For IntelliJ IDEA (Alternative):
**Option 1: EnvFile Plugin**
1. Install "EnvFile" plugin
2. Run → Edit Configurations → EnvFile tab → Add `.env` file

**Option 2: Manual Environment Variables**
1. Run → Edit Configurations → Environment Variables
2. Add all variables from `.env` file manually

**Option 3: Use application-dev.properties**
- Already created with all values
- Set active profile to 'dev' in run configuration

## 🧪 Testing OAuth2

### Test Authorization URL
```bash
curl http://localhost:8080/api/auth/google/url
```

### Swagger UI
```
http://localhost:8080/swagger-ui.html
```

## 🔧 Common Issues

### Permission Denied on gradlew
```bash
chmod +x gradlew
```

### Port 8080 Already in Use
```bash
# Find process using port 8080
lsof -ti:8080

# Kill the process (replace PID with actual process ID)
kill -9 <PID>
```

### Database Connection Issues
- Check MySQL is running
- Verify database `db_taskmanagement` exists
- Update password in `.env` if needed

### CORS Issues (Frontend can't connect to Backend)
- CORS configuration is already implemented in `CorsConfig.java`
- Restart backend after any CORS changes
- Frontend should be able to call `http://localhost:8080/api/**`

### Compilation Errors
- Check Java version (requires 21+)
- Clean and rebuild: `./gradlew clean build`

## 📚 Documentation
- Setup Guide: `docs/GOOGLE_OAUTH2_SETUP.md`
- Environment Setup: `docs/ENVIRONMENT_SETUP.md`
- Implementation Summary: `docs/IMPLEMENTATION_SUMMARY.md`