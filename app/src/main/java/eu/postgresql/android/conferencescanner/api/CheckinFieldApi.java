package eu.postgresql.android.conferencescanner.api;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

import eu.postgresql.android.conferencescanner.ScanType;
import eu.postgresql.android.conferencescanner.params.ConferenceEntry;

public class CheckinFieldApi extends ApiBase {
    private String _confname = null;
    private String _fieldname = null;

    public CheckinFieldApi(Context ctx, String baseurl) {
        super(ctx, baseurl);
    }

    @Override
    public String GetConferenceName() {
        if (_confname == null)
            RefreshNames();
        return _confname;
    }

    public String GetFieldName() {
        if (_fieldname == null)
            RefreshNames();
        return _fieldname;
    }

    private void RefreshNames() {
        JSONObject status = ApiGetJSONObject("api/status/");
        if (status == null)
            return;

        try {
            _confname = status.getString("confname");
            _fieldname = status.getString("fieldname");
        } catch (JSONException e) {
            lasterror = "Could not parse JSON contents";
            return;
        }
    }

    @Override
    public OpenAndAdmin GetIsOpenAndAdmin() {
        return new OpenAndAdmin(true, false);
    }

    public JSONObject Lookup(String qrcode) {
        return ApiGetJSONObject(String.format("api/lookup/?lookup=%s", urlencode(qrcode)));
    }

    @Override
    public ScanType GetScanType() {
        return ScanType.CHECKINFIELD;
    }

    @Override
    public boolean CanSearch() {
        return false;
    }

    @Override
    public String getIntroText(boolean open, ConferenceEntry conf) {
        if (open) {
            return String.format("Ready to check in field %s for attendees of %s.\n\nTo scan an attendee, turn on the camera below and focus it on the QR code on the badge!", conf.fieldname, conf.confname);
        }
        else {
            return "Check-in processing is not currently open for this conference.";
        }
    }

    public Boolean PerformFieldCheckin(String token) {
        HashMap<String, String> params = new HashMap<>();
        params.put("token", token);
        JSONObject store = ApiPostForJSONObject("api/store/", params);
        return (store != null);
    }
}
