module com.kass.vocalanalysistool {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires java.logging;
    requires java.desktop;

    opens com.kass.vocalanalysistool.view to javafx.fxml;
    exports com.kass.vocalanalysistool.view;
    exports com.kass.vocalanalysistool.controller;
    opens com.kass.vocalanalysistool.controller to javafx.fxml;
}