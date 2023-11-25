package eu.postgresql.android.conferencescanner.api;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

import eu.postgresql.android.conferencescanner.ScanType;

public class CheckinApi extends ApiBase {
    public CheckinApi(Context ctx, String baseurl) {
        super(ctx, baseurl);
    }

    @Override
    public String GetConferenceName() {
        JSONObject status = ApiGetJSONObject("api/status/");
        if (status == null)
            return null;

        try {
            return status.getString("confname");
        } catch (JSONException e) {
            lasterror = "Could not parse JSON contents";
            return null;
        }
    }

    @Override
    public OpenAndAdmin GetIsOpenAndAdmin() {
        JSONObject status = ApiGetJSONObject("api/status/");
        if (status == null)
            return null;

        try {
            return new OpenAndAdmin(status.getBoolean("active"), status.getBoolean("admin"));
        } catch (JSONException e) {
            lasterror = "Could not parse JSON contents";
            return null;
        }
    }

    public JSONObject Lookup(String qrcode) {
        return ApiGetJSONObject(String.format("api/lookup/?lookup=%s", urlencode(qrcode)));
    }

    public JSONObject Search(String searchterm) {
        return ApiGetJSONObject(String.format("api/search/?search=%s", urlencode(searchterm)));
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
    public String getIntroText(boolean open, String confname) {
        if (open) {
            return String.format("Ready to check attendees in to %s!\n\nTo scan an attendee, turn on the camera below and focus it on the QR code on the ticket!", confname);
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
