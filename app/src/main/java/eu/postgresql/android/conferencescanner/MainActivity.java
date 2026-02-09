package eu.postgresql.android.conferencescanner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;

import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Log;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.appcompat.app.ActionBarDrawerToggle;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.ListenableFuture;

import androidx.drawerlayout.widget.DrawerLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.postgresql.android.conferencescanner.api.ApiBase;
import eu.postgresql.android.conferencescanner.api.CheckinApi;
import eu.postgresql.android.conferencescanner.api.CheckinFieldApi;
import eu.postgresql.android.conferencescanner.api.SponsorApi;
import eu.postgresql.android.conferencescanner.params.ParamManager;
import eu.postgresql.android.conferencescanner.params.ConferenceEntry;


public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, QRAnalyzer.QRNotificationReceiver {

    private static final int PERMSSION_REQUEST_CAMERA = 17;

    private ProgressBar progressBar;
    private NavigationView navigationView;
    private TextView txtintro;
    private PreviewView viewfinder;
    private Button scanbutton;
    private Button searchbutton;
    private ImageView settingsbutton;
    private boolean cameraActive = false;

    private ArrayList<ConferenceEntry> conferences;
    private ConferenceEntry currentConference = null;
    private final int MENU_FIRST_CONFERENCE = 100000;

    private static final int INTENT_RESULT_LIST_UPDATED = 1;
    private static final int INTENT_RESULT_CHECKED_IN = 2;

    public static final int RESULT_ERROR = -2;
    private Menu optionsMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        conferences = ParamManager.LoadConferences(this);
        String lastbase = ParamManager.LoadLastConference(this);
        if (lastbase != null) {
            for (int i = 0; i < conferences.size(); i++) {
                if (conferences.get(i).baseurl.equals(lastbase)) {
                    currentConference = conferences.get(i);
                    break;
                }
            }
        }

        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);
        progressBar = findViewById(R.id.progress);
        progressBar.setVisibility(View.INVISIBLE);

        /* Set up our own views */
        txtintro = findViewById(R.id.txt_intro);
        viewfinder = findViewById(R.id.view_finder);
        scanbutton = findViewById(R.id.scanbutton);
        searchbutton = findViewById(R.id.searchbutton);
        settingsbutton = navigationView.getHeaderView(0).findViewById(R.id.header_preferences);

        scanbutton.setOnClickListener(view -> {
            if (!cameraActive) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.CAMERA},
                            PERMSSION_REQUEST_CAMERA);
                    return;
                }

                StartCamera();
            } else {
                StopCamera();
            }
        });

        searchbutton.setOnClickListener(view -> {
            if (cameraActive)
                StopCamera();
            DoSearchAttendee();
        });

        settingsbutton.setClickable(true);
        settingsbutton.setOnClickListener(view -> {
            Intent intent = new Intent(this, GlobalSettingsActivity.class);
            startActivity(intent);
        });

        Intent intent = getIntent();
        if (intent != null && intent.getDataString() != null) {
            onNewIntent(intent);
        } else {
            UpdateNavigationView();
            UpdateMainView();
        }

        if (savedInstanceState != null) {
            if (savedInstanceState.getBoolean("cameraActive", false)) {
                /*
                 * For some reason the camera can't be immediately started after a rotation change, so delay the start
                 * by 100ms. Given the "jump" in the rotation change anyway this should not be noticeable, but fixes
                 * the annoying need to restart the camera.
                 */
                final Handler handler = new Handler();
                handler.postDelayed(() -> {
                    StartCamera();
                }, 100);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("cameraActive", cameraActive);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);

        if (intent.getDataString() == null)
            return;

        currentConference = null;
        UpdateMainView(); /* Disable all buttons etc */

        /* Called with an URL. Let's see if the conf is already registered */
        String url = _clean_conference_url(intent.getDataString());
        for (int i = 0; i < conferences.size(); i++) {
            if (url.equals(conferences.get(i).baseurl)) {
                StopCamera();
                currentConference = conferences.get(i);
                ParamManager.SaveLastConference(this, currentConference.baseurl);
                UpdateSelectedConference();
                UpdateMainView();

                return;
            }
        }

        /* Existing conference not found, so add it */
        AddNewConference(url);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMSSION_REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                /* Permission granted for camera, so start up the scanning */
                StartCamera();
            } else {
                ErrorBox("Camera permissions required", "As this app deals with scanning barcodes, it requires access to the camera to be able to provide any functionality at all. Please try again, and this time grant the camera permissions.");
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (currentConference != null) {
            switch (currentConference.scantype) {
            case CHECKIN:
                getMenuInflater().inflate(R.menu.checkin, menu);
                break;
            case SPONSORBADGE:
                getMenuInflater().inflate(R.menu.sponsor, menu);
                break;
            case CHECKINFIELD:
                getMenuInflater().inflate(R.menu.checkinfield, menu);
                break;
            }
        }
        optionsMenu = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_statistics) {
            ShowStatistics();
        }

        return super.onOptionsItemSelected(item);
    }

    private class aShowStatistics extends AsyncTask<Void, Void, JSONArray> {
        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected JSONArray doInBackground(Void... voids) {
            return currentConference.getApi(MainActivity.this).GetStatistics();
        }

        @Override
        protected void onPostExecute(JSONArray data) {
            progressBar.setVisibility(View.INVISIBLE);

            if (data != null) {
                Intent intent = new Intent(MainActivity.this, CheckinStatsActivity.class);
                intent.putExtra("data", data.toString());
                intent.putExtra("conference", currentConference.GetMenuTitle());
                startActivity(intent);
            } else {
                ErrorBox("Error", "Failed to get checkin statistics");
            }
        }
    }

    private void ShowStatistics() {
        if (currentConference == null) {
            Log.e("conferencescanner", "There is no current conference!");
            return;
        }


        new aShowStatistics().execute();
    }

    private class aUpdateMainView extends AsyncTask<Void, Void, ApiBase.OpenAndAdmin> {
        private ApiBase api;

        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected ApiBase.OpenAndAdmin doInBackground(Void... voids) {
            api = currentConference.getApi(MainActivity.this);
            return api.GetIsOpenAndAdmin();
        }

        @Override
        protected void onPostExecute(ApiBase.OpenAndAdmin data) {
            progressBar.setVisibility(View.INVISIBLE);

            getSupportActionBar().setTitle(String.format("%s - %s", currentConference.GetMenuTitle(), currentConference.getTypeString()));
            if (data == null) {
                ErrorBox("Network error", api.LastError());
                viewfinder.setVisibility(View.INVISIBLE);
                searchbutton.setVisibility(View.GONE);
                scanbutton.setVisibility(View.GONE);
                txtintro.setText("A network error occurred when communicating with the server. Please pick a different conference in the menu on the left.");
            } else {
                viewfinder.setVisibility(View.INVISIBLE);
                scanbutton.setVisibility(View.VISIBLE);
                searchbutton.setVisibility(api.CanSearch() ? View.VISIBLE : View.GONE);
                searchbutton.setEnabled(data.open);
                txtintro.setText(api.getIntroText(data.open, currentConference));
                if (currentConference.scantype == ScanType.CHECKIN && optionsMenu != null) {
                    optionsMenu.findItem(R.id.action_statistics).setEnabled(data.admin);
                }

                /* Each status response includes a list of all conferences we have permissions on. So add any that we don't have already! */
                AddConferencesFrom(data.permissions, data.sitebase);
            }
        }
    }

    private void UpdateMainView() {
        if (currentConference == null) {
            viewfinder.setVisibility(View.INVISIBLE);
            scanbutton.setVisibility(View.INVISIBLE);
            searchbutton.setVisibility(View.INVISIBLE);
            getSupportActionBar().setTitle("Conference Scanner");
            txtintro.setText("No conference is currently selected. Use the menu on the left to add or select a conference!");
            StopCamera(); // To be on the safe side
        } else {
            /* Make a call to see if check-in is actually open here. Thus, async task! */
            new aUpdateMainView().execute();
        }
    }

    private void AddConferencesFrom(JSONObject permissions, String sitebase) {
        if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean("autoadd", false))
            return;

        int added = 0;
        Iterator<String> urlnames = permissions.keys();
        while (urlnames.hasNext()) {
            final String urlname = urlnames.next();

            try {
                final JSONObject confperm = permissions.getJSONObject(urlname);
                final String confname = confperm.getString("name");
                final boolean checkin = confperm.getBoolean("checkin");
                final String startdate = confperm.getString("startdate");
                final JSONArray scannerfields = confperm.has("scannerfields") ? confperm.getJSONArray("scannerfields") : new JSONArray();
                final JSONArray sponsors = confperm.has("sponsors") ? confperm.getJSONArray("sponsors") : new JSONArray();

                if (checkin) {
                    final ConferenceEntry newentry = new ConferenceEntry();
                    newentry.confname = confname;
                    newentry.startdate = startdate;
                    newentry.scantype = ScanType.CHECKIN;
                    newentry.baseurl = String.format("%s/events/%s/checkin/%s/", sitebase, urlname, confperm.getString("token"));
                    if (ConditionalAddConference(newentry))
                        added++;
                }
                for (int i = 0; i < scannerfields.length(); i++) {
                    final ConferenceEntry newentry = new ConferenceEntry();
                    final String fieldname = scannerfields.getString(i);
                    newentry.confname = confname;
                    newentry.startdate = startdate;
                    newentry.scantype = ScanType.CHECKINFIELD;
                    newentry.fieldname = fieldname;
                    newentry.baseurl = String.format("%s/events/%s/checkin/%s/f%s/", sitebase, urlname, confperm.getString("token"), fieldname);

                    if (ConditionalAddConference(newentry))
                        added++;
                }
                for (int i = 0; i < sponsors.length(); i++) {
                    final JSONObject sponsor = sponsors.getJSONObject(i);
                    final ConferenceEntry newentry = new ConferenceEntry();
                    newentry.confname = confname;
                    newentry.startdate = startdate;
                    newentry.scantype = ScanType.SPONSORBADGE;
                    newentry.sponsorname = sponsor.getString("sponsor");
                    newentry.baseurl = String.format("%s/events/sponsor/scanning/%s/", sitebase, sponsor.getString("token"));

                    if (ConditionalAddConference(newentry))
                        added++;
                }
            }
            catch (JSONException je) {
                Log.w("conferencescanner", String.format("JSON parse error for conference %s, ignoring this entry", urlname));
            }
        }

        if (added > 0) {
            Collections.sort(conferences);
            ParamManager.SaveConferences(this, conferences);
            UpdateNavigationView();
        }
    }

    private boolean ConditionalAddConference(ConferenceEntry newentry) {
        /* Loop over all existing entries and see if this one is already registered */
        for (ConferenceEntry entry : conferences) {
            if (newentry.baseurl.equals(entry.baseurl))
                return false;
        }

        /* No match, so add it */
        conferences.add(newentry);
        return true;
    }

    private void StopCamera() {
        if (!cameraActive)
            return;

        try {
            ProcessCameraProvider.getInstance(this).get().unbindAll();
        } catch (ExecutionException e) {
            Log.e("conferencescanner", "Unable to unbind");
        } catch (InterruptedException e) {
            Log.e("conferencescanner", "Interrupted in unbind");
        }
        cameraActive = false;
        scanbutton.setText("Start camera");
        viewfinder.setVisibility(View.INVISIBLE);
    }

    private void StartCamera() {
        if (currentConference == null) {
            Log.e("conferencescanner", "There is no current conference!");
            return;
        }

        cameraActive = true;
        scanbutton.setText("Stop camera");

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(new Runnable() {
                                             @Override
                                             public void run() {
                                                 ProcessCameraProvider cameraProvider = null;
                                                 try {
                                                     cameraProvider = cameraProviderFuture.get();
                                                 } catch (ExecutionException e) {
                                                     return;
                                                 } catch (InterruptedException e) {
                                                     return;
                                                 }

                                                 Preview preview = new Preview.Builder().build();
                                                 preview.setSurfaceProvider(viewfinder.getSurfaceProvider());

                                                 viewfinder.setVisibility(View.VISIBLE);

                                                 ImageAnalysis analysis = new ImageAnalysis.Builder()
                                                         .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                                         .build();
                                                 analysis.setAnalyzer(ContextCompat.getMainExecutor(MainActivity.this), new QRAnalyzer(MainActivity.this));

                                                 CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                                                 cameraProvider.unbindAll();
                                                 cameraProvider.bindToLifecycle(MainActivity.this, cameraSelector, preview, analysis);
                                             }
                                         },
                                        ContextCompat.getMainExecutor(this)
                                    );

    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.itemAdd) {
            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            input.setHint("https://test.com/events/test/checkin/abc123def456/");

            new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog_FullWidthButtons)
                    .setTitle("Enter URL")
                    .setMessage("Paste the full URL for scanning application (this will be an URL that contains a long random set of characters at the end).\n\nNote that in most cases you can also click the link in the email or on the website where you received it, and the conference will automatically be added.")
                    .setView(input)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Add", (dialogInterface, i) -> AddNewConference(input.getText().toString()))
                    .show();
        } else if (id == R.id.itemManage) {
            startActivityForResult(new Intent(this, ListConferencesActivity.class), INTENT_RESULT_LIST_UPDATED);
        } else if (id >= MENU_FIRST_CONFERENCE && id < MENU_FIRST_CONFERENCE + conferences.size()) {
            StopCamera();
            currentConference = conferences.get(id - MENU_FIRST_CONFERENCE);
            UpdateMainView();
            ParamManager.SaveLastConference(this, currentConference.baseurl);
            UpdateSelectedConference();
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == INTENT_RESULT_LIST_UPDATED && resultCode == RESULT_OK) {
            /*
             * We re-load the full list of conferences here, which means we need to re-find our
             * currently selected one. Yes, it's ugly.
             */
            String currentbaseurl = currentConference == null ? null : currentConference.baseurl;
            currentConference = null;

            conferences = ParamManager.LoadConferences(this);

            if (currentbaseurl != null) {
                for (int i = 0; i < conferences.size(); i++) {
                    if (currentbaseurl.equals(conferences.get(i).baseurl)) {
                        currentConference = conferences.get(i);
                        break;
                    }
                }
            }

            UpdateNavigationView();
            if (currentConference == null) {
                /* The conference we selected has dissappeared, so update the checkin view to reflect */
                UpdateMainView();
            }
        } else if (requestCode == INTENT_RESULT_CHECKED_IN) {
            if (resultCode == RESULT_OK) {
                ScanType scantype = (ScanType) data.getSerializableExtra("scantype");
                switch (scantype) {
                case CHECKIN:
                    CompleteAttendeeCheckin(data);
                    break;
                case SPONSORBADGE:
                    CompleteBadgeScan(data);
                    break;
                case CHECKINFIELD:
                    CompleteCheckinField(data);
                    break;
                }
            } else if (resultCode == RESULT_ERROR) {
                ScanCompletedDialog("Error storing data", data.getStringExtra("msg"));
            } else {
                /* Canceled */
                pauseDetection = false;
            }
        }
    }

    private String _clean_conference_url(String url) {
        return url.replaceAll("[/#]+$", "");
    }

    private final Pattern urlpattern = Pattern.compile("^https?://[^/]+/events/[^/]+/(checkin|scanning)/[a-z0-9]+(/f([A-Za-z0-9]+))?$");

    private void AddNewConference(String url) {
        String cleanurl = _clean_conference_url(url);

        StopCamera();

        Matcher m = urlpattern.matcher(cleanurl);
        if (m.matches()) {
            if (m.group(1).equals("checkin")) {
                if (m.group(2) != null) {
                    /* Field scanner */
                    new DoAddConference(new CheckinFieldApi(this, cleanurl)).execute();
                }
                else {
                    /* Regular check-in */
                    new DoAddConference(new CheckinApi(this, cleanurl)).execute();
                }
            } else if (m.group(1).equals("scanning")) {
                /* Sponsor scanner */
                new DoAddConference(new SponsorApi(this, cleanurl)).execute();
            }
        } else {
            Log.w("conferencescanner", String.format("Unmatched URL: %s", cleanurl));
            ErrorBox("Invalid URL", "URL does not look like a check-in or sponsor URL");
        }
    }

    private class DoAddConference extends AsyncTask<Void, Void, Void> {
        private final ApiBase api;
        private boolean skip = false;
        private String confname = null;
        private String fieldname = null;
        private String sponsorname = null;

        private DoAddConference(ApiBase api) {
            this.api = api;
        }

        @Override
        protected void onPreExecute() {
            for (int i = 0; i < conferences.size(); i++) {
                if (conferences.get(i).baseurl.equals(api.baseurl)) {
                    ErrorBox("Conference already added",
                            "Conference is already added.");

                    // Switch to it
                    currentConference = conferences.get(i);
                    UpdateMainView();

                    skip = true;
                    return;
                }
            }

            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected Void doInBackground(Void... Void) {
            if (skip)
                return null;

            confname = api.GetConferenceName();
            if (api.GetScanType() == ScanType.CHECKINFIELD) {
                fieldname = ((CheckinFieldApi) api).GetFieldName();
            }
            else if (api.GetScanType() == ScanType.SPONSORBADGE) {
                sponsorname = ((SponsorApi) api).GetSponsorName();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void success) {
            progressBar.setVisibility(View.INVISIBLE);

            if (confname == null) {
                ErrorBox("Could not get conference name",
                        String.format("Failed to get the name of the conference:\n%s", api.LastError()));
                currentConference = null;
                UpdateNavigationView();
                UpdateMainView();
                return;
            }

            ConferenceEntry r = new ConferenceEntry();
            r.baseurl = api.baseurl;
            r.confname = confname;
            r.scantype = api.GetScanType();
            if (r.scantype == ScanType.CHECKINFIELD) {
                r.fieldname = fieldname;
            }
            else if (r.scantype == ScanType.SPONSORBADGE) {
                r.sponsorname = sponsorname;
            }
            conferences.add(0, r); // Always insert at the top of the list!
            ParamManager.SaveConferences(MainActivity.this, conferences);

            UpdateNavigationView();

            // Switch to the newly picked one
            currentConference = r;
            ParamManager.SaveLastConference(MainActivity.this, currentConference.baseurl);
            UpdateMainView();
        }
    }


    private void ErrorBox(String title, String msg) {
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog_FullWidthButtons)
                .setTitle(title)
                .setMessage(msg)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("OK", null)
                .show();

    }

    private void UpdateNavigationView() {
        Menu mainMenu = navigationView.getMenu();
        SubMenu checkinMenu = mainMenu.findItem(R.id.itemCheckin).getSubMenu();

        checkinMenu.clear();

        for (int i = 0; i < conferences.size(); i++) {
            checkinMenu.add(0, MENU_FIRST_CONFERENCE + i, Menu.NONE, conferences.get(i).GetMenuTitle()).setCheckable(true);
        }
        UpdateSelectedConference();
    }

    private void UpdateSelectedConference() {
        Menu mainMenu = navigationView.getMenu();
        int selected_id = -1;
        if (currentConference != null)
            selected_id = MENU_FIRST_CONFERENCE + conferences.indexOf(currentConference);

        _clear_submenu(mainMenu.findItem(R.id.itemCheckin).getSubMenu());

        if (selected_id > 0) {
            MenuItem itm = mainMenu.findItem(selected_id);
            if (itm != null)
                itm.setChecked(true);
        }

        invalidateOptionsMenu();
    }

    private void _clear_submenu(SubMenu sm) {
        for (int i = 0; i < sm.size(); i++) {
            sm.getItem(i).setChecked(false);
        }
    }


    private boolean pauseDetection = false;

    private void ScanCompletedDialog(String title, String msg) {
        pauseDetection = true;
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog_FullWidthButtons)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("OK", (dialogInterface, i) -> pauseDetection = false)
                .show();
    }

    private class aHandleScannedCode extends AsyncTask<Void, Void, JSONObject> {
        private final ApiBase api;
        private final String qrstring;

        private aHandleScannedCode(String qrstring) {
            this.qrstring = qrstring;
            api = currentConference.getApi(MainActivity.this);
        }

        @Override
        protected void onPreExecute() {
            pauseDetection = true;
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected JSONObject doInBackground(Void... voids) {
            return api.Lookup(qrstring);
        }

        @Override
        protected void onPostExecute(JSONObject data) {
            progressBar.setVisibility(View.INVISIBLE);
            if (data == null) {
                /* Is it a 404? */
                if (api.LastStatus() == 404) {
                    ScanCompletedDialog("Attendee not found", "The scanned code does not appear to be a valid attendee of this conference.");
                } else if (api.LastStatus() == 412) {
                    ScanCompletedDialog("Not ready for scan", api.LastData());
                } else if (api.LastStatus() == 403) {
                    ScanCompletedDialog("Scanning failed", api.LastData());
                } else {
                    ScanCompletedDialog("Network error", api.LastError());
                }
                return;
            }

            try {
                Intent intent = new Intent(MainActivity.this, AttendeeCheckinActivity.class);
                intent.putExtra("scantype", currentConference.scantype);
                intent.putExtra("fieldname", currentConference.fieldname);
                intent.putExtra("token", qrstring);

                JSONObject reg = data.getJSONObject("reg");
                intent.putExtra("reg", reg.toString());

                startActivityForResult(intent, INTENT_RESULT_CHECKED_IN);
            } catch (JSONException e) {
                ScanCompletedDialog("Bad data format", "The data returned from the server was badly formatted");
            }
        }
    }

    @Override
    public void OnQRCodeFound(String qrstring) {
        final String testCheckin = "ID$TESTTESTTESTTEST$ID";
        final String testBadge = "AT$TESTTESTTESTTEST$AT";

        if (pauseDetection)
            return;

        if (currentConference == null)
            // Should never happen, but just in case
            return;

        Pattern tokenRe = null;
        try {
            tokenRe = currentConference.getTokenRegexp();
        } catch (MalformedURLException e) {
            /* Should never happen since it's already been validated, so just return if it does */
            return;
        }

        Matcher tokenMatcher = tokenRe.matcher(qrstring);
        if (tokenMatcher.matches()) {
            if (tokenMatcher.group(2).equals("TESTTESTTESTTEST")) {
                ScanCompletedDialog("Test code scanned", String.format("You hace successfully scanned a test code!"));
            }
            else {
                /* Not a test code, so a real one then */
                String tokentype = tokenMatcher.group(1);
                if (tokentype.equals(currentConference.expectedTokenType())) {
                    new aHandleScannedCode(qrstring).execute();
                }
                else {
                    ScanCompletedDialog(String.format("%s scanned", TokenType.tokenIsFrom(tokentype)),
                                        String.format("You have scanned a %s. For %s, you must scan the %s, not the %s.",
                                                      TokenType.tokenIsFrom(tokentype).toLowerCase(),
                                                      currentConference.getTypeString().toLowerCase(),
                                                      TokenType.tokenIsFrom(currentConference.expectedTokenType()).toLowerCase(),
                                                      TokenType.tokenIsFrom(tokentype).toLowerCase()
                                                      ));
                }
            }
        }
        else {
            ScanCompletedDialog("Unknown code scanned",
                                "You have scanned a code is not recognized by this system");
        }

    }

    private class aDoCheckin extends AsyncTask<Void, Void, JSONObject> {
        private final CheckinApi api;
        private final String token;

        private aDoCheckin(String token) {
            this.token = token;
            api = (CheckinApi) currentConference.getApi(MainActivity.this);
        }

        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected JSONObject doInBackground(Void... voids) {
            return api.PerformCheckin(token);
        }

        @Override
        protected void onPostExecute(JSONObject data) {
            progressBar.setVisibility(View.INVISIBLE);

            if (data != null) {
                Toast.makeText(MainActivity.this, "Attendee successfully checked in", Toast.LENGTH_LONG).show();

                try {
                    JSONObject reg = data.getJSONObject("reg");

                    Intent intent = new Intent(MainActivity.this, AttendeeCheckinActivity.class);
                    intent.putExtra("scantype", currentConference.scantype);
                    intent.putExtra("fieldname", currentConference.fieldname);
                    intent.putExtra("reg", reg.toString());
                    intent.putExtra("completed", true);
                    startActivityForResult(intent, INTENT_RESULT_CHECKED_IN);
                } catch (JSONException e) {
                    ScanCompletedDialog("Bad data format", "The data returned from the server was badly formatted");
                }
            } else {
                /* Ugh, something is wrong */
                if (api.LastStatus() == 412) {
                    /* We know how to handle this! */
                    ScanCompletedDialog("Error checking in", api.LastData());
                } else {
                    ScanCompletedDialog("Network error", api.LastError());
                }
            }
        }
    }

    private void CompleteAttendeeCheckin(Intent data) {
        /* The current attendee needs to be checked in */
        if (currentConference == null) {
            /* Should never happen */
            pauseDetection = false;
            return;
        }

        new aDoCheckin(data.getStringExtra("token")).execute();
    }

    private class DoSearchAttendee extends AsyncTask<Void, Void, JSONObject> {
        private final String searchterm;
        private final CheckinApi api;

        private DoSearchAttendee(String searchterm) {
            this.searchterm = searchterm;
            this.api = (CheckinApi) currentConference.getApi(MainActivity.this);
        }

        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected JSONObject doInBackground(Void... voids) {
            return api.Search(searchterm);
        }

        @Override
        protected void onPostExecute(JSONObject data) {
            progressBar.setVisibility(View.INVISIBLE);

            if (data == null) {
                if (api.LastStatus() == 412) {
                    ScanCompletedDialog("Not ready for scan", api.LastData());
                } else {
                    ScanCompletedDialog("Network error", api.LastError());
                }
                return;
            }

            /* If a single entry is returned, then proceed as if it was a direct scan */
            try {
                JSONArray regs = data.getJSONArray("regs");
                if (regs.length() == 0) {
                    ErrorBox("No attendees found", "No attendees matching search found.");
                } else if (regs.length() == 1) {
                    Intent intent = new Intent(MainActivity.this, AttendeeCheckinActivity.class);
                    intent.putExtra("scantype", currentConference.scantype);
                    intent.putExtra("fieldname", currentConference.fieldname);
                    intent.putExtra("token", regs.getJSONObject(0).getString("token"));
                    intent.putExtra("reg", regs.getJSONObject(0).toString());
                    startActivityForResult(intent, INTENT_RESULT_CHECKED_IN);
                } else {
                    String[] regnames = new String[regs.length()];
                    for (int i = 0; i < regs.length(); i++) {
                        regnames[i] = regs.getJSONObject(i).getString("name");
                    }

                    /* Show the list */
                    AlertDialog dlg = new MaterialAlertDialogBuilder(MainActivity.this, R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog_FullWidthButtons)
                            .setTitle("Select attendee")
                            .setItems(regnames, (dialogInterface, i) -> {
                                try {
                                    Intent intent = new Intent(MainActivity.this, AttendeeCheckinActivity.class);
                                    intent.putExtra("scantype", currentConference.scantype);
                                    intent.putExtra("fieldname", currentConference.fieldname);
                                    intent.putExtra("token", regs.getJSONObject(i).getString("token"));
                                    intent.putExtra("reg", regs.getJSONObject(i).toString());
                                    startActivityForResult(intent, INTENT_RESULT_CHECKED_IN);
                                }
                                catch (JSONException e) {
                                    ErrorBox("API Error", "JSON Parser Error on API response");
                                }
                            })
                            .create();
                    dlg.show();
                }
            }
            catch (JSONException e) {
                ErrorBox("API error", "JSON Parser Error on API response");
            }
        }
    }

    private void DoSearchAttendee() {
        if (currentConference == null) {
            Log.e("conferencescanner", "There is no current conference!");
            return;
        }

        final EditText input = new EditText(this);
        input.setSingleLine();
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new  FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = (int)(20 * this.getResources().getDisplayMetrics().density);
        params.rightMargin = (int)(20 * this.getResources().getDisplayMetrics().density);
        input.setLayoutParams(params);
        container.addView(input);

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog_FullWidthButtons)
                .setTitle("Search attendee")
                .setMessage("Enter part of attendee name")
                .setView(container)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Search", (dialogInterface, i) -> {
                    new DoSearchAttendee(input.getText().toString()).execute();
                })
                .show();
    }

    private class aDoSponsorScan extends AsyncTask<Void, Void, Boolean> {
        private final String token;
        private final String note;
        private final SponsorApi api;

        private aDoSponsorScan(String token, String note) {
            this.token = token;
            this.note = note;
            api = (SponsorApi) currentConference.getApi(MainActivity.this);
        }

        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            return api.StoreScan(token, note);
        }

        @Override
        protected void onPostExecute(Boolean success) {
            progressBar.setVisibility(View.INVISIBLE);

            if (success) {
                ScanCompletedDialog("Attendee scanned", "The attendee scan and note has been stored.");
            } else {
                if (api.LastStatus() == 403 || api.LastStatus() == 404) {
                    ScanCompletedDialog("Scanning failed", api.LastData());
                } else {
                    ScanCompletedDialog("Network error", api.LastError());
                }
            }
        }
    }

    private void CompleteBadgeScan(Intent data) {
        if (currentConference == null) {
            /* Should never happen */
            pauseDetection = false;
            return;
        }

        new aDoSponsorScan(data.getStringExtra("token"), data.getStringExtra("note")).execute();
    }

    private class aDoCheckinField extends AsyncTask<Void, Void, Boolean> {
        private final CheckinFieldApi api;
        private final String token;
        private final String fieldname;

        private aDoCheckinField(String token, String fieldname) {
            this.token = token;
            this.fieldname = fieldname;
            api = (CheckinFieldApi) currentConference.getApi(MainActivity.this);
        }

        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            return api.PerformFieldCheckin(token);
        }

        @Override
        protected void onPostExecute(Boolean success) {
            progressBar.setVisibility(View.INVISIBLE);

            if (success) {
                ScanCompletedDialog("Badge scanned", String.format("The attendee field %s has been marked.", fieldname));
            } else {
                if (api.LastStatus() == 403 || api.LastStatus() == 404) {
                    ScanCompletedDialog("Scanning failed", api.LastData());
                } else {
                    ScanCompletedDialog("Network error", api.LastError());
                }
            }
        }
    }

    private void CompleteCheckinField(Intent data) {
        /* The current attendee needs to be checked in */
        if (currentConference == null) {
            /* Should never happen */
            pauseDetection = false;
            return;
        }

        new aDoCheckinField(data.getStringExtra("token"), currentConference.fieldname).execute();
    }

}
