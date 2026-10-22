module com.example.app {
    requires javafx.controls;
    requires javafx.fxml;
    requires transitive javafx.media;
    requires org.json;
    requires org.python.jython2.standalone;
    requires java.sql;
    requires com.google.gson;
    requires jlatexmath;
    requires javafx.swing;


    opens com.example.app to javafx.fxml;
    exports com.example.app;
}