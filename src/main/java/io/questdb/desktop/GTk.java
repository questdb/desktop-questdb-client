package io.questdb.desktop;

import io.questdb.desktop.model.Table;
import io.questdb.desktop.ui.NoopMouseListener;
import io.questdb.log.Log;
import io.questdb.log.LogFactory;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.*;
import java.awt.font.TextAttribute;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.Border;

public final class GTk {

    // https://patorjk.com/software/taag/#p=display&h=0&f=Ivrit&t=questdb
    public static final String BANNER = """ 
                                          _         _   _
              __ _   _   _    ___   ___  | |_    __| | | |__
             / _` | | | | |  / _ \\ / __| | __|  / _` | | '_
            | (_| | | |_| | |  __/ \\__ \\ | |_  | (_| | | |_) |
             \\__, |  \\__,_|  \\___| |___/  \\__|  \\__,_| |_.__/
                |_|
             Copyright (c) 2019 -\s""" + Calendar.getInstance().get(Calendar.YEAR);

    public static final String KEYBOARD_SHORTCUTS = """
                        
            (note for Mac keyboard: ctrl, alt -> command, option)
             
            ctrl^.            run QuestDB in the background
            ctrl^m            open metadata files explorer
            ctrl^j            open plot on results, if two numeric columns
            ctrl^t            open connection assigner/editor
            ctrl^o            open assigned connection
            ctrl^h            open documentation in a browser tab

            ctrl^d            copy line under caret & paste it under current line
            ctrl^x            remove line under caret (upward direction)
            ctrl^c            copy selection to clipboard
            ctrl^v            paste the content of the clipboard
            ctrl^z            undo last edit
            ctrl^y            redo last undo
            ctrl^1            select all
            ctrl^f            find text or regular expression
            ctrl^r            replace text or regular expression
            ctrl^/            toggle line comment
            ctrl^'            wrap selection in 'selection'

            ctrl^l            execute line under caret
            ctrl^enter        execute selection, or full content of editor
            ctrl^w            abort current execution
            ctrl^p            prev page in results table
            ctrl^n            next page in results table

            ctrl^up           go to top
            ctrl^down         go to bottom
            ctrl^left         go to beginning of line
            ctrl^right        go to end of line

            ctrl+shift^up     select from current caret position to top
            ctrl+shift^down   select from current caret position to bottom
            ctrl+shift^left   select from current caret to beginning of line
            ctrl+shift^right  select from current caret to end of line

            alt^up            select current word
            alt^left          go to beginning of line
            alt^down          go to end of line
            alt+shift^left    select from current caret position go to beginning of word
            alt+shift^right   select from current caret position go to end of word

            ctrl+^s          increase font size
            ctrl+shift^s     decrease font size
            """;
    public static final String APP_NAME = "Q.U.E.S.T.D.B";
    public static final String MAIN_FONT_NAME = "Monospaced";
    public static final Color APP_BACKGROUND_COLOR = new Color(0, 0, 0);
    public static final int METADATA_EXPLORER_FONT_SIZE = 14;
    public static final Font MENU_FONT = new Font(MAIN_FONT_NAME, Font.BOLD, 14);
    public static final Font TABLE_HEADER_FONT = new Font(MAIN_FONT_NAME, Font.BOLD, Editor.DEFAULT_FONT_SIZE);
    private static final Font TABLE_HEADER_UNDERLINE_FONT = TABLE_HEADER_FONT.deriveFont(Map.of(
            TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON
    ));
    public static final Font TABLE_CELL_FONT = new Font(MAIN_FONT_NAME, Font.PLAIN, Editor.DEFAULT_FONT_SIZE);
    private static final String DOCUMENTATION_URL = "https://questdb.io/docs/introduction/";
    private static final Log LOG = LogFactory.getLog(GTk.class);
    private static final Toolkit TK = Toolkit.getDefaultToolkit();
    private static final DataFlavor[] SUPPORTED_COPY_PASTE_FLAVOR = {DataFlavor.stringFlavor};

    static {
        // anti-aliased fonts
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        String lookAndFeel = UIManager.getCrossPlatformLookAndFeelClassName();
        try {
            UIManager.setLookAndFeel(lookAndFeel);
        } catch (Exception e) {
            LOG.infoW().$("CrossPlatformLookAndFeel unavailable [name=").$(lookAndFeel).I$();
        }
    }

    private GTk() {
        throw new IllegalStateException("not meant to be instantiated");
    }

    public static void shutdownExecutor(ExecutorService executor) {
        executor.shutdown();
        try {
            boolean completed;
            int attempts = 2;
            do {
                completed = executor.awaitTermination(200L, TimeUnit.MILLISECONDS);
            } while (!completed && attempts-- > 0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void showErrorDialog(Component owner, String message) {
        JOptionPane.showMessageDialog(owner, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void invokeLater(Runnable... tasks) {
        if (EventQueue.isDispatchThread()) {
            for (Runnable r : tasks) {
                if (r != null) {
                    r.run();
                }
            }
        } else {
            try {
                EventQueue.invokeLater(() -> {
                    for (Runnable r : tasks) {
                        if (r != null) {
                            r.run();
                        }
                    }
                });
            } catch (Throwable fail) {
                throw new RuntimeException(fail);
            }
        }
    }

    public static String getClipboardContent() {
        try {
            return (String) TK.getSystemClipboard().getData(DataFlavor.stringFlavor);
        } catch (IOException | UnsupportedFlavorException err) {
            return "";
        }
    }

    public static void setClipboardContent(final String str) {
        TK.getSystemClipboard().setContents(new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return SUPPORTED_COPY_PASTE_FLAVOR;
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return DataFlavor.stringFlavor.equals(flavor);
            }

            @Override
            @NotNull
            public Object getTransferData(DataFlavor flavor) {
                return isDataFlavorSupported(flavor) ? str : "";
            }
        }, null);
    }

    public static Dimension frameDimension() {
        return frameDimension(0.9F, 0.9F);
    }

    public static Dimension frameDimension(float xScale, float yScale) {
        assert xScale > 0.5 && xScale < 1.0; // 50..99% percent of screen
        assert yScale > 0.5 && yScale < 1.0;
        Dimension screenSize = TK.getScreenSize();
        int width = (int) (screenSize.getWidth() * xScale);
        int height = (int) (screenSize.getHeight() * yScale);
        return new Dimension(width, height);
    }

    public static Dimension frameLocation(Dimension frameDimension) {
        Dimension screenSize = TK.getScreenSize();
        int x = (int) (screenSize.getWidth() - frameDimension.getWidth()) / 2;
        int y = (int) (screenSize.getHeight() - frameDimension.getHeight()) / 2;
        return new Dimension(x, y);
    }

    public static JFrame frame(String title) {
        JFrame frame = new JFrame() {
            @Override
            public void dispose() {
                super.dispose();
                System.exit(0);
            }
        };
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setType(Window.Type.NORMAL);
        if (title != null && !title.isEmpty()) {
            frame.setTitle(title);
        }
        Dimension dimension = frameDimension();
        Dimension location = frameLocation(dimension);
        frame.setSize(dimension.width, dimension.height);
        frame.setLocation(location.width, location.height);
        frame.setLayout(new BorderLayout());
        return frame;
    }

    public static JButton button(Icon icon, String tooltip, ActionListener listener) {
        return button(icon.getText(), icon, tooltip, listener);
    }

    public static JButton button(String text, Runnable runnable) {
        return button(text, Icon.NO_ICON, null, e -> runnable.run());
    }

    public static JButton button(String text, String tooltip, int width, int height, Color foregroundColor, ActionListener listener) {
        JButton button = button(text, Icon.NO_ICON, tooltip, listener);
        button.setPreferredSize(new Dimension(width, height));
        button.setForeground(foregroundColor);
        return button;
    }

    private static JButton button(String text, Icon icon, String tooltip, ActionListener listener) {
        JButton button = new JButton(Objects.requireNonNull(text));
        button.setFont(MENU_FONT);
        button.setBackground(APP_BACKGROUND_COLOR);
        button.setForeground(Color.WHITE);
        if (icon != Icon.NO_ICON) {
            button.setIcon(icon.icon());
        }
        if (tooltip != null && !tooltip.isBlank()) {
            button.setToolTipText(tooltip);
        }
        button.addActionListener(Objects.requireNonNull(listener));
        button.setEnabled(true);
        return button;
    }

    public static void configureDialog(JDialog dialog, float widthScale, float heightScale, Runnable onClose) {
        dialog.setModalityType(Dialog.ModalityType.MODELESS);
        dialog.setAlwaysOnTop(false);
        dialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        Dimension dimension = frameDimension(widthScale, heightScale);
        dialog.setSize(dimension);
        dialog.setPreferredSize(dimension);
        Dimension location = frameLocation(dimension);
        dialog.setLocation(location.width, location.height);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent we) {
                onClose.run();
            }
        });
    }

    public static JPanel flowPanel(JComponent... components) {
        return flowPanel(null, APP_BACKGROUND_COLOR, 0, 0, components);
    }

    public static JPanel flowPanel(Border border, Color backgroundColor, int hgap, int vgap, JComponent... components) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, hgap, vgap));
        if (backgroundColor != null) {
            panel.setBackground(backgroundColor);
        }
        if (border != null) {
            panel.setBorder(border);
        }
        for (JComponent comp : components) {
            panel.add(comp);
        }
        return panel;
    }

    public static JPanel gap(int hgap) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, hgap, 0));
        panel.setBackground(APP_BACKGROUND_COLOR);
        return panel;
    }

    public static JMenu menu(Icon icon) {
        return menu(icon, "");
    }

    public static JMenu menu(Icon icon, String title) {
        JMenu connsMenu = new JMenu(title);
        connsMenu.setFont(MENU_FONT);
        if (icon != Icon.NO_ICON) {
            connsMenu.setIcon(icon.icon());
        }
        return connsMenu;
    }

    public static JMenuItem menuItem(Icon icon, String title, int keyEvent, ActionListener listener) {
        return menuItem(new JMenuItem(), icon, title, null, keyEvent, listener);
    }

    public static JMenuItem menuItem(Icon icon, String title, ActionListener listener) {
        return menuItem(new JMenuItem(), icon, title, null, Keyboard.NO_KEY_EVENT, listener);
    }

    public static JMenuItem menuItem(Icon icon, String title, String tooltip, int keyEvent, ActionListener listener) {
        return menuItem(new JMenuItem(), icon, title, tooltip, keyEvent, listener);
    }

    public static JMenuItem menuItem(JMenuItem item, Icon icon, String title, int keyEvent, ActionListener listener) {
        return menuItem(item, icon, title, null, keyEvent, listener);
    }

    private static JMenuItem menuItem(JMenuItem item, Icon icon, String title, String tooltip, int keyEvent, ActionListener listener) {
        if (icon != Icon.NO_ICON) {
            item.setIcon(icon.icon());
        }
        item.setFont(MENU_FONT);
        item.setText(title);
        if (tooltip != null) {
            item.setToolTipText(tooltip);
        }
        if (keyEvent != Keyboard.NO_KEY_EVENT) {
            item.setMnemonic(keyEvent);
            item.setAccelerator(KeyStroke.getKeyStroke(keyEvent, Keyboard.CMD_DOWN_MASK));
        }
        item.addActionListener(listener);
        return item;
    }

    public static JLabel label(String text, Color foregroundColor) {
        JLabel label = label(Icon.NO_ICON, text, null);
        label.setBackground(APP_BACKGROUND_COLOR);
        label.setForeground(foregroundColor);
        return label;
    }

    public static JLabel label(Icon icon, String text, Consumer<MouseEvent> consumer) {
        JLabel label = new JLabel();
        label.setFont(TABLE_HEADER_FONT);
        label.setBackground(APP_BACKGROUND_COLOR);
        if (icon != Icon.NO_ICON) {
            label.setIcon(icon.icon());
        }
        if (text != null) {
            label.setText(text);
        }
        if (consumer != null) {
            label.addMouseListener(new UnderlineLabelMouseListener(label) {
                @Override
                public void mouseClicked(MouseEvent e) {
                    super.mouseClicked(e);
                    consumer.accept(e);
                }
            });
        }
        return label;
    }

    public static boolean isMac() {
        return System.getProperty("os.name").toLowerCase().contains("mac");
    }

    public static void openQuestDBDocs(ActionEvent ignore) {
        Runtime rt = Runtime.getRuntime();
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("mac")) {
                rt.exec(String.format("open %s", DOCUMENTATION_URL));
            } else if (os.contains("win")) {
                rt.exec(String.format("rundll32 url.dll,FileProtocolHandler %s", DOCUMENTATION_URL));
            } else if (os.contains("nix") || os.contains("nux")) {
                String[] browsers = {
                        "google-chrome",
                        "firefox",
                        "mozilla",
                        "epiphany",
                        "konqueror",
                        "netscape",
                        "opera",
                        "links",
                        "lynx"
                };
                StringBuilder cmd = new StringBuilder();
                for (int i = 0; i < browsers.length; i++) {
                    if (i != 0) {
                        cmd.append(" || ");
                    }
                    cmd.append(browsers[i]).append("\"").append(DOCUMENTATION_URL).append("\"");
                }
                // If the first didn't work, try the next
                rt.exec(new String[]{"sh", "-c", cmd.toString()});
            }
        } catch (IOException err) {
            JOptionPane.showMessageDialog(
                    null,
                    String.format("Failed to open browser [%s:%s]: %s", os, DOCUMENTATION_URL, err.getMessage()),
                    "Helpless",
                    JOptionPane.INFORMATION_MESSAGE
            );
        }
    }

    public enum Icon {
        // https://p.yusukekamiyamane.com/
        // 16x16 icons
        NO_ICON(null, null),
        HELP("Help.png"),
        CONNS("Conns.png"),
        CONN_ADD("ConnAdd.png", "Add"),
        CONN_ASSIGN("ConnAssign.png", "ASSIGN"),
        CONN_CLONE("ConnClone.png", "Clone"),
        CONN_CONNECT("ConnConnect.png", "Connect"),
        CONN_DISCONNECT("ConnDisconnect.png"),
        CONN_REMOVE("ConnRemove.png", "Remove"),
        CONN_SHOW("ConnShow.png"), CONN_HIDE("ConnHide.png"),
        CONN_TEST("ConnTest.png", "Test"), COMMANDS("Commands.png"),
        COMMAND_ADD("CommandAdd.png"), COMMAND_REMOVE("CommandRemove.png"),
        COMMAND_EDIT("CommandEdit.png"), COMMAND_CLEAR("CommandClear.png"),
        COMMAND_SAVE("CommandSave.png"),
        COMMAND_RELOAD("CommandReload.png", "Reload"),
        COMMAND_STORE_BACKUP("CommandStoreBackup.png"),
        COMMAND_STORE_LOAD("CommandStoreLoad.png"),
        COMMAND_FIND("CommandFind.png"),
        COMMAND_REPLACE("CommandReplace.png"),
        COMMAND_EXEC("CommandExec.png"), COMMAND_EXEC_ABORT("CommandExecAbort.png"),
        COMMAND_EXEC_LINE("CommandExecLine.png"),
        PLOT("Plot.png"),
        QUESTDB("QuestDB.png"),
        MENU("Menu.png"),
        META("Meta.png"),
        META_FILE("MetaFile.png"),
        META_UNKNOWN("MetaUnknown.png"),
        META_FOLDER("MetaFolder.png"),
        RESULTS("Results.png"),
        RESULTS_NEXT("ResultsNext.png", "Next"),
        RESULTS_PREV("ResultsPrev.png", "Prev"),
        ROCKET("Rocket.png");

        private static final Map<String, ImageIcon> ICON_MAP = new HashMap<>();

        private final String iconName;
        private final String text;

        Icon(String iconName) {
            this.iconName = iconName;
            this.text = null;
        }

        Icon(String iconName, String text) {
            this.iconName = iconName;
            this.text = text;
        }

        public String getText() {
            return text != null ? text : iconName;
        }

        public ImageIcon icon() {
            if (this == NO_ICON) {
                throw new UnsupportedOperationException();
            }
            ImageIcon icon = ICON_MAP.get(iconName);
            String resource = "/images/" + iconName;
            try {
                if (icon == null) {
                    URL url = GTk.class.getResource(resource);
                    ICON_MAP.put(iconName, icon = new ImageIcon(TK.getImage(url)));
                }
            } catch (Throwable err) {
                LOG.error()
                        .$("Icon not available [resource=").$(resource)
                        .$(", e=").$(err.getMessage())
                        .I$();
            }
            return icon;
        }
    }

    public static final class Editor {
        public static final Color ERROR_FOREGROUND_COLOR = new Color(255, 55, 5);
        public static final Color MENU_FOREGROUND_COLOR = new Color(15, 205, 150);
        public static final Color NORMAL_FOREGROUND_COLOR = new Color(180, 255, 210);
        public static final Color KEYWORD_FOREGROUND_COLOR = new Color(15, 255, 150);
        public static final Color FUNCTION_FOREGROUND_COLOR = new Color(0, 255, 4, 92);
        public static final Color TYPE_FOREGROUND_COLOR = new Color(243, 156, 18);
        public static final Color LINENO_COLOR = Color.LIGHT_GRAY.darker().darker();
        public static final Color MATCH_FOREGROUND_COLOR = new Color(250, 255, 116);
        public static final Color PLOT_BORDER_COLOR = new Color(153, 153, 153);
        public static final int DEFAULT_FONT_SIZE = 15;
        public static final int MIN_FONT_SIZE = 11;
        public static final int MAX_FONT_SIZE = 21;
        public static final Font DEFAULT_FONT = new Font(MAIN_FONT_NAME, Font.BOLD, DEFAULT_FONT_SIZE);

        public static Font newFont(int newFontSize) {
            return new Font(MAIN_FONT_NAME, Font.BOLD, newFontSize);
        }
    }

    public static final class Keyboard {
        public static final int CMD_DOWN_MASK = isMac() ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
        public static final int CMD_SHIFT_DOWN_MASK = CMD_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
        public static final int ALT_DOWN_MASK = InputEvent.ALT_DOWN_MASK;
        public static final int ALT_SHIFT_DOWN_MASK = ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
        public static final int NO_KEY_EVENT = -1;

        public static void addCmdKeyAction(int keyEvent, JComponent component, ActionListener action) {
            addAction(CMD_DOWN_MASK, keyEvent, component, action);
        }

        public static void addCmdShiftKeyAction(int keyEvent, JComponent component, ActionListener action) {
            addAction(CMD_SHIFT_DOWN_MASK, keyEvent, component, action);
        }

        public static void addAltKeyAction(int keyEvent, JComponent component, ActionListener action) {
            addAction(ALT_DOWN_MASK, keyEvent, component, action);
        }

        public static void addAltShiftKeyAction(int keyEvent, JComponent component, ActionListener action) {
            addAction(ALT_SHIFT_DOWN_MASK, keyEvent, component, action);
        }

        public static void setupTableCmdKeyActions(JTable table) {
            addCmdKeyAction(KeyEvent.VK_A, table, e -> table.selectAll()); // cmd-a, select all
            final StringBuilder sb = new StringBuilder();
            addCmdKeyAction(KeyEvent.VK_C, table, e -> { // cmd-c, copy selection/all to clipboard
                int[] selectedRows = table.getSelectedRows();
                int[] selectedCols = table.getSelectedColumns();
                if (selectedRows.length == 0) {
                    table.selectAll();
                    selectedRows = table.getSelectedRows();
                }
                int[] widths = new int[selectedCols.length];
                for (int c = 0; c < selectedCols.length; c++) {
                    for (int r = 0; r < selectedRows.length; r++) {
                        int len = table.getValueAt(r, c).toString().length();
                        if (widths[c] < len) {
                            widths[c] = len;
                        }
                    }
                }
                sb.setLength(0);
                int rowIdx;
                int colIdx;
                for (int selectedRow : selectedRows) {
                    rowIdx = selectedRow;
                    for (int c = 0; c < selectedCols.length; c++) {
                        colIdx = selectedCols[c];
                        if (!table.getColumnName(colIdx).equals(Table.ROWID_COL_NAME)) {
                            String value = table.getValueAt(rowIdx, colIdx).toString();
                            int len = value.length();
                            sb.append(value);
                            sb.append(" ".repeat(Math.max(0, widths[c] - len)));
                            sb.append(", ");
                        }
                    }
                    sb.setLength(sb.length() - 2);
                    sb.append("\n");
                }
                if (!sb.isEmpty()) {
                    sb.setLength(sb.length() - 1); // last \n
                    setClipboardContent(sb.toString());
                }
            });
        }

        private static void addAction(int mask, int keyEvent, JComponent component, ActionListener action) {
            Action cmd = action(action);
            InputMap whenFocused = component.getInputMap(JComponent.WHEN_FOCUSED);
            whenFocused.put(KeyStroke.getKeyStroke(keyEvent, mask), cmd);
            component.getActionMap().put(cmd, cmd);
        }

        private static Action action(ActionListener action) {
            return new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    action.actionPerformed(e);
                }
            };
        }
    }

    private static class UnderlineLabelMouseListener implements NoopMouseListener {
        private final JLabel label;

        private UnderlineLabelMouseListener(JLabel label) {
            this.label = label;
        }

        @Override
        public void mouseEntered(MouseEvent e) {
            label.setFont(TABLE_HEADER_UNDERLINE_FONT);
        }

        @Override
        public void mouseExited(MouseEvent e) {
            label.setFont(TABLE_HEADER_FONT);
        }
    }
}
