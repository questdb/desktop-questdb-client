package io.questdb.desktop.ui.editor;

import io.questdb.desktop.GTk;
import io.questdb.desktop.ui.EventConsumer;
import io.questdb.desktop.ui.EventProducer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.regex.Pattern;


public class FindReplace extends JPanel implements EventProducer<FindReplace.EventType> {

    private static final Color FIND_FONT_COLOR = new Color(58, 138, 138);
    private static final Pattern SPECIAL_REGEX_CHARS = Pattern.compile("[{}()\\[\\].+*?^$\\\\|]");
    private final EventConsumer<FindReplace, Object> eventConsumer;
    private final JTextField findText;
    private final JCheckBox findTextIsRegex;
    private final JTextField replaceWithText;
    private final JLabel findMatchesLabel;

    public FindReplace(EventConsumer<FindReplace, Object> eventConsumer) {
        this.eventConsumer = eventConsumer;
        findText = new JTextField(20) {
            @Override
            public String getText() {
                String txt = super.getText();
                if (txt != null && !findTextIsRegex.isSelected()) {
                    txt = SPECIAL_REGEX_CHARS.matcher(txt).replaceAll("\\\\$0");
                }
                return txt;
            }
        };
        setupSearchTextField(findText, this::fireFindEvent);
        findTextIsRegex = new JCheckBox("regex?", false);
        findTextIsRegex.setBackground(GTk.APP_BACKGROUND_COLOR);
        findTextIsRegex.setForeground(Color.WHITE);
        replaceWithText = new JTextField(20);
        setupSearchTextField(replaceWithText, this::fireReplaceEvent);
        findMatchesLabel = GTk.label("  0 matches", FIND_FONT_COLOR);
        setLayout(new FlowLayout(FlowLayout.RIGHT, 5, 4));
        setBackground(GTk.APP_BACKGROUND_COLOR);
        setBorder(BorderFactory.createDashedBorder(Color.LIGHT_GRAY.darker()));
        add(GTk.label("Find", FIND_FONT_COLOR));
        add(findText);
        add(findTextIsRegex);
        add(GTk.label("replace All with", FIND_FONT_COLOR));
        add(replaceWithText);
        add(GTk.gap(4));
        add(findMatchesLabel);
        add(GTk.gap(4));
        add(GTk.button(
                "X",
                "Close find/replace view",
                75,
                22,
                FIND_FONT_COLOR,
                this::onCloseFindReplaceView));
        setVisible(false);
    }

    public void updateMatches(int matches) {
        findMatchesLabel.setText(String.format(
                "%4d %s",
                matches,
                matches == 1 ? "match" : "matches"));
    }

    @Override
    public boolean requestFocusInWindow() {
        return super.requestFocusInWindow() && findText.requestFocusInWindow();
    }

    public String getFind() {
        return findText.getText();
    }

    public String getReplace() {
        return replaceWithText.getText();
    }

    private void fireFindEvent(ActionEvent event) {
        eventConsumer.onSourceEvent(this, EventType.FIND, null);
    }

    private void fireReplaceEvent(ActionEvent event) {
        eventConsumer.onSourceEvent(this, EventType.REPLACE, null);
    }

    private void onCloseFindReplaceView(ActionEvent event) {
        setVisible(false);
    }

    private void setupSearchTextField(JTextField field, ActionListener listener) {
        field.setFont(GTk.TABLE_HEADER_FONT);
        field.setBackground(GTk.APP_BACKGROUND_COLOR);
        field.setForeground(FIND_FONT_COLOR);
        field.setCaretColor(Color.CYAN);
        field.setCaretPosition(0);

        // cmd-a, select the full content
        GTk.Keyboard.addCmdKeyAction(KeyEvent.VK_A, field, e -> field.selectAll());
        // cmd-c, copy to clipboard, selection or current line
        GTk.Keyboard.addCmdKeyAction(KeyEvent.VK_C, field, e -> {
            String selected = field.getSelectedText();
            if (selected == null) {
                selected = field.getText();
            }
            if (!selected.isEmpty()) {
                GTk.setClipboardContent(selected);
            }
        });
        // cmd-v, paste content of clipboard into selection or caret position
        final StringBuilder sb = new StringBuilder();
        GTk.Keyboard.addCmdKeyAction(KeyEvent.VK_V, field, e -> {
            try {
                String data = GTk.getClipboardContent();
                if (data != null && !data.isEmpty()) {
                    int start = field.getSelectionStart();
                    int end = field.getSelectionEnd();
                    String text = field.getText();
                    sb.setLength(0);
                    sb.append(text, 0, start);
                    sb.append(data);
                    sb.append(text, end, text.length());
                    field.setText(sb.toString());
                }
            } catch (Exception fail) {
                // do nothing
            }
        });
        // cmd-left, jump to the beginning of the line
        GTk.Keyboard.addCmdKeyAction(KeyEvent.VK_LEFT, field,
                e -> field.setCaretPosition(0));
        // cmd-right, jump to the end of the line
        GTk.Keyboard.addCmdKeyAction(KeyEvent.VK_RIGHT, field,
                e -> field.setCaretPosition(field.getText().length()));
        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    listener.actionPerformed(null);
                } else if (e.getKeyCode() == KeyEvent.VK_TAB) {
                    replaceWithText.requestFocusInWindow();
                } else {
                    super.keyReleased(e);
                }
            }
        });
    }

    public enum EventType {
        FIND, REPLACE
    }
}
