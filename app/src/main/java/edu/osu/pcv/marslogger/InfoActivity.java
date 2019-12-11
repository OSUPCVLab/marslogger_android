package edu.osu.pcv.marslogger;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

// TODO(jhuai): use a toolbar(import android.support.v7.widget.Toolbar) to navigate the activities
// see ch8 p313 of head first Android development a brain friendly guide
public class InfoActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);
        addButtonListeners();
    }

    // TODO(jhuai): depending on the build flavor, create a paypal link
    // or use the google pay
    private void addButtonListeners() {
        Button button_done = (Button) findViewById(R.id.button_done);
        button_done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InfoActivity.this.finish();
            }
        });
    }
}
