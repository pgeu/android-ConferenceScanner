package eu.postgresql.android.conferencescanner.params;

import android.content.Context;
import android.content.SharedPreferences;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Iterator;

import static android.content.Context.MODE_PRIVATE;

import eu.postgresql.android.conferencescanner.ScanType;

public class ParamManager {
    public static ArrayList<ConferenceEntry> LoadConferences(Context ctx) {
        SharedPreferences pref = ctx.getSharedPreferences("conferences", MODE_PRIVATE);
        String s = pref.getString("confs", "");
        if (s.isEmpty()) {
            return new ArrayList<>();
        } else {
            try {
                Gson gson = new Gson();
                ArrayList<ConferenceEntry> entries = gson.fromJson(s, new TypeToken<ArrayList<ConferenceEntry>>() {
                    }.getType());

                // Verify that all entries are actually valid and don't have NULLs where
                // we can't deal with them.
                Iterator<ConferenceEntry> itr = entries.iterator();
                while (itr.hasNext()) {
                    ConferenceEntry e = itr.next();
                    if (e.confname == null || e.baseurl == null || e.scantype == null) {
                        Log.w("conferencescanner", String.format("Invalid values in conference %s, removing", e.confname));
                        itr.remove();
                    }
                    if (e.scantype == ScanType.CHECKINFIELD && e.fieldname == null) {
                        Log.w("conferencescanner", String.format("Mandatory fieldname missing for conference %s, removing", e.confname));
                        itr.remove();
                    }
                }

                return entries;
            }
            catch (Exception e) {
                Log.e("Failed to load conferences: %s", e.toString());
                return new ArrayList<>();
            }
        }
    }

    public static void SaveConferences(Context ctx, ArrayList<ConferenceEntry> conferences) {
        SharedPreferences pref = ctx.getSharedPreferences("conferences", MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        Gson gson = new Gson();
        editor.putString("confs", gson.toJson(conferences));
        editor.apply();
    }

    public static String LoadLastConference(Context ctx) {
        SharedPreferences pref = ctx.getSharedPreferences("conferences", MODE_PRIVATE);
        return pref.getString("lastbase", null);
    }

    public static void SaveLastConference(Context ctx, String baseurl) {
        SharedPreferences pref = ctx.getSharedPreferences("conferences", MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString("lastbase", baseurl);
        editor.apply();
    }
}
