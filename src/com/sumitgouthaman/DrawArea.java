package com.sumitgouthaman;

import javafx.scene.shape.StrokeType;
import org.json.JSONObject;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

import javax.swing.JComponent;

/**
 * Component for drawing !
 *
 * @author sylsau
 */
public class DrawArea extends JComponent {

    // Image in which we're going to draw
    private Image image;
    // Graphics2D object ==> used to draw on
    private Graphics2D g2;
    // Mouse coordinates
    private int currentX, currentY, oldX, oldY;

    public void pressed(MyPoint e) {
        // save coord x,y when mouse is pressed
        oldX = (int) e.getX();
        oldY = (int) e.getY();
    }

    Whiteboard whiteboard;

    public void moved(MyPoint e) {
        // coord x,y when drag mouse
        currentX = (int) e.getX();
        currentY = (int) e.getY();

        if (g2 != null) {
            // draw line if g2 context not null
            g2.drawLine(oldX, oldY, currentX, currentY);
            // refresh draw area to repaint
            repaint();
            // store current coords x,y as olds x,y
            oldX = currentX;
            oldY = currentY;
        }
    }

    public DrawArea(Whiteboard w) {
        whiteboard = w;
        setDoubleBuffered(false);
        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                // save coord x,y when mouse is pressed
                whiteboard.strokeStart(new MyPoint(e.getX(), e.getY()));
                oldX = e.getX();
                oldY = e.getY();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                whiteboard.strokeEnd(new MyPoint(e.getX(), e.getY()));

            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                // coord x,y when drag mouse
                currentX = e.getX();
                currentY = e.getY();

                if (g2 != null) {
                    // draw line if g2 context not null
                    g2.drawLine(oldX, oldY, currentX, currentY);
                    // refresh draw area to repaint
                    repaint();
                    // store current coords x,y as olds x,y
                    oldX = currentX;
                    oldY = currentY;
                }
                whiteboard.strokeMove(new MyPoint(e.getX(), e.getY()));
            }
        });
    }

    protected void paintComponent(Graphics g) {
        if (image == null) {
            // image to draw null ==> we create
            image = createImage(getSize().width, getSize().height);
            g2 = (Graphics2D) image.getGraphics();
            setStrokeWidth(4);
            // enable antialiasing
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // clear draw area
            clear();
        }

        g.drawImage(image, 0, 0, null);
    }

    // now we create exposed methods
    public void clear() {
        Color prevC = g2.getColor();
        g2.setPaint(Color.white);
        // draw white on entire draw area to clear
        g2.fillRect(0, 0, getSize().width, getSize().height);
        g2.setPaint(prevC);
        repaint();
    }

    public void setStrokeWidth(int thickness) {
        g2.setStroke(new BasicStroke(thickness));
    }

    public void setColor(Color c) {
        g2.setColor(c);
    }

}