package eu.postgresql.android.conferencescanner.api;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

import eu.postgresql.android.conferencescanner.ScanType;
import eu.postgresql.android.conferencescanner.params.ConferenceEntry;

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
    public ScanType GetScanType() {
        return ScanType.SPONSORBADGE;
    }

    @Override
    public boolean CanSearch() {
        return false;
    }

    @Override
    public String getIntroText(boolean open, ConferenceEntry conf) {
        if (open) {
            return String.format("Welcome as a sponsor scanner for %s.\n\nTo scan an attendee badge, turn on the camera below and focus it on the QR code on the attendee badge. Once a QR code is detected, the system will proceed automatically.", conf.confname);
        }
        else {
            return "Badge scanning is not currently open for this conference .";
        }
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
