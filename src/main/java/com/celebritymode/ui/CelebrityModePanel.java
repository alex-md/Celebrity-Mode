package com.celebritymode.ui;

import com.celebritymode.CelebrityModeConfig;
import com.celebritymode.appearance.FanGearTier;
import com.celebritymode.chat.*;
import com.celebritymode.fan.*;
import com.celebritymode.movement.FormationStyle;
import com.celebritymode.pm.TrafficIntensity;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;

/** All UI state lives on the EDT; config events drive the existing client-thread behavior. */
public final class CelebrityModePanel extends PluginPanel {
    private static final Color BACKGROUND = new Color(32, 35, 39);
    private static final Color SURFACE = new Color(43, 47, 52);
    private static final Color INPUT = new Color(27, 30, 34);
    private static final Color GOLD = new Color(234, 190, 98);
    private static final Color TEXT = new Color(232, 235, 237);
    private static final Color MUTED = new Color(167, 176, 185);
    private static final Font BODY = FontManager.getDefaultFont().deriveFont(12f);
    private static final Font SMALL = BODY.deriveFont(11f);
    private final CelebrityModeConfig config;
    private final BiConsumer<String, Object> writeConfig;
    private final Map<String, Runnable> refreshers = new LinkedHashMap<>();
    private final JPanel body = column();
    private final List<JPanel> pages = new ArrayList<>();
    private final List<JButton> tabs = new ArrayList<>();
    private final JLabel summary = label("", GOLD, SMALL);
    private final JTextArea quotes = new JTextArea(9, 16);
    private final JLabel quoteStatus = label("", MUTED, SMALL);
    private final JButton save = button("Save lines", true);
    private final JButton revert = button("Revert", false);
    private String savedQuotes = "";
    private boolean syncing;
    private int selectedPage;

    public CelebrityModePanel(CelebrityModeConfig config, BiConsumer<String, Object> writeConfig) {
        this.config = config;
        this.writeConfig = writeConfig;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(BACKGROUND);
        setBorder(new EmptyBorder(14, 10, 12, 10));
        getScrollPane().setBorder(null);
        getScrollPane().getVerticalScrollBar().setUnitIncrement(18);
        getScrollPane().getViewport().setBackground(BACKGROUND);
        getWrappedPanel().setBackground(BACKGROUND);
        add(header());
        add(Box.createVerticalStrut(12));
        JPanel navigation = new ContentPanel(new GridLayout(1, 3, 3, 0));
        navigation.setOpaque(false);
        for (String title : new String[] {"Crowd", "Dialogue", "Extras"}) {
            int index = tabs.size();
            JButton tab = button(title, false);
            tab.setFont(SMALL.deriveFont(Font.BOLD));
            tab.addActionListener(event -> selectPage(index));
            tabs.add(tab);
            navigation.add(tab);
        }
        fit(navigation);
        add(navigation);
        add(Box.createVerticalStrut(12));
        pages.add(crowdPage());
        pages.add(dialoguePage());
        pages.add(extrasPage());
        add(body);
        add(Box.createVerticalStrut(12));
        add(hint("Your own fan club, just for fun. Only you see the fans."));
        refresh(null);
        selectPage(0);
    }

    private JPanel header() {
        JPanel header = new ContentPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.add(new JLabel(new ImageIcon(CelebrityModeIcon.create(36))), BorderLayout.WEST);
        JPanel titles = column();
        JLabel title = label("Fame Simulator", TEXT, FontManager.getRunescapeBoldFont().deriveFont(20f));
        titles.add(title);
        titles.add(label("YOUR OWN FAN CLUB", MUTED, SMALL.deriveFont(10f)));
        titles.add(Box.createVerticalStrut(4));
        titles.add(summary);
        header.add(titles, BorderLayout.CENTER);
        fit(header);
        return header;
    }

    private JPanel crowdPage() {
        JPanel page = column();
        JPanel crowd = section("Your crowd", "Changes apply while you play.");
        toggle(crowd, "Show crowd", "Hide or show your fans without losing your setup.",
                "showCrowd", config::showCrowd);
        number(crowd, "Fans", "crowdSize", 1, CrowdArrival.MAX_FANS, config::crowdSize, "fans");
        JLabel scaleNote = hint("");
        crowd.add(scaleNote);
        crowd.add(Box.createVerticalStrut(8));
        refreshers.put("largeCrowdNote", () -> scaleNote.setText(html(config.crowdSize() > 30
                ? "Large crowd: more overlap and rendering work. Try a wider spread."
                : "A few loyal fans, or a crowd wherever you go.")));
        combo(crowd, "Formation", "formationStyle", FormationStyle.values(), config::formationStyle);
        combo(crowd, "Arrival pace", "arrivalPace", ArrivalPace.values(), config::arrivalPace);
        crowd.add(hint("Arrival pace applies to new fans. Hide and show the crowd to start a fresh entrance."));
        finish(page, crowd);

        JPanel presets = section("Choose your crowd", "Presets change fans, formation, and chatter.");
        preset(presets, "Small audience", "8 fans · Entourage · Low chatter", 8, FormationStyle.ENTOURAGE, ChatFrequency.LOW);
        preset(presets, "Fan club", "24 fans · Loose crowd · Normal chatter", 24, FormationStyle.LOOSE_CROWD, ChatFrequency.NORMAL);
        preset(presets, "Big entrance", "60 fans · Swarm · Spam chatter", 60, FormationStyle.SWARM, ChatFrequency.SPAM);
        finish(page, presets);
        return page;
    }

    private JPanel dialoguePage() {
        JPanel page = column();
        JPanel chatter = section("Crowd chatter", "Let the fans riff, use your lines, or mix both.");
        combo(chatter, "Frequency", "chatFrequency", ChatFrequency.values(), config::chatFrequency);
        combo(chatter, "Dialogue source", "customQuoteMode", CustomQuoteMode.values(), config::customQuoteMode);
        chatter.add(hint("Off mutes overhead dialogue. Built-in chatter reacts to banking, combat, gear, and skilling."));
        finish(page, chatter);

        JPanel inbox = section("Fan inbox", "A flood of incoming fan PMs in native private chat.");
        toggle(inbox, "Enable fan PMs", "Local simulation. No messages are sent to other players.",
                "enableFanPms", config::enableFanPms);
        combo(inbox, "Traffic intensity", "pmTrafficIntensity", TrafficIntensity.values(), config::pmTrafficIntensity);
        JLabel pmRate = hint("");
        inbox.add(pmRate);
        inbox.add(Box.createVerticalStrut(8));
        refreshers.put("pmRateSummary", () -> pmRate.setText(html("About "
                + config.pmTrafficIntensity().getMessagesPerMinute() + " incoming PMs/min, with random clumps and lulls.")));
        toggle(inbox, "Notification sound", "Native UI sound, at most once every two seconds.",
                "pmNotificationSound", config::pmNotificationSound);
        inbox.add(hint("Uses your private-chat display settings. Runs independently of overhead chatter and Show crowd."));
        finish(page, inbox);

        JPanel editor = section("Your lines", "One quote per line. Fans pick from the pool automatically.");
        quotes.setFont(BODY);
        quotes.setBackground(INPUT);
        quotes.setForeground(TEXT);
        quotes.setCaretColor(GOLD);
        quotes.setLineWrap(true);
        quotes.setWrapStyleWord(true);
        quotes.setBorder(new EmptyBorder(8, 8, 8, 8));
        quotes.setName("customQuotes");
        quotes.getAccessibleContext().setAccessibleName("Custom fan dialogue, one quote per line");
        JScrollPane scroll = new JScrollPane(quotes);
        scroll.setBorder(new LineBorder(new Color(77, 83, 90)));
        scroll.setPreferredSize(new Dimension(190, 180));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        editor.add(scroll);
        editor.add(Box.createVerticalStrut(6));
        editor.add(label("Insert a placeholder", MUTED, SMALL));
        JPanel tokens = new ContentPanel(new GridLayout(0, 2, 4, 4));
        tokens.setOpaque(false);
        for (String token : new String[] {"player", "gear", "target", "skill", "level"}) {
            JButton insert = button("{" + token + "}", false);
            insert.setFont(SMALL);
            insert.setToolTipText(placeholderHelp(token));
            insert.addActionListener(event -> {
                quotes.replaceSelection("{" + token + "}");
                quotes.requestFocusInWindow();
            });
            tokens.add(insert);
        }
        fit(tokens);
        editor.add(tokens);
        editor.add(Box.createVerticalStrut(8));
        JPanel actions = new ContentPanel(new GridLayout(1, 2, 5, 0));
        actions.setOpaque(false);
        save.setName("saveQuotes");
        save.addActionListener(event -> {
            write("customQuotes", quotes.getText());
            loadQuotes();
        });
        revert.addActionListener(event -> loadQuotes());
        actions.add(save);
        actions.add(revert);
        fit(actions);
        editor.add(actions);
        editor.add(Box.createVerticalStrut(6));
        editor.add(quoteStatus);
        editor.add(Box.createVerticalStrut(6));
        editor.add(hint("Up to 100 quotes, 100 characters each. Use multiple lines to keep commas inside a quote."));
        quotes.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { updateQuoteStatus(); }
            public void removeUpdate(DocumentEvent event) { updateQuoteStatus(); }
            public void changedUpdate(DocumentEvent event) { updateQuoteStatus(); }
        });
        finish(page, editor);
        return page;
    }

    private JPanel extrasPage() {
        JPanel page = column();
        JPanel appearance = section("The look", "Dress your fans and give them room.");
        combo(appearance, "Gear theme", "crowdGearTier", FanGearTier.values(), config::crowdGearTier);
        toggle(appearance, "Fan names", "Show simulated usernames above followers.", "showFanNames", config::showFanNames);
        number(appearance, "Travelling spread", "maxSpread", 1, 8, config::maxSpread, "tiles");
        toggle(appearance, "Personal space", "Prefer separate tiles. Large crowds may share.", "respectPersonalSpace", config::respectPersonalSpace);
        finish(page, appearance);
        JPanel reactions = section("Reactions", "A little extra life in the crowd.");
        toggle(reactions, "Event reactions", "Recognition, levels, deaths, and occasional idle waves or cheers.", "reactToEvents", config::reactToEvents);
        reactions.add(hint("Activity-aware dialogue is controlled in Dialogue. Custom-only also uses your pool for event lines."));
        finish(page, reactions);
        JPanel diagnostics = section("Diagnostics", "For checking movement and formations.");
        toggle(diagnostics, "Debug overlay", "Show routes, targets, and follower states.", "debugMode", config::debugMode);
        finish(page, diagnostics);
        return page;
    }

    private void number(JPanel section, String title, String key, int min, int max, IntSupplier read, String unit) {
        section.add(label(title, TEXT, BODY));
        JPanel row = new ContentPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        JSlider slider = new JSlider(min, max);
        slider.setOpaque(false);
        slider.setForeground(GOLD);
        slider.setName(key + "Slider");
        slider.getAccessibleContext().setAccessibleName(title);
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(Math.max(min, Math.min(max, read.getAsInt())), min, max, 1));
        spinner.setName(key);
        spinner.setPreferredSize(new Dimension(58, 28));
        spinner.setFont(BODY);
        spinner.getAccessibleContext().setAccessibleName(title);
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "0");
        spinner.setEditor(editor);
        editor.getTextField().setBackground(INPUT);
        editor.getTextField().setForeground(TEXT);
        editor.getTextField().setCaretColor(GOLD);
        slider.addChangeListener(event -> {
            if (syncing) return;
            syncing = true;
            spinner.setValue(slider.getValue());
            syncing = false;
            if (!slider.getValueIsAdjusting()) write(key, slider.getValue());
        });
        spinner.addChangeListener(event -> {
            if (syncing) return;
            syncing = true;
            slider.setValue((Integer) spinner.getValue());
            syncing = false;
            write(key, spinner.getValue());
        });
        row.add(slider, BorderLayout.CENTER);
        row.add(spinner, BorderLayout.EAST);
        fit(row);
        section.add(row);
        section.add(label(min + "–" + max + " " + unit, MUTED, SMALL));
        section.add(Box.createVerticalStrut(8));
        refreshers.put(key, () -> {
            int value = Math.max(min, Math.min(max, read.getAsInt()));
            // Don't interrupt a drag with an unrelated config event.
            if (!slider.getValueIsAdjusting()) {
                slider.setValue(value);
                spinner.setValue(value);
            }
        });
    }

    private <T extends Enum<T>> void combo(JPanel section, String title, String key, T[] options, Supplier<T> read) {
        section.add(label(title, TEXT, BODY));
        section.add(Box.createVerticalStrut(3));
        JComboBox<T> combo = new JComboBox<>(options);
        combo.setName(key);
        combo.setFont(BODY);
        combo.setBackground(INPUT);
        combo.setForeground(TEXT);
        combo.getAccessibleContext().setAccessibleName(title);
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value == null ? "" : display((Enum<?>) value), index, selected, focus);
                setBorder(new EmptyBorder(5, 6, 5, 6));
                setBackground(selected ? new Color(77, 68, 48) : INPUT);
                setForeground(TEXT);
                return this;
            }
        });
        combo.addActionListener(event -> { if (!syncing) write(key, combo.getSelectedItem()); });
        fit(combo);
        section.add(combo);
        section.add(Box.createVerticalStrut(10));
        refreshers.put(key, () -> combo.setSelectedItem(read.get()));
    }

    private void toggle(JPanel section, String title, String help, String key, BooleanSupplier read) {
        JCheckBox toggle = new JCheckBox(title);
        toggle.setName(key);
        toggle.setOpaque(false);
        toggle.setForeground(TEXT);
        toggle.setFont(BODY);
        toggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        toggle.setToolTipText(help);
        toggle.addActionListener(event -> { if (!syncing) write(key, toggle.isSelected()); });
        section.add(toggle);
        section.add(hint(help));
        section.add(Box.createVerticalStrut(8));
        refreshers.put(key, () -> toggle.setSelected(read.getAsBoolean()));
    }

    private void preset(JPanel section, String title, String description, int count, FormationStyle formation, ChatFrequency frequency) {
        JButton preset = button(title, false);
        preset.setToolTipText(description);
        preset.addActionListener(event -> {
            write("crowdSize", count);
            write("formationStyle", formation);
            write("chatFrequency", frequency);
        });
        fit(preset);
        section.add(preset);
        section.add(hint(description));
        section.add(Box.createVerticalStrut(7));
    }

    private void write(String key, Object value) {
        if (syncing) return;
        writeConfig.accept(key, value);
        refresh(key);
    }

    public void refresh(String changedKey) {
        boolean dirty = !quotes.getText().equals(savedQuotes);
        syncing = true;
        try {
            refreshers.values().forEach(Runnable::run);
            summary.setText(config.showCrowd() ? config.crowdSize() + " fans · " + config.chatFrequency().name().toLowerCase(Locale.ENGLISH) : "Crowd hidden · settings saved");
            if (changedKey == null || "customQuotes".equals(changedKey) && !dirty) {
                savedQuotes = config.customQuotes();
                quotes.setText(savedQuotes);
                quotes.setCaretPosition(0);
            }
        } finally {
            syncing = false;
        }
        updateQuoteStatus();
    }

    private void loadQuotes() {
        savedQuotes = config.customQuotes();
        quotes.setText(savedQuotes);
        quotes.setCaretPosition(0);
        updateQuoteStatus();
    }

    private void updateQuoteStatus() {
        boolean dirty = !quotes.getText().equals(savedQuotes);
        int count = FanQuoteLibrary.parse(quotes.getText()).size();
        save.setEnabled(dirty);
        revert.setEnabled(dirty);
        String status = dirty ? "Unsaved · " : "Saved · ";
        status += count + (count == 1 ? " quote" : " quotes");
        String[] entries = quotes.getText().split(quotes.getText().contains("\n") || quotes.getText().contains("\r") ? "\\r\\n|[\\r\\n]" : ",");
        long total = Arrays.stream(entries).filter(line -> !line.trim().isEmpty()).count();
        boolean longLine = Arrays.stream(entries).anyMatch(line -> line.trim().length() > 100);
        if (dirty && !savedQuotes.equals(config.customQuotes())) status += ". Saved lines changed elsewhere; Revert reloads them.";
        if (total > 100) status += ". Only the first 100 are used.";
        if (longLine) status += ". Long lines are shortened.";
        if (count == 0 && config.customQuoteMode() == CustomQuoteMode.CUSTOM_ONLY) status += ". Custom-only is silent.";
        quoteStatus.setText(html(status));
        quoteStatus.setForeground(dirty || total > 100 || longLine ? GOLD : MUTED);
    }

    private void selectPage(int index) {
        selectedPage = index;
        body.removeAll();
        body.add(pages.get(index));
        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).setBackground(i == index ? GOLD : SURFACE);
            tabs.get(i).setForeground(i == index ? BACKGROUND : TEXT);
        }
        revalidate();
        repaint();
        SwingUtilities.invokeLater(() -> { if (selectedPage == index) getScrollPane().getVerticalScrollBar().setValue(0); });
    }

    private static JPanel section(String title, String subtitle) {
        JPanel panel = column();
        panel.setBackground(SURFACE);
        panel.setOpaque(true);
        panel.setBorder(new CompoundBorder(new MatteBorder(1, 0, 0, 0, new Color(94, 82, 56)), new EmptyBorder(10, 9, 6, 9)));
        panel.add(label(title, GOLD, BODY.deriveFont(Font.BOLD, 13f)));
        panel.add(Box.createVerticalStrut(4));
        panel.add(hint(subtitle));
        panel.add(Box.createVerticalStrut(10));
        return panel;
    }

    private static void finish(JPanel page, JPanel section) {
        fit(section);
        page.add(section);
        page.add(Box.createVerticalStrut(10));
    }

    /** Content height follows live labels instead of being frozen before config is loaded. */
    private static final class ContentPanel extends JPanel {
        private ContentPanel() { super(); }
        private ContentPanel(LayoutManager layout) { super(layout); }
        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    private static JPanel column() {
        JPanel panel = new ContentPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private static void fit(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (!(component instanceof ContentPanel)) {
            component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        }
    }

    private static JLabel label(String text, Color color, Font font) {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        label.setFont(font);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static JLabel hint(String text) {
        return label(html(text), MUTED, SMALL);
    }

    private static String html(String text) {
        return "<html><div style='width: " + ((PANEL_WIDTH - 42) * 3 / 4) + "px'>" + text + "</div></html>";
    }

    private static JButton button(String text, boolean primary) {
        JButton button = new JButton(text);
        button.setFont(BODY.deriveFont(Font.BOLD));
        button.setBackground(primary ? GOLD : INPUT);
        button.setForeground(primary ? BACKGROUND : TEXT);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorder(new CompoundBorder(new LineBorder(new Color(80, 85, 91)), new EmptyBorder(7, 5, 7, 5)));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        Border normalBorder = button.getBorder();
        button.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent event) {
                button.setBorder(new CompoundBorder(new LineBorder(GOLD), new EmptyBorder(7, 5, 7, 5)));
            }
            @Override
            public void focusLost(java.awt.event.FocusEvent event) {
                button.setBorder(normalBorder);
            }
        });
        return button;
    }

    private static String placeholderHelp(String token) {
        switch (token) {
            case "player": return "Your character's name";
            case "gear": return "Equipped weapon, or fit when unavailable";
            case "target": return "Combat target, or opponent when unavailable";
            case "skill": return "Recent skill, or skill when unavailable";
            default: return "Current skill-context level, or 0 when unavailable";
        }
    }

    private static String display(Enum<?> value) {
        if (value instanceof TrafficIntensity) {
            switch ((TrafficIntensity) value) {
                case RELAXED: return "Relaxed";
                case STREAMER: return "Streamer";
                case GLOBAL_CELEBRITY: return "Global celebrity";
                default: return "Peak world record";
            }
        }
        switch (value.name()) {
            case "BUILT_IN_ONLY": return "Built-in reactions";
            case "CUSTOM_ONLY": return "My lines only";
            case "MIXED": return value instanceof CustomQuoteMode ? "Built-in + my lines" : "Mixed outfits";
            case "DEFAULT_BOB": return "Bob";
            case "BRONZE_NOOB": return "Bronze beginner";
            case "F2P_WARRIOR": return "F2P warrior";
            case "MIDGAME_WARRIOR": return "Midgame warrior";
            case "RANDOM_FASHIONSCAPE": return "Fashionscape";
            case "MODERN_GEAR": return "Modern gear";
            case "LOW": return "Low · occasional";
            case "NORMAL": return "Normal · lively";
            case "SPAM": return "Spam · nonstop";
            default:
                String text = value.name().toLowerCase(Locale.ENGLISH).replace('_', ' ');
                return Character.toUpperCase(text.charAt(0)) + text.substring(1);
        }
    }
}
