# Carpool Route Planning

Small Spring Boot API for planning carpool routes with Google Maps geocoding and routing.

## Folders
- `src/main/java/`: Java source code.
- `src/main/resources/`: Application configuration.

## REST API

The API server entry point is `com.santana.carpool.CarpoolApplication`.

Routing mode:
- Pickup ordering uses Google Routes API driving travel cost (road-aware).
- If a Routes call fails for a leg, the service falls back to Haversine distance for that leg.
- Fallback speed is configurable via `googleMaps.fallbackKmh` (default: 35.0 km/h).

### Compile

```bash
mvn clean package
```

### Run

```bash
export GOOGLE_MAPS_API_KEY=your_google_maps_key
export MONGODB_URI="mongodb://localhost:27017/carpool"
export JWT_SECRET="a-very-long-secret-value"
mvn spring-boot:run
```

### Endpoints

- `POST http://localhost:8080/api/route`
- `POST http://localhost:8080/api/weekly-route`
- `POST http://localhost:8080/api/auth/register`
- `POST http://localhost:8080/api/auth/login`
- `GET http://localhost:8080/api/user/saved-postal-codes`
- `POST http://localhost:8080/api/user/saved-postal-codes`
- `DELETE http://localhost:8080/api/user/saved-postal-codes/{id}`
- `GET http://localhost:8080/actuator/health`

## Authentication

This application supports both JWT-based authentication with username/password and OAuth2 social login (Google and GitHub). Sessions expire after 15 minutes.

**Public endpoints:**
- `/api/route` - route planning
- `/api/weekly-route` - weekly route planning
- `/api/auth/register` - create new local user
- `/api/auth/login` - login local user
- `/oauth2/authorization/google` - initiate Google OAuth2 login
- `/oauth2/authorization/github` - initiate GitHub OAuth2 login
- `/actuator/health` - health check

**Protected endpoints:**
- `/api/user/**` - user-specific saved postal codes

### Register a local user

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "bruno",
    "password": "StrongPassword123!"
  }'
```

### Login a local user

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "bruno",
    "password": "StrongPassword123!"
  }'
```

Response:

```json
{
  "token": "eyJhbGciOi..."
}
```

### Use the JWT token

```bash
curl http://localhost:8080/api/user/saved-postal-codes \
  -H "Authorization: Bearer eyJhbGciOi..."
```

### OAuth2 Social Login

The application supports OAuth2 login with Google and GitHub. Users are automatically created or updated upon successful OAuth2 authentication.

**Google OAuth2 Setup:**
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create OAuth2 client ID for Web application
3. Add authorized redirect URI: `http://localhost:8080/login/oauth2/code/google` (local) or `https://your-app.fly.dev/login/oauth2/code/google` (production)
4. Set environment variables: `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`

**GitHub OAuth2 Setup:**
1. Go to GitHub → Settings → Developer settings → OAuth Apps
2. Create new OAuth App with callback URL: `http://localhost:8080/login/oauth2/code/github` (local) or `https://your-app.fly.dev/login/oauth2/code/github` (production)
3. Set environment variables: `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET`

**Initiate OAuth2 Login:**
```bash
# Google
curl http://localhost:8080/oauth2/authorization/google

# GitHub
curl http://localhost:8080/oauth2/authorization/github
```

### Save a postal code for the authenticated user

```bash
curl -X POST http://localhost:8080/api/user/saved-postal-codes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOi..." \
  -d '{
    "label": "Home",
    "postalCode": "N37 A1B2",
    "country": "IE"
  }'
```

### Delete a saved postal code

```bash
curl -X DELETE http://localhost:8080/api/user/saved-postal-codes/{id} \
  -H "Authorization: Bearer eyJhbGciOi..."
```

## Route requests

### Single route request

```json
{
  "country": "IE",
  "driverName": "Dunnes Stores Athlone",
  "driverPostalCode": "N37 A1B2",
  "officeName": "Athlone Business Campus",
  "officePostalCode": "N37 XR90",
  "tripType": "MORNING_TO_OFFICE",
  "colleagues": [
    { "name": "SuperValu Golden Island", "postalCode": "N37 C3D4" },
    { "name": "B&Q Athlone", "postalCode": "N37 E5F6" },
    { "name": "Athlone Regional Sports Centre", "postalCode": "N37 G7H8" }
  ]
}
```

The response includes the ordered pickup or dropoff list.

### Weekly route request

```json
{
  "country": "IE",
  "officeName": "Athlone Business Campus",
  "officePostalCode": "N37 XR90",
  "members": [
    { "name": "Dunnes Stores Athlone", "postalCode": "N37 A1B2", "canDrive": true },
    { "name": "SuperValu Golden Island", "postalCode": "N37 C3D4", "canDrive": true },
    { "name": "B&Q Athlone", "postalCode": "N37 E5F6", "canDrive": true },
    { "name": "Athlone Regional Sports Centre", "postalCode": "N37 G7H8", "canDrive": true }
  ],
  "days": [
    { "day": "Monday", "fixedDriverName": "Dunnes Stores Athlone", "tripType": "MORNING_TO_OFFICE" },
    { "day": "Tuesday", "tripType": "MORNING_TO_OFFICE" },
    { "day": "Wednesday", "tripType": "MORNING_TO_OFFICE" },
    { "day": "Thursday", "tripType": "MORNING_TO_OFFICE" },
    { "day": "Friday", "tripType": "EVENING_TO_HOME" }
  ]
}
```

Notes:
- `fixedDriverName` is optional per day.
- `tripType` is optional and defaults to `MORNING_TO_OFFICE`.
- Supported values: `MORNING_TO_OFFICE`, `EVENING_TO_HOME`.
- If omitted, the API auto-assigns the least-used eligible driver for fairness.

## Configuration

### Required environment variables

- `GOOGLE_MAPS_API_KEY` - Google Maps API key for geocoding and routing
- `MONGODB_URI` - MongoDB connection string (defaults to `mongodb://localhost:27017/carpool`)
- `JWT_SECRET` - Secret key for JWT token signing (minimum 32 characters)
- `GOOGLE_CLIENT_ID` - Google OAuth2 client ID (for social login)
- `GOOGLE_CLIENT_SECRET` - Google OAuth2 client secret (for social login)
- `GITHUB_CLIENT_ID` - GitHub OAuth2 client ID (for social login)
- `GITHUB_CLIENT_SECRET` - GitHub OAuth2 client secret (for social login)

### MongoDB setup

The application requires MongoDB for user authentication and saved postal codes. The `/actuator/health` endpoint will check MongoDB connectivity and report DOWN if the database is unreachable.

**Local MongoDB:**
```bash
# Start MongoDB locally on default port
docker run -d -p 27017:27017 mongo:latest
```

**MongoDB Atlas:**
```bash
export MONGODB_URI="mongodb+srv://user:password@cluster0.mongodb.net/carpool?retryWrites=true&w=majority"
```

### Full example

```bash
export GOOGLE_MAPS_API_KEY=your_google_maps_key
export MONGODB_URI="mongodb://localhost:27017/carpool"
export JWT_SECRET="a-very-long-secret-value-at-least-32-chars"
mvn spring-boot:run
```

## Fly.io Deployment

The application can be deployed to Fly.io using Docker. The project includes a multi-stage Dockerfile that builds the JAR during the Docker build process.

### Prerequisites

- Install Fly.io CLI: `brew install flyctl` (macOS)
- Authenticate: `flyctl auth signup` and `flyctl auth login`

### Deployment steps

1. **Initialize the Fly.io app:**
   ```bash
   flyctl launch
   ```
   - Select or create an organization
   - Choose a region (e.g., London `lhr`)
   - Skip database setup (MongoDB Atlas is already configured)
   - Set the app name (e.g., `carpool-route-planning`)

2. **Set environment variables:**
   ```bash
   flyctl secrets set GOOGLE_MAPS_API_KEY=your_google_maps_key
   flyctl secrets set MONGODB_URI="mongodb+srv://user:password@cluster0.mongodb.net/carpool?retryWrites=true&w=majority"
   flyctl secrets set JWT_SECRET="a-very-long-secret-value-at-least-32-chars"
   flyctl secrets set GOOGLE_CLIENT_ID=your_google_oauth2_client_id
   flyctl secrets set GOOGLE_CLIENT_SECRET=your_google_oauth2_client_secret
   flyctl secrets set GITHUB_CLIENT_ID=your_github_oauth2_client_id
   flyctl secrets set GITHUB_CLIENT_SECRET=your_github_oauth2_client_secret
   ```

3. **Deploy:**
   ```bash
   flyctl deploy
   ```

The application will be built using the multi-stage Dockerfile (Maven build + JAR copy) and deployed to Fly.io. The app is configured to:
- Listen on `0.0.0.0:8080` for Fly.io proxy routing
- Run health checks on `/actuator/health`
- Keep at least 1 machine running at all times

### Access the deployed app

- API: `https://your-app-name.fly.dev`
- Swagger UI: `https://your-app-name.fly.dev/swagger-ui.html`
- Health check: `https://your-app-name.fly.dev/actuator/health`
