---
name: testing-carpool-api
description: End-to-end testing procedure for the Carpool Route Optimization API. Use when verifying route optimization, auth, MongoDB persistence, or health check changes.
---

# Testing the Carpool API

## Devin Secrets Needed

- `GOOGLE_MAPS_API_KEY` — Google Maps API key with Geocoding and Routes APIs enabled
- `MONGODB_URI` — (optional) MongoDB Atlas connection string. For E2E testing, prefer the local Docker MongoDB instead of Atlas.

## Prerequisites

1. Java 25 via SDKMAN: `source "$HOME/.sdkman/bin/sdkman-init.sh"`
2. Start local MongoDB via Docker Compose:
   ```bash
   docker compose up -d
   ```
3. Start the app against local MongoDB:
   ```bash
   MONGODB_URI="mongodb://localhost:27017/carpool" GOOGLE_MAPS_API_KEY="$GOOGLE_MAPS_API_KEY" mvn spring-boot:run
   ```
4. App runs on `http://localhost:8080`

**Important:** Always use local Docker MongoDB for E2E testing — never test against the production Atlas database.

## Test Flows

### 1. Route Optimization (core feature)

```bash
# Single route
curl -s -X POST http://localhost:8080/api/route \
  -H "Content-Type: application/json" \
  -d '{"country":"IE","driverName":"Luiz","driverPostalCode":"D08 XY12","officeName":"Office","officePostalCode":"D02 YX88","tripType":"MORNING_TO_OFFICE","colleagues":[{"name":"Alice","postalCode":"D04 AB34"}]}'
# Expect: HTTP 200, JSON with tripType, driver, pickupOrder, totalEstimatedKm, cacheStats

# Weekly route
curl -s -X POST http://localhost:8080/api/weekly-route \
  -H "Content-Type: application/json" \
  -d '{"country":"IE","officeName":"Office","officePostalCode":"D02 YX88","tripType":"MORNING_TO_OFFICE","members":[{"name":"Luiz","postalCode":"D08 XY12","canDrive":true},{"name":"Alice","postalCode":"D04 AB34","canDrive":true},{"name":"Bob","postalCode":"D06 CD56","canDrive":false}]}'
# Expect: HTTP 200, 5 days, fair driver rotation among canDrive=true members
```

### 2. Bean Validation

```bash
# Empty body → HTTP 400
curl -s -X POST http://localhost:8080/api/route -H "Content-Type: application/json" -d '{}'

# Empty members → HTTP 400
curl -s -X POST http://localhost:8080/api/weekly-route -H "Content-Type: application/json" \
  -d '{"country":"IE","officeName":"Office","officePostalCode":"D02 YX88","tripType":"MORNING_TO_OFFICE","members":[]}'
```

### 3. Auth Flow (JWT + MongoDB)

```bash
# Register
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" -d '{"username":"testuser","password":"TestPass123"}'
# Expect: HTTP 201, {"token": "eyJ..."}

# Login
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" -d '{"username":"testuser","password":"TestPass123"}'
# Expect: HTTP 200, {"token": "eyJ..."} (different from registration token)

# Save postal code (use token from login)
curl -s -X POST http://localhost:8080/api/user/saved-postal-codes \
  -H "Content-Type: application/json" -H "Authorization: Bearer <TOKEN>" \
  -d '{"label":"Home","postalCode":"D08 XY12","country":"IE"}'
# Expect: HTTP 201, includes MongoDB ObjectId

# List postal codes
curl -s http://localhost:8080/api/user/saved-postal-codes -H "Authorization: Bearer <TOKEN>"
# Expect: HTTP 200, array with saved entry

# Unauthenticated → HTTP 403
curl -s http://localhost:8080/api/user/saved-postal-codes

# Login with wrong password → HTTP 401
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" -d '{"username":"testuser","password":"WrongPass"}'
# Expect: 401

# Login with non-existent user → HTTP 401
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" -d '{"username":"noexist","password":"Pass123"}'
# Expect: 401

# Register duplicate username → HTTP 400
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" -d '{"username":"testuser","password":"TestPass123"}'
# Expect: 400
```

### 4. Health Endpoint

```bash
curl -s http://localhost:8080/actuator/health | python3 -m json.tool
# Expect: status "UP", mongo.status "UP", mongo.details.database "carpool"
```

### 5. Swagger UI (if OpenAPI PR is merged)

- Browser: `http://localhost:8080/swagger-ui.html` (redirects to `/swagger-ui/index.html`)
- API spec: `GET /v3/api-docs`
- If Swagger returns 404, it means the OpenAPI PR hasn't been merged into the current branch

## Teardown

After testing, stop the local MongoDB container:
```bash
docker compose down
```

To also remove persisted data:
```bash
docker compose down -v
```

## Known Issues & Tips

- **Always use local Docker MongoDB for testing** — never run E2E tests against Atlas. Use `MONGODB_URI="mongodb://localhost:27017/carpool"`.
- **MongoDB Atlas health check:** The default Spring Boot `MongoHealthIndicator` runs `{hello: 1}` against the `local` database, which Atlas users don't have access to. The custom `MongoHealthConfig` bean fixes this by querying the configured application database. If health shows `DOWN` with `AtlasError: Unauthorized on local`, check that `MongoHealthConfig.java` is present.
- **Atlas IP whitelist:** If MongoDB connection fails with SSL/TLS errors, ensure the machine's IP is whitelisted in Atlas Network Access (or use `0.0.0.0/0` for testing).
- **Auth error responses:** Login with wrong credentials returns HTTP 401 (not 500). Registration with a duplicate username returns HTTP 400.
- **All PRs target `develop`**, not `main`.
