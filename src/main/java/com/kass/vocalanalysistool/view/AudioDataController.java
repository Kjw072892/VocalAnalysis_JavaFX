package com.kass.vocalanalysistool.view;

import com.kass.vocalanalysistool.model.UserFormantDatabase;
import java.io.ByteArrayInputStream;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class AudioDataController {
    @FXML
    private ImageView myScatterPlotImage;

    @FXML
    private Label myInstructionLabel;

    @FXML
    private MenuItem myNewAnalysisMenuItem;

    @FXML
    private MenuItem myOpenMenuItem;

    @FXML
    private MenuItem mySaveAnalysisMenuItem;

    @FXML
    private MenuItem myExportMenuItem;

    @FXML
    private MenuItem myCloseMenuItem;

    @FXML
    private Menu myHelpButton;

    @FXML
    private Menu myFileMenuButton;

    @FXML
    private void initialize() {
        final UserFormantDatabase db = new UserFormantDatabase(false);
        myScatterPlotImage.setImage(new Image(new ByteArrayInputStream(db.getScatterPlot())));

        myInstructionLabel.setText(
                """    
                * Formant [F1-F4] are frequency ranges of vowel's formant.
                
                * Vowels and vocal-tract-shape influences your formant frequencies.
                
                * Bellow are typical formant ranges of Cisgender Women and Cisgender Men.
                
                * Formant data can give you a good idea about how you are forming your
                  vowels, tongue placements, lips placements, jaw placements,
                  and resonance locations
                
                * Tongue Height (Jaw Opening):
                    ** F1: Cis Women [300 - 900] Hz | Cis Men [250 - 750] Hz
                
                    - High tongue (closed mouth) -> Low F1
                        + Vowels like /i/("ee" as in beet), /u/("00" in boot)
                
                    - Low tongue (open mouth) -> High F1
                        + Vowels like /a/("ah" in father), /æ/("a" in cat)
                
                    - Inversely related to tongue height
                
                * Tongue Frontness:
                
                    ** F2: Cis Women [1450- 2850] Hz | Cis Men [725 - 1900] Hz
                
                    - Tongue forward -> High F2
                        + Vowels like /i/("ee"), /e/("ay")
                
                    - Tongue back -> Low F2
                        + Vowels like /u/ ("oo"),/o/ ("oh", /a/("aw")
                
                    - Directly related to tongue front-ness
                
                * Lip Shape & Resonance Characteristics:
                
                    ** F3: Cis Women [2600 - 3600] Hz | Cis Men [1600 - 2800] Hz
                
                    - Influenced by:
                        + Lip rounding -> Lowers F3 (as in /r/ and /u/)
                        + Constrictions in oral cavity
                
                    - Less dramatically tied to vowels than F1/F2, but:
                        + /i/ tends to have high F3
                        + /u/ and /r/ have low F3
                
                * Speaker & Timbre Characteristics:
                
                    ** F4: Cis Women [3500 - 5000] Hz | Cis Men [2800 - 3700] Hz
                
                    - Not directly vowel-diagnostic
                
                    - Affected by:
                        + Vocal tract length
                        + Voice quality
                        + Subtle articulation cues
                
                    - Used more in voice profiling (e.g., gender, vocal effort),
                      not vowel identification
                """
        );

    }

    @FXML
    private void handleAnalyzeButton() {

    }

    @FXML
    private void handleNewRecordingButton() {

    }
}
