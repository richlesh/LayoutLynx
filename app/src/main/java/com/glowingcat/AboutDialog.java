/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class AboutDialog {

    public static void show(JFrame parent, Preferences prefs) {
        JDialog dialog = new JDialog(parent, "About LayoutLynx", true);
        dialog.setUndecorated(true);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(new Color(80, 80, 80), 1));

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(40, 40, 40));
        panel.setBorder(BorderFactory.createEmptyBorder(30, 50, 30, 50));

        // Icon
        var iconUrl = AboutDialog.class.getClassLoader().getResource("app_icon_256.png");
        if (iconUrl != null) {
            JLabel iconLabel = new JLabel(new ImageIcon(new ImageIcon(iconUrl)
                .getImage().getScaledInstance(96, 96, Image.SCALE_SMOOTH)));
            iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            panel.add(iconLabel);
        }
        panel.add(Box.createVerticalStrut(14));

        JLabel name = new JLabel("LayoutLynx");
        name.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        name.setForeground(Color.WHITE);
        name.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(name);
        panel.add(Box.createVerticalStrut(4));

        JLabel subtitle = new JLabel("A CSS Editor with AI Styling Assistant");
        subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        subtitle.setForeground(new Color(150, 150, 150));
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(subtitle);
        panel.add(Box.createVerticalStrut(10));

        JLabel ver = new JLabel("Version 1.1.0");
        ver.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        ver.setForeground(new Color(180, 180, 180));
        ver.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(ver);
        panel.add(Box.createVerticalStrut(4));

        JLabel copy = new JLabel("\u00a9 2026 Glowing Cat Software");
        copy.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        copy.setForeground(new Color(180, 180, 180));
        copy.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(copy);
        panel.add(Box.createVerticalStrut(12));

        JLabel link1 = new JLabel("<html><a style='color:#4da3ff;'>Glowing Cat Software</a></html>");
        link1.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        link1.setHorizontalAlignment(SwingConstants.CENTER);
        link1.setAlignmentX(Component.CENTER_ALIGNMENT);
        link1.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link1.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                try { Desktop.getDesktop().browse(java.net.URI.create("https://glowingcatsoftware.com")); }
                catch (Exception ignored) {}
            }
        });
        panel.add(link1);
        panel.add(Box.createVerticalStrut(4));

        JLabel link2 = new JLabel("<html><a style='color:#4da3ff;'>Report issues on GitHub</a></html>");
        link2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        link2.setHorizontalAlignment(SwingConstants.CENTER);
        link2.setAlignmentX(Component.CENTER_ALIGNMENT);
        link2.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link2.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                try { Desktop.getDesktop().browse(java.net.URI.create("https://github.com/richlesh/LayoutLynx/issues")); }
                catch (Exception ignored) {}
            }
        });
        panel.add(link2);
        panel.add(Box.createVerticalStrut(18));

        JButton okBtn = new JButton("OK");
        okBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        okBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        okBtn.addActionListener(ev -> dialog.dispose());
        panel.add(okBtn);

        if (LicenseDialog.isLicensed(prefs)) {
            panel.add(Box.createVerticalStrut(14));
            JLabel thanks = new JLabel("<html><p style='text-align: center;'><b>Thank you for purchasing a<br>license for LayoutLynx!</b></p></html>");
            thanks.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            thanks.setForeground(new Color(100, 200, 100));
            thanks.setHorizontalAlignment(SwingConstants.CENTER);
            thanks.setAlignmentX(Component.CENTER_ALIGNMENT);
            panel.add(thanks);
        }

        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }
}
