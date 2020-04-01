package Manager;

import Core.Main;

import javax.swing.*;

public class InterfaceManager {

    public static JFrame interfaceFrame = new JFrame();
    private static JTextArea logArea = new JTextArea();
    public static JButton resetButton = new JButton("Change Token");


    public static void start(){
        startConsole();
    }

    private static void startConsole(){
        interfaceFrame.pack();
        interfaceFrame.setVisible( true );
        interfaceFrame.setSize(800,600);
        interfaceFrame.setResizable(false);
        interfaceFrame.setLayout(null);

        // Add change token button
        resetButton.setEnabled(false);
        JPanel buttonPanel = new JPanel();
        buttonPanel.setSize(resetButton.getPreferredSize().width, 50);
        buttonPanel.setLocation(700 - (resetButton.getPreferredSize().width / 2), 60);


        resetButton.addActionListener(e -> {
            // Remove token from config
            Main.settingsConfig.remove("general");
            Main.saveConfig();

            // Open new info panel
            JOptionPane.showConfirmDialog(InterfaceManager.interfaceFrame, "Restart application to enter a new Discord Bot Token", "Token Request", JOptionPane.PLAIN_MESSAGE);
        });
        buttonPanel.add(resetButton);
        interfaceFrame.getContentPane().add(buttonPanel);

        // Add Log Pane
        logArea.setEditable(false);
        JScrollPane logPane = new JScrollPane( logArea );
        logPane.setSize(600, 600);

        interfaceFrame.add(logPane);

        // Close window event
        interfaceFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                System.exit(0);
            }
        });
    }

    public static void writeLog(String logMsg){
        logArea.append(logMsg + "\n");
    }

}
