package com.pencilerly.ciloopsmoke;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * Single-activity tap-the-target game.
 *
 * <p>Displays a score and a movable target button. Tapping the target increments the
 * score and repositions the target somewhere inside the playing area. No network,
 * permissions, or persistence are involved.
 */
public class MainActivity extends Activity {

    private GameState gameState;
    private TextView scoreView;
    private Button targetButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        gameState = new GameState();
        scoreView = findViewById(R.id.scoreText);
        targetButton = findViewById(R.id.targetButton);

        updateScoreLabel();

        targetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onTargetTapped();
            }
        });

        // Place the target once the layout has measured the playing area.
        targetButton.post(new Runnable() {
            @Override
            public void run() {
                placeTarget(false);
            }
        });
    }

    private void onTargetTapped() {
        placeTarget(true);
        updateScoreLabel();
    }

    /**
     * @param scoring when true this is a real tap (score++ and move); when false only
     *                the target position is randomised (initial placement).
     */
    private void placeTarget(boolean scoring) {
        View parent = (View) targetButton.getParent();
        int maxX = parent.getWidth() - targetButton.getWidth();
        int maxY = parent.getHeight() - targetButton.getHeight();

        if (scoring) {
            gameState.tap(maxX, maxY);
        } else {
            gameState.randomizeTarget(maxX, maxY);
        }

        targetButton.setX(gameState.getTargetX());
        targetButton.setY(gameState.getTargetY());
    }

    private void updateScoreLabel() {
        scoreView.setText(getString(R.string.score_label, gameState.getScore()));
    }
}
