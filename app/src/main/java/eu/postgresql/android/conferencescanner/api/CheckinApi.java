package eu.postgresql.android.conferencescanner.api;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

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

    @Override
    public boolean IsCheckin() {
        return true;
    }

    public JSONObject PerformCheckin(int id) {
        HashMap<String, String> params = new HashMap<>();
        params.put("reg", String.format("%d", id));
        return ApiPostForJSONObject("api/checkin/", params);
    }

    public JSONArray GetStatistics() {
        return ApiGetJSONArray("api/stats/");
    }
}
