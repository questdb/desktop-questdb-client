/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2023 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.quest.plot;

import io.quest.GTk;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;


public class PlotCanvas extends JPanel {
    private static final float[] DASHED_LINE = new float[]{1, 8};
    private static final int INSET_TOP = 20;
    private static final int INSET_BOTTOM = 80;
    private static final int INSET_LEFT = 80;
    private static final int INSET_RIGHT = 20;
    private static final Insets PLOT_INSETS = new Insets(INSET_TOP, INSET_LEFT, INSET_BOTTOM, INSET_RIGHT);
    public Column[] columns;
    private BasicStroke dashedStroke;
    private String title;

    public PlotCanvas() {
        setOpaque(true);
    }

    public synchronized void setDataSet(String title, Column... columns) {
        if (columns == null || columns.length != 2) {
            throw new IllegalArgumentException("two columns are required");
        }
        for (int i = 1; i < columns.length; i++) {
            if (columns[0].size() != columns[i].size()) {
                throw new IllegalArgumentException("sizes must match and be greater than zero, at index " + i);
            }
        }
        this.title = title;
        this.columns = columns;
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        super.paintComponent(g2);

        int height = getHeight();
        int width = getWidth();
        int plotHeight = height - (PLOT_INSETS.top + PLOT_INSETS.bottom);
        int plotWidth = width - (PLOT_INSETS.left + PLOT_INSETS.right);

        // Fill background and draw border around plot area.
        g2.setColor(GTk.QUEST_APP_BACKGROUND_COLOR);
        g2.fillRect(0, 0, width, height);
        g2.setColor(GTk.EDITOR_PLOT_BORDER_COLOR);
        g2.drawRect(PLOT_INSETS.left, PLOT_INSETS.top, plotWidth, plotHeight);

        // draw curve
        if (null != columns) {
            // Shift coordinate centre to bottom-left corner of the internal rectangle.
            g2.translate(PLOT_INSETS.left, height - PLOT_INSETS.bottom);

            double minX;
            double maxX;
            double minY;
            double maxY;
            synchronized (this) {
                minX = columns[0].min();
                maxX = columns[0].max();
                minY = columns[1].min();
                maxY = columns[1].max();
            }
            double deltaX = Math.abs(maxX - minX) * 0.005F;
            double deltaY = Math.abs(maxY - minY) * 0.07F;
            minX -= deltaX;
            maxX += deltaX;
            minY -= deltaY;
            maxY += deltaY;
            double rangeX = maxX - minX;
            double rangeY = maxY - minY;
            double scaleX = plotWidth / rangeX;
            double scaleY = plotHeight / rangeY;
            double pointSizeFactor = 1.2;
            double xTick = pointSizeFactor / scaleX;
            double yTick = pointSizeFactor / scaleY;
            double xPointWidth = xTick * 2.0F;
            double yPointWidth = yTick * 2.0F;
            Axis x = Axis.forX(g2, minX, rangeX, scaleX);
            Axis y = Axis.forY(g2, minY, rangeY, scaleY);
            if (x == null || y == null) {
                return;
            }

            // Draw Zero line
            int yPositionOfZero = y.getYPositionOfZeroLabel();
            g2.drawLine(0, yPositionOfZero, plotWidth, yPositionOfZero);

            // Draw ticks and their labels
            int verticalPos = Axis.TICK_LENGTH + x.getHeight(0);
            BasicStroke stroke = (BasicStroke) g2.getStroke();
            if (dashedStroke == null) {
                dashedStroke = new BasicStroke(stroke.getLineWidth(), stroke.getEndCap(), stroke.getLineJoin(), stroke.getMiterLimit(), DASHED_LINE, 0);
            }
            for (int i = 0, n = x.size(); i < n; i++) {
                int pos = x.position(i);
                g2.setColor(GTk.EDITOR_PLOT_BORDER_COLOR);
                g2.drawLine(pos, 0, pos, Axis.TICK_LENGTH);
                g2.drawString(x.label(i), pos - x.width(i) / 2, verticalPos);
                g2.setColor(GTk.EDITOR_LINENO_COLOR);
                g2.setStroke(dashedStroke);
                g2.drawLine(pos, 0, pos, -plotHeight);
                g2.setStroke(stroke);
            }
            for (int i = 0, n = y.size(); i < n; i++) {
                int pos = y.position(i);
                g2.setColor(GTk.EDITOR_PLOT_BORDER_COLOR);
                g2.drawLine(0, pos, -Axis.TICK_LENGTH, pos);
                g2.drawString(y.label(i), -(y.width(i) + Axis.TICK_LENGTH + 2), pos + y.getHeight(i) / 2 - 2);
                if (i == 0 || i == n - 1 || y.isZero(i)) {
                    continue;
                }
                g2.setColor(GTk.EDITOR_LINENO_COLOR);
                g2.setStroke(dashedStroke);
                g2.drawLine(0, pos, plotWidth, pos);
                g2.setStroke(stroke);
            }

            // Draw title and ranges
            g2.setColor(GTk.EDITOR_MENU_FOREGROUND_COLOR);
            g2.drawString(String.format("%s x:[%s, %s], y:[%s, %s]", title != null ? title : "", Axis.fmtX(minX), Axis.fmtX(maxX), Axis.fmtY(minY), Axis.fmtY(maxY)), 0, Math.round(INSET_BOTTOM * 3 / 4.0F));

            // Scale the coordinate system to match plot coordinates
            g2.scale(scaleX, -scaleY);
            g2.translate(-1.0F * minX, -1.0F * minY);

            // Draw only within plotting area
            g2.setClip(new Rectangle2D.Double(minX, minY, rangeX, rangeY));

            // Set stroke for curve
            g2.setStroke(new BasicStroke((float) Math.abs(1.0F / (100.0F * Math.max(scaleX, scaleY)))));
            g2.setColor(columns[1].color());

            GeneralPath path = null;
            synchronized (this) {
                int n = columns[1].size();
                if (n > 0) {
                    path = new GeneralPath(GeneralPath.WIND_NON_ZERO, n);
                    double px = columns[0].get(0);
                    double py = columns[1].get(0);
                    path.moveTo(px, py);
                    g2.fill(new Ellipse2D.Double(px - xTick, py - yTick, xPointWidth, yPointWidth));
                    for (int i = 1; i < n; i++) {
                        px = columns[0].get(i);
                        py = columns[1].get(i);
                        path.lineTo(px, py);
                        g2.fill(new Ellipse2D.Double(px - xTick, py - yTick, xPointWidth, yPointWidth));
                    }
                }
            }
            if (path != null) {
                g2.draw(path);
            }
        }
    }
}
