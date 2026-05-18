---
name: testing-carpool-api
description: End-to-end testing procedure for the Carpool Route Optimization API. Use when verifying route optimization, auth, MongoDB persistence, or health check changes.
---

# Testing the Carpool API

## Devin Secrets Needed

- `GOOGLE_MAPS_API_KEY` — Google Maps API key with Geocoding and Routes APIs enabled
- `MONGODB_URI` — MongoDB Atlas connection string (e.g. `mongodb+srv://user:pass@cluster.mongodb.net/?appName=Cluster0`)

## Prerequisites

1. Java 25 via SDKMAN: `source "$HOME/.sdkman/bin/sdkman-init.sh"`
2. Start the app:
   ```bash
   MONGODB_URI="$MONGODB_URI" GOOGLE_MAPS_API_KEY="$GOOGLE_MAPS_API_KEY" mvn spring-boot:run
   ```
3. App runs on `http://localhost:8080`

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

## Known Issues & Tips

- **MongoDB Atlas health check:** The default Spring Boot `MongoHealthIndicator` runs `{hello: 1}` against the `local` database, which Atlas users don't have access to. The custom `MongoHealthConfig` bean fixes this by querying the configured application database. If health shows `DOWN` with `AtlasError: Unauthorized on local`, check that `MongoHealthConfig.java` is present.
- **Atlas IP whitelist:** If MongoDB connection fails with SSL/TLS errors, ensure the machine's IP is whitelisted in Atlas Network Access (or use `0.0.0.0/0` for testing).
- **Feature availability per branch:** Not all features exist on every branch. Swagger UI, logging, OpenAPI, and CI are on separate PR branches. Check which PRs are merged before expecting those features to work.
- **Spring Boot 4 property naming:** Use `spring.mongodb.*` (not `spring.data.mongodb.*`) for MongoDB configuration.
- **All PRs target `develop`**, not `main`.
