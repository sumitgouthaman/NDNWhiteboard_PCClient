package com.sumitgouthaman;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Created by sumit on 5/17/15.
 */
public class Info {
    private JPanel panel1;
    private JTextField usernameField;
    private JTextField whiteboardField;
    private JButton nextButton;
    private JTextField prefixField;

    public void show() {
        JFrame frame = new JFrame("NDN Whiteboard");
        frame.setContentPane(panel1);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);

        usernameField.setText(Utils.generateRandomName());
        whiteboardField.setText(Utils.genWhiteboardName());

        nextButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String username = usernameField.getText();
                String whiteboard = whiteboardField.getText();
                String prefix = prefixField.getText();

                Whiteboard whiteboardForm = new Whiteboard(username, whiteboard, prefix);
                whiteboardForm.show();
                frame.dispose();
            }
        });
    }
}
