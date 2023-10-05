package io.questdb.desktop.ui.editor;

import io.questdb.desktop.GTk;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.PatternSyntaxException;
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.undo.UndoManager;

import static io.questdb.desktop.GTk.Keyboard.addCmdKeyAction;
import static io.questdb.desktop.GTk.Keyboard.addCmdShiftKeyAction;
import static io.questdb.desktop.GTk.Keyboard.addAltKeyAction;
import static io.questdb.desktop.GTk.Keyboard.addAltShiftKeyAction;

public class Editor extends JPanel {
    private static final String MARGIN_TOKEN = ":99999:";
    protected final JTextPane textPane;
    private final EditorHighlighter highlighter;
    private final AtomicReference<UndoManager> undoManager;
    private final StringBuilder sb;

    public Editor() {
        this(true, true, EditorHighlighter::of);
    }

    public Editor(boolean isEditable, boolean hasLineNumbers) {
        this(isEditable, hasLineNumbers, EditorHighlighter::of);
    }

    public Editor(boolean isEditable, boolean hasLineNumbers, Function<JTextPane, EditorHighlighter> highlighterFactory) {
        sb = new StringBuilder();
        undoManager = new AtomicReference<>();
        textPane = new JTextPane() {
            public boolean getScrollableTracksViewportWidth() {
                return getUI().getPreferredSize(this).width <= getParent().getSize().width;
            }
        };
        textPane.setFont(GTk.Editor.DEFAULT_FONT);
        textPane.setMargin(createInsets(GTk.Editor.DEFAULT_FONT));
        textPane.setBackground(GTk.APP_BACKGROUND_COLOR);
        textPane.setCaretColor(GTk.Editor.MATCH_FOREGROUND_COLOR);
        textPane.setCaretPosition(0);
        if (hasLineNumbers) {
            textPane.setEditorKit(new StyledEditorKit() {
                @Override
                public ViewFactory getViewFactory() {
                    final ViewFactory defaultViewFactory = super.getViewFactory();
                    return elem -> {
                        if (elem.getName().equals(AbstractDocument.ParagraphElementName)) {
                            return new ParagraphView(elem) {
                                @Override
                                public void paintChild(Graphics g, Rectangle alloc, int index) {
                                    super.paintChild(g, alloc, index);
                                    Font font = textPane.getFont();
                                    g.setFont(font);
                                    g.setColor(GTk.Editor.LINENO_COLOR);
                                    int n = getLineNumber(getDocument().getDefaultRootElement(), getElement());
                                    String lineNo = String.valueOf(n);
                                    FontMetrics fm = g.getFontMetrics(font);
                                    int x = fm.stringWidth(MARGIN_TOKEN) - fm.stringWidth(lineNo) - 10;
                                    int y = fm.getHeight() / 3 + n * fm.getHeight();
                                    g.drawString(lineNo, x, y);
                                }
                            };
                        }
                        return defaultViewFactory.create(elem);
                    };
                }
            });
        }
        textPane.setEditable(isEditable);
        Keyboard keyboard = new Keyboard();
        keyboard.setupKeyboardActions(isEditable);
        highlighter = highlighterFactory.apply(textPane); // produces "style change" events
        JScrollPane scrollPane = new JScrollPane(textPane);
        scrollPane.getViewport().setBackground(GTk.APP_BACKGROUND_COLOR);
        scrollPane.getVerticalScrollBar().setUnitIncrement(5);
        scrollPane.getVerticalScrollBar().setBlockIncrement(15);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(5);
        scrollPane.getHorizontalScrollBar().setBlockIncrement(15);
        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);
    }

    private static int getLineNumber(Element root, Element target) {
        for (int i = 0, n = root.getElementCount(); i < n; i++) {
            if (root.getElement(i) == target) {
                return i + 1;
            }
        }
        return 1;
    }

    public void setFontSize(int size) {
        Font font = GTk.Editor.newFont(size);
        textPane.setFont(font);
        textPane.setMargin(createInsets(font));
    }

    public void displayMessage(String message) {
        textPane.setText(message);
        repaint();
    }

    public void displayError(String error) {
        textPane.setText(highlighter.highlightError(error));
        repaint();
    }

    public void displayError(Throwable error) {
        textPane.setText(highlighter.highlightError(error));
        repaint();
    }

    protected String getCurrentLine() {
        try {
            int caretPos = textPane.getCaretPosition();
            int start = Utilities.getRowStart(textPane, caretPos);
            int end = Utilities.getRowEnd(textPane, caretPos);
            return getText(start, end - start);
        } catch (BadLocationException ignore) {
            // do nothing
        }
        return "";
    }

    protected String getText() {
        return getText(0, -1);
    }

    protected String getText(int start, int len) {
        Document doc = textPane.getDocument();
        int length = len < 0 ? doc.getLength() - start : len;
        if (length > 0) {
            try {
                String txt = doc.getText(start, length);
                if (txt != null) {
                    return txt;
                }
            } catch (BadLocationException ignore) {
                // do nothing
            }
        }
        return "";
    }

    protected int highlightContent(String findRegex) {
        return findRegex != null ? highlighter.handleTextChanged(findRegex, null) : 0; // number of matches
    }

    protected int replaceContent(String findRegex, String replaceWith) {
        if (findRegex != null) {
            try {
                textPane.setText(getText().replaceAll(findRegex, replaceWith));
                return highlighter.handleTextChanged();
            } catch (PatternSyntaxException err) {
                JOptionPane.showMessageDialog(
                        this,
                        String.format("Not a valid filter: %s", err.getMessage())
                );
            }
        }
        return 0;
    }

    protected void setUndoManager(UndoManager newUndoManager) {
        AbstractDocument doc = (AbstractDocument) textPane.getDocument();
        UndoManager current = undoManager.get();
        if (current != null) {
            doc.removeUndoableEditListener(current);
        }
        newUndoManager.discardAllEdits();
        undoManager.set(newUndoManager);
        doc.addUndoableEditListener(newUndoManager);
    }

    private Insets createInsets(Font font) {
        FontMetrics fm = textPane.getFontMetrics(font);
        int h = fm.getHeight() / 2;
        return new Insets(h, fm.stringWidth(MARGIN_TOKEN), h, fm.stringWidth(":"));
    }

    private class Keyboard {
        private void setupKeyboardActions(boolean isEditable) {
            // move cursor all the way to the
            addCmdKeyAction(KeyEvent.VK_UP, textPane, this::cmdUp); // top
            addCmdKeyAction(KeyEvent.VK_DOWN, textPane, this::cmdDown); // bottom
            addCmdKeyAction(KeyEvent.VK_LEFT, textPane, this::cmdLeft); // left
            addCmdKeyAction(KeyEvent.VK_RIGHT, textPane, this::cmdRight); // right

            // same as above, selecting text
            addCmdShiftKeyAction(KeyEvent.VK_UP, textPane, this::cmdShiftUp);
            addCmdShiftKeyAction(KeyEvent.VK_DOWN, textPane, this::cmdShiftDown);
            addCmdShiftKeyAction(KeyEvent.VK_LEFT, textPane, this::cmdShiftLeft);
            addCmdShiftKeyAction(KeyEvent.VK_RIGHT, textPane, this::cmdShiftRight);

            // moves to the nearest boundary of the word to the
            addAltKeyAction(KeyEvent.VK_LEFT, textPane, this::altLeft); // left
            addAltKeyAction(KeyEvent.VK_RIGHT, textPane, this::altRight); // right

            // same as above, selecting text
            addAltShiftKeyAction(KeyEvent.VK_LEFT, textPane, this::altShiftLeft);
            addAltShiftKeyAction(KeyEvent.VK_RIGHT, textPane, this::altShiftRight);

            // selects current word under the cursor
            addAltKeyAction(KeyEvent.VK_UP, textPane, this::altUp);

            // scales up/down (zooms in/out) the editor's font size
            addCmdKeyAction(KeyEvent.VK_S, textPane, this::cmdSFontUp);
            addCmdShiftKeyAction(KeyEvent.VK_S, textPane, this::cmdShiftSFontDown);

            addCmdKeyAction(KeyEvent.VK_A, textPane, this::cmdASelectAll);
            addCmdKeyAction(KeyEvent.VK_C, textPane, this::cmdCCopyToClipboard);

            if (isEditable) {
                addCmdKeyAction(KeyEvent.VK_X, textPane, this::cmdXCutToClipboard);
                addCmdKeyAction(KeyEvent.VK_V, textPane, this::cmdVPasteFromClipboard);
                addCmdKeyAction(KeyEvent.VK_D, textPane, this::cmdDDupLine);
                addCmdKeyAction(KeyEvent.VK_SLASH, textPane, this::cmdForwardSlashToggleComment);
                addCmdKeyAction(KeyEvent.VK_QUOTE, textPane, this::cmdQuoteToggleQuote); // requires word to be selected
                addCmdKeyAction(KeyEvent.VK_Z, textPane, this::cmdZUndo);
                addCmdKeyAction(KeyEvent.VK_Y, textPane, this::cmdYRedo);
            }
        }

        private void cmdZUndo(ActionEvent event) {
            // cmd-z, undo edit
            UndoManager um = undoManager.get();
            if (um != null && um.canUndo()) {
                try {
                    um.undo();
                    highlighter.handleTextChanged();
                } catch (Throwable ignore) {
                    // do nothing
                }
            }
        }

        private void cmdYRedo(ActionEvent event) {
            // cmd-y, redo last undo edit
            UndoManager um = undoManager.get();
            if (um != null && um.canRedo()) {
                try {
                    um.redo();
                    highlighter.handleTextChanged();
                } catch (Throwable ignore) {
                    // do nothing
                }
            }
        }

        private void cmdDDupLine(ActionEvent event) {
            // cmd-d, duplicate line under caret, and append it under
            try {
                int caretPos = textPane.getCaretPosition();
                int start = Utilities.getRowStart(textPane, caretPos);
                int end = Utilities.getRowEnd(textPane, caretPos);
                Document doc = textPane.getDocument();
                String line = doc.getText(start, end - start);
                String insert = line.isEmpty() ? "\n" : "\n" + line;
                doc.insertString(end, insert, null);
                textPane.setCaretPosition(caretPos + insert.length());
                highlighter.handleTextChanged();
            } catch (BadLocationException ignore) {
                // do nothing
            }
        }

        private void cmdVPasteFromClipboard(ActionEvent event) {
            // cmd-v, paste content of clipboard into selection or caret position
            try {
                String text = GTk.getClipboardContent();
                if (text != null) {
                    int start = textPane.getSelectionStart();
                    int end = textPane.getSelectionEnd();
                    Document doc = textPane.getDocument();
                    if (end > start) {
                        doc.remove(start, end - start);
                    }
                    doc.insertString(textPane.getCaretPosition(), text, null);
                    highlighter.handleTextChanged();
                }
            } catch (Exception fail) {
                // do nothing
            }
        }

        private void cmdASelectAll(ActionEvent event) {
            textPane.selectAll();
        }

        private void cmdCCopyToClipboard(ActionEvent event) {
            // cmd-c, copy selection or whole line under caret to clipboard
            try {
                Document doc = textPane.getDocument();
                int start = textPane.getSelectionStart();
                int len = textPane.getSelectionEnd() - start;
                if (len > 0) {
                    // there is selection
                    GTk.setClipboardContent(doc.getText(start, len));
                    return;
                }

                // no selection
                int caretPos = textPane.getCaretPosition();
                start = Utilities.getRowStart(textPane, caretPos);
                len = Utilities.getRowEnd(textPane, caretPos) - start;
                GTk.setClipboardContent(doc.getText(start, len));
            } catch (Exception fail) {
                // do nothing
            }
        }

        private void cmdXCutToClipboard(ActionEvent event) {
            // cmd-x, remove selection or whole line under caret and copy to clipboard
            try {
                Document doc = textPane.getDocument();
                int start = textPane.getSelectionStart();
                int len = textPane.getSelectionEnd() - start;
                if (len > 0) {
                    // there is selection
                    GTk.setClipboardContent(doc.getText(start, len));
                    doc.remove(start, len);
                    return;
                }

                // no selection
                int caretPos = textPane.getCaretPosition();
                start = Utilities.getRowStart(textPane, caretPos);
                len = Utilities.getRowEnd(textPane, caretPos) - start;
                GTk.setClipboardContent(doc.getText(start, len));
                doc.remove(start - 1, len + 1);
            } catch (Exception fail) {
                // do nothing
            }
        }

        private void cmdUp(ActionEvent event) {
            // cmd-up, jump to the beginning of the document
            textPane.setCaretPosition(0);
        }

        private void cmdSFontUp(ActionEvent event) {
            // cmd-s, font size up
            int newFontSize = textPane.getFont().getSize() + 1;
            if (newFontSize <= GTk.Editor.MAX_FONT_SIZE) {
                setFontSize(newFontSize);
            }
        }

        private void cmdShiftSFontDown(ActionEvent event) {
            // cmd-shift-F, jump to the beginning of the document
            int newFontSize = textPane.getFont().getSize() - 1;
            if (newFontSize >= GTk.Editor.MIN_FONT_SIZE) {
                setFontSize(newFontSize);
            }
        }

        private void cmdDown(ActionEvent event) {
            // cmd-down, jump to the end of the document
            textPane.setCaretPosition(textPane.getDocument().getLength());
        }

        private void cmdLeft(ActionEvent event) {
            // cmd-left, jump to the beginning of the line
            try {
                int caretPos = textPane.getCaretPosition();
                textPane.setCaretPosition(Utilities.getRowStart(textPane, caretPos));
            } catch (BadLocationException ignore) {
                // do nothing
            }
        }

        private void cmdRight(ActionEvent event) {
            // cmd-right, jump to the end of the line
            try {
                int caretPos = textPane.getCaretPosition();
                textPane.setCaretPosition(Utilities.getRowEnd(textPane, caretPos));
            } catch (BadLocationException ignore) {
                // do nothing
            }
        }

        private void cmdShiftUp(ActionEvent event) {
            // cmd-shift-up, jump to the beginning of the page selecting
            int end = textPane.getSelectionEnd();
            textPane.setCaretPosition(0);
            if (textPane.getSelectionStart() != end) {
                textPane.select(0, end);
            }
        }

        private void cmdShiftDown(ActionEvent event) {
            // cmd-shift-down, jump to the end of the page selecting
            int start = textPane.getSelectionStart();
            int end = textPane.getDocument().getLength();
            textPane.setCaretPosition(end);
            if (start != textPane.getSelectionEnd()) {
                textPane.select(start, end);
            }
        }

        private void cmdShiftLeft(ActionEvent event) {
            try {
                int caretPos = textPane.getCaretPosition();
                int start = Utilities.getRowStart(textPane, caretPos);
                int end = textPane.getSelectionEnd();
                textPane.setCaretPosition(start);
                if (textPane.getSelectionStart() != end) {
                    textPane.select(start, end);
                }
            } catch (BadLocationException ignore) {
                // do nothing
            }
        }

        private void cmdShiftRight(ActionEvent event) {
            // cmd-shift-right, jump to the end of the line
            try {
                int caretPos = textPane.getCaretPosition();
                int start = textPane.getSelectionStart();
                int end = Utilities.getRowEnd(textPane, caretPos);
                textPane.setCaretPosition(end);
                if (start != textPane.getSelectionEnd()) {
                    textPane.select(start, end);
                }
            } catch (BadLocationException ignore) {
                // do nothing
            }
        }

        private void altUp(ActionEvent event) {
            // alt-up, select current word under caret
            try {
                int caretPos = textPane.getCaretPosition();
                int wordStart = Utilities.getWordStart(textPane, caretPos);
                int wordEnd = Utilities.getWordEnd(textPane, caretPos);
                textPane.select(wordStart, wordEnd);
            } catch (BadLocationException ignore) {
                // do nothing
            }
        }

        private void altLeft(ActionEvent event) {
            // alt-left, jump to the beginning of the word
            try {
                int caretPos = textPane.getCaretPosition();
                int wordStart = Utilities.getWordStart(textPane, caretPos);
                if (caretPos == wordStart && caretPos > 0) {
                    wordStart = Utilities.getWordStart(textPane, caretPos - 1);
                }
                textPane.setCaretPosition(wordStart);
            } catch (BadLocationException ignore) {
                // do nothing
            }
        }

        private void altRight(ActionEvent event) {
            // alt-right, jump to the end of the word
            try {
                int caretPos = textPane.getCaretPosition();
                int wordEnd = Utilities.getWordEnd(textPane, caretPos);
                if (caretPos == wordEnd && caretPos < textPane.getDocument().getLength()) {
                    wordEnd = Utilities.getWordEnd(textPane, caretPos + 1);
                }
                textPane.setCaretPosition(wordEnd);
            } catch (BadLocationException ignore) {
                // do nothing
            }
        }

        private void altShiftLeft(ActionEvent event) {
            // alt-shift-left, select from previous word start to current selection end
            try {
                int caretPos = textPane.getCaretPosition();
                if (caretPos > 0) {
                    int start = Utilities.getWordStart(textPane, caretPos - 1);
                    int end = textPane.getSelectionEnd();
                    textPane.setCaretPosition(end);
                    textPane.moveCaretPosition(start);
                }
            } catch (BadLocationException ignore) {
                // do nothing
            }
        }

        private void altShiftRight(ActionEvent event) {
            // alt-shift-right, select from current selection start to next word end
            try {
                int caretPos = textPane.getCaretPosition();
                if (caretPos < textPane.getDocument().getLength()) {
                    int start = textPane.getSelectionStart();
                    int end = Utilities.getWordEnd(textPane, caretPos + 1);
                    textPane.setCaretPosition(start);
                    textPane.moveCaretPosition(end);
                }
            } catch (BadLocationException ignore) {
                // do nothing
            }
        }

        private void cmdForwardSlashToggleComment(ActionEvent event) {
            try {
                int start = Utilities.getRowStart(textPane, textPane.getSelectionStart());
                int end = Utilities.getRowEnd(textPane, textPane.getSelectionEnd());
                Document doc = textPane.getDocument();
                int len = end - start;
                String lines = doc.getText(start, len);
                int linesLen = lines.length();
                sb.setLength(0);
                int lineStart = 0;
                for (int i = 0; i < linesLen; i++) {
                    char c = lines.charAt(i);
                    if (c == '\n') {
                        if (i - lineStart >= 2 && lines.charAt(lineStart) == '-' && lines.charAt(lineStart + 1) == '-') {
                            sb.append(lines, lineStart + 2, i + 1);
                        } else {
                            sb.append("--").append(lines, lineStart, i + 1);
                        }
                        lineStart = i + 1;
                    }
                }
                if (linesLen - lineStart >= 2 && lines.charAt(lineStart) == '-' && lines.charAt(lineStart + 1) == '-') {
                    sb.append(lines, lineStart + 2, linesLen);
                } else {
                    sb.append("--").append(lines, lineStart, linesLen);
                }
                doc.remove(start, len);
                doc.insertString(start, sb.toString(), null);
                highlighter.handleTextChanged();
            } catch (Exception fail) {
                // do nothing
            }
        }

        private void cmdQuoteToggleQuote(ActionEvent event) {
            try {
                Document doc = textPane.getDocument();
                int docLen = doc.getLength();
                int start = textPane.getSelectionStart();
                int end = textPane.getSelectionEnd();
                int len = end - start;
                if (len != 0 && start > 0 && len <= docLen) {
                    String targetText = doc.getText(start, len);
                    String window = doc.getText(start - 1, len + 2);
                    char first = window.charAt(0);
                    char last = window.charAt(len + 1);
                    String finalText;
                    if ((first == '\'' || first == '"') && first == last) {
                        finalText = targetText;
                        start--;
                        len += 2;
                    } else {
                        finalText = "'" + targetText + "'";
                    }
                    doc.remove(start, len);
                    doc.insertString(textPane.getCaretPosition(), finalText, null);
                    highlighter.handleTextChanged();
                }
            } catch (Exception fail) {
                // do nothing
            }
        }
    }
}
