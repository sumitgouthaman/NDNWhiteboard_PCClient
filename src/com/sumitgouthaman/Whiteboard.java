package com.sumitgouthaman;

import javax.swing.*;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.util.ArrayList;

import javafx.scene.paint.*;
import org.json.*;
import sun.jvm.hotspot.ui.classbrowser.ClassBrowserPanel;

/**
 * Created by sumit on 5/17/15.
 */
public class Whiteboard {

    public String username;
    public String whiteboard;
    public String prefix;

    private boolean isEraser = false;
    private int currentColor = 0;
    private int viewWidth = 500;

    DrawArea drawArea = new DrawArea(this);

    private Color[] colors = {Color.BLACK, Color.RED, Color.BLUE, new Color(0xFF458B00), new Color(0xFFED9121)};
    int num_colors = colors.length;

    private enum MotionEvent{
        ACTION_DOWN, ACTION_MOVE, ACTION_UP
    }

    private ArrayList<String> history = new ArrayList<String>();
    private ArrayList<MyPoint> points = new ArrayList<MyPoint>();

    JSONObject jsonObject = null;

    NDNWhiteboard ndnWhiteboard = null;

    private JPanel panel1;
    private JButton penButton;
    private JButton eraserButton;
    public JButton exitButton;
    public JPanel canvasPanel;
    public JLabel statusLabel;
    private JButton clearButton;
    private JButton undoButton;
    private JButton colorButton;

    public Whiteboard(String username, String whiteboard, String prefix) {
        this.username = username;
        this.whiteboard = whiteboard;
        this.prefix = prefix;
    }

    public void show() {
        JFrame frame = new JFrame("Whiteboard");
        frame.setContentPane(panel1);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        canvasPanel.setLayout(new BorderLayout());
        canvasPanel.add(drawArea, BorderLayout.CENTER);
        frame.pack();
        frame.setVisible(true);

        ndnWhiteboard = new NDNWhiteboard(this);
        exitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose();
            }
        });

        eraserButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setEraser();
            }
        });

        penButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setPencil();
            }
        });

        clearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                clear();
            }
        });

        undoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                undo();
            }
        });
        colorButton.setUI(new BasicButtonUI());
        colorButton.setBackground(Color.BLACK);
        colorButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                incrementColor();
            }
        });
    }

    public void callback(String string) {
        parseJSON(string, true);
    }

    public void parseJSON(String string, boolean addToHistory) {
        try {
            JSONObject jsonObject = new JSONObject(string);
            try {
                int colorBefore = currentColor;
                boolean isEraserBefore = isEraser;
                String type = jsonObject.get("type").toString();
                if (type.equals("pen")) {
                    int color = jsonObject.getInt("color");
                    setColor(color);
                } else if (type.equals("eraser")) {
                    setEraser();
                } else if (type.equals("undo")) {
                    if (history.isEmpty()) {
                        return;
                    }
                    String userStr = jsonObject.getString("user");
                    for (int i = history.size() - 1; i >= 0; i--) {
                        if (history.get(i).contains("\"user\":\"" + userStr + "\"")) {
                            history.remove(i);
                            break;
                        }
                    }
                    drawArea.clear();
                    for (String str : history) {
                        parseJSON(str, false);
                    }
                } else if (type.equals("clear")) {
                    history.clear();
                    drawArea.clear();
                } else if (type.equals("text")){
                    String message = jsonObject.getString("data");
                    ndnWhiteboard.setStatus(jsonObject.getString("user") + ": " + message);
                } else {
                    throw new JSONException("Unrecognized string: " + string);
                }
                if (type.equals("pen") || type.equals("eraser")) {
                    JSONArray coordinates = jsonObject.getJSONArray("coordinates");
                    JSONArray startPoint = coordinates.getJSONArray(0);
                    float touchX = (float) startPoint.getDouble(0) * viewWidth;
                    float touchY = (float) startPoint.getDouble(1) * viewWidth;
                    drawArea.pressed(new MyPoint(touchX, touchY));
                    for (int i = 1; i < coordinates.length(); i++) {
                        JSONArray point = coordinates.getJSONArray(i);
                        float x = (float) point.getDouble(0) * viewWidth;
                        float y = (float) point.getDouble(1) * viewWidth;
                        drawArea.moved(new MyPoint(x, y));
                    }
                    if (addToHistory) {
                        history.add(string);
                    }
                    if (isEraserBefore) {
                        setEraser();
                    } else {
                        setColor(colorBefore);
                    }
                }
            } catch (JSONException e) {
                System.out.println("JSON string error: " + string);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void setPencil() {
        setColor(currentColor);
    }

    public void setEraser() {
        drawArea.setColor(Color.WHITE);
        colorButton.setBackground(Color.WHITE);
        drawArea.setStrokeWidth(40);
        isEraser = true;
    }

    private void setColor(int c) {
        currentColor = c;
        drawArea.setColor(colors[currentColor]);
        drawArea.setStrokeWidth(4);
        isEraser = false;
        colorButton.setBackground(colors[currentColor]);
    }

    public void incrementColor() {
        if (!isEraser && ++currentColor > num_colors-1) {
            currentColor = 0;
        }
        setColor(currentColor);
    }

    public boolean onTouchEvent(MotionEvent event, MyPoint e) {
        DecimalFormat df = new DecimalFormat("#.###");
        float touchX = (float) e.getX();
        float touchY = (float) e.getY();
        switch (event) {
            case ACTION_DOWN:
                try {
                    jsonObject = new JSONObject();
                    jsonObject.put("user", username);
                    jsonObject.put("type", (isEraser) ? "eraser" : "pen");
                    if (!isEraser) {
                        jsonObject.put("color", currentColor);
                    }
                } catch (JSONException ex) {
                    ex.printStackTrace();
                }
                points.add(new MyPoint(touchX, touchY));
                break;
            case ACTION_MOVE:
                points.add(new MyPoint(touchX, touchY));
                int maxCoordSize = (8000 - username.length()) / 18;
                if (points.size() == maxCoordSize) {

                    try {
                        JSONArray coordinates = new JSONArray();
                        for (MyPoint p : points) {
                            JSONArray ja = new JSONArray();
                            ja.put(df.format(p.x / viewWidth));
                            ja.put(df.format(p.y / viewWidth));
                            coordinates.put(ja);
                        }
                        jsonObject.put("coordinates", coordinates);
                        points.clear();
                        points.add(new MyPoint(touchX, touchY));
                    } catch (JSONException ex) {
                        ex.printStackTrace();
                    }
                    String jsonString = jsonObject.toString();
                    history.add(jsonString);
                    ndnWhiteboard.callback(jsonString);
                }
                break;
            case ACTION_UP:

                try {
                    JSONArray coordinates = new JSONArray();
                    for (MyPoint p : points) {
                        JSONArray ja = new JSONArray();
                        ja.put(df.format(p.x / viewWidth));
                        ja.put(df.format(p.y / viewWidth));
                        coordinates.put(ja);
                    }
                    jsonObject.put("coordinates", coordinates);
                    points.clear();
                } catch (JSONException ex) {
                    ex.printStackTrace();
                }
                String jsonString = jsonObject.toString();
                history.add(jsonString);
                ndnWhiteboard.callback(jsonString);
                break;
            default:
                return false;
        }
        return true;
    }

    public void clear() {
        history.clear();
        drawArea.clear();

        try {
            jsonObject = new JSONObject();
            jsonObject.put("user", username);
            jsonObject.put("type", "clear");
            ndnWhiteboard.callback(jsonObject.toString());
        } catch (JSONException ex) {
            ex.printStackTrace();
        }
    }

    public void undo() {
        if (history.isEmpty()) {
            ndnWhiteboard.setStatus("No more history");
            return;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).contains("\"user\":\"" + username + "\"")) {
                history.remove(i);
                try {
                    jsonObject = new JSONObject();
                    jsonObject.put("user", username);
                    jsonObject.put("type", "undo");
                    ndnWhiteboard.callback(jsonObject.toString());
                } catch (JSONException ex) {
                    ex.printStackTrace();
                }
                break;
            }
        }
        drawArea.clear();
        for (String string : history) {
            parseJSON(string, false);
        }
    }

    public void strokeStart(MyPoint e) {
        onTouchEvent(MotionEvent.ACTION_DOWN, e);
    }

    public void strokeMove (MyPoint e) {
        onTouchEvent(MotionEvent.ACTION_MOVE, e);
    }

    public void strokeEnd(MyPoint e) {
        onTouchEvent(MotionEvent.ACTION_UP, e);
    }
}
