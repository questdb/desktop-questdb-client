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

package io.quest;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;


/**
 * Extends {@link MouseListener} and {@link MouseMotionListener} overriding all
 * the methods with default behaviour to do nothing.
 */
public interface NoopMouseListener extends MouseListener, MouseMotionListener {

    @Override
    default void mouseClicked(MouseEvent e) {
        // nothing
    }

    @Override
    default void mousePressed(MouseEvent e) {
        // nothing
    }

    @Override
    default void mouseReleased(MouseEvent e) {
        // nothing
    }

    @Override
    default void mouseEntered(MouseEvent e) {
        // nothing
    }

    @Override
    default void mouseExited(MouseEvent e) {
        // nothing
    }

    @Override
    default void mouseDragged(MouseEvent e) {
        // nothing
    }

    @Override
    default void mouseMoved(MouseEvent e) {
        // nothing
    }
}
