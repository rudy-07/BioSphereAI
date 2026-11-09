import os
import glob
import pandas as pd
import numpy as np

def clean_shanghai_data():
    summary_path = '/home/kaptaan45/dexcom_benchmarks/shanghai/Shanghai_T2DM_Summary.xlsx'
    sum_df = pd.read_excel(summary_path)
    print(f"Loaded clinical summary for {len(sum_df)} T2D patients.")

    patient_files = sorted(glob.glob('/home/kaptaan45/dexcom_benchmarks/shanghai/Shanghai_T2DM/*.xlsx'))
    print(f"Found {len(patient_files)} patient Excel files.")

    all_patient_metrics = []
    clean_sample_rows = []

    # Process all patients
    for p_idx, fpath in enumerate(patient_files):
        fname = os.path.basename(fpath).replace('.xlsx', '')
        try:
            df = pd.read_excel(fpath)
        except Exception as e:
            continue

        if 'CGM (mg / dl)' not in df.columns or 'Date' not in df.columns:
            continue

        df['Date'] = pd.to_datetime(df['Date'], errors='coerce')
        df = df.dropna(subset=['Date', 'CGM (mg / dl)']).sort_values('Date')
        if len(df) < 50:
            continue

        gl = df['CGM (mg / dl)'].values
        mean_g = float(np.mean(gl))
        sd_g = float(np.std(gl))
        cv_g = float((sd_g / mean_g) * 100.0)
        tir = float(np.mean((gl >= 70) & (gl <= 180)) * 100.0)
        tbr = float(np.mean(gl < 70) * 100.0)
        tar = float(np.mean(gl > 180) * 100.0)

        # Match clinical metadata
        meta = sum_df[sum_df['Patient Number'].astype(str) == str(fname)]
        age = float(meta['Age (years)'].values[0]) if not meta.empty and 'Age (years)' in meta else np.nan
        bmi = float(meta['BMI (kg/m2)'].values[0]) if not meta.empty and 'BMI (kg/m2)' in meta else np.nan
        hba1c_mmol = float(meta['HbA1c (mmol/mol)'].values[0]) if not meta.empty and 'HbA1c (mmol/mol)' in meta else np.nan
        # Convert mmol/mol to NGSP %: HbA1c(%) = (0.09148 * mmol/mol) + 2.152
        hba1c_pct = round((0.09148 * hba1c_mmol) + 2.152, 2) if not np.isnan(hba1c_mmol) else np.nan
        meds = str(meta['Hypoglycemic Agents'].values[0]) if not meta.empty and 'Hypoglycemic Agents' in meta else 'None'

        # Analyze meal responses if Dietary intake logged
        meal_spikes = []
        if 'Dietary intake' in df.columns:
            meal_rows = df[df['Dietary intake'].notnull() & (df['Dietary intake'] != '')]
            for m_time in meal_rows['Date']:
                # Look 1-2 hours post meal
                pre_g = df[(df['Date'] >= m_time - pd.Timedelta(minutes=15)) & (df['Date'] <= m_time)]['CGM (mg / dl)']
                post_g = df[(df['Date'] >= m_time + pd.Timedelta(minutes=45)) & (df['Date'] <= m_time + pd.Timedelta(minutes=90))]['CGM (mg / dl)']
                if not pre_g.empty and not post_g.empty:
                    rise = post_g.max() - pre_g.iloc[-1]
                    meal_spikes.append(rise)

        avg_meal_rise = float(np.mean(meal_spikes)) if meal_spikes else np.nan

        metric_rec = {
            'patient_id': fname,
            'readings_count': len(df),
            'age': age,
            'bmi': bmi,
            'hba1c_pct': hba1c_pct,
            'medications': meds,
            'mean_glucose': round(mean_g, 1),
            'sd_glucose': round(sd_g, 1),
            'CV_pct': round(cv_g, 1),
            'TIR_pct': round(tir, 1),
            'TBR_pct': round(tbr, 1),
            'TAR_pct': round(tar, 1),
            'avg_1h_meal_rise_mgdl': round(avg_meal_rise, 1) if not np.isnan(avg_meal_rise) else None
        }
        all_patient_metrics.append(metric_rec)

        # Retain first 5 patients in full resolution for dashboard replay
        if p_idx < 5:
            df['patient_id'] = fname
            clean_sample_rows.append(df)

    res_df = pd.DataFrame(all_patient_metrics)
    out_summary = '/home/kaptaan45/dexcom_benchmarks/shanghai_summary.csv'
    res_df.to_csv(out_summary, index=False)
    print(f"[+] Successfully processed {len(res_df)} Shanghai T2D patients! Saved to {out_summary}")

    if clean_sample_rows:
        sample_df = pd.concat(clean_sample_rows, ignore_index=True)
        out_sample = '/home/kaptaan45/dexcom_benchmarks/shanghai_clean_sample.csv'
        sample_df.to_csv(out_sample, index=False)
        print(f"[+] Saved high-resolution sample ({len(sample_df)} rows) to {out_sample}")

    print("\n=== SHANGHAI T2DM COHORT CLINICAL OVERVIEW ===")
    print(res_df[['mean_glucose', 'TIR_pct', 'TBR_pct', 'TAR_pct', 'CV_pct', 'hba1c_pct']].describe().round(2))

if __name__ == '__main__':
    clean_shanghai_data()
