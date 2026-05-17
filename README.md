# Java Project

Carpool route planning project.

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

```powershell
mvn clean package
```

### Run

```powershell
mvn spring-boot:run -Dspring-boot.run.arguments="--googleMaps.apiKey=YOUR_GOOGLE_MAPS_API_KEY --server.port=8080"
```

### Endpoint

- `POST http://localhost:8080/api/route`
- `POST http://localhost:8080/api/weekly-route`
- `GET http://localhost:8080/actuator/health` (canonical Spring Boot health endpoint)

### Bruno Example JSON Body

```json
{
	"driverName": "Dunnes Stores Athlone",
	"driverEircode": "N37 A1B2",
	"officeName": "Athlone Business Campus",
	"officeEircode": "N37 XR90",
	"tripType": "MORNING_TO_OFFICE",
	"colleagues": [
		{ "name": "SuperValu Golden Island", "eircode": "N37 C3D4" },
		{ "name": "B&Q Athlone", "eircode": "N37 E5F6" },
		{ "name": "Athlone Regional Sports Centre", "eircode": "N37 G7H8" }
	]
}
```

The response includes the ordered pickup list in `pickupOrder`.

### Bruno Example JSON Body (Weekly)

```json
{
	"officeName": "Athlone Business Campus",
	"officeEircode": "N37 XR90",
	"members": [
		{ "name": "Dunnes Stores Athlone", "eircode": "N37 A1B2", "canDrive": true },
		{ "name": "SuperValu Golden Island", "eircode": "N37 C3D4", "canDrive": true },
		{ "name": "B&Q Athlone", "eircode": "N37 E5F6", "canDrive": true },
		{ "name": "Athlone Regional Sports Centre", "eircode": "N37 G7H8", "canDrive": true }
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
- The response contains one pickup order per day under `days`.

### Sample Response (Morning)

```json
{
	"tripType": "MORNING_TO_OFFICE",
	"driver": {
		"id": "driver",
		"name": "Dunnes Stores Athlone",
		"eircode": "N37 A1B2",
		"latitude": 53.4230,
		"longitude": -7.9404
	},
	"office": {
		"id": "office",
		"name": "Athlone Business Campus",
		"eircode": "N37 XR90",
		"latitude": 53.4180,
		"longitude": -7.9330
	},
	"pickupOrder": [
		{ "id": "p1", "name": "SuperValu Golden Island", "eircode": "N37 C3D4", "latitude": 53.4271, "longitude": -7.9141 },
		{ "id": "p2", "name": "B&Q Athlone", "eircode": "N37 E5F6", "latitude": 53.4312, "longitude": -7.9280 },
		{ "id": "p3", "name": "Athlone Regional Sports Centre", "eircode": "N37 G7H8", "latitude": 53.4306, "longitude": -7.9459 }
	],
	"dropoffOrder": [],
	"totalEstimatedKm": 4.8,
	"totalEstimatedDurationMinutes": 14
}
```

### Sample Response (Evening)

```json
{
	"tripType": "EVENING_TO_HOME",
	"driver": {
		"id": "driver",
		"name": "Dunnes Stores Athlone",
		"eircode": "N37 A1B2",
		"latitude": 53.4230,
		"longitude": -7.9404
	},
	"office": {
		"id": "office",
		"name": "Athlone Business Campus",
		"eircode": "N37 XR90",
		"latitude": 53.4180,
		"longitude": -7.9330
	},
	"pickupOrder": [],
	"dropoffOrder": [
		{ "id": "p1", "name": "SuperValu Golden Island", "eircode": "N37 C3D4", "latitude": 53.4271, "longitude": -7.9141 },
		{ "id": "p2", "name": "B&Q Athlone", "eircode": "N37 E5F6", "latitude": 53.4312, "longitude": -7.9280 },
		{ "id": "p3", "name": "Athlone Regional Sports Centre", "eircode": "N37 G7H8", "latitude": 53.4306, "longitude": -7.9459 }
	],
	"totalEstimatedKm": 5.1,
	"totalEstimatedDurationMinutes": 15
}
```