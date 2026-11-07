package edu.osu.pcv.marslogger;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

/** A preference that displays its full summary, including long setup instructions. */
public class MultilineSummaryPreference extends Preference {
    public MultilineSummaryPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        TextView summary = (TextView) holder.findViewById(android.R.id.summary);
        if (summary != null) {
            // AndroidX preference layouts otherwise limit summaries to 4 or 10 lines.
            summary.setSingleLine(false);
            summary.setMaxLines(Integer.MAX_VALUE);
            summary.setEllipsize(null);
        }
    }
}
