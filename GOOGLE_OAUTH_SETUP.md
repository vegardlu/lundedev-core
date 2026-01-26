# Google OAuth2 Setup Guide

## Step 1: Go to Google Cloud Console

1. Open [Google Cloud Console](https://console.cloud.google.com/)
2. Sign in with your Google account

## Step 2: Create a New Project (or select existing)

1. Click the project dropdown at the top of the page
2. Click **"New Project"**
3. Enter a project name (e.g., `lundedev-core`)
4. Click **"Create"**

## Step 3: Enable OAuth Consent Screen

1. In the left sidebar, go to **"APIs & Services"** → **"OAuth consent screen"**
2. Select **"External"** (for testing) and click **"Create"**
3. Fill in the required fields:
   - **App name**: `Lundedev Core`
   - **User support email**: Your email
   - **Developer contact email**: Your email
4. Click **"Save and Continue"**
5. On the **Scopes** page, click **"Add or Remove Scopes"**
   - Select: `openid`, `email`, `profile`
   - Click **"Update"**
6. Click **"Save and Continue"**
7. On **Test users** page, click **"Add Users"**
   - Add your Google email address
   - Click **"Save and Continue"**
8. Click **"Back to Dashboard"**

## Step 4: Create OAuth2 Credentials

1. In the left sidebar, go to **"APIs & Services"** → **"Credentials"**
2. Click **"+ Create Credentials"** → **"OAuth client ID"**
3. Select **"Web application"** as the application type
4. Enter a name (e.g., `Lundedev Core Web Client`)
5. Under **"Authorized JavaScript origins"**, add:
   ```
   http://localhost:8080
   ```
6. Under **"Authorized redirect URIs"**, add:
   ```
   http://localhost:8080/login/oauth2/code/google
   ```
7. Click **"Create"**

## Step 5: Copy Your Credentials

After creating, you'll see a popup with:
- **Client ID**: `xxxx.apps.googleusercontent.com`
- **Client Secret**: `GOCSPX-xxxx`

**Keep these safe!**

## Step 6: Configure the Application

### Option A: Environment Variables (Recommended)

```bash
export GOOGLE_CLIENT_ID=your-client-id-here
export GOOGLE_CLIENT_SECRET=your-client-secret-here
```

Then run:
```bash
./gradlew bootRun
```

### Option B: application-local.properties (Not for Git)

Create `src/main/resources/application-local.properties`:

```properties
spring.security.oauth2.client.registration.google.client-id=your-client-id-here
spring.security.oauth2.client.registration.google.client-secret=your-client-secret-here
springdoc.swagger-ui.oauth.client-id=your-client-id-here
```

Run with:
```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

## Step 7: Test the Application

1. Start the application
2. Open [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
3. Click the **"Click here to login with Google"** link in the API description
4. Log in with your Google account
5. You'll be redirected back to Swagger UI
6. Try the `/api/hello` endpoint - it should return your user info!

## Endpoints

| Endpoint | Auth Required | Description |
|----------|---------------|-------------|
| `/api/hello` | ✅ Yes | Returns hello with user info |
| `/api/public/hello` | ❌ No | Public hello endpoint |
| `/swagger-ui.html` | ❌ No | Swagger UI |

## Troubleshooting

### "Access blocked: This app's request is invalid" / "redirect_uri_mismatch"
- Check that the redirect URI exactly matches: `http://localhost:8080/login/oauth2/code/google`

### "Error 401: deleted_client"
- Your OAuth client was deleted. Create a new one.

### "Error 403: access_denied"
- Make sure your email is added as a test user in the OAuth consent screen.
