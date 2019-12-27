package eu.postgresql.android.conferencescanner.params;

import android.content.Context;

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
}
