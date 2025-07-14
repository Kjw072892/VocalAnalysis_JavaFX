import io
import json

import parselmouth
import math
import sys
import sqlite3
import numpy as np
from matplotlib.backend_tools import cursors
from matplotlib.backends.backend_tkagg import FigureCanvasTkAgg
from matplotlib.figure import Figure

FILE_PATH = None  # Global so it can be used by the rest of your script

# Formant lists
F0_LIST = None
F1_LIST = None
F2_LIST = None
F3_LIST = None
F4_LIST = None


"""
Output the collected data for easy use

The first list added will be designated as F0, the second F1 and so on
list[float] [f0, f1, f2, f3, f4]
"""


def output(list_times:list[list[float]]):
    global F0_LIST, F1_LIST, F2_LIST, F3_LIST, F4_LIST
    fig = Figure(figsize=(6, 12), dpi=100)
    ax = fig.add_subplot(111)
    max_freqs = max(max(F0_LIST), max(F1_LIST), max(F2_LIST), max(F3_LIST), max(F4_LIST))

    ax.plot(list_times[0], F0_LIST, label='Pitch', color='black')
    ax.scatter(list_times[1], F1_LIST, label='F1', color="red")
    ax.scatter(list_times[2], F2_LIST, label='F2', color="blue")
    ax.scatter(list_times[3], F3_LIST, label='F3', color="orange")
    ax.scatter(list_times[4], F4_LIST, label='F4', color="green")

    ax.set_xlabel("Time (sec)")
    ax.set_ylabel("Frequency (Hz)")
    ax.set_yticks(list(np.arange(0, 1001, 100)) + list(np.arange(1100, max_freqs, 200)))
    ax.set_title("Formant Tracks Over Time")
    ax.legend()
    ax.grid(True)

    buf = io.BytesIO()
    fig.savefig(buf, format='png')
    plot_bytes = buf.getvalue()
    buf.close()


    connect = sqlite3.connect("Vocal_Analysis.db")
    cursor = connect.cursor()

    cursor.execute("""
                   CREATE TABLE IF NOT EXISTS Vocal_Analysis
                   (
                       id
                       INTEGER
                       PRIMARY
                       KEY
                       AUTOINCREMENT,
                       F0_list
                       TEXT
                       NOT
                       NULL,
                       F1_list
                       TEXT
                       NOT
                       NULL,
                       F2_list
                       TEXT
                       NOT
                       NULL,
                       F3_list
                       TEXT
                       NOT
                       NULL,
                       F4_list
                       TEXT
                       NOT
                       NULL,
                       Time
                       TEXT
                       NOT
                       NULL,
                       Plot
                       BLOB
                       NOT
                       NULL
                   );
                   """)


    # insert into database
    cursor.execute("""
                   INSERT INTO Vocal_Analysis (F0_list, F1_list, F2_list, F3_list, F4_list, Time, Plot)
                   VALUES (?, ?, ?, ?, ?, ?)""",
                   (json.dumps(F0_LIST), json.dumps(F1_LIST), json.dumps(F2_LIST), json.dumps(F3_LIST), json.dumps(
                       F4_LIST), json.dumps(list_times), plot_bytes))


"""
Filters anomalous frequencies

time_list: A list of the time stamp extracted from the audio sample
freqs: A list of the frequencies extracted from the audio sample

returns: tuple with the new synchronized time stamp and a list of the filtered frequencies
"""


def filter_frequency_synchronized(formant_: str, time_list: list[float], freqs: list[float]) -> tuple[
    list[float], list[float]]:
    # First Filter
    new_times = []
    new_freq = []

    for time, freqs in zip(time_list, freqs):

        if freqs > 0:
            new_times.append(time)
            new_freq.append(freqs)

    # Second Filter low/high pass filter

    prev_freq = 0

    temp_freq = []
    temp_time = []

    for time, freqs in zip(new_times, new_freq):

        if prev_freq == 0:
            prev_freq = freqs
            continue
        if formant_.casefold() == "f0":
            if 60 < freqs < 335:
                temp_freq.append(freqs)
                temp_time.append(time)
                prev_freq = freqs
                continue
        elif formant_.casefold() == "f1":
            if 250 <= freqs < 890:
                temp_freq.append(freqs)
                temp_time.append(time)
                prev_freq = freqs
                continue
        elif formant_.casefold() == "f2":
            if 800 <= freqs < 2800:
                temp_freq.append(freqs)
                temp_time.append(time)
                prev_freq = freqs
                continue
        elif formant_.casefold() == "f3":
            if 2000 < freqs < 3400:
                temp_freq.append(freqs)
                temp_time.append(time)
                prev_freq = freqs
                continue
        elif formant_.casefold() == "f4":
            if 2800 < freqs < 4500:
                temp_freq.append(freqs)
                temp_time.append(time)
                prev_freq = freqs
                continue

    new_times = temp_time
    new_freq = temp_freq

    return new_times, new_freq


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


"""
Gets the lowest Formant in the dataset

arr: the array with the formant dataset

formant_low: F0 - F4
"""


def get_low(freq_data: list[float], formant_low: str):
    low = freq_data[0]

    min_freq = 0

    match formant_low.casefold():
        case "F0":
            min_freq = 91
        case "F1":
            min_freq = 250
        case "F2":
            min_freq = 600
        case "F3":
            min_freq = 1500
        case "F4":
            min_freq = 2500

    for index in range(0, len(freq_data)):
        low = freq_data[index] if low > freq_data[index] > min_freq else low

    return low


"""
Gets the highest Formant in the data set

: the array with the formant dataset

formant_high: F0 - F4
"""


def get_high(freq_data: list[float], formant_high: str):
    high = freq_data[0]
    high_freq = 0

    match formant_high.casefold():
        case "F0":
            high_freq = 650
        case "F1":
            high_freq = 900
        case "F2":
            high_freq = 2500
        case "F3":
            high_freq = 3400
        case "F4":
            high_freq = 4500

    for index in range(0, len(freq_data)):
        high = freq_data[index] if high < freq_data[index] < high_freq else high

    return high


def get_file_path(the_file_path: str):
    global FILE_PATH
    FILE_PATH = the_file_path


if FILE_PATH:
    sound = parselmouth.Sound(FILE_PATH)
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
        F0_LIST = pitch.get_value_at_time(t)
        F1_LIST = formant.get_value_at_time(1, t)
        F2_LIST = formant.get_value_at_time(2, t)
        F3_LIST = formant.get_value_at_time(3, t)
        F4_LIST = formant.get_value_at_time(4, t)

        if (not math.isnan(F0_LIST) and not math.isnan(F1_LIST) and not math.isnan(F2_LIST)
                and not math.isnan(F3_LIST) and not math.isnan(F4_LIST)):
            f0_dict[float(t)] = F0_LIST
            f1_dict[float(t)] = F1_LIST
            f2_dict[float(t)] = F2_LIST
            f3_dict[float(t)] = F3_LIST
            f4_dict[float(t)] = F4_LIST

    times = sorted(f1_dict.keys())

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

    f0_average = get_freq_average(f0_vals_arr)
    f1_average = get_freq_average(f1_vals_arr)
    f2_average = get_freq_average(f2_vals_arr)
    f3_average = get_freq_average(f3_vals_arr)
    f4_average = get_freq_average(f4_vals_arr)

    # Get the lowest and highest frequencies
    f0_low, f0_high = get_low(f0_vals_arr, "f0".casefold()), get_high(f0_vals_arr, "f0".casefold())
    f1_low, f1_high = get_low(f1_vals_arr, "f1".casefold()), get_high(f1_vals_arr, "f1".casefold())
    f2_low, f2_high = get_low(f2_vals_arr, "f2".casefold()), get_high(f2_vals_arr, "f2".casefold())
    f3_low, f3_high = get_low(f3_vals_arr, "f3".casefold()), get_high(f3_vals_arr, "f3".casefold())
    f4_low, f4_high = get_low(f4_vals_arr, "f4".casefold()), get_high(f4_vals_arr, "f4".casefold())


def main():
    # sys.argv[0] is the script name
    # sys.argv[1] is the argument from java
    try:
        global FILE_PATH, F0_LIST, F1_LIST, F2_LIST, F3_LIST, F4_LIST

        FILE_PATH = sys.argv[1:]

        F0_LIST = f0_vals_arr

        times_list = [times_f0,times_f1,times_f2,times_f3,times_f4]

        output(times_list)

    except NameError:
        print("No File Selected")


if __name__ == "__main__":
    main()
