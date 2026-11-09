import sys
import numpy as np
import pandas as pd
from datetime import datetime, timedelta
import warnings
warnings.filterwarnings('ignore')

from simglucose.simulation.env import T1DSimEnv
from simglucose.patient.t1dpatient import T1DPatient
from simglucose.sensor.cgm import CGMSensor
from simglucose.actuator.pump import InsulinPump
from simglucose.simulation.scenario import CustomScenario
from simglucose.controller.basal_bolus_ctrller import BBController
from simglucose.controller.base import Action

class SafeCBFController(BBController):
    def __init__(self, target=140, g_min=75.0, tau_min=25.0):
        super().__init__(target=target)
        self.g_min = g_min
        self.tau_min = tau_min
        self.history = []

    def policy(self, observation, reward, done, **kwargs):
        nominal_action = super().policy(observation, reward, done, **kwargs)
        sample_time = kwargs.get('sample_time', 3)
        cgm = observation.CGM
        self.history.append(cgm)
        if len(self.history) > 10:
            self.history.pop(0)

        if len(self.history) >= 3:
            dG_dt = (self.history[-1] - self.history[-3]) / (2 * sample_time)
        elif len(self.history) == 2:
            dG_dt = (self.history[-1] - self.history[-2]) / sample_time
        else:
            dG_dt = 0.0

        projected_glucose = cgm + self.tau_min * dG_dt
        barrier_val = projected_glucose - self.g_min

        if barrier_val <= 0 or cgm < 75.0:
            alpha = 0.0
        elif barrier_val < 35.0:
            alpha = barrier_val / 35.0
        else:
            alpha = 1.0

        safe_basal = nominal_action.basal * alpha
        safe_bolus = nominal_action.bolus * alpha

        return Action(basal=safe_basal, bolus=safe_bolus)

def run_simulation(patient_name, controller_type='standard', sim_hours=24):
    start_time = datetime(2026, 1, 1, 0, 0, 0)
    meals = [
        (timedelta(hours=7), 50),
        (timedelta(hours=12, minutes=30), 75),
        (timedelta(hours=18, minutes=30), 85),
        (timedelta(hours=22), 20),
    ]
    scenario = CustomScenario(start_time=start_time, scenario=meals)
    patient = T1DPatient.withName(patient_name)
    sensor = CGMSensor.withName('Dexcom', seed=42)
    pump = InsulinPump.withName('Insulet')
    env = T1DSimEnv(patient, sensor, pump, scenario)

    if controller_type == 'safe_cbf':
        controller = SafeCBFController(target=120)
    else:
        controller = BBController(target=120)

    obs, reward, done, info = env.reset()
    cgm_readings = [obs.CGM]
    insulin_delivered = [0.0]
    total_steps = int(sim_hours * 60 / env.sample_time)

    for _ in range(total_steps):
        act = controller.policy(obs, reward, done, **info)
        obs, reward, done, info = env.step(act)
        cgm_readings.append(obs.CGM)
        insulin_delivered.append((act.basal + act.bolus) * env.sample_time)
        if done:
            break

    cgm_arr = np.array(cgm_readings)
    tir = np.mean((cgm_arr >= 70) & (cgm_arr <= 180)) * 100
    tbr = np.mean(cgm_arr < 70) * 100
    severe_hypo = np.mean(cgm_arr < 54) * 100
    tar = np.mean(cgm_arr > 180) * 100
    mean_glucose = np.mean(cgm_arr)
    cv = (np.std(cgm_arr) / mean_glucose) * 100
    total_insulin = np.sum(insulin_delivered)

    f_g = 1.509 * ((np.log(np.maximum(cgm_arr, 1.0)))**1.084 - 5.381)
    rl = np.where(f_g < 0, 10 * (f_g**2), 0)
    lbgi = np.mean(rl)

    return {
        'patient': patient_name,
        'cohort': patient_name.split('#')[0],
        'controller': controller_type,
        'mean_glucose': round(mean_glucose, 1),
        'TIR_pct': round(tir, 1),
        'TBR_pct': round(tbr, 1),
        'severe_hypo_pct': round(severe_hypo, 1),
        'TAR_pct': round(tar, 1),
        'CV_pct': round(cv, 1),
        'LBGI': round(lbgi, 2),
        'total_insulin_U': round(total_insulin, 1)
    }

def main():
    patients = [f'adult#{i:03d}' for i in range(1, 11)] +                [f'adolescent#{i:03d}' for i in range(1, 11)] +                [f'child#{i:03d}' for i in range(1, 11)]

    print(f"Starting simglucose benchmark across {len(patients)} virtual patients...")
    results = []
    for idx, p in enumerate(patients):
        print(f"[{idx+1}/{len(patients)}] Simulating {p}...")
        res_std = run_simulation(p, controller_type='standard')
        res_cbf = run_simulation(p, controller_type='safe_cbf')
        results.append(res_std)
        results.append(res_cbf)

    df = pd.DataFrame(results)
    out_csv = '/home/kaptaan45/dexcom_benchmarks/simglucose_benchmark_results.csv'
    df.to_csv(out_csv, index=False)
    print(f"\n[+] Successfully saved {len(df)} simulation runs to {out_csv}")
    
    print("\n=== SUMMARY BY COHORT & CONTROLLER ===")
    summary = df.groupby(['cohort', 'controller'])[['TIR_pct', 'TBR_pct', 'severe_hypo_pct', 'LBGI', 'mean_glucose']].mean().round(2)
    print(summary)

if __name__ == '__main__':
    main()
