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

package io.questdb.desktop.plot;

import io.questdb.desktop.GTk;
import io.questdb.std.Os;

import javax.swing.*;
import java.awt.*;


public class SlidingPlotDemo extends JPanel {

    public static void main(String[] args) {
        PlotCanvas plot = new PlotCanvas();

        int windowSize = 360;
        int refreshRateMillis = 135;
        Column xValues = new SlidingColumn("a", plot, windowSize);
        Column yValues = new SlidingColumn("sin", plot, windowSize);
        plot.setDataSet("Sin(∂) in stepts of π/4", xValues, yValues);
        Thread thread = new Thread(() -> {
            final double step = Math.PI / 90; // degrees to radians
            double angle = Math.PI;
            synchronized (plot) {
                for (int i = 0; i < windowSize; i++) {
                    xValues.append(angle);
                    yValues.append(Math.sin(angle));
                    angle += step;
                }
            }
            long ticks = 0;
            while (!Thread.currentThread().isInterrupted()) {
                synchronized (plot) {
                    xValues.append(angle);
                    yValues.append(Math.sin(angle));
                }
                angle += step;
                if ((ticks + 1) % refreshRateMillis == 0) {
                    GTk.invokeLater(plot::repaint);
                }
                ticks++;
                Os.sleep(1L);
            }
        });
        thread.setDaemon(true);
        thread.setName("Sensor");
        thread.start();

        JFrame frame = GTk.frame("Plot");
        Dimension size = GTk.frameDimension(7.0F, 7.0F);
        frame.add(plot, BorderLayout.CENTER);
        Dimension location = GTk.frameLocation(size);
        frame.setLocation(location.width, location.height);
        frame.setVisible(true);
    }
}
