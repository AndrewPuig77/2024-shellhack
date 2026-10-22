module com.example.app {
    requires javafx.controls;
    requires javafx.fxml;
    requires transitive javafx.media;
    requires org.json;
    requires java.sql;
    requires com.google.gson;
    requires jlatexmath;
    requires javafx.swing;
    requires com.github.librepdf.openpdf; //Study notes PDF


    opens com.example.app to javafx.fxml;
    exports com.example.app;
}