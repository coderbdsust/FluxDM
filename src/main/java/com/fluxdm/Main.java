package com.fluxdm;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Must run on Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            ThemeManager.init();
            FluxDMFrame frame = new FluxDMFrame();

            List<Image> icons = IconGenerator.appIcons();
            frame.setIconImages(icons);

            // macOS dock icon
            if (Taskbar.isTaskbarSupported()) {
                Taskbar taskbar = Taskbar.getTaskbar();
                if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                    taskbar.setIconImage(icons.getLast());
                }
            }

            frame.setVisible(true);
        });
    }
}
