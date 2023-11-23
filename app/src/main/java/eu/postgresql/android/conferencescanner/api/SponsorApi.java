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
        JSONObject status = ApiGetJSONObject("api/status/");
        if (status == null)
            return null;

        try {
            return String.format("%s for %s", status.getString("confname"), status.getString("sponsorname"));
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
        return ApiGetJSONObject(String.format("api/lookup/?lookup=%s", urlencode(qrcode)));
    }

    public boolean StoreScan(String token, String note) {
        HashMap<String, String> params = new HashMap<>();
        params.put("token", token);
        params.put("note", note);
        JSONObject store = ApiPostForJSONObject("api/store/", params);
        return (store != null);
    }
}
