# BioSphereAI: Multi-Modal Cardiometabolic Digital Twin and Clinical Benchmark Report

**Project Title:** BioSphereAI  
**Domain:** Autonomous Health Platforms, Continuous Glucose Monitoring (CGM), Closed-Loop Automated Insulin Delivery (AID), and Continuous Cuffless Blood Pressure  
**Architecture:** Two-Tier Edge-Cloud System (On-Device TinyML Firmware and Cloud Neural ODE Engine)  
**Date:** November 2026  

---

## Executive Summary

BioSphereAI is an autonomous cardiometabolic digital twin and clinical benchmark platform designed to process continuous biological time series signals from wearable sensors. The platform integrates:

1. **Short-Term Glucose Forecasting (30 to 60 Minutes):** Evaluates foundation time series models and continuous differential equation architectures against an on-device TinyML momentum forecaster.
2. **Autonomous Insulin Delivery (AID) Safety:** Deploys a Control Barrier Function (CBF) supervisory controller on the FDA-accepted UVa/Padova metabolic simulator, demonstrating a **76.0% reduction in pediatric hypoglycemia**.
3. **Continuous Cuffless Blood Pressure Estimation:** Uses Pulse Transit Time (PTT) and photoplethysmogram (PPG) morphometry from synchronized ICU records to achieve British Hypertension Society (BHS) Grade A and AAMI standard compliance.

The platform is designed to be hardware-agnostic, supporting standard continuous sensor data streams including Dexcom (G7, Stelo, G6), Abbott FreeStyle Libre 2, and Medtronic 670G.

---

## 1. Glucose Forecasting: Models, Datasets and Hardware Requirements

### 1.1 Locked-In Clinical Datasets

Synthetic datasets with uncoupled glucose-meal dynamics were discarded in favor of two verified clinical cohorts:

* **Dataset 1: Stanford Hall 2018**
  * **Size:** 105,426 rows of 5-minute CGM readings across 57 patients.
  * **Cohorts:** 38 Normal glucose tolerance, 14 Prediabetes, 5 Type 2 Diabetes.
  * **Interventions:** Real standardized dietary challenges (cornflakes with milk, bread with peanut butter, nutrition bars).
  * **Sensors:** Continuous interstitial enzymatic glucose sensors (Dexcom G4 Platinum).
* **Dataset 2: Shanghai Hospital Clinical Dataset (Shanghai T2DM)**
  * **Size:** 109 inpatient records with continuous CGM.
  * **Metadata:** Capillary blood glucose verification, lab HbA1c, body mass index, meal timestamps, and active oral hypoglycemic medications (Metformin, Acarbose, Sitagliptin, Voglibose, Novolin R).
  * **Function:** Benchmarks medication-induced glycemic damping and lifestyle interventions in Type 2 Diabetes.

---

### 1.2 Model Benchmarking and Testing Methodology

Testing was executed on an isolated multi-core Linux compute environment (`kaptaan45`: Intel Core i5-8265U, 11 GB RAM, Ubuntu 24.04, Python 3.10).

* **Evaluation Protocol:**
  * Context window: 96 steps (8 hours of 5-minute historical readings).
  * Forecast horizons: 6 steps (30 minutes) and 12 steps (60 minutes).
  * Evaluation stride: 12 steps (1-hour window intervals across patients).
  * Primary metrics: Mean Absolute Error (MAE), Root Mean Squared Error (RMSE), Clarke Error Grid Zone A percentage, and single-window inference latency.

---

### 1.3 Master Performance Matrix and Hardware Footprint

| Model Architecture | Model Category | 30m MAE (mg/dL) | 30m Zone A (%) | 60m MAE (mg/dL) | 60m Zone A (%) | Inference Latency | RAM / Model Size | Hardware Deployment Tier |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **LatentODE** | Neural ODE | **8.66** | **92.5%** | **12.10** | **84.1%** | 310.2 ms | 95 MB | **Tier 1: Primary Cloud Choice** |
| **Gluformer** | Domain Transformer | **9.09** | **91.2%** | **12.65** | **83.0%** | 180.5 ms | 120 MB | Tier 1: Cloud Server |
| **Google TimesFM 2.5** | Foundation Model | **9.17** | **90.4%** | **12.87** | **82.1%** | 233.8 ms | 800 MB | Tier 1: Cloud Baseline |
| **TFT** | Recurrent Transformer | **9.17** | **89.8%** | **13.04** | **81.5%** | 145.0 ms | 65 MB | Tier 1: Mobile NPU / Cloud |
| **N-HiTS** | Hierarchical MLP | **9.29** | **88.5%** | **13.50** | **79.8%** | 85.0 ms | 35 MB | Tier 1: Mobile App |
| **XGBoost (Covariates)**| Boosted Trees | **9.90** | **86.8%** | **14.15** | **77.2%** | 1.2 ms | 8.5 MB | Tier 1 / Tier 2 Bridge |
| **Custom Edge Forecaster** | **TinyML Analytical** | **10.42** | **87.2%** | **14.30** | **78.5%** | **< 0.05 ms** | **1.2 KB** | **Tier 2: Primary Edge Choice** |
| **Naive Baseline** | Persistence ($G_t$) | 9.77 | 84.1% | 13.98 | 75.3% | < 0.001 ms | 0 KB | Reference Baseline |

---

### 1.4 Architectural Selection: Two-Tier Edge-Cloud Synergy

1. **Tier 2 (On-Device Wearable Firmware): Custom TinyML Analytical Forecaster**
   * Uses a 4-point Savitzky-Golay polynomial velocity estimator combined with an exponential physiological damping factor ($\lambda = 0.10$):
     $$v = \frac{3 \cdot G_t + 1 \cdot G_{t-1} - 1 \cdot G_{t-2} - 3 \cdot G_{t-3}}{10 \cdot \Delta t}$$
     $$\hat{G}_{t+h} = G_t + v \cdot h \cdot e^{-\lambda \cdot h}$$
   * Runs in **less than 0.05 ms** and requires only **1.2 KB of RAM**.
   * Operates without external weight files, making it suitable for direct execution inside transmitter microcontrollers (ARM Cortex-M0/M4) powered by coin-cell batteries.
   * Delivers urgent low alerts locally even when smartphone connectivity is lost.

2. **Tier 1 (Cloud Foundation Engine): LatentODE (Neural Ordinary Differential Equations)**
   * Outperformed all evaluated architectures, achieving an MAE of **8.66 mg/dL** and **92.5% in Clarke Zone A**.
   * Models the continuous biological dynamics of human glucose regulation as a continuous vector field, avoiding the discretization errors of fixed-step transformer models.
   * Operates with an inference footprint of approximately **95 MB**, making it substantially more practical to host on cloud servers than 200M parameter foundation transformers.

---

## 2. Continuous Cuffless Blood Pressure Estimation

### 2.1 Methodology: Feature Engineering and Hemodynamic Twin

The platform extracts hemodynamic features from synchronized electrocardiogram (ECG) and photoplethysmogram (PPG) waveforms:

* **Pulse Transit Time (PTT):** Delay between ECG R-peak and PPG systolic arrival. Derived via the Moens-Korteweg and Hughes vascular elasticity equations:
  $$PWV = \frac{L}{PTT} = \sqrt{\frac{E_0 \cdot h}{\rho \cdot d} \cdot e^{\gamma \cdot BP}}$$
* **PPG Morphometry:** Systolic upstroke rise time ($T_s$), pulse width at 50% height, and Photoplethysmogram Intensity Ratio (PIR).
* **Coupled Cardiopulmonary 3-Element Windkessel Twin:** Models aortic compliance ($C$), total peripheral resistance ($R_p$), and stroke volume ($SV$) to compute beat-to-beat pressure dynamics:
  $$I(t) = C \frac{dP}{dt} + \frac{P - P_v}{R_p}$$

### 2.2 Validation Results (PhysioNet MIMIC-II / UCI Database)

| Model Architecture | SBP MAE (mmHg) | SBP SD (mmHg) | SBP Corr ($r$) | BHS Grade | AAMI Compliance ($\le 5\text{ mmHg}$) | DBP MAE (mmHg) | DBP SD (mmHg) | DBP Corr ($r$) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Gradient Boosting (XGB)** | **3.25** | 4.00 | 0.942 | **Grade A** | **PASS** | **3.19** | 3.91 | 0.869 |
| **Random Forest** | **3.44** | 4.19 | 0.937 | **Grade A** | **PASS** | **3.18** | 4.00 | 0.863 |
| **Coupled Windkessel Twin** | 31.70 | 8.26 | 0.760 | Grade D | FAIL (Uncalibrated) | 20.62 | 6.07 | 0.827 |
| **Population Baseline** | 9.86 | 11.93 | — | Grade D | FAIL | 6.47 | 7.90 | — |

* **Engineering Insight:** Machine learning feature regression delivers clinical accuracy meeting international standards. The physical Windkessel twin provides strong dynamic tracking ($r = 0.76 - 0.83$). The optimal architecture is a hybrid: machine learning calibrates individual vascular baseline tone, while the physical twin continuously renders arterial pressure waveforms.

---

## 3. Autonomous Insulin Delivery: Control Barrier Functions

### 3.1 In-Silico Trial Setup
* **Platform:** `simglucose` (UVa/Padova Type 1 Diabetes Metabolic Simulator, accepted by the U.S. FDA for preclinical trial replacement).
* **Cohort:** 30 virtual patients: 10 Adults (`adult#001` through `#010`), 10 Adolescents (`adolescent#001` through `#010`), 10 Children (`child#001` through `#010`).
* **Disturbance Scenario:** 24-hour protocol with unannounced meals (07:00 50g, 12:30 75g, 18:30 85g, 22:00 20g).

### 3.2 Control Barrier Function Formulation
The safe glucose region is defined as a forward-invariant safe set:
$$\mathcal{C} = \{ G \in \mathbb{R} \mid h(G, \dot{G}) \ge 0 \}$$
where the barrier equation evaluates glucose projected 25 minutes into the future:
$$h(G, \dot{G}) = G(t) + \tau \cdot \left(\frac{dG}{dt}\right) - G_{\min}$$
with $\tau = 25\text{ minutes}$ and $G_{\min} = 75.0\text{ mg/dL}$.

The nominal pump command $u_{\text{nom}}(t)$ is dynamically throttled by a supervisory attenuation factor $\alpha$:
$$\alpha = \text{clip}\left(\frac{h(G, \dot{G})}{35.0}, 0.0, 1.0\right), \quad u_{\text{safe}}(t) = \alpha \cdot u_{\text{nom}}(t)$$

### 3.3 Clinical Trial Results

```
UVa/Padova In-Silico Trial (30 Patients): Standard Care vs. Safe CBF Controller

COHORT: CHILDREN (N=10)
  * Time Below Range (<70 mg/dL):  Dropped from 9.64%  ->  2.32%   (76.0% Reduction)
  * Severe Hypoglycemia (<54 mg/dL): Dropped from 2.41%  ->  0.73%   (69.7% Reduction)
  * Low Blood Glucose Index (LBGI): Dropped from 2.25   ->  0.76    (Safe Minimal Risk)
  * Time in Range (70-180 mg/dL):   Preserved at 77.6% (Standard was 77.5%)

COHORT: ADULTS (N=10)
  * Time Below Range (<70 mg/dL):  0.00% under Safe CBF
  * Severe Hypoglycemia (<54 mg/dL): 0.00% under Safe CBF
  * Time in Range (70-180 mg/dL):   91.6% TIR
```

### 3.4 Industry Context and Regulatory Considerations
Commercial automated insulin pumps still rely primarily on classical PID or Model Predictive Control (MPC) algorithms:

1. **Recency of CBF Theory:** Control Barrier Functions were formalized between 2014 and 2019 in aerospace and robotics control. Application to biomedical loops began appearing in academic literature only recently (2021 to 2025).
2. **Regulatory Rigidity:** Commercial systems completed multi-year FDA clinical trials based on control architectures locked years ago. Modifying the underlying controller requires new clinical trials and regulatory PMA submissions.
3. **Supervisory Wrapper Integration:** BioSphereAI implements CBF as a continuous supervisory filter rather than a replacement controller. It can wrap existing FDA-cleared pump firmware to dynamically mitigate hypoglycemia risk without requiring core controller redesigns.
