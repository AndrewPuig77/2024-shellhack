package com.example.app;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;

public class UserInput {

    JsonCreation reader = new JsonCreation();
    @FXML
    private TextField textBox1;
    @FXML
    private TextField textBox2;
    @FXML
    private TextField textBox3;
    private TextField gentText;
    public void setData(TextField value1, TextField value2, TextField value3) {
        this.textBox1 = value1;
        this.textBox2 = value2;
        this.textBox3 = value3;
    }

    public void setTextBox1(TextField textBox1) {
        this.textBox1 = textBox1;
    }

    public void setTextBox2(TextField textBox2) {
        this.textBox2 = textBox2;
    }

    public void setTextBox3(TextField textBox3) {
        this.textBox3 = textBox3;
    }

    @FXML
    private Button myButton; // Optional, if you have a button to trigger the action

    @FXML
    public void handleButtonClick() {
        // Get the text from the TextField
        if (textBox1.getText() != null && textBox2.getText() != null && textBox3.getText() != null) {
            String inputText1 = textBox1.getText();
            String inputText2 = textBox2.getText();
            String inputText3 = textBox3.getText();
            // Do something with the inputText, like printing it
            reader.jsonCreate(inputText1, inputText2, inputText3);
        } else if(gentText != null) {
            String genInput = gentText.getText();
            reader.genJson(genInput);
        }
    }
}