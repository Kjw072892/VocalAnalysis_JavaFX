import io
import json
import math
import sys
from sqlite3 import Binary
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import parselmouth
import sqlite3

FILE_PATH = None
UNVOICE_DB = -200.0

"""
Filters anomalous frequencies

time_list: A list of the time stamp extracted from the audio sample
freqs: A list of the frequencies extracted from the audio sample

returns: tuple with the new synchronized time stamp and a list of the filtered frequencies
"""


def filter_frequency_synchronized(formant_: str, time_list: list[float], freqs: list[float]) -> tuple[list[float],
list[float]]:
    # First Filter
    new_times = []
    new_freqs = []
    temp_freq = []
    temp_time = []
    prev_freq = 0

    # Filters out all the quiet spaces.
    for time, freq in zip(time_list, freqs):

        if freq > 0:
            new_times.append(time)
            new_freqs.append(freq)

    # Second Filter low/high pass filter
    for time, freqs in zip(new_times, new_freqs):

        if prev_freq == 0:
            prev_freq = freqs
            continue
        if formant_.casefold() == "f0":
            if 75 < freqs < 550:
                if prev_freq_helper(freqs, prev_freq, "f0"):
                    temp_freq.append(freqs)
                    temp_time.append(time)
                    prev_freq = freqs
                    continue
                prev_freq = 0
                continue

        elif formant_.casefold() == "f1":
            if 250 <= freqs < 890:
                if prev_freq_helper(freqs, prev_freq, "f1"):
                    temp_freq.append(freqs)
                    temp_time.append(time)
                    prev_freq = freqs
                    continue
                prev_freq = 0
                continue

        elif formant_.casefold() == "f2":
            if 800 <= freqs < 2800:
                if prev_freq_helper(freqs, prev_freq, "f2"):
                    temp_freq.append(freqs)
                    temp_time.append(time)
                    prev_freq = freqs
                    continue
                prev_freq = 0
                continue

        elif formant_.casefold() == "f3":
            if 1750 < freqs < 3600:
                if prev_freq_helper(freqs, prev_freq, "f3"):
                    temp_freq.append(freqs)
                    temp_time.append(time)
                    prev_freq = freqs
                    continue
                prev_freq = 0
                continue

        elif formant_.casefold() == "f4":
            if 2850 < freqs < 4500:
                if prev_freq_helper(freqs, prev_freq, "f4"):
                    temp_freq.append(freqs)
                    temp_time.append(time)
                    prev_freq = freqs
                    continue
                prev_freq = 0
                continue

    return temp_time, temp_freq


"""
Filters out anomalies based on formant.
"""


def prev_freq_helper(curr_freq: int, prev_freq: int, formant: str) -> bool:
    result = True

    match formant.casefold():
        case "f0":
            result = bool(not abs(curr_freq - prev_freq) >= 60)
        case "f1":
            result = bool(not abs(curr_freq - prev_freq) >= 250)
        case "f2":
            result = bool(not abs(curr_freq - prev_freq) >= 450)
        case "f3":
            result = bool(not abs(curr_freq - prev_freq) >= 650)
        case "f4":
            result = bool(not abs(curr_freq - prev_freq) >= 850)

    return result


"""
Get the average frequency for the designated formant frequency band

freq_data: list of frequencies extracted from the sample
sel_formant: the formant frequency band( F0, F1, F2, F3, F4)
"""


def get_freq_average(freq_data: list[float]):
    result = 0
    num_of_freq = 0

    for freq in freq_data:
        result += freq
        num_of_freq += 1

    return result / num_of_freq


def _hz_to_semitones(hz, ref=55.0):
    hz = np.asanyarray(hz, dtype=float)
    hz = hz[hz > 0]
    return 12.0 * np.log2(hz / ref)


def _semitones(a, b):
    a = np.asarray(a, float)
    b = np.asarray(b, float)
    mask = (a > 0) & (b > 0)
    out = np.full(np.broadcast(a, b).shape, np.nan, dtype=float)
    out[mask] = 12.0 * np.log2(a[mask] / b[mask])
    return out


def _pitch_stats(f0_hz, t_s, floor=75.0, ceil=500.0):
    f0 = np.asarray(f0_hz, float)
    t = np.asarray(t_s, float)
    voiced = np.isfinite(f0) & (f0 >= floor) & (f0 <= ceil)
    f0 = f0[voiced]
    t = t[voiced]
    if f0.size < 5:
        return None
    st = _hz_to_semitones(f0)
    A = np.c_[t, np.ones_like(t)]
    m, _ = np.linalg.lstsq(A, st, rcond=None)[0]
    p5, p95 = np.percentile(f0, [5, 95])
    return {
        "f0_mean_hz": float(np.mean(f0)),
        "f0_sd_st": float(np.std(st, ddof=1)) if st.size > 1 else 0.0,
        "f0_min_hz": float(np.min(f0)),
        "f0_max_hz": float(np.max(f0)),
        "f0_p5_hz": float(p5),
        "f0_p95_hz": float(p95),
        "range_semitones": float(_semitones(np.max(f0), np.min(f0))) if np.min(f0) > 0 else np.nan,
        "range_st_5_95": float(_semitones(p95, p5)) if p5 > 0 else np.nan,
        "slope_st_per_sec": float(m),
    }


def extract_breathiness_and_intonation(
        sound: parselmouth.Sound,
        *,
        time_step: float = 0.01,
        pitch_floor: float = 75.0,
        pitch_ceiling: float = 600) -> dict:
    pitch = sound.to_pitch(time_step=time_step, pitch_floor=pitch_floor, pitch_ceiling=pitch_ceiling)
    f0 = pitch.selected_array['frequency']
    times = pitch.xs()

    # ---- Intonation block ----
    voiced_mask = f0 > 0
    f0_v = f0[voiced_mask]
    t_v = times[voiced_mask]

    voiced_frames = int(f0_v.size)
    total_frames = int(f0.size)
    voiced_frac = (voiced_frames / total_frames) if total_frames else np.nan

    stats = _pitch_stats(f0_v, t_v, floor=pitch_floor, ceil=pitch_ceiling)
    if stats is None:
        intonation = dict(
            f0_mean_hz=np.nan, f0_sd_hz=np.nan, f0_min_hz=np.nan, f0_max_hz=np.nan,
            range_semitones=np.nan, slope_st_per_sec=np.nan, voiced_frac=np.nan
        )
    else:
        intonation = dict(
            f0_mean_hz=stats["f0_mean_hz"],
            f0_sd_hz=float(np.std(f0_v, ddof=1)) if f0_v.size > 1 else 0.0,
            f0_min_hz=stats["f0_min_hz"],
            f0_max_hz=stats["f0_max_hz"],
            range_semitones=stats["range_semitones"],
            slope_st_per_sec=stats["slope_st_per_sec"],
            f0_p5_hz=stats["f0_p5_hz"],
            f0_p95_hz=stats["f0_p95_hz"],
            range_st_5_95=stats["range_st_5_95"],
            f0_sd_st=stats["f0_sd_st"],
            voiced_frac=voiced_frac
        )

    # ---- Breathiness block ----
    harm = sound.to_harmonicity_cc(time_step=time_step, minimum_pitch=pitch_floor, periods_per_window=1.0)
    hnr_raw = harm.values.ravel()  # includes UNVOICE_DB for unvoiced
    hnr_total = int(hnr_raw.size)
    hnr_voiced_mask = (hnr_raw != UNVOICE_DB)
    hnr_voiced_frames = int(np.sum(hnr_voiced_mask))
    hnr_voiced_fraction = (hnr_voiced_frames / hnr_total) if hnr_total else np.nan

    hnr_vals = hnr_raw[hnr_voiced_mask]  # use voiced-only values for stats
    breathiness = dict(
        hnr_mean_db=float(np.mean(hnr_vals)) if hnr_vals.size else np.nan,
        hnr_median_db=float(np.median(hnr_vals)) if hnr_vals.size else np.nan,
        hnr_frames_total=hnr_total,
        hnr_voiced_frames=hnr_voiced_frames,
        hnr_voiced_fraction=float(hnr_voiced_fraction)
    )

    return {"breathiness": breathiness, "intonation": intonation}


"""
Generates a dot graph of all the different formant and returns 
"""


def plot_formants(time_0: list[float], f0_: list[float],
                  time_1: list[float], f1_: list[float],
                  time_2: list[float], f2_: list[float],
                  time_3: list[float], f3_: list[float],
                  time_4: list[float], f4_: list[float]) -> bytes:
    ticks1 = np.arange(0, 1001, 100)
    ticks2 = np.arange(1000, 8001, 500)
    yticks = np.unique(np.concatenate([ticks1, ticks2]))

    fig, ax = plt.subplots(figsize=(6, 12), dpi=300)

    ax.plot(time_0, f0_, label="Pitch", color="black")
    ax.set_yticks(yticks)
    ax.minorticks_on()
    ax.scatter(time_1, f1_, s=5, label="F1")
    ax.scatter(time_2, f2_, s=5, label="F2")
    ax.scatter(time_3, f3_, s=5, label="F3")
    ax.scatter(time_4, f4_, s=5, label="F4")
    ax.set_xlabel("Times (s)")
    ax.set_ylabel("Frequency (Hz)")
    ax.set_ybound(0, 5501)
    ax.grid(True)
    ax.legend(loc="upper left", frameon=True)
    buf = io.BytesIO()
    fig.savefig(buf, format="png", bbox_inches="tight")
    plt.close(fig)
    return buf.getvalue()


"""
Generate a SQLite3 table that holds 
"""


def connect_table():
    conn = sqlite3.connect("Vocal_Analysis.db")
    cursor = conn.cursor()

    cursor.execute("""
                   CREATE TABLE IF NOT EXISTS user_formants
                   (
                       id               INTEGER PRIMARY KEY AUTOINCREMENT,
                       timestamp        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       f0_json          TEXT NOT NULL CHECK (json_valid(f0_json)),
                       f1_json          TEXT NOT NULL CHECK (json_valid(f1_json)),
                       f2_json          TEXT NOT NULL CHECK (json_valid(f2_json)),
                       f3_json          TEXT NOT NULL CHECK (json_valid(f3_json)),
                       f4_json          TEXT NOT NULL CHECK (json_valid(f4_json)),
                       formant_avg_json TEXT NOT NULL CHECK (json_valid(formant_avg_json)),
                       scatter_plot     BLOB NOT NULL
                   )
                   """)
    conn.commit()
    conn.close()


"""
Inserts the formant data (raw and average) into the sql database.

"""


def insert_to_table(time_0, f0_, time_1, f1_, time_2, f2_, time_3, f3_, time_4, f4_: list[float],
                    formant_avg: list[int]) -> None:
    png_bytes = plot_formants(time_0, f0_, time_1, f1_, time_2, f2_, time_3, f3_, time_4, f4_)
    payload = (
        json.dumps(list(map(float, f0_))),
        json.dumps(list(map(float, f1_))),
        json.dumps(list(map(float, f2_))),
        json.dumps(list(map(float, f3_))),
        json.dumps(list(map(float, f4_))),
        json.dumps(list(map(float, formant_avg))),
        Binary(png_bytes)
    )

    conn = sqlite3.connect("Vocal_Analysis.db")
    cur = conn.cursor()
    cur.execute("""
                INSERT INTO user_formants(f0_json, f1_json, f2_json, f3_json, f4_json, formant_avg_json, scatter_plot)
                VALUES (json(?), json(?), json(?), json(?), json(?), json(?), ?)
                """, payload)
    conn.commit()
    conn.close()


"""
Collects the vocal analysis data from the users vocal recording.
"""


def main():
    # sys.argv[0] is the script name
    # sys.argv[1] is the argument from java
    try:
        global FILE_PATH
        FILE_PATH = sys.argv[1]
        if FILE_PATH:
            sound = parselmouth.Sound(FILE_PATH)
            metrics = extract_breathiness_and_intonation(
                sound,
                time_step=0.005,
                pitch_floor=60.0,
                pitch_ceiling=500.0  # keep high to cover feminine ranges
            )
            print(metrics)

            formant = sound.to_formant_burg(time_step=0.01)
            pitch = sound.to_pitch(time_step=0.01)
            times = np.arange(0, sound.get_total_duration(), .01)
            f0_dict = {}
            f1_dict = {}
            f2_dict = {}
            f3_dict = {}
            f4_dict = {}
            # Initializes the formant and pitch
            for t in times:
                f0 = pitch.get_value_at_time(t)
                f1 = formant.get_value_at_time(1, t)
                f2 = formant.get_value_at_time(2, t)
                f3 = formant.get_value_at_time(3, t)
                f4 = formant.get_value_at_time(4, t)
                if (not math.isnan(f0) and not math.isnan(f1) and not math.isnan(f2) and not math.isnan(f3)
                        and not math.isnan(f4)):
                    f0_dict[float(t)] = f0
                    f1_dict[float(t)] = f1
                    f2_dict[float(t)] = f2
                    f3_dict[float(t)] = f3
                    f4_dict[float(t)] = f4
            times = sorted(f1_dict.keys())
            # Rounds the formant up to the nearest integer and stores data in a list
            f0_vals_arr = [round(f0_dict[t], 0) for t in times]
            f1_vals_arr = [round(f1_dict[t], 0) for t in times]
            f2_vals_arr = [round(f2_dict[t], 0) for t in times]
            f3_vals_arr = [round(f3_dict[t], 0) for t in times]
            f4_vals_arr = [round(f4_dict[t], 0) for t in times]
            # Filters out all the frequency anomalies
            times_f0, f0_vals_arr = filter_frequency_synchronized("F0", times, f0_vals_arr)
            times_f1, f1_vals_arr = filter_frequency_synchronized("F1", times, f1_vals_arr)
            times_f2, f2_vals_arr = filter_frequency_synchronized("F2", times, f2_vals_arr)
            times_f3, f3_vals_arr = filter_frequency_synchronized("F3", times, f3_vals_arr)
            times_f4, f4_vals_arr = filter_frequency_synchronized("F4", times, f4_vals_arr)
            # Gets the average formants
            f0_average = get_freq_average(f0_vals_arr)
            f1_average = get_freq_average(f1_vals_arr)
            f2_average = get_freq_average(f2_vals_arr)
            f3_average = get_freq_average(f3_vals_arr)
            f4_average = get_freq_average(f4_vals_arr)
            # Crates a list of averages where i = 0 is f0_average and i = 4 is f4_average
            avg_formants = [f0_average, f1_average, f2_average, f3_average, f4_average]
            # Connects to the sqlite db
            connect_table()
            # Inserts the formant data into the sqlite3 database
            insert_to_table(times_f0, f0_vals_arr, times_f1, f1_vals_arr, times_f2, f2_vals_arr,
                            times_f3, f3_vals_arr, times_f4, f4_vals_arr, avg_formants)

    except NameError:
        print("No File Selected")


if __name__ == "__main__":
    main()
