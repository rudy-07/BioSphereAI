from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.config import settings
from app.routers import glucose, bp, meal, activity

app = FastAPI(
    title=settings.PROJECT_NAME,
    version=settings.VERSION,
    description=(
        "BioSphereAI Multi-Modal Cardiometabolic Backend API: "
        "Continuous LatentODE Glucose Forecasting, Cuffless Blood Pressure Estimation, "
        "and Nutrition/Activity Clinical Impact Analysis."
    ),
    docs_url="/docs",
    redoc_url="/redoc"
)

# Enable CORS for Android client, emulator (10.0.2.2), and local network devices
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Register routers under /api/v1
app.include_router(glucose.router, prefix=settings.API_V1_STR)
app.include_router(bp.router, prefix=settings.API_V1_STR)
app.include_router(meal.router, prefix=settings.API_V1_STR)
app.include_router(activity.router, prefix=settings.API_V1_STR)

@app.get("/", tags=["System Health"])
def root():
    return {
        "service": settings.PROJECT_NAME,
        "version": settings.VERSION,
        "status": "ONLINE",
        "documentation": "/docs"
    }

@app.get("/health", tags=["System Health"])
def health_check():
    return {
        "status": "HEALTHY",
        "cloud_glucose_forecaster": "LatentODE (Neural ODE / RK4)",
        "hemodynamic_engine": "Windkessel 3-Element Biomechanical Twin",
        "meal_intelligence": "Gemini API with Clinical Nutrition Fallback",
        "activity_engine": "GLUT4 Metabolic Equivalent Engine"
    }
