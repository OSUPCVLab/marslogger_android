package edu.osu.pcv.marslogger;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.Spinner;

// TODO(jhuai): use a toolbar(import android.support.v7.widget.Toolbar) to navigate the activities
// see ch8 p313 of head first Android development a brain friendly guide
public class InfoActivity extends AppCompatActivity {
    private static final String TAG = InfoActivity.class.getName();;
    protected boolean mGoogleEnabled = false;
    protected boolean mPaypalEnabled = true;

    protected boolean mDebug = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);
        mGoogleEnabled = BuildConfig.DONATIONS_GOOGLE;
        mPaypalEnabled = !BuildConfig.DONATIONS_GOOGLE;
        /* Google */
//        if (mGoogleEnabled) {
//
//        }

        /* PayPal */
        if (mPaypalEnabled) {
            ViewStub paypalViewStub = (ViewStub)findViewById(R.id.donations__paypal_stub);
            paypalViewStub.inflate();

            Button btPayPal = (Button)findViewById(R.id.donations__paypal_donate_button);
            btPayPal.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    donatePayPalOnClick(v);
                }
            });
        }
        addButtonListeners();
    }

    private void addButtonListeners() {
        Button button_done = (Button) findViewById(R.id.button_done);
        button_done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InfoActivity.this.finish();
            }
        });
    }

    /**
     * Donate button with PayPal by opening browser with defined URL For possible parameters see:
     * https://developer.paypal.com/webapps/developer/docs/classic/paypal-payments-standard/integration-guide/Appx_websitestandard_htmlvariables/
     */
    public void donatePayPalOnClick(View view) {
        Uri payPalUri = Uri.parse("https://www.paypal.me/jianzhuhuai"); // missing 'http://' will cause crashed
        if (mDebug)
            Log.d(TAG, "Opening the browser with the url: " + payPalUri.toString());

        Intent viewIntent = new Intent(Intent.ACTION_VIEW, payPalUri);
        // force intent chooser, do not automatically use PayPal app
        // https://github.com/PrivacyApps/donations/issues/28
        String title = getResources().getString(R.string.donations__paypal);
        Intent chooser = Intent.createChooser(viewIntent, title);

        if (viewIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(chooser);
        } else {
            openDialog(android.R.drawable.ic_dialog_alert, R.string.donations__alert_dialog_title,
                    getString(R.string.donations__alert_dialog_no_browser));
        }
    }

    /**
     * Open dialog
     */
    void openDialog(int icon, int title, String message) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setIcon(icon);
        dialog.setTitle(title);
        dialog.setMessage(message);
        dialog.setCancelable(true);
        dialog.setNeutralButton(R.string.donations__button_close,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }
        );
        dialog.show();
    }
}
