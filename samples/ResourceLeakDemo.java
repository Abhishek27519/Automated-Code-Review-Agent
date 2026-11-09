import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ResourceLeakDemo {

    public List<String> readErrorLines(String path) throws IOException {
        List<String> errors = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new FileReader(path));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains("ERROR")) {
                errors.add(line);
            }
        }
        return errors;
    }
}