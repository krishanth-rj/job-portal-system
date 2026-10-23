package edu.college.jobportal.gui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/** Small, dependency-free line icons used by the login actions. */
final class FeatureIcon implements Icon {
    enum Type {
        SIGN_IN,
        USER_PLUS
    }

    private static final int SIZE = 20;
    private final Type type;
    private final Color color;

    FeatureIcon(Type type, Color color) {
        this.type = type;
        this.color = color;
    }

    @Override
    public int getIconWidth() {
        return SIZE;
    }

    @Override
    public int getIconHeight() {
        return SIZE;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.translate(x, y);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (type == Type.SIGN_IN) {
                paintSignIn(g);
            } else {
                paintUserPlus(g);
            }
        } finally {
            g.dispose();
        }
    }

    private void paintSignIn(Graphics2D g) {
        g.draw(new RoundRectangle2D.Float(11.5f, 2.5f, 6f, 15f, 1f, 1f));
        g.drawLine(2, 10, 14, 10);
        g.drawLine(10, 6, 14, 10);
        g.drawLine(10, 14, 14, 10);
    }

    private void paintUserPlus(Graphics2D g) {
        g.draw(new Ellipse2D.Float(4f, 2.1f, 7f, 7f));
        Path2D shoulders = new Path2D.Float();
        shoulders.moveTo(1.8f, 17f);
        shoulders.curveTo(2.2f, 13.5f, 4.1f, 11.7f, 7.5f, 11.7f);
        shoulders.curveTo(9.5f, 11.7f, 11f, 12.4f, 12f, 13.7f);
        g.draw(shoulders);
        g.drawLine(16, 8, 16, 16);
        g.drawLine(12, 12, 20, 12);
    }
}
