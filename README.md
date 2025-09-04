# VocalAnalysis_JavaFX
## Development Instructions:

### Generating a CSV for the learning model
* {F0_avg, F1_avg, F2_avg, F3_avg, F4_avg,
  hnr_mean_db, hnr_median_db,
  voiced_fraction,
  f0_mean_hz, f0_sd_st, range_st_5_95, slope_st_per_sec,
  f0_p5_hz, f0_p95_hz,
  formant_dispersion,
  gender_perception}
  - formant_dispersion = mean([F2_avg - F1_avg, F3_avg - F2_avg, F4_avg - F3_avg])
  - You standardize features (fit scaler on train only), Your dataset is balanced/diverse (mics, rooms, content),
    You do stratified CV and report F1/ROC-AUC, not just accuracy. 
