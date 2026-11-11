package org.accessgraph.view;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.LayoutManager;

/** Small shared Swing styling helpers; all layout dimensions are logical pixels. */
final class Ui {
    static final Color INK = new Color(0x173744);
    static final Color TEAL = new Color(0x006B68);
    static final Color BACKGROUND = new Color(0xF0F5F6);
    static final Color MUTED = new Color(0x4F626C);
    static final Color LINE = new Color(0xCEDBDF);
    static final Color ERROR = new Color(0xA02632);
    static final Color AMBER = new Color(0x795000);
    static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 15);

    private Ui() { }

    static void configure() {
        for (String key : new String[]{"Button.font", "Label.font", "ComboBox.font", "TextField.font", "PasswordField.font",
                "CheckBox.font", "Spinner.font", "TabbedPane.font", "List.font", "ToolTip.font"}) {
            UIManager.put(key, BODY);
        }
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("Label.foreground", INK);
        UIManager.put("TabbedPane.selected", Color.WHITE);
        UIManager.put("TabbedPane.contentAreaColor", BACKGROUND);
        UIManager.put("TabbedPane.tabInsets", new Insets(13, 20, 13, 20));
    }

    static JPanel column() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new ColumnLayout());
        return panel;
    }

    /** Stack content without stretching paragraphs or caching their pre-wrap heights. */
    private static final class ColumnLayout implements LayoutManager {
        @Override
        public void addLayoutComponent(String name, Component component) { }

        @Override
        public void removeLayoutComponent(Component component) { }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Insets padding = parent.getInsets();
            int width = 0;
            int height = padding.top + padding.bottom;
            for (Component child : parent.getComponents()) {
                if (child.isVisible()) {
                    Dimension preferred = child.getPreferredSize();
                    width = Math.max(width, preferred.width);
                    height += preferred.height;
                }
            }
            return new Dimension(width + padding.left + padding.right, height);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) { return new Dimension(0, 0); }

        @Override
        public void layoutContainer(Container parent) {
            Insets padding = parent.getInsets();
            int width = Math.max(0, parent.getWidth() - padding.left - padding.right);
            int y = padding.top;
            for (Component child : parent.getComponents()) {
                if (child.isVisible()) {
                    int childWidth = Math.min(width, child.getMaximumSize().width);
                    int height = child.getPreferredSize().height;
                    child.setBounds(padding.left, y, childWidth, height);
                    y += height;
                }
            }
        }
    }

    static JPanel card() {
        JPanel panel = column();
        panel.setOpaque(true);
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE), BorderFactory.createEmptyBorder(22, 22, 22, 22)));
        return panel;
    }

    static JLabel label(String text, int size, boolean bold) {
        JLabel label = new JLabel(text);
        label.putClientProperty("html.disable", true);
        label.setFont(BODY.deriveFont(bold ? Font.BOLD : Font.PLAIN, size));
        label.setForeground(INK);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    static JTextArea text(String text) {
        JTextArea area = new WrappingText(text);
        area.setFont(BODY);
        area.setForeground(MUTED);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setOpaque(false);
        area.setFocusable(false);
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        return area;
    }

    /** Measure wrapped paragraphs at the available column width. */
    private static final class WrappingText extends JTextArea {
        WrappingText(String text) { super(text); }

        @Override
        public Dimension getPreferredSize() {
            if (getParent() != null && getParent().getWidth() > 0) {
                Insets padding = getParent().getInsets();
                int width = Math.max(1, getParent().getWidth() - padding.left - padding.right);
                Insets ownPadding = getInsets();
                int textWidth = Math.max(1, width - ownPadding.left - ownPadding.right);
                int rows = 0;
                for (String line : getText().split("\n", -1)) {
                    if (line.isEmpty()) {
                        rows++;
                    } else {
                        int offset = 0;
                        while (offset < line.length()) {
                            int end = offset + 1;
                            while (end < line.length() && getFontMetrics(getFont()).stringWidth(
                                    line.substring(offset, end + 1)) <= Math.max(1, textWidth - 4)) {
                                end++;
                            }
                            if (end < line.length()) {
                                int space = line.lastIndexOf(' ', end - 1);
                                if (space >= offset) { end = space + 1; }
                            }
                            offset = end;
                            rows++;
                        }
                    }
                }
                return new Dimension(width, rows * getFontMetrics(getFont()).getHeight()
                        + ownPadding.top + ownPadding.bottom);
            }
            return super.getPreferredSize();
        }

        @Override
        public void setBounds(int x, int y, int width, int height) {
            boolean widthChanged = width != getWidth();
            super.setBounds(x, y, width, height);
            if (widthChanged && getParent() != null) { getParent().invalidate(); }
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(0, getPreferredSize().height);
        }
    }

    static JButton button(String text, boolean primary) {
        JButton button = new JButton(text);
        button.setFont(BODY.deriveFont(Font.BOLD));
        button.setMargin(new Insets(10, 16, 10, 16));
        button.setBackground(primary ? TEAL : Color.WHITE);
        button.setForeground(primary ? Color.WHITE : INK);
        button.setOpaque(true);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        return button;
    }

    static void add(JPanel column, JComponent component, int gapAfter) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(component);
        if (gapAfter > 0) { column.add(Box.createVerticalStrut(gapAfter)); }
    }

    static void field(JPanel panel, String name, JComponent field) {
        JLabel label = label(name, 15, true);
        label.setLabelFor(field);
        field.getAccessibleContext().setAccessibleName(name);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        field.setPreferredSize(new Dimension(250, 38));
        add(panel, label, 7);
        add(panel, field, 14);
    }

    static JPanel page(String title, String description, JComponent body) {
        JPanel panel = new ScrollablePage();
        panel.setBackground(BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(24, 26, 24, 26));
        JPanel heading = column();
        add(heading, label(title, 27, true), 8);
        add(heading, text(description), 0);
        panel.add(heading, BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    private static final class ScrollablePage extends JPanel implements Scrollable {
        ScrollablePage() { super(new BorderLayout(0, 20)); }

        @Override
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 18; }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(18, visible.height - 18);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() { return true; }

        @Override
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    static JScrollPane scroll(JComponent component) {
        JScrollPane scroll = new JScrollPane(component);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    static void notice(JTextArea area, String message, boolean error) {
        area.setText(message);
        area.setForeground(error ? ERROR : MUTED);
        area.getAccessibleContext().setAccessibleName(error ? "Input error" : "Status");
    }
}
