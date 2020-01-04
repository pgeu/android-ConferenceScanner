package eu.postgresql.android.conferencescanner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;

import android.os.Handler;
import android.text.InputType;
import android.view.SubMenu;
import android.view.TextureView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.appcompat.app.ActionBarDrawerToggle;

import android.view.MenuItem;

import com.google.android.material.navigation.NavigationView;

import androidx.drawerlayout.widget.DrawerLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.view.Menu;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.postgresql.android.conferencescanner.api.ApiBase;
import eu.postgresql.android.conferencescanner.api.CheckinApi;
import eu.postgresql.android.conferencescanner.api.SponsorApi;
import eu.postgresql.android.conferencescanner.params.ParamManager;
import eu.postgresql.android.conferencescanner.params.ConferenceEntry;


public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, QRAnalyzer.QRNotificationReceiver {

    private static final int PERMSSION_REQUEST_CAMERA = 17;

    private ProgressBar progressBar;
    private NavigationView navigationView;
    private TextView txtintro;
    private TextureView viewfinder;
    private Button scanbutton;
    private Button searchbutton;
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

        currentConference = null;

        if (intent.getDataString() == null)
            return;

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
            if (currentConference.ischeckin) {
                getMenuInflater().inflate(R.menu.checkin, menu);
            } else {
                getMenuInflater().inflate(R.menu.sponsor, menu);
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
            CheckinApi api = (CheckinApi) currentConference.getApi(MainActivity.this);
            return api.GetStatistics();
        }

        @Override
        protected void onPostExecute(JSONArray data) {
            progressBar.setVisibility(View.INVISIBLE);

            if (data != null) {
                Intent intent = new Intent(MainActivity.this, CheckinStatsActivity.class);
                intent.putExtra("data", data.toString());
                startActivity(intent);
            } else {
                ErrorBox("Error", "Failed to get checkin statistics");
            }
        }
    }

    private void ShowStatistics() {
        if (currentConference == null)
            return;


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

            getSupportActionBar().setTitle(String.format("%s - %s", currentConference.confname, currentConference.ischeckin ? "checkin attendees" : "badge scanning"));
            if (data == null) {
                ErrorBox("Network error", api.LastError());
                viewfinder.setVisibility(View.INVISIBLE);
                searchbutton.setVisibility(View.GONE);
                scanbutton.setVisibility(View.GONE);
                txtintro.setText("A network error occurred when communicating with the server. Please pick a different conference in the menu on the left.");
            } else {
                viewfinder.setVisibility(View.INVISIBLE);
                scanbutton.setVisibility(View.VISIBLE);
                if (currentConference.ischeckin) {
                    searchbutton.setVisibility(View.VISIBLE);
                    if (data.open) {
                        txtintro.setText(String.format("Ready to check attendees in to %s!\n\nTo scan an attendee, turn on the camera below and focus it on the QR code on the ticket!", currentConference.confname));
                        searchbutton.setEnabled(true);
                    } else {
                        txtintro.setText("Check-in processing is not currently open for this conference.");
                        searchbutton.setEnabled(false);
                    }
                    if (optionsMenu != null) {
                        optionsMenu.findItem(R.id.action_statistics).setEnabled(data.admin);
                    }
                } else {
                    searchbutton.setVisibility(View.GONE);
                    if (data.open) {
                        txtintro.setText(String.format("Welcome as a sponsor scanner for %s.\n\nTo scan an attendee badge, turn on the camera below and focus it on the QR code on the attendee badge. Once a QR code is detected, the system will proceed automatically.", currentConference.confname));
                    } else {
                        txtintro.setText("Badge scanning is not currently open for this conference .");
                    }
                }
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

    private void StopCamera() {
        if (!cameraActive)
            return;

        CameraX.unbindAll();
        cameraActive = false;
        scanbutton.setText("Start camera");
        viewfinder.setVisibility(View.INVISIBLE);
    }

    private void StartCamera() {
        cameraActive = true;
        scanbutton.setText("Stop camera");

        PreviewConfig config = (new PreviewConfig.Builder())
                .setLensFacing(CameraX.LensFacing.BACK)
                .build();
        Preview preview = new Preview(config);

        viewfinder.setVisibility(View.VISIBLE);

        preview.setOnPreviewOutputUpdateListener(output -> {
            ViewGroup parent = (ViewGroup) viewfinder.getParent();
            parent.removeView(viewfinder);
            parent.addView(viewfinder, 0);
            viewfinder.setSurfaceTexture(output.getSurfaceTexture());

        });

        ImageAnalysisConfig analysisConfig = (new ImageAnalysisConfig.Builder()).build();
        ImageAnalysis analysis = new ImageAnalysis(analysisConfig);
        analysis.setAnalyzer(ContextCompat.getMainExecutor(this), new QRAnalyzer(this));

        CameraX.bindToLifecycle(this, preview);
        CameraX.bindToLifecycle(this, analysis);
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
            input.setHint("https://test.com/events/test/checking/abc123def456/");

            new AlertDialog.Builder(this)
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
                if (data.hasExtra("regid"))
                    CompleteAttendeeCheckin(data);
                else
                    CompleteBadgeScan(data);
            } else if (resultCode == RESULT_ERROR) {
                ScanCompletedDialog("Error checking in", data.getStringExtra("msg"));
            } else {
                /* Canceled */
                pauseDetection = false;
            }
        }
    }

    private String _clean_conference_url(String url) {
        return url.replaceAll("[/#]+$", "");
    }

    private final Pattern urlpattern = Pattern.compile("^https?://[^/]+/events/[^/]+/(checkin|scanning)/.*");

    private void AddNewConference(String url) {
        String cleanurl = _clean_conference_url(url);

        StopCamera();

        Matcher m = urlpattern.matcher(cleanurl);
        if (m.matches()) {
            if (m.group(1).equals("checkin")) {
                new DoAddConference(new CheckinApi(this, cleanurl)).execute();
            } else if (m.group(1).equals("scanning")) {
                new DoAddConference(new SponsorApi(this, cleanurl)).execute();
            }
        } else {
            ErrorBox("Invalid URL", "URL does not look like a check-in or sponsor URL");
        }
    }

    private class DoAddConference extends AsyncTask<Void, Void, Void> {
        private final ApiBase api;
        private boolean skip = false;
        private String confname = null;

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
            return null;
        }

        @Override
        protected void onPostExecute(Void success) {
            progressBar.setVisibility(View.INVISIBLE);

            if (confname == null) {
                ErrorBox("Could not get conference name",
                        String.format("Failed to get the name of the conference:\n%s", api.LastError()));
                return;
            }

            ConferenceEntry r = new ConferenceEntry();
            r.baseurl = api.baseurl;
            r.confname = confname;
            r.ischeckin = api.IsCheckin();
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
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("OK", null)
                .show();

    }

    private void UpdateNavigationView() {
        Menu mainMenu = navigationView.getMenu();
        SubMenu checkinMenu = mainMenu.findItem(R.id.itemCheckin).getSubMenu();
        SubMenu sponsorMenu = mainMenu.findItem(R.id.itemSponsor).getSubMenu();

        checkinMenu.clear();
        sponsorMenu.clear();

        for (int i = 0; i < conferences.size(); i++) {
            if (conferences.get(i).ischeckin) {
                checkinMenu.add(0, MENU_FIRST_CONFERENCE + i, Menu.NONE, conferences.get(i).confname).setCheckable(true);
            } else {
                sponsorMenu.add(0, MENU_FIRST_CONFERENCE + i, Menu.NONE, conferences.get(i).confname).setCheckable(true);
            }
        }
        UpdateSelectedConference();
    }

    private void UpdateSelectedConference() {
        Menu mainMenu = navigationView.getMenu();
        int selected_id = -1;
        if (currentConference != null)
            selected_id = MENU_FIRST_CONFERENCE + conferences.indexOf(currentConference);

        _clear_submenu(mainMenu.findItem(R.id.itemCheckin).getSubMenu());
        _clear_submenu(mainMenu.findItem(R.id.itemSponsor).getSubMenu());

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
        new AlertDialog.Builder(this)
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
                } else {
                    ScanCompletedDialog("Network error", api.LastError());
                }
                return;
            }

            try {
                Intent intent = new Intent(MainActivity.this, AttendeeCheckinActivity.class);
                intent.putExtra("token", qrstring);
                if (currentConference.ischeckin) {
                    JSONObject reg = data.getJSONObject("reg");

                    intent.putExtra("reg", reg.toString());
                } else {
                    intent.putExtra("spons", data.toString());
                }

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

        if (currentConference.ischeckin) {
            if (qrstring.equals(testCheckin)) {
                ScanCompletedDialog("Test code scanned", "You have successfully scanned the test code for tickets!");
            } else if (qrstring.equals(testBadge)) {
                ScanCompletedDialog("Test code scanned", "You have successfully scanned the test code for badges!");
            } else if (qrstring.startsWith("ID$") && qrstring.endsWith("$ID")) {
                new aHandleScannedCode(qrstring).execute();
            } else if (qrstring.startsWith("AT$") && qrstring.endsWith("$AT")) {
                ScanCompletedDialog("Attendee badge scanned",
                        "You have scanned an attendee badge. For checking an attendee in, you must scan their ticket, not the badge.");
            } else {
                ScanCompletedDialog("Unknown code scanned",
                        "You have scanned a code is not recognized by this system");
            }
        } else {
            if (qrstring.equals(testCheckin)) {
                ScanCompletedDialog("Test code scanned", "You have successfully scanned the test code for tickets!");
            } else if (qrstring.equals(testBadge)) {
                ScanCompletedDialog("Test code scanned", "You have successfully scanned the test code for badges!");
            } else if (qrstring.startsWith("AT$") && qrstring.endsWith("$AT")) {
                new aHandleScannedCode(qrstring).execute();
            } else if (qrstring.startsWith("ID$") && qrstring.endsWith("$ID")) {
                ScanCompletedDialog("Ticket scanned",
                        "You have scanned a ticket. For sponsor scannings, you must scan their badge, not the ticket");
            } else {
                ScanCompletedDialog("Unknown code scanned",
                        "You have scanned a code is not recognized by this system");
            }
        }

    }

    private class aDoCheckin extends AsyncTask<Void, Void, JSONObject> {
        private final CheckinApi api;
        private final int regid;

        private aDoCheckin(int regid) {
            this.regid = regid;
            api = (CheckinApi) currentConference.getApi(MainActivity.this);
        }

        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected JSONObject doInBackground(Void... voids) {
            return api.PerformCheckin(regid);
        }

        @Override
        protected void onPostExecute(JSONObject data) {
            progressBar.setVisibility(View.INVISIBLE);

            if (data != null) {
                Toast.makeText(MainActivity.this, "Attendee successfully checked in", Toast.LENGTH_LONG).show();

                try {
                    JSONObject reg = data.getJSONObject("reg");

                    Intent intent = new Intent(MainActivity.this, AttendeeCheckinActivity.class);
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

        new aDoCheckin(data.getIntExtra("regid", -1)).execute();
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
                    intent.putExtra("reg", regs.getJSONObject(0).toString());
                    startActivityForResult(intent, INTENT_RESULT_CHECKED_IN);
                } else {
                    String[] regnames = new String[regs.length()];
                    for (int i = 0; i < regs.length(); i++) {
                        regnames[i] = regs.getJSONObject(i).getString("name");
                    }

                    /* Show the list */
                    AlertDialog dlg = new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Select attendee")
                            .setItems(regnames, (dialogInterface, i) -> {
                                try {
                                    Intent intent = new Intent(MainActivity.this, AttendeeCheckinActivity.class);
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
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        new AlertDialog.Builder(this)
                .setTitle("Search attendee")
                .setMessage("Enter part of attendee name")
                .setView(input)
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
}
