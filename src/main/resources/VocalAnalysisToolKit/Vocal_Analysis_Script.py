import io
import json
import math
import sys
from sqlite3 import Binary
from typing import Optional

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import parselmouth
import sqlite3

FILE_PATH = None
PREVIOUS_TIME = 0
PREVIOUS_FREQ = 0
PREVIOUS_FREQ_F0 = 0
PREVIOUS_FREQ_F1 = 0
PREVIOUS_FREQ_F2 = 0
PREVIOUS_FREQ_F3 = 0
PREVIOUS_FREQ_F4 = 0
UNVOICE_DB = -200.0

"""
Filters anomalous frequencies

time_list: A list of the time stamp extracted from the audio sample
freqs: A list of the frequencies extracted from the audio sample

returns: tuple with the new synchronized time stamp and a list of the filtered frequencies
"""


# list[[time_0, f0_0, f1_0, f2_0, f3_0, f4_0], [time_1, f0_1, f1_1, f2_1, f3_1, f4_1]]
def filter_frequency_synchronized_patch(data: list[list[float]]) -> list[list[float]]:
    global PREVIOUS_FREQ_F0, PREVIOUS_FREQ_F1, PREVIOUS_FREQ_F2, PREVIOUS_FREQ_F3, PREVIOUS_FREQ_F4, PREVIOUS_TIME
    post_filter = []
    for i in range(len(data)):
        sub_data = data[i]
        time = sub_data[0]  # type: float
        f0 = sub_data[1]  # type: float
        f1 = sub_data[2]  # type: float
        f2 = sub_data[3]  # type: float
        f3 = sub_data[4]  # type: float
        f4 = sub_data[5]  # type: float

        is_started = (PREVIOUS_FREQ_F1 == 0 and PREVIOUS_FREQ_F2 == 0 and PREVIOUS_FREQ_F3 == 0 and PREVIOUS_FREQ_F4
                      == 0)
        # Initialize the previous frequencies
        if is_started:
            PREVIOUS_FREQ_F0 = f0
            PREVIOUS_FREQ_F1 = f1
            PREVIOUS_FREQ_F2 = f2
            PREVIOUS_FREQ_F3 = f3
            PREVIOUS_FREQ_F4 = f4

        # Skips if the pitch is zero
        if f0 is None or f0 <= 0:
            continue

        f0_ok = filter_helper("f0", f0, time)
        f1_ok = filter_helper("f1", f1, time)
        f2_ok = filter_helper("f2", f2, time)
        f3_ok = filter_helper("f3", f3, time)
        f4_ok = filter_helper("f4", f4, time)

        low_ok_count = int(f1_ok) + int(f2_ok) + int(f3_ok) + int(f4_ok)

        valid = f0_ok and low_ok_count > 2

        if valid:
            post_filter.append([time, f0, f1, f2, f3, f4])
            PREVIOUS_FREQ_F0 = f0
            PREVIOUS_FREQ_F1 = f1
            PREVIOUS_FREQ_F2 = f2
            PREVIOUS_FREQ_F3 = f3
            PREVIOUS_FREQ_F4 = f4
            PREVIOUS_TIME = time

    return post_filter


def filter_helper(formant: str, frequency: float, time: Optional[float] = None):
    global PREVIOUS_FREQ_F0, PREVIOUS_FREQ_F1, PREVIOUS_FREQ_F2, PREVIOUS_FREQ_F3, PREVIOUS_FREQ_F4, PREVIOUS_TIME

    # invalid current value
    if frequency is None or frequency <= 0:
        return False

    # optional spacing gate (ignore frames closer than 50 ms to the previous)
    if (time is not None and PREVIOUS_TIME is not None and
            abs(time - PREVIOUS_TIME) <= 0.05):
        return False

    form = formant.casefold()

    # thresholds (continuity) in semitones and plausibility ranges in Hz
    # tune if needed
    ranges = {
        "f0": (None, 75.0, 400.0),  # (th set below), hard range gate for pitch
        "f1": (5.0, 150.0, 1200.0),
        "f2": (7.0, 500.0, 3500.0),
        "f3": (8.0, 1500.0, 4000.0),
        "f4": (9.0, 2500.0, 5000.0),
    }

    if form not in ranges:
        return False

    fth, lo, hi = ranges[form]


    # select previous value
    if form == "f0":
        prev_freq = PREVIOUS_FREQ_F0
        # hard range gate for pitch
        if frequency <= lo or frequency >= hi:
            return False

        # if we have a valid previous, enforce jump rate and continuity
        if prev_freq is not None and prev_freq > 0:
            # jump-size in semitones
            st = 12.0 * abs(math.log2(frequency / prev_freq))

            # jump-rate gate: >= 2 octaves in < 1s -> > 24 st/s
            if time is not None and PREVIOUS_TIME is not None and time > PREVIOUS_TIME:
                dt = time - PREVIOUS_TIME
                if dt > 0 and (st / dt) > 24.0:
                    return False

            # continuity gate for F0 (tunable; default 4 st)
            if st > 4.0:
                return False

        # if no previous, accept current (caller should update PREVIOUS_* and PREVIOUS_TIME on accept)
        return True

    else:
        # formants F1..F4: plausibility gate first
        if frequency <= lo or frequency >= hi:
            return False

        # pick previous for this track
        if form == "f1":
            prev_freq = PREVIOUS_FREQ_F1
        elif form == "f2":
            prev_freq = PREVIOUS_FREQ_F2
        elif form == "f3":
            prev_freq = PREVIOUS_FREQ_F3
        else:  # f4
            prev_freq = PREVIOUS_FREQ_F4

        # no previous → accept
        if prev_freq is None or prev_freq <= 0:
            return True

        # continuity in semitones
        st = 12.0 * abs(math.log2(frequency / prev_freq))
        return st <= fth


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


def plot_formants(time_: list[float], f0_: list[float],
                  f1_: list[float], f2_: list[float],
                  f3_: list[float], f4_: list[float]) -> bytes:
    ticks1 = np.arange(0, 1001, 100)
    ticks2 = np.arange(1000, 8001, 500)
    yticks = np.unique(np.concatenate([ticks1, ticks2]))

    fig, ax = plt.subplots(figsize=(6, 12), dpi=300)

    ax.plot(time_, f0_, label="Pitch", color="black")
    ax.set_yticks(yticks)
    ax.minorticks_on()
    ax.scatter(time_, f1_, s=5, label="F1")
    ax.scatter(time_, f2_, s=5, label="F2")
    ax.scatter(time_, f3_, s=5, label="F3")
    ax.scatter(time_, f4_, s=5, label="F4")
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


def insert_to_table(time_0, f0_, f1_, f2_, f3_, f4_: list[float],
                    formant_avg: list[int]) -> None:
    png_bytes = plot_formants(time_0, f0_, f1_, f2_, f3_, f4_)
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
            times = np.arange(0, sound.get_total_duration(), 0.01)
            f0_dict = {}
            f1_dict = {}
            f2_dict = {}
            f3_dict = {}
            f4_dict = {}
            # Initializes the formant and pitch

            full_data = []

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

                    full_data.append([float(round(t,2)), round(f0, 5), round(f1, 5), round(f2, 5), round(f3, 5),
                                      round(f4, 5)])


            full_data = filter_frequency_synchronized_patch(full_data)
            times_, f0_vals_arr, f1_vals_arr, f2_vals_arr, f3_vals_arr, f4_vals_arr = [], [], [], [], [], []
            print(full_data)

            for i in range(len(full_data)):
                times_.append(full_data[i][0])
                f0_vals_arr.append(full_data[i][1])
                f1_vals_arr.append(full_data[i][2])
                f2_vals_arr.append(full_data[i][3])
                f3_vals_arr.append(full_data[i][4])
                f4_vals_arr.append(full_data[i][5])

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
            insert_to_table(times_, f0_vals_arr, f1_vals_arr, f2_vals_arr,
                            f3_vals_arr, f4_vals_arr, avg_formants)


    except NameError:
        print("No File Selected")


if __name__ == "__main__":
    main()
