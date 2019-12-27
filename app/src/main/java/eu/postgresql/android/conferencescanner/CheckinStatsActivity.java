package eu.postgresql.android.conferencescanner;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;

public class CheckinStatsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkin_stats);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        JSONArray data = null;
        try {
            data = new JSONArray(getIntent().getStringExtra("data"));
        } catch (JSONException e) {
            finish();
            return;
        }

        ExpandableListView lvStats = findViewById(R.id.lv_stats);
        StatsAdapter adapter = new StatsAdapter(this, data);
        lvStats.setAdapter(adapter);
        lvStats.expandGroup(0);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }

        return super.onOptionsItemSelected(item);
    }

    protected class StatsAdapter extends BaseExpandableListAdapter {
        private final JSONArray data;
        private final Context ctx;

        private static final String UNPARSEABLE_JSON = "Unparseable JSON";

        public StatsAdapter(Context ctx, JSONArray data) {
            this.ctx = ctx;
            this.data = data;
        }

        @Override
        public Object getChild(int listPosition, int expandedListPosition) {
            try {
                return data.getJSONArray(listPosition).getJSONArray(1).getJSONArray(expandedListPosition);
            } catch (JSONException e) {
                return null;
            }
        }

        public long getChildId(int listPosition, int expandedListPosition) {
            return expandedListPosition;
        }

        @Override
        public int getChildrenCount(int listPosition) {
            try {
                return data.getJSONArray(listPosition).getJSONArray(1).length();
            } catch (JSONException e) {
                return 0;
            }
        }

        @Override
        public Object getGroup(int listPosition) {
            try {
                return data.getJSONArray(listPosition).getJSONArray(0);
            } catch (JSONException e) {
                return null;
            }
        }

        @Override
        public int getGroupCount() {
            return data.length();
        }

        @Override
        public long getGroupId(int listPosition) {
            return listPosition;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public boolean isChildSelectable(int listPosition, int expandedListPosition) {
            return true;
        }

        @Override
        public View getChildView(int listPosition, final int expandedListPosition,
                                 boolean isLastChild, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(this.ctx).inflate(R.layout.stats_item, parent, false);
            }


            JSONArray row = (JSONArray) getChild(listPosition, expandedListPosition);
            SetColsFromRow(convertView, row);

            return convertView;
        }

        private void SetColsFromRow(View view, JSONArray row) {
            TextView col1 = view.findViewById(R.id.col1);
            TextView col2 = view.findViewById(R.id.col2);
            TextView col3 = view.findViewById(R.id.col3);

            SetJSONCol(col1, row, 0);
            SetJSONCol(col2, row, 1);
            SetJSONCol(col3, row, 2);

            if (row.isNull(0)) {
                /* First column NULL means that this is a summary row */
                col2.setTypeface(col2.getTypeface(), Typeface.BOLD_ITALIC);
                col3.setTypeface(col3.getTypeface(), Typeface.BOLD_ITALIC);
            }
        }

        private void SetJSONCol(TextView tv, JSONArray row, int idx) {
            if (row.isNull(idx))
                tv.setText("");
            else {
                try {
                    tv.setText(row.getString(idx));
                } catch (JSONException e) {
                    tv.setText(UNPARSEABLE_JSON);
                }
            }
        }

        @Override
        public View getGroupView(int listPosition, boolean isExpanded,
                                 View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(this.ctx).inflate(R.layout.stats_group, parent, false);
            }

            JSONArray row = (JSONArray) getGroup(listPosition);
            if (isExpanded)
                SetColsFromRow(convertView, row);
            else {
                /* For an unexpanded group, only show the first col = headline */
                SetJSONCol(convertView.findViewById(R.id.col1), row, 0);
            }

            return convertView;
        }
    }
}
