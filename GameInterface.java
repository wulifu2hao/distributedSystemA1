/**
 * Created by yichao.wang on 19/9/16.
 */

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Hashtable;
import java.util.Map;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class GameInterface extends JFrame {

    /**
     * Main-Method : initialize JFrame
     *
     * @param args
     */
    public static void main(String[] args) {

        // test game info
        String[][] maze = new String[][]{
            {"*","dd","*",""},
            {"","","aa",""},
            {"","*","","xx"},
            {"","","zz",""}
        };
        Map<String, Integer> playerScores = new Hashtable<>();
        playerScores.put("dd",5);
        playerScores.put("aa",0);
        playerScores.put("xx",3);
        playerScores.put("zz",2);

        GameInfo gameInfo = new GameInfo();
        gameInfo.playerID = "xx";
        gameInfo.dim = 4;
        gameInfo.maze = maze;
        gameInfo.playerScores = playerScores;

        JFrame f = new GameInterface(gameInfo);
        // f.setSize(251,202); // -> set window size explicitly
        f.pack(); // -> causes this window to be sized to fit the preferred size
        // and layouts of its subcomponents.
        f.setVisible(true);
    }

    /**
     * GameInterface Creator
     */
    GameInterface(GameInfo gameInfo) {

        super("PlayerID: " + gameInfo.playerID); // -> set title

        int dim = gameInfo.dim;
        String[][] maze = gameInfo.maze;
        Map<String, Integer> playerScores = gameInfo.playerScores;

		/* init frame */
        setDefaultCloseOperation(EXIT_ON_CLOSE); // -> otherwise window will not
        // close
        setLayout(new BorderLayout());

		/* add center panel with flowlayout */
        JPanel center = new JPanel();
        center.setLayout(new FlowLayout());
        center.setBorder(new EmptyBorder(10, 10, 10, 10));
        add(center, BorderLayout.CENTER);

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

        for (Map.Entry<String,Integer> entry : playerScores.entrySet()) {
            JLabel block = new JLabel(entry.getKey() + ": " + entry.getValue());
            centerright.add(block);
        }

    }

}

class GameInfo {
    public String playerID;
    public int dim;
    public String[][] maze;
    public Map<String, Integer> playerScores;
}