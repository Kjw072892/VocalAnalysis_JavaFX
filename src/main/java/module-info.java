module com.kass.vocalanalysistool {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;

    opens com.kass.vocalanalysistool to javafx.fxml;
    exports com.kass.vocalanalysistool;
}