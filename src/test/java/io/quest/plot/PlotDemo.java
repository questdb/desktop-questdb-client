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


public class PlotDemo extends JPanel {

    public static void main(String[] args) {
        PlotCanvas plot = new PlotCanvas();

        Column xValues = new BasicColumn("x");
        Column yValues = new BasicColumn("y", Color.YELLOW);
        double angle = 0.0;
        double step = Math.PI / 180.0;
        for (int i = 0; i <= 360; i++) {
            xValues.append(Math.cos(angle));
            yValues.append(Math.sin(angle));
            angle += step;
        }
        plot.setDataSet("Circle in steps of π/4", xValues, yValues);

        JFrame frame = GTk.frame("Plot");
        Dimension size = GTk.frameDimension(7.0F, 7.0F);
        frame.add(plot, BorderLayout.CENTER);
        Dimension location = GTk.frameLocation(size);
        frame.setLocation(location.width, location.height);
        frame.setVisible(true);
    }
}
