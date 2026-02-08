package eu.postgresql.android.conferencescanner;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import eu.postgresql.android.conferencescanner.params.ParamManager;
import eu.postgresql.android.conferencescanner.params.ConferenceEntry;

public class ListConferencesActivity extends AppCompatActivity {

    private ArrayList<ConferenceEntry> registrations;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_conferences);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);


        registrations = ParamManager.LoadConferences(this);
        if (registrations.size() == 0) {
            Toast.makeText(this, "No configured conferences exist", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        ListView lvConferences = findViewById(R.id.lv_conferences);
        ConferencesAdapter adapter = new ConferencesAdapter(this, registrations);
        lvConferences.setAdapter(adapter);

        getSupportActionBar().setTitle("Edit conferences");

        Button btnDeleteSelected = findViewById(R.id.btn_deleteSelected);
        btnDeleteSelected.setOnClickListener(view -> {
            int num = 0;
            ArrayList<ConferenceEntry> toremove = new ArrayList<>();

            for (int i = 0; i < registrations.size(); i++) {
                if (registrations.get(i).selected) {
                    num++;
                    toremove.add(registrations.get(i));
                }
            }
            if (num == 0) {
                Toast.makeText(ListConferencesActivity.this, "No registrations selected to delete", Toast.LENGTH_LONG).show();
                finish();
            } else {
                new MaterialAlertDialogBuilder(ListConferencesActivity.this, R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog_FullWidthButtons)
                        .setTitle("Are you sure?")
                        .setMessage(String.format("Are you sure you want to remove these %d registrations?", num))
                        .setNegativeButton("No", (dialogInterface, i) -> finish())
                        .setPositiveButton("Yes", (dialogInterface, i) -> {
                            for (i = 0; i < toremove.size(); i++) {
                                registrations.remove(toremove.get(i));
                            }
                            ParamManager.SaveConferences(ListConferencesActivity.this, registrations);
                            setResult(RESULT_OK);
                            finish();
                        })
                        .show();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }

        return super.onOptionsItemSelected(item);
    }

    private class ConferencesAdapter extends ArrayAdapter<ConferenceEntry> {
        private ConferencesAdapter(@NonNull Context context, @NonNull List<ConferenceEntry> objects) {
            super(context, 0, objects);
        }

        @Override
        @NonNull
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            ConferenceEntry r = registrations.get(position);

            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_conference, parent, false);
            }

            TextView confname = convertView.findViewById(R.id.txt_confname);
            confname.setText(r.confname);

            TextView conftype = convertView.findViewById(R.id.txt_conftype);
            conftype.setText(r.getTypeString());

            CheckBox selectcb = convertView.findViewById(R.id.cb_check);
            selectcb.setChecked(r.selected);
            selectcb.setTag(position);
            selectcb.setOnClickListener(view -> {
                ConferenceEntry reg = registrations.get((Integer) view.getTag());
                reg.selected = !reg.selected;
            });

            return convertView;
        }

    }
}
