package core;

import java.awt.Desktop;
import java.io.File;

public class DesktopUtils {

    /**
     * A shared, reusable method to safely try opening a file with the system's default application.
     * @param file The file to open.
     */
    public static void tryOpenFile(File file) {
        if (file != null && file.exists() && Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(file);
            } catch (Exception e) {
                System.out.println("⚠️ Could not auto-open file: " + e.getMessage());
            }
        }
    }
}