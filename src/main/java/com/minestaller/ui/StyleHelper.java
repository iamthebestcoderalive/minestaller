package com.minestaller.ui;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class StyleHelper {

    // Palette Colors
    public static final Color COLOR_BG = Color.decode("#0B0E14");       // Dark deep base
    public static final Color COLOR_BG_PANEL = Color.decode("#070A0F"); // Darker card background
    public static final Color COLOR_CYAN = Color.decode("#00F0FF");     // Cyber cyan accent
    public static final Color COLOR_GREEN = Color.decode("#39FF14");    // Cyber green accent
    public static final Color COLOR_AMBER = Color.decode("#FFB300");    // Cyber warning amber
    public static final Color COLOR_GRID = Color.decode("#18202C");     // Cyber grid/lines
    public static final Color COLOR_TEXT = Color.decode("#D1D9E6");     // Readable light gray

    // Font
    public static final Font FONT_MONO_TITLE = new Font("Monospaced", Font.BOLD, 18);
    public static final Font FONT_MONO_HEADER = new Font("Monospaced", Font.BOLD, 14);
    public static final Font FONT_MONO_BODY = new Font("Monospaced", Font.PLAIN, 13);
    public static final Font FONT_MONO_SMALL = new Font("Monospaced", Font.PLAIN, 11);

    /**
     * Styles a standard panel to fit the cyberpunk theme.
     */
    public static void stylePanel(JPanel panel) {
        panel.setBackground(COLOR_BG);
        panel.setForeground(COLOR_TEXT);
    }

    /**
     * Creates a card-style panel with grid line borders and custom padding.
     */
    public static JPanel createGridPanel(String title) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(COLOR_BG_PANEL);

        Border lineBorder = BorderFactory.createLineBorder(COLOR_GRID, 1);
        if (title != null && !title.isEmpty()) {
            TitledBorder titled = BorderFactory.createTitledBorder(lineBorder, " " + title + " ");
            titled.setTitleColor(COLOR_CYAN);
            titled.setTitleFont(FONT_MONO_HEADER);
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(5, 5, 5, 5),
                    BorderFactory.createCompoundBorder(titled, BorderFactory.createEmptyBorder(10, 10, 10, 10))
            ));
        } else {
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(5, 5, 5, 5),
                    BorderFactory.createCompoundBorder(lineBorder, BorderFactory.createEmptyBorder(10, 10, 10, 10))
            ));
        }
        return panel;
    }

    /**
     * Styles a text field.
     */
    public static void styleTextField(JTextField field) {
        field.setBackground(COLOR_BG_PANEL);
        field.setForeground(COLOR_CYAN);
        field.setCaretColor(COLOR_CYAN);
        field.setFont(FONT_MONO_BODY);
        field.setBorder(BorderFactory.createLineBorder(COLOR_GRID, 1));
    }

    /**
     * Styles a scroll pane.
     */
    public static void styleScrollPane(JScrollPane scrollPane) {
        scrollPane.setBackground(COLOR_BG_PANEL);
        scrollPane.getViewport().setBackground(COLOR_BG_PANEL);
        scrollPane.setBorder(BorderFactory.createLineBorder(COLOR_GRID, 1));
        
        // Custom scrollbar UI is handled by FlatLaf, but we can set background
        scrollPane.getVerticalScrollBar().setBackground(COLOR_BG);
        scrollPane.getHorizontalScrollBar().setBackground(COLOR_BG);
    }

    /**
     * Styles a combo box.
     */
    public static void styleComboBox(JComboBox<?> comboBox) {
        comboBox.setBackground(COLOR_BG_PANEL);
        comboBox.setForeground(COLOR_CYAN);
        comboBox.setFont(FONT_MONO_BODY);
        comboBox.setBorder(BorderFactory.createLineBorder(COLOR_GRID, 1));
    }

    /**
     * Creates a customized cyberpunk button with glow effects and hover transitions.
     */
    public static JButton createCyberButton(String text, boolean primary) {
        JButton button = new JButton(text);
        button.setFont(FONT_MONO_HEADER);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(true);

        Color mainColor = primary ? COLOR_GREEN : COLOR_CYAN;
        Color hoverBg = mainColor;
        Color hoverFg = COLOR_BG;
        Color normalBg = COLOR_BG_PANEL;
        Color normalFg = mainColor;

        button.setBackground(normalBg);
        button.setForeground(normalFg);
        button.setBorder(BorderFactory.createLineBorder(mainColor, 1));

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setContentAreaFilled(true);
                    button.setBackground(hoverBg);
                    button.setForeground(hoverFg);
                    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setContentAreaFilled(false);
                button.setBackground(normalBg);
                button.setForeground(normalFg);
            }
        });

        return button;
    }

    /**
     * Creates a simple styled label.
     */
    public static JLabel createLabel(String text, Color color, Font font) {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        label.setFont(font);
        return label;
    }
}
