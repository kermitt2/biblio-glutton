package loader;

import java.io.InputStream;

public interface DataLoader {

    void load(String path);

    void load(InputStream input);
}
