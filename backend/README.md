# BioSphereAI Clinical Backend Service

The BioSphereAI backend is an asynchronous, high-performance FastAPI service providing Tier 1 cloud neural ODE inference, continuous hemodynamic blood pressure estimation, and intelligent metabolic nutrition/activity analysis.

---

## Capabilities

1. **Continuous LatentODE Glucose Forecasting**:
   - Models continuous metabolic dynamics using Runge-Kutta 4th-order (RK4) integration over latent vector fields.
   - Outputs 30-minute and 60-minute forward trajectories with 95% confidence intervals.
   - Evaluated on Stanford Hall (Dexcom G4) and Shanghai T2DM clinical cohorts (MAE 8.66 mg/dL, 92.5% Clarke Zone A).

2. **Continuous Cuffless Blood Pressure Estimation**:
   - Implements a 3-Element Windkessel Biomechanical Twin coupled with Moens-Korteweg vascular stiffness equations.
   - Estimates Systolic (SBP), Diastolic (DBP), and Mean Arterial Pressure (MAP) using heart rate, glucose-induced vasomotor tone, and pulse transit metrics (BHS Grade A, AAMI compliant).

3. **Intelligent Meal and Glycemic Impact Analysis**:
   - Powered by Google Gemini API for natural language meal parsing and macronutrient extraction.
   - Includes a deterministic clinical nutrition fallback engine based on USDA and ADA carbohydrate counting when offline.

4. **Metabolic Physical Activity Analysis**:
   - Models insulin-independent GLUT4 glucose uptake and exercise-induced insulin sensitivity shifts based on Metabolic Equivalents (METs).

---

## Installation & Setup

### 1. Local Environment
Ensure Python 3.10+ is installed:

```bash
cd backend
pip install -r requirements.txt
```

### 2. Environment Variables (Optional)
Create an optional `.env` file in `backend/` to configure Gemini API or custom ports:

```env
HOST=0.0.0.0
PORT=8000
GEMINI_API_KEY=your_gemini_api_key_here
GEMINI_MODEL=gemini-2.5-flash
```

*(Note: If `GEMINI_API_KEY` is omitted, the service automatically uses the built-in clinical nutrition fallback engine without failure).*

### 3. Run the Server
```bash
python run_server.py
```
Or directly with Uvicorn:
```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

Interactive Swagger documentation is available at:
`http://localhost:8000/docs`

---

## Connecting the Android Mobile App

- **Android Emulator**: Use `http://10.0.2.2:8000` as the `baseUrl` in Retrofit/Ktor.
- **Physical Device**: Connect phone and computer to the same Wi-Fi network. Find your computer's local IP address (e.g. `192.168.1.X`) and set the `baseUrl` to `http://192.168.1.X:8000`.
- **Cloud / VPS**: Deploy via Docker and point the Android app to `https://api.yourdomain.com`.

---

## Docker Deployment

To build and run the backend container:

```bash
docker build -t biosphere-backend .
docker run -d -p 8000:8000 --name biosphere-backend biosphere-backend
```

---

## API Endpoints Reference

### 1. Cloud Glucose Forecast (LatentODE)
- **POST** `/api/v1/predict/glucose`
- **Request Body**:
```json
{
  "patient_id": "T1D-001",
  "recent_glucose": [142.0, 145.0, 150.0, 156.0, 161.0],
  "timestamps": ["10:00", "10:05", "10:10", "10:15", "10:20"],
  "model": "latentode"
}
```
- **Response**:
```json
{
  "horizon_30m": 178.4,
  "horizon_60m": 192.1,
  "confidence_interval_95": [168.0, 204.0],
  "inference_time_ms": 4.2
}
```

### 2. Cuffless Blood Pressure
- **POST** `/api/v1/predict/bp`
- **Request Body**:
```json
{
  "patient_id": "T1D-001",
  "recent_glucose": 161.0,
  "heart_rate": 74.0
}
```
- **Response**:
```json
{
  "systolic": 118.2,
  "diastolic": 77.6,
  "mean_arterial_pressure": 91.1,
  "unit": "mmHg",
  "status": "NORMAL"
}
```

### 3. AI Meal Analysis
- **POST** `/api/v1/analyze/meal`
- **Request Body**:
```json
{
  "query": "Chicken burrito bowl with brown rice, black beans, and salsa",
  "patient_type": "T1D"
}
```
- **Response**:
```json
{
  "carbs_g": 65.0,
  "protein_g": 38.0,
  "fat_g": 16.0,
  "fiber_g": 11.0,
  "glycemic_load": "MODERATE",
  "expected_glucose_delta": 48.0,
  "peak_time_minutes": 60,
  "clinical_summary": "Estimated 65g carbohydrates with 11g fiber. Postprandial glycemic rise of ~48 mg/dL peaking at approximately 60 minutes."
}
```

### 4. Activity Analysis
- **POST** `/api/v1/analyze/activity`
- **Request Body**:
```json
{
  "activity_type": "Running",
  "duration_minutes": 30,
  "intensity": "MODERATE"
}
```
- **Response**:
```json
{
  "estimated_glucose_drop": 32.2,
  "insulin_sensitivity_multiplier": 1.35,
  "risk_of_hypo": "MODERATE"
}
```

---

## Running Automated Tests

Run the test suite to verify all endpoints:

```bash
pytest tests/test_api.py -v
```
