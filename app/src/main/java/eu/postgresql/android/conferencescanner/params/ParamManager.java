package eu.postgresql.android.conferencescanner.params;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;

import static android.content.Context.MODE_PRIVATE;

public class ParamManager {
    public static ArrayList<ConferenceEntry> LoadConferences(Context ctx) {
        SharedPreferences pref = ctx.getSharedPreferences("conferences", MODE_PRIVATE);
        String s = pref.getString("confs", "");
        if (s.isEmpty()) {
            return new ArrayList<>();
        } else {
            Gson gson = new Gson();
            return gson.fromJson(s, new TypeToken<ArrayList<ConferenceEntry>>() {
            }.getType());
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
