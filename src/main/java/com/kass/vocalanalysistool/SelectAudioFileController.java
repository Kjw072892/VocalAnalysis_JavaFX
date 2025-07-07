package com.kass.vocalanalysistool;


import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class SelectAudioFileController {
    Logger logger = Logger.getLogger("com.kass.vocalanalysistool" +
                        ".SelectAudioFileController");
    @FXML
    private Button openFileButton;

    @FXML
    private Button exitButton;

    @FXML
    public void initialize() {

    }

    @FXML
    private void handleOpenFile() {
        FileChooser fileChooser = new FileChooser();

        fileChooser.setTitle("Select Audio File");

        File file = fileChooser.showOpenDialog(null);

        if (file != null) {
             String path = file.getAbsolutePath();

             //TODO::Create a python class that runs parslemouth
              //add python class
             //PythonAudioProcessor processor = new PythonAudioProcessor();
            //String results = processor.process(path); [Method in processor that returns
            // string]

            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/kass" +
                        "/vocalanalysistool/AudioData.fxml"));
                Parent root = loader.load();

                //Passing the data to the AudioDataController class
                AudioDataController dataController = loader.getController();
                //dataController.setResults(results);

                Stage audioDataControllerStage = new Stage();
                audioDataControllerStage.setTitle("Analysis Results");
                audioDataControllerStage.setScene(new Scene(root));
                audioDataControllerStage.show();
                audioDataControllerStage.setResizable(false);
                audioDataControllerStage.getIcons().add(new Image(Objects.requireNonNull
                        (getClass().getResourceAsStream
                                ("/com/kass/vocalanalysistool/vocal_analysis_icon.png"))));

                Stage selectAudioFileControllerScene = (Stage) openFileButton.getScene().getWindow();
                selectAudioFileControllerScene.close();

            } catch (IOException e) {

                logger.log(Level.SEVERE, "No File Found!", e);

            }

            logger.fine("done");
        }
    }

    @FXML
    private void handleExit() {
        Stage exitStage = (Stage) exitButton.getScene().getWindow();
        exitStage.close();
    }
//


}
