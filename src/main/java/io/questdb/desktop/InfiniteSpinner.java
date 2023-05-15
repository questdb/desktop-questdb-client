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

package io.questdb.desktop;

import static java.awt.geom.AffineTransform.getRotateInstance;
import static java.awt.geom.AffineTransform.getTranslateInstance;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.io.Closeable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JPanel;


/**
 * A panel featuring an animated spinning wheel, or spinner, composed of a
 * predefined number of bars. The spinner can be re-started after it has been
 * closed. The panel masks all mouse events.
 */
public class InfiniteSpinner extends JPanel implements NoopMouseListener, Closeable, Runnable {
    private static final int BAR_COUNT = 24;
    private static final int BAR_HEIGHT = 5;
    private static final int BAR_WIDTH = BAR_HEIGHT * 10;
    private static final int BAR_SHIFT = BAR_HEIGHT * 4;
    private static final double ANGLE_STEP = 2.0 * Math.PI / BAR_COUNT;
    private static final Color BACKGROUND_COLOR = GTk.APP_BACKGROUND_COLOR;
    private static final long REFRESH_MILLIS = 80L;

    private final AtomicReference<Thread> animation;
    private final AtomicReference<Spinner> spinner;

    public InfiniteSpinner() {
        spinner = new AtomicReference<>();
        animation = new AtomicReference<>();
        setOpaque(false);
        addMouseListener(this);
    }

    public boolean isRunning() {
        return animation.get() != null;
    }

    public void start() {
        if (animation.compareAndSet(null, new Thread(this))) {
            Thread t = animation.get();
            if (null != t) {
                t.start();
                setVisible(true);
                requestFocusInWindow();
                setFocusTraversalKeysEnabled(false);
            }
        }
    }

    @Override
    public void close() {
        Thread t = animation.getAndSet(null);
        if (null != t) {
            setVisible(false);
            t.interrupt();
        }
    }

    @Override
    public void run() {
        // lazily created once, and only once, across several start/end cycles
        spinner.compareAndExchange(null, new Spinner(getWidth() / 2, getHeight() / 2));
        while (!Thread.currentThread().isInterrupted()) {
            EventQueue.invokeLater(this::repaint);
            spinner.get().rotateByFixedAngle();
            try {
                TimeUnit.MILLISECONDS.sleep(REFRESH_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(BACKGROUND_COLOR);
        g2.fillRect(0, 0, getWidth(), getHeight());
        if (isRunning()) {
            Spinner sp = spinner.get();
            if (sp != null) {
                for (int i = 0; i < BAR_COUNT; i++) {
                    g2.setColor(sp.getColor(i));
                    g2.fill(sp.getBar(i));
                }
            }
        }
    }

    private static class Spinner {
        private static final AffineTransform MOVE_ALONG_SPINNER_RADIUS = getTranslateInstance(BAR_SHIFT, -BAR_HEIGHT / 2.0);

        private final Area[] bars;
        private final Color[] colors;
        private final AtomicInteger colorIdx;

        Spinner(int x, int y) {
            AffineTransform toPanelCenter = getTranslateInstance(x, y);
            bars = new Area[BAR_COUNT];
            colors = new Color[BAR_COUNT];
            for (int i = 0; i < BAR_COUNT; i++) {
                Area bar = new Area();
                bar.add(new Area(new Ellipse2D.Double(0, 0, BAR_HEIGHT, BAR_HEIGHT))); // inner end circle
                bar.add(new Area(new Ellipse2D.Double(BAR_WIDTH, 0, BAR_HEIGHT, BAR_HEIGHT))); // outer end circle
                bar.add(new Area(new Rectangle2D.Double(BAR_HEIGHT / 2.0, 0, BAR_WIDTH, BAR_HEIGHT))); // body, a bar
                bar.transform(toPanelCenter);
                bar.transform(MOVE_ALONG_SPINNER_RADIUS);
                bar.transform(getRotateInstance(ANGLE_STEP * i, x, y));
                bars[i] = bar;
                int g = 0xFF - 0x7F / (i + 1);
                int r = g >>> 2;
                int b = 0xFF;
                colors[i] = new Color(r, g, b); // shades of blue
            }
            colorIdx = new AtomicInteger(BAR_COUNT - 1);
        }

        void rotateByFixedAngle() {
            colorIdx.getAndUpdate(current -> {
                int next = current - 1; // clock-wise
                return next < 0 ? BAR_COUNT - 1 : next; // check underflow
            });
        }

        Area getBar(int i) {
            return bars[i];
        }

        Color getColor(int i) {
            return colors[(i + colorIdx.get()) % colors.length];
        }
    }
}
