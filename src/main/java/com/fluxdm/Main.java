package com.fluxdm;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        // Must run on Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            ThemeManager.init();
            new FluxDMFrame().setVisible(true);
        });
    }
}
