package eu.postgresql.android.conferencescanner.params;

import android.content.Context;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

import eu.postgresql.android.conferencescanner.ScanType;
import eu.postgresql.android.conferencescanner.api.ApiBase;
import eu.postgresql.android.conferencescanner.api.CheckinApi;
import eu.postgresql.android.conferencescanner.api.SponsorApi;
import eu.postgresql.android.conferencescanner.api.CheckinFieldApi;

public class ConferenceEntry {

    public String confname;
    public String baseurl;
    public ScanType scantype;
    public String fieldname;

    public transient boolean selected;

    public ApiBase getApi(Context ctx) {
        if (scantype == null) {
            return null;
        }

        switch (scantype) {
        case CHECKIN: return new CheckinApi(ctx, baseurl);
        case SPONSORBADGE: return new SponsorApi(ctx, baseurl);
        case CHECKINFIELD: return new CheckinFieldApi(ctx, baseurl);
        }
        return null;
    }

    public Pattern getTokenRegexp() throws MalformedURLException {
        URL u = new URL(baseurl);
        if (u.getPort() == -1) {
            return Pattern.compile(String.format("^%s://%s/t/(id|at)/([a-z0-9]+|TESTTESTTESTTEST)/$", u.getProtocol(), u.getHost()));
        } else {
            return Pattern.compile(String.format("^%s://%s/t/(id|at)/([a-z0-9]+|TESTTESTTESTTEST)/$", u.getProtocol(), u.getHost(), u.getPort()));
        }
    }

    public String getTypeString() {
        switch (scantype) {
        case CHECKIN: return "Check-in processing";
        case SPONSORBADGE: return "Attendee badge scanning";
        case CHECKINFIELD: return "Check-in field scanning";
        }
        return null;
    }

    public String expectedTokenType() {
        switch (scantype) {
        case CHECKIN: return "id";
        case SPONSORBADGE: return "at";
        case CHECKINFIELD: return "at";
        }
        return null;
    }

    public String GetMenuTitle() {
        if (scantype == ScanType.CHECKINFIELD) {
            return String.format("%s: %s", confname, fieldname);
        }
        else {
            return confname;
        }
    }
}
