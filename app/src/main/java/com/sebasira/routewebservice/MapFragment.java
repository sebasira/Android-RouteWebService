package com.sebasira.routewebservice;

import android.app.Activity;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.mapquest.android.maps.DefaultItemizedOverlay;
import com.mapquest.android.maps.GeoPoint;
import com.mapquest.android.maps.LineOverlay;
import com.mapquest.android.maps.MapView;
import com.mapquest.android.maps.Overlay;
import com.mapquest.android.maps.OverlayItem;
import com.mapquest.android.maps.RouteResponse;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.ClientProtocolException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Locale;

/**
 * MapFragment
 */
public class MapFragment extends Fragment implements TextToSpeech.OnInitListener {
    // TAG
    private static final String TAG = "MapFragment";

    // Mapquest Map View
    private MapView map;

    // Mapquest API KEY
    private String MAPQUEST_API_KEY;

    // Mapquest Directions Api StatusCode
    private static final String MAPQUEST_STATUS_CODE_OK = "0";

    // Default locations
    private GeoPoint defaultCenterPoint =  new GeoPoint(-32.9476917,-60.6304694);
    private GeoPoint defaultStartPoint =  new GeoPoint(-32.947444,-60.630304);
    private GeoPoint defaultEndPoint =  new GeoPoint(-32.945859,-60.632354);

    // Maneuvers
    private JSONArray maneuvers;
    private int displayingManeuverIndex;
    private int totalMeneuvers;

    // Maneuvers UI
    private TextView tv_maneuver;
    private Button btn_prev;
    private Button btn_next;

    // Maneuvers Overlay
    private DefaultItemizedOverlay maneuverOverlay;

    // Text-to-Speech
    private TextToSpeech tts;

    // Directions API URL query
    private String directions_api_request_url;

/************************************************************************************/
/** LIFE CYLCE **/

	/* ON CREATE */
	/* ********* */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);                // Tells Action Bar that this fragment has its own actions

        // Initialize the TextToSpeech
        tts = new TextToSpeech(getActivity().getApplicationContext(), this);
    }

    /* ON INIT */
	/* ******* */
    /** Include onInit method which signals the completion of the TextToSpeech engine initialization.
     *  Your device will take some small fraction of time to initialize TTS engine, so we are
     *  calling onInit method to make sure that whether it is initialized properly or not.
     *
     * @param status TextToSpeech.SUCCESS or TextToSpeech.ERROR
     */
    @Override
    public void onInit(final int status) {
        // TTS initialization seems to overload the UI Thread. Therefore, it's better to do it
        // on a separate thread.
        // source: http://stackoverflow.com/a/24398365
        new Thread(new Runnable() {
            public void run() {
                String msg;

                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(Locale.ENGLISH);
                    msg = getResources().getString(R.string.tts_init_done);
                    Log.e(TAG, msg);

                } else {
                    tts = null;
                    msg = getResources().getString(R.string.tts_fail);
                    Log.e(TAG, msg);
                    Toast.makeText(getActivity().getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                }
            }
        }).start();
    }


    /* ON CREATE VIEW */
	/* ************** */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.estado_fragment_map, container, false);

        // You Need to create a string resource with id "MQ_apiKey" which contains your
        // mapquest api key
        MAPQUEST_API_KEY = getResources().getString(R.string.MQ_apiKey);

        // Setup the map
        map = (MapView) rootView.findViewById(R.id.map);
        map.getController().setZoom(17);
        map.getController().setCenter(defaultCenterPoint);
        map.setBuiltInZoomControls(true);

        // Adding default Markers Overlays
        Drawable icon = getResources().getDrawable(R.drawable.dot_marker);
        DefaultItemizedOverlay marker_overlay = new DefaultItemizedOverlay(icon);
        marker_overlay.setAlignment(icon, Overlay.CENTER);

        OverlayItem beginMarker = new OverlayItem(defaultStartPoint,"Start Here","The trip will start here");
        marker_overlay.addItem(beginMarker);

        OverlayItem endMarker = new OverlayItem(defaultEndPoint,"Stop Here","The trip will end here");
        marker_overlay.addItem(endMarker);

        map.getOverlays().add(marker_overlay);

        map.invalidate();                           // Re-Draw the map

        // Maneuvers Display and controls
        tv_maneuver = (TextView) rootView.findViewById(R.id.tv_manouver);

        btn_prev = (Button) rootView.findViewById(R.id.btn_prev);
        btn_prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                displayManeuver(displayingManeuverIndex - 1);               // Display de previous maneuver
            }
        });

        btn_next = (Button) rootView.findViewById(R.id.btn_next);
        btn_next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                displayManeuver(displayingManeuverIndex + 1);               // Display de next maneuver
            }
        });

        // Setup Maneuvers Marker Overlay (so we can show marker where the maneuver need to be done)
        Drawable iconFlag = getResources().getDrawable(R.drawable.flag_marker_pink);
        maneuverOverlay = new DefaultItemizedOverlay(iconFlag);
        marker_overlay.setAlignment(iconFlag, Overlay.RIGHT | Overlay.BOTTOM);

        // Request the route (Direction API) from mapquest
        directions_api_request_url = "http://open.mapquestapi.com/directions/v2/route?key=" + MAPQUEST_API_KEY +
                "&callback=renderAdvancedNarrative&outFormat=json&routeType=fastest&timeType=1&enhancedNarrative=false&shapeFormat=raw&generalize=0"+
                "&locale=" + Locale.getDefault() +              // Set query with default locale (for narratives)
                "&unit=m" +
                "&from=" + defaultStartPoint.getLatitudeE6()/1E6 + "," + defaultStartPoint.getLongitudeE6()/1E6 +
                "&to=" + defaultEndPoint.getLatitudeE6()/1E6 + "," + defaultEndPoint.getLongitudeE6()/1E6 +
                "&drivingStyle=2&highwayEfficiency=21.0";
        Log.i(TAG, "DIRECTIONS API URL: " + directions_api_request_url);

        return rootView;
    }


    /* ON RESUME */
    /* ********* */
    @Override
    public void onResume() {
        super.onResume();
    }


    /* ON PAUSE */
    /* ******** */
    @Override
    public void onPause() {
        super.onPause();
    }

    /* ON DESTROY */
	/* ********** */
    @Override
    public void onDestroy() {
        // Possible solution to some leaks
        // http://developer.mapquest.com/web/products/forums/-/message_boards/view_message/744551
        if (null != map){
            map.destroy();
        }

        // Shutdown TTS before destroying the APP
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }

        super.onDestroy();
    }


/************************************************************************************/
/** ACTION MENU **/

    /* ON CREATE OPTIONS MENU */
    /* ********************** */
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_actionbar, menu);
    }

    /* ON OPTIONS ITEM SELECTED */
    /* ************************ */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menu_createRoute:
                new GetRouteTask(getActivity()).execute(directions_api_request_url);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

/************************************************************************************/
/** PRIVATE ROUTINES **/

    /* DISPLAY MANEUVER */
    /* **************** */
    /** This method display the maneuver #idx from the JSONArray of maneuvers.
     * It also modiffy the UI accordingly (enable/disable prev/next buttons)
     *
     * @param idx index of the maneuver to display
     */
    private void displayManeuver (int idx){
        // Before displaying, there must be at least one maneuver, and the index must be lower
        // than the total, and also greater or equal to zero
        if ((totalMeneuvers > 0) && (idx >= 0) && (idx < totalMeneuvers)) {
            String maneuver_narrative = getResources().getString(R.string.no_maneuver_narrative);   // Assume no narrative
            try{
                maneuver_narrative = maneuvers.getJSONObject(idx).optString("narrative");     // Get the "narrative" of the IDX maneuver object
            }catch (JSONException e){
                // TODO Handle problems.
                Log.e(TAG, Log.getStackTraceString(e));
            }
            tv_maneuver.setText(maneuver_narrative);    // Set maneuver text
            displayingManeuverIndex = idx;              // Displaying maneuver index 0

            // Next/Prev Buttons Enable Control
            if (idx == 0){
                btn_prev.setEnabled(false);             // Index = 0 -> No previous
                if (totalMeneuvers == 1){
                    btn_next.setEnabled(false);         // Total = 1 -> No next
                }else{
                    btn_next.setEnabled(true);          // Has others
                }

            }else if (idx == totalMeneuvers - 1){
                btn_prev.setEnabled(true);
                btn_next.setEnabled(false);             // Index = (total - 1) -> No next

            }else{
                btn_next.setEnabled(true);
                btn_prev.setEnabled(true);
            }

            // Maneuver Marker Overlay
            String lat = "";
            String lng = "";
            try{
                JSONObject startPoint =  maneuvers.getJSONObject(idx).getJSONObject("startPoint");
                lat = startPoint.optString("lat");
                lng = startPoint.optString("lng");
            }catch (JSONException e){
                // TODO Handle problems.
                Log.e(TAG, Log.getStackTraceString(e));
            }

            if (null != maneuverOverlay) {
                map.getOverlays().remove(maneuverOverlay);

                if ((!lat.equals("")) && (!lng.equals(""))){
                    GeoPoint geoP = new GeoPoint(Double.parseDouble(lat), Double.parseDouble(lng));
                    OverlayItem maneuverMarker = new OverlayItem(geoP, "", "");
                    maneuverOverlay.addItem(maneuverMarker);

                    map.getOverlays().add(maneuverOverlay);

                    map.postInvalidate();                   // Re-Draw the map
                }
            }

            // Maneuver Speech (TTS)
            speakOut(maneuver_narrative);
        }
    }


    /* SPEAK OUT */
    /* ********* */
    /** Speaks out the text passed as argument
     *
     * @param textToSpeak text to speak out
     */
    private void speakOut (String textToSpeak){
        // Only speak if the TTS was succesfully initialized
        if (null != tts) {
            tts.setSpeechRate((float) 0.85);                    // Set speech rate a bit slower than normal
            tts.setLanguage(Locale.getDefault());               // Set deafualt Locale as Speech Languaje

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null);   // Don't need and utteranceID to track
            } else {
                tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null);
            }
        }else{
            String msg = getResources().getString(R.string.tts_cant_speak);
            Log.e(TAG, msg);
            Toast.makeText(getActivity().getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
        }
    }

/************************************************************************************/
/** ROUTE FROM WEB SERVICE **/

    /** This asynTask will request the route from a MapQuest webService. This is an Open Data API
     * so the Free & Open key could to use to get the route. Using the mapquest android SDK the
     * only way to get the route is having an Enterprise Key.
     * This is a workaround for the Free & Open keys.
     *
     * Idea from here:
     * http://developer.mapquest.com/web/products/forums/-/message_boards/message/1368370
     * and here:
     * http://developer.mapquest.com/widget/web/products/forums/-/message_boards/message/1381991
     */
    private class GetRouteTask extends AsyncTask<String, Void, JSONObject> {
        private ProgressDialog progress_dialog;

        // CONSTRUCTOR
        public GetRouteTask(Activity activity) {
            // Create the progress dialog
            progress_dialog = new ProgressDialog(activity);
        }

        // ON PRE EXECUTE
        protected void onPreExecute() {
            // Show the progress dialog
            progress_dialog.setMessage(getResources().getString(R.string.waiting_route));
            progress_dialog.show();
        }

        // DO IN BACKGROUND
        protected JSONObject doInBackground(String... str_url) {
            URLConnection connection = null;
            InputStream input = null;
            JSONObject jsonResponse = null;

            try {
                URL url = new URL(str_url[0]);

                connection = url.openConnection();

                // This line of code is a security breach , but it is how mapquest uses to refer to a key interprise
                connection.setRequestProperty("Referer", "MY_REFERER");

                input = connection.getInputStream();
                String str = IOUtils.toString(input, "utf-8");

                jsonResponse = new JSONObject(str.replace("renderAdvancedNarrative(", "").replace(")", ""));

            }catch (MalformedURLException e){
                // TODO Handle problems..
                Log.e(TAG, Log.getStackTraceString(e));

            } catch (ClientProtocolException e) {
                // TODO Handle problems..
                Log.e(TAG, Log.getStackTraceString(e));

            } catch (IOException e) {
                // TODO Handle problems..
                Log.e(TAG, Log.getStackTraceString(e));

            } catch (JSONException e) {
                // TODO Handle problems..
                Log.e(TAG, Log.getStackTraceString(e));


            }finally {
                try{
                    input.close();
                } catch (IOException e) {
                    // TODO Handle problems..
                    Log.e(TAG, Log.getStackTraceString(e));
                }
            }

            return jsonResponse;
        }

        // POST EXECUTE
        protected void onPostExecute(JSONObject jsonResponse) {
            // Dismiss the progress dialog
            if (progress_dialog.isShowing()) {
                progress_dialog.dismiss();
            }

            // Beofore processing the response, let's check it has no errors. If no error is
            // present, then "statuscode" (string) inside "info"(object) must be "0"
            String statuscode = "-1";               // Assume Error
            String message = "null";                // Assume No Message
            try{
                JSONObject info = jsonResponse.getJSONObject("info");
                statuscode = info.optString("statuscode");
                message = info.optString("messages");
            } catch (JSONException e) {
                // TODO Handle problems.
                Log.e(TAG, Log.getStackTraceString(e));
            }

            if(statuscode.equals(MAPQUEST_STATUS_CODE_OK)) {
                // Create the RouteResponse from JSON Response, aka from Web Service
                RouteResponse response = new RouteResponse(jsonResponse);

                // Create a RouteManager as John Duncan suggested using your API KEY
                Duncan_RouteManager duncanRouteManager = new Duncan_RouteManager(getActivity().getApplicationContext(), MAPQUEST_API_KEY);

                // Get the route from the response
                LineOverlay routeLine = duncanRouteManager.getRouteOverlay(response);
                map.getOverlays().add(routeLine);
                map.postInvalidate();

                // This response also have "maneuvers" inside "legs", so let's extract them
                getManeuvers(jsonResponse);
                displayManeuver(0);                         // Display the first maneuver



            }else{
                // If statuscode is not "0" then we can't create the route, because it would fail and
                // app will force close
                String errText = getResources().getString(R.string.error_route_status_code);
                errText += statuscode + "\r\n" ;
                errText += getResources().getString(R.string.error_route_message);
                errText += message;
                Toast.makeText(getActivity().getApplicationContext(), errText, Toast.LENGTH_LONG).show();
            }
        }


        /* GET MANEUVERS FORM ROUTE RESPONSE */
        /* ********************************* */
        /** If you look at the response you'll see something like this:
         *  "route":{
         *      ... other values..
         *      "legs":[{
         *          ... other values..
         *          "maneuvers":[{
         *              ..maneuver0..
         *          },{
         *              ..maneuver1..
         *          },{
         *              ..maneuverN..
         *          }],
         *  }
         *
         *  So, there is a JSONObject named "route" wich contain a JSONArray name "legs". As
         *  we have NO WAYPOINTS then our trip consist in ONLY ONE LEG. Then we get the first object
         *  of that leg (index = 0); and finally get a JSONArray for the maneuvers within that
         *  object.
         *
         * @param response JSON response from MapQuest Directions Api Web Service
         */
        private void getManeuvers (JSONObject response){
            totalMeneuvers = 0;                                                 // Assume no maneuvers
            try{
                JSONObject route = response.getJSONObject("route");             // Get JSONObjet route
                JSONArray legs= route.getJSONArray("legs");                     // Get JSONArray legs from JSONObjet route
                maneuvers = legs.getJSONObject(0).getJSONArray("maneuvers");    // Get JSONArray maneuvers from first (and only) object inside JSONArray legs

                totalMeneuvers = maneuvers.length();
                Log.i(TAG, "MANEUVERS LENGTH: " + totalMeneuvers);

            }catch (JSONException e){
                // TODO Handle problems.
                Log.e(TAG, Log.getStackTraceString(e));
            }
        }
    }

}
