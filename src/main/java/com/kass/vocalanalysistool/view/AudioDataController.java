package com.kass.vocalanalysistool.view;

import com.kass.vocalanalysistool.controller.Main;
import com.kass.vocalanalysistool.model.UserFormantDatabase;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

public class AudioDataController {

    /**
     * The formant database object.
     */
    private final UserFormantDatabase db = new UserFormantDatabase(false);

    /**
     * The scatterplot image container
     */
    @FXML
    private ImageView myScatterPlotImage;

    /**
     * The label inside the scrollable container
     */
    @FXML
    private Label myInformationLabel;

    /**
     * Initializes the scene prior to showcasing it.
     */
    @FXML
    private void initialize() {

        myScatterPlotImage.setImage(new Image(new ByteArrayInputStream(db.getScatterPlot())));

        myInformationLabel.setText(
                """    
                * Formant [F1–F4] are resonant frequency ranges shaped by the vocal tract.
                    Vowels and vocal-tract configuration (tongue, lips, jaw) strongly influence
                    formant placement.
                
                * Typical ranges for cisgender women and men are listed below, but note that
                    these values overlap and vary by age, dialect, context, and articulation.
                
                * Formant data can give you a good idea about how you are forming your
                    vowels, tongue placements, lips placements, jaw placements,
                    resonance locations, as well as how you are articulating your words.
                
                * Gender perception of your voice can be predicted based on pitch,
                    formant data, your breathiness index, and intonation data.
                
                ---------------------------------- Pitch ----------------------------------
                
                ** Pitch [F0] Cis Women [165 - 300] Hz | Cis Men [85 - 155] Hz:
                    - Pitch may play a huge role on how a person may gender your voice.
                        However, pitch alone can not be reliably used to determine the
                        gender perception of your voice. We must take into
                        consideration of how we enunciate our words, as well as our resonance.
                
                -------------------------------- Resonance --------------------------------
     
                ** F1: Cis Women [300 - 900] Hz | Cis Men [250 - 750] Hz:
                    - Influenced by Tongue Height (Mouth Openness).

                    - High tongue (closed mouth) -> Low F1:
                        + Vowels like /i/ ("ee" in beet), /u/ ("oo" in boot)
                
                    - Low tongue (open mouth) -> High F1:
                        + Vowels like /a/ ("ah" in father), /æ/ ("a" in cat)
                
                    - Inversely related to tongue height:
                        + To increase F1: lower the tongue (open the mouth more).
                        + To decrease F1: raise the tongue (close the mouth more).
                
                
                ** F2: Cis Women [1450- 3200] Hz | Cis Men [725 - 1900] Hz:
                    - Influenced by Tongue Frontness.

                    - Tongue forward -> High F2:
                        + Vowels like /i/ ("ee"), /e/ ("ay")
                
                    - Tongue back -> Low F2:
                        + Vowels like /u/ ("oo"), /o/ ("oh"), /a/ ("ah")
                
                    - Directly related to tongue front-ness.
                        + To increase F2: keep your tongue in the front most part of your mouth.
                        + To decrease F2: keep your tongue in the back most part of your mouth.


                ** F3: Cis Women [2600 - 3600] Hz | Cis Men [1600 - 2800] Hz:
                    - Influenced by Lip Shape & Resonance Characteristics.
     
                    - Influenced by lip rounding -> Lowers F3:
                        + Vowels like (/r/ ("rr" in red and run) and /u/ ("oo" in boot))
                        + Constrictions in oral cavity.
                
                    - Less dramatically tied to vowels than F1/F2, but:
                        + /i/ ("ee") tends to have a higher F3
                        + /u/ ("oo") and /r/ ("rr") tends to have a lower F3
                
                    - Directly related to larynx position, mouth shape, and tongue placement.
                        + To increase F3:
                            - Spread your lips (think gentle smile) when speaking.
                            - Keep your tongue as flat as possible, don't bunch up your tongue.
                            - Raise your larynx.
                            - The tongue should be rubbing the back of your two front teeth.
                            - "The tip of the tongue the teeth the lips".
                        + To decrease F3:
                            - Round your lips more (think puckering your lips) when speaking.
                            - Bunch up your tongue (think trilling your r's but not actually trilling).
                            - Back the tongue slightly towards your throat.
                            - Lower your larynx.
                
                ** F4: Cis Women [3500 - 5000] Hz | Cis Men [2800 - 3700] Hz:
                    - Speaker & Timbre Characteristics.

                    - Not directly vowel-diagnostic.
                
                    - Affected by:
                        + Vocal tract length
                        + Voice quality
                        + Subtle articulation cues
                
                    - Used more in vocal profiling (e.g., gender, vocal effort), not vowel
                        identification.
                
                NOTE:
                      Your data is compared against a Machine Learning model in order to
                      generate a high-confidence prediction of the gender perception of your
                      vocal sample. Your data is yours and yours alone. Your data is not
                      shared or used as a training dataset for the Machine Learning Model.
                      With that said, if you would like to share your audio recording with
                      the developer in order to further improve the Machine Learning Model,
                      please contact the developer directly. Contact information can be found
                      in the about section. Thank you!
                
                
                """
        );

    }

    /**
     * Opens the analysis scene where it breaks down formant data and outputs gender perception
     */
    @FXML
    private void handleAnalyzeButton() {

    }

    /**
     * Reopens a new selectAudioFileController scene
     */
    @FXML
    private void handleNewRecordingButton(){
        final Main start = new Main();
        try {
            start.start(new Stage());
        } catch (final IOException theEvent) {
            final Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setHeaderText("Unable to open a new recording!");
            alert.setTitle("Error Opening New Recording");
            alert.showAndWait();

        }

    }

    /**
     * Exports the scatter plot as a .jpg and the formant data as a .csv
     */
    @FXML
    private void handleMyExportButton() {

    }

    /**
     * Opens the UserGuide scene.
     */
    @FXML
    private void handleMyUserGuideMenuItem() {

    }

    /**
     * Opens the about scene
     */
    @FXML
    private void handleMyAboutVocalAnalysisMenuItem() {

    }

    /**
     * Closes the program.
     */
    @FXML
    private void handleCloseProgram() {
        final Stage myStage = (Stage) myScatterPlotImage.getScene().getWindow();
        myStage.close();
    }
}
