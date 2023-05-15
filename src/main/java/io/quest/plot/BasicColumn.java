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


import java.awt.*;

public class BasicColumn implements Column {
    private static final int SCALE = 5000;

    private final String name;
    private final Color color;
    private double[] points;
    private int offset;
    private int size;
    private double min, max;

    public BasicColumn(String name) {
        this(name, Color.WHITE);
    }

    public BasicColumn(String name, Color color) {
        this.name = name;
        this.color = color;
        points = new double[SCALE];
        offset = 0;
        size = SCALE;
        min = Double.MAX_VALUE;
        max = Double.MIN_VALUE;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Color color() {
        return color;
    }

    @Override
    public int size() {
        return offset;
    }

    @Override
    public void append(double value) {
        if (offset >= size) {
            double[] tmpPoints = new double[size + SCALE];
            System.arraycopy(points, 0, tmpPoints, 0, size);
            points = tmpPoints;
            size += SCALE;
        }
        points[offset++] = value;
        min = Math.min(min, value);
        max = Math.max(max, value);
    }

    @Override
    public double get(int i) {
        return points[i];
    }

    @Override
    public double min() {
        return min;
    }

    @Override
    public double max() {
        return max;
    }
}
