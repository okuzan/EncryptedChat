package maindir.utils;

import javafx.scene.control.TextArea;
import java.io.IOException;
import java.io.OutputStream;

public class CustomOutput extends OutputStream {

    private final TextArea txtArea;
    private final StringBuilder sb = new StringBuilder();

    public CustomOutput(TextArea txtArea) {
        this.txtArea = txtArea;
    }

    @Override
    public void write(int b) throws IOException {
        if (b == '\r') return;
        if (b == '\n') {
            final String text = sb.toString() + "\n";
            txtArea.setText(txtArea.getText()+ "\n" + text);
            sb.setLength(0);
        } else {
            sb.append((char) b);
        }
    }
}