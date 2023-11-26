package eu.postgresql.android.conferencescanner.api;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

import eu.postgresql.android.conferencescanner.ScanType;
import eu.postgresql.android.conferencescanner.params.ConferenceEntry;

public class CheckinApi extends ApiBase {
    public CheckinApi(Context ctx, String baseurl) {
        super(ctx, baseurl);
    }

    @Override
    public String FormatConferenceName(JSONObject status) throws JSONException {
        return status.getString("confname");
    }

    @Override
    public ScanType GetScanType() {
        return ScanType.CHECKIN;
    }

    @Override
    public boolean CanSearch() {
        return true;
    }

    @Override
    public String getIntroText(boolean open, ConferenceEntry conf) {
        if (open) {
            return String.format("Ready to check attendees in to %s!\n\nTo scan an attendee, turn on the camera below and focus it on the QR code on the ticket!", conf.confname);
        }
        else {
            return "Check-in processing is not currently open for this conference.";
        }
    }

    public JSONObject PerformCheckin(String token) {
        HashMap<String, String> params = new HashMap<>();
        params.put("token", token);
        return ApiPostForJSONObject("api/store/", params);
    }

    public JSONArray GetStatistics() {
        return ApiGetJSONArray("api/stats/");
    }
}
