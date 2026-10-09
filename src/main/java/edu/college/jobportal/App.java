package edu.college.jobportal;

import edu.college.jobportal.gui.LoginFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Application entry point. */
public final class App {
    private App() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception exception) {
                System.err.println("Using the default Swing look and feel.");
            }
            new LoginFrame().setVisible(true);
        });
    }
}
