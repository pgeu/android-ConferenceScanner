package eu.postgresql.android.conferencescanner;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AttendeeCheckinActivity extends AppCompatActivity {

    private JSONObject reg;
    private boolean alreadyCheckedIn = false;
    private int regid;

    private class CheckinParam {
        final String name;
        final String value;
        final boolean alertonnot;

        private CheckinParam(String name, String value) {
            this.name = name;
            this.value = value;
            this.alertonnot = false;
        }

        private CheckinParam(String name, String value, boolean alertonnot) {
            this.name = name;
            this.value = value;
            this.alertonnot = alertonnot;
        }
    }

    private ArrayList<CheckinParam> params;
    private Button btnCancel;
    private Button btnCheckin;
    private EditText editNotes;
    private TextView txtNotesHeader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendee_checkin);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        ListView lvCheckin = findViewById(R.id.lv_checkin);
        btnCancel = findViewById(R.id.btn_Cancel);
        btnCheckin = findViewById(R.id.btn_CheckIn);
        editNotes = findViewById(R.id.edit_notes);
        txtNotesHeader = findViewById(R.id.txt_notesHeader);

        btnCancel.setOnClickListener(view -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        params = new ArrayList<>();
        if (getIntent().getBooleanExtra("ischeckin", true)) {
            SetupForCheckin();
        } else {
            SetupForSponsor();
        }

        lvCheckin.setAdapter(new CheckinAdapter(this, params));
    }

    private void SetupForCheckin() {
        try {
            reg = new JSONObject(getIntent().getStringExtra("reg"));

            regid = reg.getInt("id");

            params.add(new CheckinParam("Name", reg.getString("name")));
            params.add(new CheckinParam("Registration type", reg.getString("type")));
            if (reg.has("photoconsent"))
                params.add(new CheckinParam("Photo consent", reg.getString("photoconsent"), true));
            if (reg.has("policyconfirmed"))
                params.add(new CheckinParam("Policy confirmed", reg.getString("policyconfirmed"), true));
            if (reg.has("tshirt") && !reg.isNull("tshirt"))
                params.add(new CheckinParam("T-Shirt size", reg.getString("tshirt")));
            if (reg.has("company") && !reg.isNull("company") && !reg.getString("company").isEmpty())
                params.add(new CheckinParam("Company", reg.getString("company")));
            if (reg.has("partition"))
                params.add(new CheckinParam("Queue Partition", reg.getString("partition")));
            if (reg.has("additional")) {
                JSONArray additional = reg.getJSONArray("additional");
                if (additional.length() > 0) {
                    StringBuilder b = new StringBuilder();
                    for (int i = 0; i < additional.length(); i++) {
                        b.append("\u2022 ");
                        b.append(additional.getString(i));
                        b.append("\n");
                    }
                    params.add(new CheckinParam("Additional options", b.toString()));
                }
            }

            if (reg.has("checkedin")) {
                JSONObject ci = reg.getJSONObject("checkedin");
                alreadyCheckedIn = true;
                params.add(new CheckinParam("Checked in by", ci.getString("by")));
                params.add(new CheckinParam("Checked in at", ci.getString("at")));
            }

        } catch (JSONException e) {
            Log.w("conferencescanner", String.format("Failed to parse returned JSON for checkin: %s", e.toString()));
            FinishWithError("Failed to parse returned JSON");
            return;
        }


        if (getIntent().getBooleanExtra("completed", false)) {
            getSupportActionBar().setTitle("Check-in completed");
            btnCheckin.setVisibility(View.GONE);
            btnCancel.setText("Close");
        } else if (alreadyCheckedIn) {
            getSupportActionBar().setTitle("Already checked in");
            btnCheckin.setVisibility(View.VISIBLE);
            btnCheckin.setEnabled(false);
            btnCancel.setText("Cancel");
        } else {
            getSupportActionBar().setTitle("Check in attendee");
            btnCheckin.setVisibility(View.VISIBLE);
            btnCancel.setText("Cancel");
            btnCheckin.setText("Check in!");
            btnCheckin.setOnClickListener(view -> {
                Intent i = new Intent();
                i.putExtra("token", getIntent().getStringExtra("token"));
                i.putExtra("ischeckin", 1);
                setResult(RESULT_OK, i);
                finish();
            });
        }

        editNotes.setVisibility(View.GONE);
        txtNotesHeader.setVisibility(View.GONE);
    }

    private void SetupForSponsor() {
        try {
            reg = new JSONObject(getIntent().getStringExtra("reg"));

            params.add(new CheckinParam("Name", reg.getString("name")));
            params.add(new CheckinParam("Company", reg.getString("company")));
            params.add(new CheckinParam("Country", reg.getString("country")));
            params.add(new CheckinParam("E-mail", reg.getString("email")));

            editNotes.setText(reg.getString("note"));
        } catch (JSONException e) {
            Log.w("conferencescanner", String.format("Failed to parse returned JSON for sponsor: %s", e.toString()));
            FinishWithError("Failed to parse returned JSON");
            return;
        }

        getSupportActionBar().setTitle("Store attendee details");
        btnCheckin.setVisibility(View.VISIBLE);
        btnCancel.setText("Cancel");
        btnCheckin.setText("Store scan");
        btnCheckin.setOnClickListener(view -> {
            Intent i = new Intent();
            i.putExtra("token", getIntent().getStringExtra("token"));
            i.putExtra("note", editNotes.getText().toString());
            setResult(RESULT_OK, i);
            finish();
        });

        editNotes.setVisibility(View.VISIBLE);
        txtNotesHeader.setVisibility(View.VISIBLE);
    }


    @SuppressWarnings("SameParameterValue")
    private void FinishWithError(String msg) {
        Intent i = new Intent();
        i.putExtra("msg", msg);
        setResult(MainActivity.RESULT_ERROR, i);

        finish();
    }

    private class CheckinAdapter extends ArrayAdapter<CheckinParam> {
        private CheckinAdapter(@NonNull Context context, @NonNull List<CheckinParam> objects) {
            super(context, 0, objects);
        }

        @Override
        @NonNull
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            CheckinParam p = params.get(position);

            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_checkin, parent, false);
            }

            TextView fieldname = convertView.findViewById(R.id.txt_fieldname);
            fieldname.setText(p.name);

            TextView fieldval = convertView.findViewById(R.id.txt_fieldval);
            if (p.value == null)
                fieldval.setText("");
            else {
                fieldval.setText(p.value);
                if (p.alertonnot && p.value.contains(" NOT ")) {
                    fieldval.setBackgroundColor(Color.RED);
                } else {
                    fieldval.setBackgroundColor(Color.TRANSPARENT);
                }
            }

            return convertView;
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }

        return super.onOptionsItemSelected(item);
    }

}
