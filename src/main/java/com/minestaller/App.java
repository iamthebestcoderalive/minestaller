package com.minestaller;

import com.formdev.flatlaf.FlatDarkLaf;
import com.minestaller.ui.MainFrame;

import javax.swing.*;

public class App {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Setup FlatLaf Dark Theme
                FlatDarkLaf.setup();
            } catch (Exception e) {
                System.err.println("Failed to initialize FlatLaf theme: " + e.getMessage());
            }

            // Create and present the application frame
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
