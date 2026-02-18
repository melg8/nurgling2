package nurgling.widgets;

import haven.*;
import nurgling.NUtils;
import nurgling.i18n.L10n;

import java.awt.*;
import java.awt.datatransfer.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Debug log window for displaying bot messages and logs.
 * Shows history of messages with scrollback and copy-to-clipboard functionality.
 */
public class DebugLogWindow extends haven.Window {
    private final List<String> logLines = new ArrayList<>();
    private final int maxLines = 100;  // Maximum lines to keep in history

    private final haven.Label logLabel;
    private final IButton copyButton;
    private final IButton clearButton;

    private static final int WIDTH = UI.scale(400);
    private static final int HEIGHT = UI.scale(250);

    public DebugLogWindow(String title) {
        super(new Coord(WIDTH + UI.scale(10), HEIGHT), title, false);
        
        // Log display area using Label
        logLabel = new haven.Label("");
        logLabel.settip(L10n.get("debuglog.tooltip"));
        add(logLabel, UI.scale(5), UI.scale(25));
        
        // Copy button - using existing icon
        copyButton = new IButton(
            Resource.loadsimg("nurgling/hud/buttons/toggle_panel/geoloc/d"),
            Resource.loadsimg("nurgling/hud/buttons/toggle_panel/geoloc/u"),
            Resource.loadsimg("nurgling/hud/buttons/toggle_panel/geoloc/h"),
            this::copyToClipboard
        );
        copyButton.settip(L10n.get("debuglog.copy"));
        add(copyButton, WIDTH - UI.scale(55), HEIGHT - UI.scale(25));
        
        // Clear button - using grid icon
        clearButton = new IButton(
            Resource.loadsimg("nurgling/hud/buttons/toggle_panel/grid/d"),
            Resource.loadsimg("nurgling/hud/buttons/toggle_panel/grid/u"),
            Resource.loadsimg("nurgling/hud/buttons/toggle_panel/grid/h"),
            this::clearLog
        );
        clearButton.settip(L10n.get("debuglog.clear"));
        add(clearButton, WIDTH - UI.scale(35), HEIGHT - UI.scale(25));
    }
    
    /**
     * Add a message to the log
     */
    public void addMessage(String message) {
        String timestamp = String.format("[%1$tH:%1$tM:%1$tS]", new java.util.Date());
        String line = timestamp + " " + message;
        
        logLines.add(line);
        
        // Trim old lines
        while (logLines.size() > maxLines) {
            logLines.remove(0);
        }
        
        // Update display
        updateDisplay();
    }
    
    /**
     * Add a message with color coding based on content
     */
    public void addMessage(String message, LogLevel level) {
        String prefix = "";
        switch (level) {
            case ERROR: prefix = "❌ "; break;
            case WARNING: prefix = "⚠️ "; break;
            case SUCCESS: prefix = "✅ "; break;
            case INFO: default: break;
        }
        addMessage(prefix + message);
    }
    
    /**
     * Update the text display with all log lines
     */
    private void updateDisplay() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < logLines.size(); i++) {
            sb.append(logLines.get(i));
            if (i < logLines.size() - 1) {
                sb.append("\n");
            }
        }
        logLabel.settext(sb.toString());
    }
    
    /**
     * Copy all log content to clipboard
     */
    private void copyToClipboard() {
        try {
            StringBuilder sb = new StringBuilder();
            for (String line : logLines) {
                sb.append(line).append("\n");
            }
            
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            StringSelection selection = new StringSelection(sb.toString());
            clipboard.setContents(selection, selection);
            
            // Show feedback
            NUtils.getGameUI().ui.gui.msg("Log copied to clipboard!");
        } catch (Exception e) {
            NUtils.getGameUI().ui.gui.msg("Failed to copy to clipboard");
        }
    }
    
    /**
     * Clear all log messages
     */
    private void clearLog() {
        logLines.clear();
        logLabel.settext("");
        NUtils.getGameUI().ui.gui.msg("Log cleared");
    }

    public enum LogLevel {
        INFO,
        SUCCESS,
        WARNING,
        ERROR
    }
}
