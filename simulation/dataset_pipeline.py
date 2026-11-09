"""
Dataset Pipeline: run the simulator's analytics on the GlucoBench CSV.

Steps
  1. Load & clean   -> one regular 5-minute grid per patient (gaps <= 45 min interpolated)
  2. CGM metrics    -> TIR / TBR / TAR / %CV / GMI per patient, GMI vs. recorded HbA1c
  3. Forecasting    -> 30- and 60-min forecaster + Clarke Error Grid, scored against a
                       naive "glucose stays the same" baseline
  4. Replay         -> build a virtual patient from each profile (weight, insulin
                       sensitivity), feed it the logged meals / boluses / exercise
                       day by day, and compare simulated vs. recorded glucose

Usage
  python dataset_pipeline.py                      # all patients
  python dataset_pipeline.py --user U003          # one patient
  python dataset_pipeline.py --data /path/to.csv  # other file with the same columns
"""

import argparse
import os
from dataclasses import replace

import numpy as np
import pandas as pd

from engine import create_t1d_profile, create_t2d_profile, CardiometabolicSimulator
from ai_modules import MultiHorizonForecaster, CardiometabolicAnalyzer

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_DATA = os.path.join(HERE, "..", "Dataset", "GlucoBench_benchmark_dataset.csv")
OUTPUT_DIR = os.path.join(HERE, "output", "dataset")

STEP_MIN = 5
MAX_GAP_STEPS = 9            # interpolate gaps up to 45 minutes; longer gaps stay empty
MIN_HISTORY = 6              # the forecaster needs 30 min of history
REPLAY_LOOKBACK_H = 6        # carry meals/insulin from the previous 6 h into each replayed day
REFERENCE_ISF = 45.0         # mg/dL per unit that the default engine profile roughly represents
INTENSITY_SCALE = {"low": 0.7, "medium": 1.0, "high": 1.5}
PROFILE_COLS = ["device_id", "hbA1c", "age", "sex", "weight", "carb_ratio",
                "insulin_sensitivity", "region"]


# ---------------------------------------------------------------------------
# 1. LOAD & CLEAN
# ---------------------------------------------------------------------------
def load_dataset(path: str) -> pd.DataFrame:
    df = pd.read_csv(path, parse_dates=["timestamp"])
    return df.sort_values(["user_id", "timestamp"]).reset_index(drop=True)


def clean_patient(raw: pd.DataFrame) -> pd.DataFrame:
    """Puts one patient's rows on a regular 5-minute grid."""
    raw = raw.set_index("timestamp")
    grid = pd.date_range(raw.index.min().floor(f"{STEP_MIN}min"), raw.index.max(),
                         freq=f"{STEP_MIN}min")

    # Glucose: drop readings the device flagged as bad quality, then fill short gaps
    good = raw.loc[raw["cgm_quality_flag"] == 1, "glucose"]
    observed = good.resample(f"{STEP_MIN}min").mean().reindex(grid)
    filled = observed.interpolate(limit=MAX_GAP_STEPS, limit_area="inside")

    # Events: keep every logged event, even on bad-quality rows
    events = raw[["carbs", "insulin_bolus", "exercise_steps"]].resample(f"{STEP_MIN}min").sum()
    events = events.reindex(grid, fill_value=0)
    intensity = (raw["exercise_intensity"].where(raw["exercise_intensity"] != "none")
                 .resample(f"{STEP_MIN}min").first().reindex(grid))

    clean = pd.DataFrame({
        "glucose_observed": observed,
        "glucose": filled,
        "is_interpolated": observed.isna() & filled.notna(),
        "carbs": events["carbs"],
        "insulin_bolus": events["insulin_bolus"],
        "exercise_steps": events["exercise_steps"],
        "exercise_intensity": intensity,
    }, index=grid)
    clean.index.name = "timestamp"
    return clean


def contiguous_segments(values: np.ndarray):
    """Yields (start, end) index pairs of runs without NaN."""
    valid = ~np.isnan(values)
    start = None
    for i, ok in enumerate(valid):
        if ok and start is None:
            start = i
        elif not ok and start is not None:
            yield start, i
            start = None
    if start is not None:
        yield start, len(values)


# ---------------------------------------------------------------------------
# 3. FORECASTING
# ---------------------------------------------------------------------------
def evaluate_forecasts(clean: pd.DataFrame, horizon_min: int):
    """Runs the forecaster on each gap-free stretch and scores it only where the
    future value was really measured (not interpolated). Returns (model, naive) scores."""
    forecaster = MultiHorizonForecaster(sample_step_min=STEP_MIN)
    steps = horizon_min // STEP_MIN
    glucose = clean["glucose"].to_numpy()
    measured = clean["glucose_observed"].notna().to_numpy()

    preds, naive, targets = [], [], []
    for start, end in contiguous_segments(glucose):
        seg = glucose[start:end]
        if len(seg) <= MIN_HISTORY + steps:
            continue
        p, t = forecaster.forecast(seg, horizon_min=horizon_min)
        keep = measured[start + MIN_HISTORY + steps:end]
        preds.append(p[keep])
        targets.append(t[keep])
        naive.append(seg[MIN_HISTORY:len(seg) - steps][keep])

    preds, naive, targets = (np.concatenate(x) for x in (preds, naive, targets))
    score = MultiHorizonForecaster.evaluate_clarke_error_grid
    return score(preds, targets), score(naive, targets)


# ---------------------------------------------------------------------------
# 4. REPLAY THROUGH THE PHYSIOLOGY ENGINE
# ---------------------------------------------------------------------------
def build_virtual_patient(user_id: str, profile: pd.Series, uses_insulin: bool,
                          baseline_glucose: float):
    """Personalises the default engine profile with the dataset's patient settings."""
    if uses_insulin:
        base = create_t1d_profile(f"{user_id}_virtual")
    else:
        base = create_t2d_profile(f"{user_id}_virtual")
    weight = float(profile["weight"])
    return replace(
        base,
        p3=base.p3 * float(profile["insulin_sensitivity"]) / REFERENCE_ISF,
        Vg=1.7 * weight,        # ~1.7 dL of glucose space per kg
        Vi=50.0 * weight,       # ~50 mL of insulin space per kg
        G0=baseline_glucose,
    )


def events_for_day(clean: pd.DataFrame, day_start: pd.Timestamp):
    """Converts logged events into the engine's meal/insulin/exercise lists,
    with times in minutes from midnight (previous-evening events are negative)."""
    window = clean.loc[day_start - pd.Timedelta(hours=REPLAY_LOOKBACK_H):
                       day_start + pd.Timedelta(minutes=1440 - STEP_MIN)]
    minutes = ((window.index - day_start).total_seconds() / 60.0).to_numpy()

    meals, insulins, exercises = [], [], []
    for t, row in zip(minutes, window.itertuples()):
        if row.carbs > 0:
            meals.append({"time_min": t, "carbs_g": float(row.carbs)})
        if row.insulin_bolus > 0:
            insulins.append({"time_min": t, "units": float(row.insulin_bolus)})
        if row.exercise_steps > 0:
            exercises.append({
                "time_min": t,
                "duration_min": float(np.clip(row.exercise_steps / 100.0, 5, 60)),  # ~100 steps/min
                "intensity": INTENSITY_SCALE.get(row.exercise_intensity, 1.0),
            })
    return meals, insulins, exercises


def replay_patient(user_id: str, clean: pd.DataFrame, profile: pd.Series, seed: int):
    """Simulates every calendar day and lines simulated glucose up with the recording."""
    uses_insulin = clean["insulin_bolus"].sum() > 0
    rows = []
    meal_rises = []
    for day_start in pd.date_range(clean.index.min().normalize(), clean.index.max(), freq="D"):
        day = clean.loc[day_start:day_start + pd.Timedelta(minutes=1440 - STEP_MIN)]
        recorded = day["glucose_observed"].dropna()
        if len(recorded) < 12:
            continue

        patient = build_virtual_patient(user_id, profile, uses_insulin,
                                        baseline_glucose=float(recorded.median()))
        meals, insulins, exercises = events_for_day(clean, day_start)
        result = CardiometabolicSimulator(patient, dt_min=1.0).simulate_24h(
            meals, insulins, exercises, random_seed=seed + day_start.day_of_year)

        day_minutes = ((day.index - day_start).total_seconds() / 60.0).to_numpy()
        simulated = np.interp(day_minutes, result["time_min"], result["G_cgm"])
        rows.append(pd.DataFrame({
            "user_id": user_id,
            "timestamp": day.index,
            "recorded_glucose": day["glucose_observed"].to_numpy(),
            "simulated_glucose": np.round(simulated, 1),
            "carbs": day["carbs"].to_numpy(),
            "insulin_bolus": day["insulin_bolus"].to_numpy(),
        }))

        # Glucose rise 60 min after each meal, recorded vs simulated
        rec = day["glucose_observed"].to_numpy()
        lag = 60 // STEP_MIN
        for i in np.flatnonzero(day["carbs"].to_numpy() > 0):
            if i + lag < len(day) and not np.isnan(rec[i]) and not np.isnan(rec[i + lag]):
                meal_rises.append((rec[i + lag] - rec[i], simulated[i + lag] - simulated[i]))

    replay = pd.concat(rows, ignore_index=True)
    both = replay.dropna(subset=["recorded_glucose"])
    err = both["simulated_glucose"] - both["recorded_glucose"]
    rises = np.array(meal_rises) if meal_rises else np.full((1, 2), np.nan)
    summary = {
        "replay_cohort": "T1D (insulin)" if uses_insulin else "T2D (no insulin)",
        "replay_rmse_mgdl": round(float(np.sqrt(np.mean(err ** 2))), 1),
        "replay_correlation": round(float(both["simulated_glucose"].corr(both["recorded_glucose"])), 2),
        "meal_rise_recorded_mgdl": round(float(np.nanmean(rises[:, 0])), 1),
        "meal_rise_simulated_mgdl": round(float(np.nanmean(rises[:, 1])), 1),
    }
    return replay, summary


# ---------------------------------------------------------------------------
# PLOT
# ---------------------------------------------------------------------------
def plot_replay(replay: pd.DataFrame, path: str, day_index: int = 1):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    ink, muted, surface = "#0b0b0b", "#898781", "#fcfcfb"
    recorded_color, simulated_color = "#2a78d6", "#eb6834"

    users = sorted(replay["user_id"].unique())
    cols = 2 if len(users) > 1 else 1
    rows = int(np.ceil(len(users) / cols))
    fig, axes = plt.subplots(rows, cols, figsize=(12, 2.4 * rows + 0.8), sharey=True,
                             squeeze=False, facecolor=surface)

    for ax, user in zip(axes.flat, users):
        u = replay[replay["user_id"] == user]
        days = u["timestamp"].dt.normalize().unique()
        day = days[min(day_index, len(days) - 1)]
        d = u[u["timestamp"].dt.normalize() == day]
        hours = (d["timestamp"] - day).dt.total_seconds() / 3600.0

        ax.set_facecolor(surface)
        ax.axhspan(70, 180, color="#f0efec", zorder=0)
        ax.plot(hours, d["recorded_glucose"], color=recorded_color, lw=2, label="Recorded (dataset)")
        ax.plot(hours, d["simulated_glucose"], color=simulated_color, lw=2, label="Simulated (engine replay)")
        for h in hours[d["carbs"] > 0]:
            ax.axvline(h, color=muted, lw=0.8, ls=":", zorder=1)
        ax.set_title(f"{user} · {pd.Timestamp(day):%d %b %Y}", loc="left", fontsize=10, color=ink)
        ax.set_xlim(0, 24)
        ax.set_xticks(range(0, 25, 6))
        ax.tick_params(colors=muted, labelsize=8)
        for side in ("top", "right"):
            ax.spines[side].set_visible(False)
        for side in ("left", "bottom"):
            ax.spines[side].set_color(muted)

    for ax in axes.flat[len(users):]:
        ax.set_visible(False)
    for ax in axes[:, 0]:
        ax.set_ylabel("Glucose (mg/dL)", fontsize=9, color=ink)
    for ax in axes[-1, :]:
        ax.set_xlabel("Hour of day", fontsize=9, color=ink)

    handles, labels = axes.flat[0].get_legend_handles_labels()
    fig.legend(handles, labels, loc="upper right", ncol=2, frameon=False, fontsize=9)
    fig.suptitle("Logged meals replayed through the simulator vs. recorded glucose\n"
                 "(dotted lines = meals, shaded band = 70–180 mg/dL target range)",
                 x=0.01, ha="left", fontsize=11, color=ink)
    fig.tight_layout(rect=(0, 0, 1, 0.95))
    fig.savefig(path, dpi=130, facecolor=surface)
    plt.close(fig)


# ---------------------------------------------------------------------------
# MAIN
# ---------------------------------------------------------------------------
def run_pipeline(data_path: str, only_user: str = None, make_plot: bool = True):
    print("=" * 80)
    print("  DATASET PIPELINE: GlucoBench CSV -> clean -> metrics -> forecast -> replay")
    print("=" * 80)
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    df = load_dataset(data_path)
    if only_user:
        df = df[df["user_id"] == only_user]
        if df.empty:
            raise SystemExit(f"No rows for user '{only_user}'.")
    users = sorted(df["user_id"].unique())
    print(f"\n[1/4] Loaded {len(df):,} rows for {len(users)} patients from {os.path.basename(data_path)}")

    cleaned, summaries, replays = [], [], []
    for n, (user_id, raw) in enumerate(df.groupby("user_id", sort=True)):
        clean = clean_patient(raw)
        profile = raw.iloc[0][PROFILE_COLS]

        observed = clean["glucose_observed"].notna().mean() * 100
        interpolated = clean["is_interpolated"].mean() * 100

        metrics = CardiometabolicAnalyzer.calculate_cgm_metrics(clean["glucose_observed"].dropna().to_numpy())
        fc30, naive30 = evaluate_forecasts(clean, 30)
        fc60, naive60 = evaluate_forecasts(clean, 60)
        replay, replay_summary = replay_patient(user_id, clean, profile, seed=1000 + n)

        summaries.append({
            "user_id": user_id,
            **profile.to_dict(),
            "grid_measured_pct": round(observed, 1),
            "grid_interpolated_pct": round(interpolated, 1),
            **metrics,
            "fc30_rmse": fc30["rmse_mgdl"], "fc30_naive_rmse": naive30["rmse_mgdl"],
            "fc30_zone_a_pct": fc30["zone_a_pct"],
            "fc60_rmse": fc60["rmse_mgdl"], "fc60_naive_rmse": naive60["rmse_mgdl"],
            "fc60_zone_a_pct": fc60["zone_a_pct"],
            **replay_summary,
        })
        cleaned.append(clean.reset_index().assign(user_id=user_id))
        replays.append(replay)

    summary = pd.DataFrame(summaries)
    cleaned = pd.concat(cleaned, ignore_index=True)
    replays = pd.concat(replays, ignore_index=True)

    print(f"  -> Regular 5-min grid: {summary['grid_measured_pct'].mean():.1f}% measured, "
          f"{summary['grid_interpolated_pct'].mean():.1f}% interpolated, rest left empty (gaps > 45 min)")

    print("\n[2/4] Clinical CGM metrics per patient")
    print(summary[["user_id", "device_id", "mean_glucose_mgdl", "tir_percent", "tbr_percent",
                   "tar_percent", "cv_percent", "gmi_percent", "hbA1c"]]
          .rename(columns={"mean_glucose_mgdl": "mean", "tir_percent": "TIR%", "tbr_percent": "TBR%",
                           "tar_percent": "TAR%", "cv_percent": "CV%", "gmi_percent": "GMI%",
                           "hbA1c": "HbA1c%"})
          .to_string(index=False))
    gap = (summary["hbA1c"] - summary["gmi_percent"]).abs().mean()
    print(f"  -> GMI (estimated from CGM) differs from the recorded HbA1c by {gap:.2f} points on average")

    print("\n[3/4] Forecasting (RMSE in mg/dL; lower is better)")
    print(summary[["user_id", "fc30_rmse", "fc30_naive_rmse", "fc30_zone_a_pct",
                   "fc60_rmse", "fc60_naive_rmse", "fc60_zone_a_pct"]]
          .rename(columns={"fc30_rmse": "30m model", "fc30_naive_rmse": "30m naive",
                           "fc30_zone_a_pct": "30m ZoneA%", "fc60_rmse": "60m model",
                           "fc60_naive_rmse": "60m naive", "fc60_zone_a_pct": "60m ZoneA%"})
          .to_string(index=False))
    print(f"  -> Mean 30-min RMSE: model {summary['fc30_rmse'].mean():.2f} vs naive "
          f"{summary['fc30_naive_rmse'].mean():.2f} | 60-min: model {summary['fc60_rmse'].mean():.2f} "
          f"vs naive {summary['fc60_naive_rmse'].mean():.2f}")

    print("\n[4/4] Replaying logged meals / insulin / exercise through the physiology engine")
    print(summary[["user_id", "replay_cohort", "replay_rmse_mgdl", "replay_correlation",
                   "meal_rise_recorded_mgdl", "meal_rise_simulated_mgdl"]]
          .rename(columns={"replay_cohort": "cohort", "replay_rmse_mgdl": "RMSE",
                           "replay_correlation": "corr", "meal_rise_recorded_mgdl": "1h meal rise (rec)",
                           "meal_rise_simulated_mgdl": "1h meal rise (sim)"})
          .to_string(index=False))
    print(f"  -> Average glucose change 1 h after a meal: recorded "
          f"{summary['meal_rise_recorded_mgdl'].mean():+.1f} mg/dL, simulated "
          f"{summary['meal_rise_simulated_mgdl'].mean():+.1f} mg/dL")

    summary.to_csv(os.path.join(OUTPUT_DIR, "patient_summary.csv"), index=False)
    cleaned.to_csv(os.path.join(OUTPUT_DIR, "cleaned_5min_grid.csv"), index=False)
    replays.to_csv(os.path.join(OUTPUT_DIR, "replay_recorded_vs_simulated.csv"), index=False)
    if make_plot:
        plot_replay(replays, os.path.join(OUTPUT_DIR, "replay_overview.png"))

    print(f"\n[+] Results written to: {OUTPUT_DIR}")
    print("=" * 80)
    return summary


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run the simulator analytics on the GlucoBench dataset.")
    parser.add_argument("--data", default=DEFAULT_DATA, help="Path to the dataset CSV")
    parser.add_argument("--user", default=None, help="Only process one patient, e.g. U003")
    parser.add_argument("--no-plot", action="store_true", help="Skip the PNG figure")
    args = parser.parse_args()
    run_pipeline(args.data, args.user, make_plot=not args.no_plot)
