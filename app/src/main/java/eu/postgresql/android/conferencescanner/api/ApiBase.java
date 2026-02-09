package eu.postgresql.android.conferencescanner.api;

import android.content.Context;

import com.android.volley.AuthFailureError;
import com.android.volley.ClientError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import eu.postgresql.android.conferencescanner.ScanType;
import eu.postgresql.android.conferencescanner.params.ConferenceEntry;

@SuppressWarnings("WeakerAccess")
public abstract class ApiBase {
    private final Context ctx;
    public final String baseurl;

    protected String lasterror = null;
    protected int laststatus = -1;
    protected byte[] lastdata = null;

    public ApiBase(Context ctx, String baseurl) {
        this.ctx = ctx;
        this.baseurl = baseurl;
    }

    public String LastError() {
        if (this.lasterror != null)
            return this.lasterror;
        return "Unknown error";
    }

    public int LastStatus() {
        return this.laststatus;
    }

    public String LastData() {
        return new String(this.lastdata, StandardCharsets.UTF_8);
    }

    protected JSONObject _status = null;
    protected void RefreshStatus() {
        if (_status == null)
            _status = ApiGetJSONObject("api/status/");
    }

    public abstract String FormatConferenceName(JSONObject status) throws JSONException;
    public String GetConferenceName() {
        RefreshStatus();

        if (_status == null)
            return null;

        try {
            return FormatConferenceName(_status);
        } catch (JSONException e) {
            lasterror = "Could not parse JSON contents";
            return null;
        }
    }

    public abstract ScanType GetScanType();


    public abstract boolean CanSearch();

    public abstract String getIntroText(boolean open, ConferenceEntry conf);

    protected JSONObject ApiGetJSONObject(String suburl) {
        try {
            String s = ApiGetString(suburl);
            if (s == null)
                return null;

            return new JSONObject(s);
        } catch (JSONException e) {
            lasterror = "JSON parse exception";
        }
        return null;
    }

    @SuppressWarnings("SameParameterValue")
    protected JSONArray ApiGetJSONArray(String suburl) {
        try {
            String s = ApiGetString(suburl);
            if (s == null)
                return null;

            return new JSONArray(s);
        } catch (JSONException e) {
            lasterror = "JSON parse exception";
        }
        return null;
    }


    protected String ApiGetString(String suburl) {
        return ApiRequestString(Request.Method.GET, suburl, null);
    }

    protected String ApiPostForString(String suburl, Map<String, String> postvals) {
        return ApiRequestString(Request.Method.POST, suburl, postvals);
    }

    protected JSONObject ApiPostForJSONObject(String suburl, Map<String, String> postvals) {
        try {
            String s = ApiRequestString(Request.Method.POST, suburl, postvals);
            if (s == null)
                return null;

            return new JSONObject(s);
        } catch (JSONException e) {
            lasterror = "JSON parse exception";
        }
        return null;
    }

    protected String ApiRequestString(int method, String suburl, Map<String, String> postvals) {
        RequestFuture<String> requestFuture = RequestFuture.newFuture();

        final String url = String.format("%s/%s", this.baseurl, suburl);

        StringRequest request;
        if (method == Request.Method.GET) {
            request = new StringRequest(Request.Method.GET, url, requestFuture, requestFuture);
        } else {
            request = new StringRequest(Request.Method.POST, url, requestFuture, requestFuture) {
                @Override
                public String getBodyContentType() {
                    return "application/x-www-form-urlencoded";
                }

                @Override
                protected Map<String, String> getParams() {
                    return postvals;
                }
            };
        }

        RequestQueue queue = Volley.newRequestQueue(this.ctx);
        queue.add(request);

        try {
            laststatus = -1;
            lastdata = null;
            String s = requestFuture.get(10, TimeUnit.SECONDS);
            laststatus = 200;
            return s;
        } catch (InterruptedException e) {
            lasterror = "Network call interrupted";
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ClientError) {
                ClientError error = (ClientError) e.getCause();
                laststatus = error.networkResponse.statusCode;
                lasterror = String.format("STATUS %d", laststatus);
                lastdata = error.networkResponse.data;
            } else if (e.getCause() instanceof AuthFailureError) {
                AuthFailureError error = (AuthFailureError) e.getCause();
                laststatus = error.networkResponse.statusCode;
                lasterror = "AUTH";
                lastdata = error.networkResponse.data;
            } else
                lasterror = String.format("Network execution error: %s", e.toString());
        } catch (TimeoutException e) {
            lasterror = "Network timeout";
        }
        return null;
    }

    protected String urlencode(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    public JSONObject Lookup(String qrcode) {
        return ApiGetJSONObject(String.format("api/lookup/?lookup=%s", urlencode(qrcode)));
    }

    public JSONObject Search(String searchterm) {
        return ApiGetJSONObject(String.format("api/search/?search=%s", urlencode(searchterm)));
    }

    public class OpenAndAdmin {
        public final boolean open;
        public final boolean admin;

        public OpenAndAdmin(boolean open, boolean admin) {
            this.open = open;
            this.admin = admin;
        }
    }

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
}
