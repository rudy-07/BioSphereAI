# Dexcom AI & Cardiometabolic Intelligence Research Suite

Welcome to the comprehensive research, technical architecture, and simulation platform developed for the **Dexcom AI Integration Internship**.

This repository is designed to give you an unfair advantage: moving beyond high-level generalities to deliver rigorous clinical domain knowledge, exact product architecture, publication-grade academic writing, and a **fully working in silico simulation codebase**.

---

## 📂 Repository Structure & Document Index

```
dexcom/
├── README.md                                 # Executive summary & roadmap (this document)
├── 01_dexcom_company_products_ecosystem.md   # Products (G7, Stelo, ONE+), APIs, Google Vertex AI, Nanowear
├── 02_ai_ml_integration_opportunities.md     # Deep AI/ML analysis: T1D vs T2D, PINNs, Safe RL, Cuffless BP, TinyML
├── 03_simulation_project_architecture.md     # In silico physiological digital twin architecture & math
├── 04_technical_paper_draft.md               # Publication-ready IEEE/Nature-style technical paper draft
├── 05_interview_and_internship_strategy.md   # Pitch script for your professor, interview Q&A, and vocabulary
└── simulation/                               # Fully functional Python simulation codebase
    ├── requirements.txt                      # Dependencies (numpy, scipy, pandas, streamlit, plotly)
    ├── engine.py                             # ODE minimal model kinetics, ISF lag, PISA, Windkessel BP
    ├── ai_modules.py                         # Multi-horizon forecasting, Clarke EGA, AID safety, Gemini nudges
    ├── run_demo.py                           # CLI benchmark runner (outputs CSV & mock Dexcom API JSON)
    ├── app.py                                # Interactive Streamlit & Plotly web dashboard
    └── output/                               # Generated simulation streams & mock API payloads
```

---

## 🧭 Executive Summary of Core Findings

### 1. Dexcom's Massive Strategic Shift: Type 2 Diabetes & Stelo
Historically, Dexcom’s business was built around Type 1 Diabetes (intensive insulin users requiring emergency hypoglycemia alarms). In August 2024, Dexcom launched **Stelo**, the first FDA-cleared over-the-counter (OTC) glucose biosensor in the US:
* **The Clinical Distinction:** Stelo is designed for non-insulin Type 2 diabetics and pre-diabetics.
* **The UX & Algorithmic Change:** Stelo eliminates low-glucose audible alarms to avoid "alarm fatigue." The value proposition is entirely **behavioral modification, meal response prediction, and metabolic feedback**.

### 2. The Google Cloud GenAI Partnership (Late 2024)
Dexcom integrated **Google Cloud’s Vertex AI and Gemini models** to launch a proprietary Generative AI engine. Currently powering **Stelo Weekly Insights**, it translates complex biometric time-series (glucose, sleep, steps) into plain-language coaching.

### 3. The Blood Pressure & Cardiovascular Connection
Why did your professor mention blood pressure alongside Dexcom?
* **Clinical Rationale:** Over **75% of Type 2 diabetes patients have hypertension** ("cardiometabolic syndrome"). Acute glucose spikes cause oxidative stress, impair nitric oxide production, and cause arterial vasoconstriction. Diabetics also suffer from **nocturnal non-dipping blood pressure** (a major predictor of heart attacks and stroke).
* **Corporate Milestone:** Dexcom officially partnered with **Nanowear** to integrate G7 CGM telemetry with Nanowear's FDA 510(k)-cleared **SimpleSense-BP cuffless blood pressure** and hemodynamic platform.

---

## 🚀 How to Run the Simulation Platform

### 1. Command-Line Benchmark
Run the standalone simulation to generate 24 hours of synchronized telemetry for both T1D and T2D patients:
```bash
cd "dexcom/simulation"
python run_demo.py
```
This script computes:
* Time In Range (TIR), Time Below Range (TBR), Glycemic Variability (%CV).
* Multi-Horizon AI Forecasting with Clarke Error Grid classification.
* Generative AI coaching nudge for postprandial meals.
* Counterfactual evaluation (blunting glucose spikes via a 15-minute post-meal walk).
* Nocturnal non-dipping blood pressure detection.
* Exports telemetry to CSV and saves a compliant mock Dexcom Developer API JSON payload (`GET /v3/users/self/egvs`).

### 2. Interactive Web Dashboard (Streamlit & Plotly)
Launch the interactive visualizer:
```bash
cd "dexcom/simulation"
streamlit run app.py
```
* **Features:**
  * Toggle between Type 1 (G7 + Insulin AID) and Type 2 (Stelo + Lifestyle AI).
  * Adjust carbohydrate sliders for breakfast, lunch, and dinner in real time.
  * Visualize synchronized continuous glucose and cuffless blood pressure curves.
  * Inspect live Clarke Error Grid metrics, AI forecasting, and simulated Gemini insights.

---

## 🎯 Next Steps for Your Internship Journey

1. **Review File 05:** Read [05_interview_and_internship_strategy.md](file:///d:/Projects/Portfolio%20Websites/rudy/dexcom/05_interview_and_internship_strategy.md) to practice the pitch script for your professor and review the interview Q&A.
2. **Review File 04:** Read [04_technical_paper_draft.md](file:///d:/Projects/Portfolio%20Websites/rudy/dexcom/04_technical_paper_draft.md) to understand the academic framing you can co-author with your professor.
3. **Launch the Demo:** Run `python run_demo.py` and `streamlit run app.py` so you can demonstrate the live dashboard directly on your laptop or screen during meetings.
