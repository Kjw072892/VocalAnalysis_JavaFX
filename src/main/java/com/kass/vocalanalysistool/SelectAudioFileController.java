package com.kass.vocalanalysistool;


import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
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
import javafx.stage.Window;

public class SelectAudioFileController {
    Logger logger = Logger.getLogger(this.getClass().getName());

    @FXML
    private Button openFileButton;

    @FXML
    private Button exitButton;

    @FXML
    public void initialize() {

    }

    @FXML
    private void handleOpenFile() throws IOException {

        logger.setLevel(Level.INFO);

        FileChooser fileChooser = new FileChooser();

        fileChooser.setTitle("Select Audio File");

        File file = fileChooser.showOpenDialog(openFileButton.getScene().getWindow());

        if (file != null) {
            String path = file.getAbsolutePath();

            logger.info(() -> "Path: " + path);


            //TODO::Create a python class that runs parslemouth
            runPythonScript(path);

            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/kass" +
                        "/vocalanalysistool/AudioData.fxml"));
                Parent root = loader.load();

                //Passing the data to the AudioDataController class
                AudioDataController dataController = loader.getController();
                //dataController.setResults(results);

                Stage audioDataController = new Stage();
                audioDataController.setTitle("Analysis Results");
                audioDataController.setScene(new Scene(root));
                audioDataController.show();
                audioDataController.setResizable(false);
                audioDataController.getIcons().add(new Image(Objects.requireNonNull
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

    private void runPythonScript(final String theFilePath) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder("dist/Vocal_Analysis_Script.exe", theFilePath);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("[Python] " + line);
            }

            int exitCode = process.waitFor();
            logger.info("Python process exited with code " + exitCode);
        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, "Failed to run Python script", e);
        }
    }


}
