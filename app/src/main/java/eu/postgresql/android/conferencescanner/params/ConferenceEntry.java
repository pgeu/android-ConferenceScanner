package eu.postgresql.android.conferencescanner.params;

import android.content.Context;

import java.net.MalformedURLException;
import java.net.URL;
import java.lang.Comparable;
import java.util.regex.Pattern;

import eu.postgresql.android.conferencescanner.ScanType;
import eu.postgresql.android.conferencescanner.api.ApiBase;
import eu.postgresql.android.conferencescanner.api.CheckinApi;
import eu.postgresql.android.conferencescanner.api.SponsorApi;
import eu.postgresql.android.conferencescanner.api.CheckinFieldApi;

public class ConferenceEntry implements Comparable<ConferenceEntry> {

    public String confname;
    public String baseurl;
    public ScanType scantype;
    public String fieldname;
    public String sponsorname;
    public String startdate;

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
        case SPONSORBADGE: return String.format("Attendee badge scanning for %s", sponsorname);
        case CHECKINFIELD: return String.format("Check-in field scanning of %s", fieldname);
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
        switch (scantype) {
        case CHECKIN: return String.format("%s: check-in", confname);
        case CHECKINFIELD: return String.format("%s: %s", confname, fieldname);
        case SPONSORBADGE: return String.format("%s for %s", confname, sponsorname);
        }
        return null;
    }

    private int compareDateTo(ConferenceEntry other) {
        if (this.startdate == null) {
            if (other.startdate == null) {
                return 0;
            }
            return 1;
        }
        else if (other.startdate == null) {
            return -1;
        }
        else {
            return this.startdate.compareTo(other.startdate);
        }
    }

    @Override
    public int compareTo(ConferenceEntry other) {
        /* Compare dates. If they are equal, compare names */
        final int compdate = this.compareDateTo(other);
        if (compdate == 0) {
            /* Compare names. If they are equal, sort based on type! */
            final int compname = this.confname.compareTo(other.confname);
            if (compname == 0) {
                return this.scantype.compareTo(other.scantype);
            }
            return compname;
        }
        else
            return compdate;
    }
}
