module com.kass.vocalanalysistool {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires java.logging;

    opens com.kass.vocalanalysistool to javafx.fxml;
    exports com.kass.vocalanalysistool;
    exports com.kass.vocalanalysistool.controller;
    opens com.kass.vocalanalysistool.controller to javafx.fxml;
}