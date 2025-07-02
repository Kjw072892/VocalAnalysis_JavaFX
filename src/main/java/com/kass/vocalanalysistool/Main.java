package com.kass.vocalanalysistool;

import java.util.Objects;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource(
                "Select Audio file.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 453, 400);
        stage.setTitle("Select Audio File");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/com/kass/vocalanalysistool/vocal_analysis_icon.png"))));
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}