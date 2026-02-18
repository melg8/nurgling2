package nurgling.widgets.bots;

import haven.*;
import nurgling.NUtils;
import nurgling.i18n.L10n;

public class CliffClimberTestWnd extends Window implements Checkable {

    Button startButton;
    boolean isReady = false;

    public CliffClimberTestWnd() {
        super(new Coord(300, 180), "Cliff Climber Test #16");
        
        prev = add(new Label("Test cliff crossing with visualization"), UI.scale(5), UI.scale(25));
        prev = add(new Label("Visualization starts automatically"), prev.pos("bl").add(UI.scale(0, 5)));
        prev = add(new Label("Look around to change direction"), prev.pos("bl").add(UI.scale(0, 5)));
        
        prev = add(startButton = new Button(UI.scale(200), "Start Cliff Test"){
            @Override
            public void click() {
                super.click();
                isReady = true;
            }
        }, prev.pos("bl").add(UI.scale(0, 15)));
        
        pack();
    }

    @Override
    public boolean check() {
        return isReady;
    }

    @Override
    public void wdgmsg(String msg, Object... args) {
        if(msg.equals("close")) {
            hide();
        }
        super.wdgmsg(msg, args);
    }
}
