package eu.postgresql.android.conferencescanner.params;

import android.content.Context;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

import eu.postgresql.android.conferencescanner.api.ApiBase;
import eu.postgresql.android.conferencescanner.api.CheckinApi;
import eu.postgresql.android.conferencescanner.api.SponsorApi;

public class ConferenceEntry {
    public String confname;
    public String baseurl;
    public boolean ischeckin;

    public transient boolean selected;

    public ApiBase getApi(Context ctx) {
        if (ischeckin)
            return new CheckinApi(ctx, baseurl);
        else
            return new SponsorApi(ctx, baseurl);
    }

    public Pattern getTokenRegexp() throws MalformedURLException {
        URL u = new URL(baseurl);
        if (u.getPort() == -1) {
            return Pattern.compile(String.format("^%s://%s/t/(id|at)/([a-z0-9]+|TESTTESTTESTTEST)/$", u.getProtocol(), u.getHost()));
        } else {
            return Pattern.compile(String.format("^%s://%s/t/(id|at)/([a-z0-9]+|TESTTESTTESTTEST)/$", u.getProtocol(), u.getHost(), u.getPort()));
        }
    }
}
