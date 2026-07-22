package eu.postgresql.android.conferencescanner.params;

import android.content.Context;
import android.content.SharedPreferences;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
                final JSONArray paramdata = new JSONArray(s);
                ArrayList<ConferenceEntry> entries = new ArrayList<ConferenceEntry>();

                for (int i = 0 ; i < paramdata.length(); i++) {
                    ConferenceEntry e = new ConferenceEntry();

                    final JSONObject confobj = paramdata.getJSONObject(i);
                    e.confname = confobj.optString("confname", null);
                    e.baseurl = confobj.optString("baseurl", null);
                    e.scantype = confobj.has("scantype") ? ScanType.valueOf(confobj.getString("scantype")) : null;
                    e.fieldname = confobj.optString("fieldname", null);
                    e.sponsorname = confobj.optString("sponsorname", null);
                    e.startdate = confobj.optString("startdate", null);

                    if (e.confname == null || e.baseurl == null || e.scantype == null) {
                        Log.w("conferencescanner", String.format("Invalid values in conference %s, removing", e.confname));
                    }
                    else if (e.scantype == ScanType.CHECKINFIELD && e.fieldname == null) {
                        Log.w("conferencescanner", String.format("Mandatory fieldname missing for conference %s, removing", e.confname));
                    }
                    else if (e.scantype == ScanType.SPONSORBADGE && e.sponsorname == null) {
                        Log.w("conferencescanner", String.format("Mandatory fieldname missing for sponsor at conference %s, removing", e.confname));
                    }
                    else {
                        entries.add(e);
                    }
                }

                Collections.sort(entries);
                return entries;
            }
            catch (Exception e) {
                Log.e("Failed to load conferences: %s", e.toString());
                return new ArrayList<>();
            }
        }
    }

    public static void SaveConferences(Context ctx, ArrayList<ConferenceEntry> conferences) {
        JSONArray paramdata = new JSONArray();

        for (ConferenceEntry conference : conferences ) {
            try {
                JSONObject e = new JSONObject();
                e.put("confname", conference.confname);
                e.put("baseurl", conference.baseurl);
                e.put("scantype", conference.scantype.toString());
                e.put("fieldname", conference.fieldname);
                e.put("sponsorname", conference.sponsorname);
                e.put("startdate", conference.startdate);
                paramdata.put(e);
            }
            catch (JSONException e) {
                Log.w("conferencescanner", String.format("Could not serialize json for %s, removing.", conference.confname));
            }
        }

        SharedPreferences pref = ctx.getSharedPreferences("conferences", MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString("confs", paramdata.toString());
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
