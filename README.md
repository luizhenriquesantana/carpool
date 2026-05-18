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
mvn spring-boot:run -Dspring-boot.run.arguments="--googleMaps.apiKey=YOUR_GOOGLE_MAPS_API_KEY --server.port=8080"
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

This application supports local JWT login and OAuth2 login via provider configuration.

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

Use `MONGODB_URI` to point the app at MongoDB Atlas or a local MongoDB instance.

Example environment variables:

```bash
export GOOGLE_MAPS_API_KEY=your_google_maps_key
export MONGODB_URI="mongodb+srv://user:password@cluster0.mongodb.net/carpool?retryWrites=true&w=majority"
export JWT_SECRET="a-very-long-secret-value"
```
