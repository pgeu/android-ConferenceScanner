package eu.postgresql.android.conferencescanner.api;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

public class SponsorApi extends ApiBase {
    public SponsorApi(Context ctx, String baseurl) {
        super(ctx, baseurl);
    }

    @Override
    public String GetConferenceName() {
        JSONObject status = ApiGetJSONObject("api/?status=1");
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
        return new OpenAndAdmin(true, false);
    }

    @Override
    public boolean IsCheckin() {
        return false;
    }

    @Override
    public JSONObject Lookup(String qrcode) {
        return ApiGetJSONObject(String.format("api/?token=%s", urlencode(qrcode)));
    }

    public boolean StoreScan(String token, String note) {
        HashMap<String, String> params = new HashMap<>();
        params.put("token", token);
        params.put("note", note);
        JSONObject store = ApiPostForJSONObject("api/", params);
        return (store != null);
    }
}
