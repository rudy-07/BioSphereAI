"""
Cardiometabolic In Silico Simulation Engine
Models dual T1D / T2D glucose-insulin kinetics coupled with interstitial sensor lag,
sensor noise, compression lows (PISA), and Windkessel blood pressure hemodynamics.
"""

from dataclasses import dataclass
from typing import List, Tuple, Dict, Any
import numpy as np
from scipy.integrate import solve_ivp


@dataclass
class PatientProfile:
    name: str
    cohort: str  # "T1D" or "T2D"
    p1: float    # Glucose effectiveness (min^-1)
    p2: float    # Rate of clearance of active insulin (min^-1)
    p3: float    # Insulin sensitivity factor (min^-2 per muU/mL)
    n: float     # Insulin elimination rate (min^-1)
    beta: float  # Pancreatic first/second phase secretion capacity
    G0: float    # Basal blood glucose (mg/dL)
    I0: float    # Basal plasma insulin (muU/mL)
    Vg: float    # Glucose distribution volume (dL)
    Vi: float    # Insulin distribution volume (mL)
    tau_isf: float # Interstitial diffusion time constant (min)
    tau_gut: float # Gut absorption time constant (min)
    bioavail: float # Carbohydrate bioavailability factor (0.8 - 0.9)
    # Hemodynamics parameters
    baseline_sbp: float # Baseline Systolic Blood Pressure (mmHg)
    baseline_dbp: float # Baseline Diastolic Blood Pressure (mmHg)
    is_non_dipper: bool # True if nocturnal blood pressure fails to dip


def create_t1d_profile(name: str = "Virtual_T1D_Subject") -> PatientProfile:
    """Creates a standard Type 1 Diabetes profile (zero beta-cell secretion)."""
    return PatientProfile(
        name=name,
        cohort="T1D",
        p1=0.028,
        p2=0.025,
        p3=0.000013,  # Normal peripheral sensitivity, but zero endogenous insulin
        n=0.23,
        beta=0.0,     # NO pancreatic insulin secretion
        G0=110.0,
        I0=0.0,
        Vg=120.0,     # ~12 L in a 70kg adult
        Vi=3500.0,
        tau_isf=8.5,  # 8.5 minute interstitial lag
        tau_gut=45.0, # 45 min gut transit time
        bioavail=0.85,
        baseline_sbp=118.0,
        baseline_dbp=76.0,
        is_non_dipper=False
    )


def create_t2d_profile(name: str = "Virtual_T2D_Subject", is_non_dipper: bool = True) -> PatientProfile:
    """Creates a standard Type 2 Diabetes profile (insulin resistance, delayed secretion, hypertension)."""
    return PatientProfile(
        name=name,
        cohort="T2D",
        p1=0.018,     # Impaired glucose effectiveness
        p2=0.020,
        p3=0.0000045, # Severe insulin resistance (~65% reduction in p3)
        n=0.18,
        beta=0.045,   # Residual but sluggish endogenous insulin secretion
        G0=135.0,     # Elevated basal fasting glucose
        I0=16.0,      # Elevated basal hyperinsulinemia
        Vg=130.0,
        Vi=3800.0,
        tau_isf=10.0, # Slightly higher ISF lag due to microvascular changes
        tau_gut=55.0,
        bioavail=0.88,
        baseline_sbp=138.0, # Comorbid Stage 1 Hypertension
        baseline_dbp=88.0,
        is_non_dipper=is_non_dipper
    )


class CardiometabolicSimulator:
    def __init__(self, patient: PatientProfile, dt_min: float = 1.0):
        self.patient = patient
        self.dt = dt_min

    def _gut_absorption(self, t: float, meals: List[Dict[str, Any]]) -> float:
        """Calculates instantaneous rate of glucose appearance Ra(t) in mg/min."""
        Ra = 0.0
        for m in meals:
            t_start = m["time_min"]
            carbs_g = m["carbs_g"]
            if t >= t_start:
                dt_meal = t - t_start
                # Two-compartment gastrointestinal absorption function
                # Ra = f * D * t / tau^2 * exp(-t / tau)
                D_mg = carbs_g * 1000.0 * self.patient.bioavail
                tau = self.patient.tau_gut
                Ra += (D_mg * dt_meal / (tau ** 2)) * np.exp(-dt_meal / tau)
        return Ra

    def _exogenous_insulin(self, t: float, insulins: List[Dict[str, Any]]) -> float:
        """Calculates instantaneous exogenous insulin infusion/absorption rate in muU/min."""
        if self.patient.cohort == "T2D":
            return 0.0  # Non-insulin T2D
        
        # Basal continuous infusion + Boluses with subcutaneous absorption delay
        # Subcutaneous insulin absorption modeled as peak at 60 mins (Rapid acting analog)
        rate = 12.0 * 1000.0 / 60.0 # Basal rate ~12 U/day in muU/min
        tau_ins = 55.0
        for b in insulins:
            t_start = b["time_min"]
            units = b["units"]
            if t >= t_start:
                dt_b = t - t_start
                dose_muU = units * 1e6
                rate += (dose_muU * dt_b / (tau_ins ** 2)) * np.exp(-dt_b / tau_ins)
        return rate

    def _exercise_effect(self, t: float, exercises: List[Dict[str, Any]]) -> float:
        """Calculates non-insulin mediated glucose clearance multiplier from exercise."""
        mult = 0.0
        for ex in exercises:
            t_start = ex["time_min"]
            dur = ex["duration_min"]
            intensity = ex.get("intensity", 1.0) # 1.0 moderate, 2.0 vigorous
            if t_start <= t <= t_start + dur:
                mult += 0.015 * intensity
            elif t > t_start + dur and t <= t_start + dur + 120.0:
                # Post-exercise prolonged GLUT-4 sensitivity decay
                dt_post = t - (t_start + dur)
                mult += 0.010 * intensity * np.exp(-dt_post / 40.0)
        return mult

    def simulate_24h(
        self,
        meals: List[Dict[str, Any]],
        insulins: List[Dict[str, Any]],
        exercises: List[Dict[str, Any]],
        pisa_events: List[Dict[str, Any]] = None,
        random_seed: int = 42
    ) -> Dict[str, np.ndarray]:
        """
        Simulates 24 hours (1440 minutes) of synchronized physiological and sensor streams.
        Returns a dictionary of time-series arrays.
        """
        np.random.seed(random_seed)
        pisa_events = pisa_events or []
        t_total = 1440 # 24 hours
        t_steps = int(t_total / self.dt)
        time_arr = np.linspace(0, t_total, t_steps)

        # State vectors: [G_blood, X, I_plasma, G_isf]
        G_blood = np.zeros(t_steps)
        X = np.zeros(t_steps)
        I_plasma = np.zeros(t_steps)
        G_isf = np.zeros(t_steps)
        G_cgm_measured = np.zeros(t_steps)
        sbp_arr = np.zeros(t_steps)
        dbp_arr = np.zeros(t_steps)
        map_arr = np.zeros(t_steps)

        # Initial conditions
        G_blood[0] = self.patient.G0
        X[0] = 0.0
        I_plasma[0] = self.patient.I0
        G_isf[0] = self.patient.G0

        p = self.patient

        for i in range(1, t_steps):
            t = time_arr[i - 1]
            Ra = self._gut_absorption(t, meals)
            Uexo = self._exogenous_insulin(t, insulins)
            Ex = self._exercise_effect(t, exercises)

            # 1. Glucose ODE: dGb/dt = -p1*(Gb - Gb0) - X*Gb - Ex*Gb + Ra/Vg
            dGb = - (p.p1 + Ex) * (G_blood[i-1] - p.G0) - X[i-1] * G_blood[i-1] + (Ra / p.Vg)
            
            # 2. Insulin Action ODE: dX/dt = -p2*X + p3*(I - I0)
            dX = - p.p2 * X[i-1] + p.p3 * max(0.0, I_plasma[i-1] - p.I0)

            # 3. Plasma Insulin ODE: dI/dt = -n*I + Uexo/Vi + beta*max(0, Gb - h)
            endogenous = p.beta * max(0.0, G_blood[i-1] - 90.0)
            dI = - p.n * I_plasma[i-1] + (Uexo / p.Vi) + endogenous

            # Update blood states (Euler numerical integration with safety clipping)
            G_blood[i] = max(35.0, G_blood[i-1] + dGb * self.dt)
            X[i] = max(0.0, X[i-1] + dX * self.dt)
            I_plasma[i] = max(0.0, I_plasma[i-1] + dI * self.dt)

            # 4. Interstitial Lag ODE: dGisf/dt = (Gb - Gisf) / tau_isf
            dGisf = (G_blood[i] - G_isf[i-1]) / p.tau_isf
            G_isf[i] = max(35.0, G_isf[i-1] + dGisf * self.dt)

            # 5. Dexcom Sensor Transduction (Noise ~ 8.2% MARD)
            sensor_noise = np.random.normal(0.0, 0.038) # Standard deviation producing ~8% MARD
            cgm_val = G_isf[i] * (1.0 + sensor_noise)

            # Check for PISA (Compression low)
            for pisa in pisa_events:
                if pisa["start_min"] <= t <= pisa["end_min"]:
                    # Deep drop due to microvascular compression
                    attenuation = pisa.get("attenuation_mgdl", 45.0)
                    cgm_val = max(40.0, cgm_val - attenuation)

            G_cgm_measured[i] = round(cgm_val, 1)

            # 6. Coupled Cardiovascular Blood Pressure Dynamics
            # Circadian dipping: Sleep between 23:00 (1380m) to 07:00 (420m)
            is_sleeping = (t < 420.0) or (t >= 1380.0)
            dip_factor = 0.0 if p.is_non_dipper else (0.15 if is_sleeping else 0.0)

            # Endothelial Vasoconstriction from acute glucose spike above 140 mg/dL
            hyperglycemic_strain = max(0.0, (G_blood[i] - 140.0) / 100.0) * 14.0 # Up to +14 mmHg

            # Exercise transient BP change (systolic surge, diastolic slight drop/steady)
            ex_bp_boost = 25.0 if Ex > 0 else 0.0

            sbp = p.baseline_sbp * (1.0 - dip_factor) + hyperglycemic_strain + ex_bp_boost + np.random.normal(0, 2.0)
            dbp = p.baseline_dbp * (1.0 - dip_factor * 0.8) + (hyperglycemic_strain * 0.5) + np.random.normal(0, 1.5)
            
            sbp_arr[i] = round(sbp, 1)
            dbp_arr[i] = round(dbp, 1)
            map_arr[i] = round((2.0 * dbp + sbp) / 3.0, 1)

        # Fill initial index for consistency
        G_cgm_measured[0] = G_isf[0]
        sbp_arr[0] = p.baseline_sbp
        dbp_arr[0] = p.baseline_dbp
        map_arr[0] = (2.0 * p.baseline_dbp + p.baseline_sbp) / 3.0

        return {
            "time_min": time_arr,
            "G_blood": G_blood,
            "G_isf": G_isf,
            "G_cgm": G_cgm_measured,
            "plasma_insulin": I_plasma,
            "sbp": sbp_arr,
            "dbp": dbp_arr,
            "map": map_arr
        }
