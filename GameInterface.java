/**
 * Created by yichao.wang on 19/9/16.
 */

import java.awt.*;
import java.util.Hashtable;
import java.util.Map;
import java.util.logging.Logger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class GameInterface extends JFrame {

    /**
     * Main-Method : initialize JFrame
     *
     * @param args
     */

    private JPanel center;
    private JLabel roleLabel;
    private InterfaceData data;
    private final Logger LOGGER = Logger.getLogger("GameInterface");

    public static void main(String[] args) {

        // test game info
        String[][] maze = new String[][]{
                {"*", "dd", "*", ""},
                {"", "", "aa", ""},
                {"", "*", "", "xx"},
                {"", "", "zz", ""}
        };
        Map<String, Integer> playerScores = new Hashtable<>();
        playerScores.put("dd", 5);
        playerScores.put("aa", 0);
        playerScores.put("xx", 3);
        playerScores.put("zz", 2);

        String playerID = "xx";
        InterfaceData data = new InterfaceData();
        data.maze = maze;
        data.playerScores = playerScores;

        JFrame f = new GameInterface(playerID);
        f.setSize(251,202); // -> set window size explicitly
//        f.pack(); // -> causes this window to be sized to fit the preferred size
        // and layouts of its subcomponents.
        f.setVisible(true);
        ((GameInterface)f).updateInterface(data);
    }

    public static GameInterface initGameInterface(String playerID, InterfaceData data) {
        GameInterface f = new GameInterface(playerID);
        f.setSize(251,202);
        f.setVisible(true);
        f.updateInterface(data);
        return f;
    }

    /**
     * GameInterface Creator
     */
    GameInterface(String playerID) {

        super("PlayerID: " + playerID); // set title
        LOGGER.info("init Game Interface");
        setDefaultCloseOperation(EXIT_ON_CLOSE); // otherwise window will not close
        setLayout(new BorderLayout());
    }

    public void updateInterface(InterfaceData data) {
        this.data = data;
        drawCenter();
        pack();
        revalidate();
        setVisible(true);
    }

    private void drawCenter() {
        int role = data.role;
        String[][] maze = data.maze;
        int dim = maze.length;
        Map<String, Integer> playerScores = data.playerScores;

        String roleStr = "Normal";
        switch (role) {
            case Game.BACKUP:
                roleStr = "Backup server"; break;
            case Game.PRIMARY:
                roleStr = "Primary server"; break;
        }

        if (roleLabel == null) {
            LOGGER.info("[drawCenter] init role to " + roleStr);
            roleLabel = new JLabel(roleStr);
            roleLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
            add(roleLabel, BorderLayout.NORTH);
        } else {
            LOGGER.info("[drawCenter] change role to " + roleStr);
            roleLabel.setText(roleStr);
        }

        /* add center panel with flowlayout */
        if (center == null) {
            center = new JPanel();
            center.setLayout(new FlowLayout());
            center.setBorder(new EmptyBorder(10, 10, 10, 10));
            add(center, BorderLayout.CENTER);
        } else {
            center.removeAll();
        }

		/* add centerleft with gridlayout */
        JPanel centerleft = new JPanel();
        centerleft.setBorder(BorderFactory.createLineBorder(Color.black));
        centerleft.setLayout(new GridLayout(dim, dim));
        center.add(centerleft);

		/* add button with a action listener */
        for (int x = 0; x < dim; x++) {
            for (int y = 0; y < dim; y++) {
                JLabel block = new JLabel(maze[x][y]);
                block.setBorder(BorderFactory.createLineBorder(Color.black));
                centerleft.add(block);
            }
        }
		/* add centerright with gridlayout */
        JPanel centerright = new JPanel();
        centerright.setLayout(new GridLayout(playerScores.size(), 2));
        centerright.setBorder(new EmptyBorder(10, 10, 10, 10));
        center.add(centerright);

        for (Map.Entry<String, Integer> entry : playerScores.entrySet()) {
            JLabel block = new JLabel(entry.getKey() + ": " + entry.getValue());
            centerright.add(block);
        }
    }
}