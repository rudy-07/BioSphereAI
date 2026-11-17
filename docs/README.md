# BioSphereAI: Master Documentation Suite & Meeting Quick-Reference

This documentation directory contains the complete technical, clinical, and architectural specifications for the BioSphereAI platform. Each document is structured with both **plain, non-specialist conceptual explanations** and **rigorous mathematical and clinical formulations**, concluding with an exhaustive **Defense Q&A** section designed to address examiner inquiries.

---

## Documentation Index

1. [**01. Glucose Forecasting: On-Device TinyML and Cloud LatentODE**](file:///D:/Projects/College%20projects/BioSphereAI/docs/01_GLUCOSE_FORECASTING_EDGE_AND_LATENTODE.md)
   - The clinical need for 30m and 60m predictive horizons.
   - On-Device TinyML Forecaster: Savitzky-Golay polynomial velocity, exponential momentum damping ($e^{-\lambda t}$), moving median baseline anchoring. (< 0.05 ms latency, 1.2 KB RAM).
   - Cloud LatentODE Engine: Continuous Neural ODEs, latent state formulation ($g, v, m$), 4th-order Runge-Kutta (RK4) integration, and 95% confidence intervals.
   - Evaluation of all tested models: LatentODE, Gluformer, Google TimesFM 2.5, TFT, N-HiTS, XGBoost, Naive persistence.
   - Why LatentODE and our custom edge forecaster were selected.

2. [**02. Control Barrier Functions and Automated Insulin Delivery Safety**](file:///D:/Projects/College%20projects/BioSphereAI/docs/02_CONTROL_BARRIER_FUNCTIONS_AND_AUTOMATED_INSULIN_DELIVERY.md)
   - Simglucose background: FDA-accepted UVa/Padova metabolic simulator (10 adults, 10 adolescents, 10 children, 13-compartment physiological differential equations).
   - The deadly asymmetry of insulin: over-bolusing, postprandial stacking, and nocturnal hypoglycemia.
   - Control Barrier Functions (CBF): Mathematical foundation in nonlinear control theory (Ames et al., forward invariance of safe sets $\mathcal{C}$).
   - Formulation: Forward projected safety margin $h(G, \dot{G})$, linear attenuation factor $\alpha$, and delivery states (`CLEAR`, `THROTTLED`, `SUSPENDED`).
   - Why we built a supervisory filter instead of replacing the controller.
   - Quantitative results: **76.0% reduction in pediatric hypoglycemia**, Time Below Range reduction, and 100% elimination of severe episodes (< 54 mg/dL).

3. [**03. Continuous Cuffless Blood Pressure Estimation**](file:///D:/Projects/College%20projects/BioSphereAI/docs/03_CONTINUOUS_CUFFLESS_BLOOD_PRESSURE_ESTIMATION.md)
   - Clinical context: Inflatable cuff limitations, cardiovascular disease (CVD) as the #1 killer in diabetes, and nocturnal non-dipping stroke risks.
   - Dataset & signals: PhysioNet MIMIC-II / UCI cuffless database (synchronized radial arterial lines, optical PPG, and lead-II ECG).
   - Biophysical derivation: Pulse Transit Time (PTT), Moens-Korteweg equation, and Hughes vascular elasticity equation.
   - Our Contribution: Cardiopulmonary 3-Element Windkessel Digital Twin (aortic compliance, total peripheral resistance, stroke volume, beat-to-beat pressure generation) coupled with acute glucose-induced vascular tone.
   - International clinical validation: British Hypertension Society (BHS) **Grade A** and **AAMI standard compliance** (SBP MAE 3.25 mmHg, DBP MAE 3.19 mmHg, $r > 0.86\text{--}0.94$).

4. [**04. Intelligent Nutrition and Metabolic Activity Analysis**](file:///D:/Projects/College%20projects/BioSphereAI/docs/04_INTELLIGENT_NUTRITION_AND_METABOLIC_ACTIVITY.md)
   - Postprandial kinetics: Carbohydrate counting errors and the "pizza effect" (delayed gastric emptying from fat and protein).
   - Two-tier meal logging architecture: Google Gemini API (natural language parsing, portion decomposition, macro extraction) paired with a mandatory deterministic clinical fallback engine (USDA/ADA carbohydrate rules).
   - Physical activity metabolism: Metabolic Equivalents of Task (METs) across 10 activity categories, non-insulin mediated GLUT4 skeletal glucose uptake, post-workout insulin sensitivity enhancement multipliers (1.05 to 1.50), and hypoglycemia risk stratification.

5. [**05. Clinical Datasets and International Evaluation Standards**](file:///D:/Projects/College%20projects/BioSphereAI/docs/05_CLINICAL_DATASETS_AND_EVALUATION_STANDARDS.md)
   - Why synthetic toy datasets were rejected (failure to capture biological delays, biofouling, and counter-regulatory rebounds).
   - Complete profiles of locked-in datasets: Stanford Hall 2018 (Dexcom G4, 105k readings, 57 patients, dietary challenges), Shanghai Clinical T2DM (109 inpatients, medications, lab HbA1c), MIMIC-II / UCI BP, and Simglucose UVa/Padova.
   - International standards explained: Clarke Error Grid Analysis (Zones A through E), Ambulatory Glucose Profile (AGP) consensus targets (TIR > 70%, TBR < 4%, %CV $\le 36\%$, GMI), and BHS/AAMI blood pressure benchmarks.

6. [**06. Software and Mobile App System Architecture**](file:///D:/Projects/College%20projects/BioSphereAI/docs/06_SOFTWARE_AND_MOBILE_APP_SYSTEM_ARCHITECTURE.md)
   - Two-tier Edge-Cloud system architecture.
   - FastAPI cloud backend (`backend/`): Endpoints, data schemas, asynchronous execution, and Docker containerization.
   - Native Android mobile app (`mobile/`): Jetpack Compose, Material 3, StateFlow, custom hardware-accelerated Canvas glucose chart.
   - Medical-grade UX: Clinical Light Mode (matching Dexcom G7/Stelo), Dark Mode (graphite metallic and pure AMOLED black), Manrope typography, zero emojis, zero em dashes, zero purple gradients.
   - 6 Virtual patient personas (Maya, Aarav, Robert for T1D; James, Priya, Linda for T2D).
   - Zero-Failure Offline Repository Pattern: Why and how `MockBioSphereRepository` guarantees presentation immunity against network drops while preserving exact mathematical parity.

---

## Quick-Reference Cheat Sheet (Keep This Open During Meetings)

### Core Numbers to Know by Heart
- **Glucose 30-min Cloud Forecast (LatentODE)**: **8.66 mg/dL MAE**, **92.5% Clarke Zone A**, 310 ms inference, 95 MB size.
- **Glucose 30-min Edge Forecast (Our TinyML Model)**: **10.42 mg/dL MAE**, **87.2% Clarke Zone A**, **< 0.05 ms latency**, **1.2 KB RAM**.
- **Automated Insulin Delivery Safety (CBF)**: **76.0% reduction in pediatric hypoglycemia**, **100% elimination of severe lows (< 54 mg/dL)**, Time in Range increased from **72.4% to 84.9%**.
- **Cuffless Blood Pressure**: Systolic MAE **3.25 mmHg**, Diastolic MAE **3.19 mmHg**. Achieves **BHS Grade A** and **passes AAMI standard** (MAE $\le 5\text{ mmHg}$, SD $\le 8\text{ mmHg}$).

### Three Critical Elevator Pitch Answers
1. **"Why two glucose models?"**
   Safety requires zero single-points-of-failure. The Edge model runs inside the sensor transmitter memory with zero network dependencies (< 0.05 ms) so safety alarms always work. The Cloud model runs LatentODE over continuous differential equations to provide high-precision 95% confidence intervals whenever connectivity is available.
2. **"What is unique about your CBF insulin filter?"**
   It does not replace the pump controller. It sits as an independent mathematical supervisory barrier between the controller and the pump actuator, continuously evaluating forward safety margin $h(G, \dot{G})$. It smoothly throttles or suspends insulin before a crash occurs, eliminating 76% of pediatric lows.
3. **"How does cuffless blood pressure work without an arm cuff?"**
   By measuring Pulse Transit Time (PTT) between the electrical heartbeat (ECG) and optical arrival at the fingertip (PPG), combined with our coupled 3-element Windkessel biomechanical twin that models aortic compliance, peripheral vascular resistance, and glucose-induced arterial stiffness.
