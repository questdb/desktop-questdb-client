package io.questdb.desktop.ui;

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
