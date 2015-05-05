package com.sebasira.routewebservice;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.mapquest.android.maps.DefaultItemizedOverlay;
import com.mapquest.android.maps.GeoPoint;
import com.mapquest.android.maps.ItemizedOverlay;
import com.mapquest.android.maps.LineOverlay;
import com.mapquest.android.maps.MapView;
import com.mapquest.android.maps.MyLocationOverlay;
import com.mapquest.android.maps.Overlay;
import com.mapquest.android.maps.OverlayItem;
import com.mapquest.android.maps.RouteResponse;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.ClientProtocolException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
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
    private GeoPoint destinationPoint;

    // Maneuvers
    private JSONArray maneuvers;
    private int displayingManeuverIndex;
    private int totalMeneuvers;

    // Maneuvers UI
    private TextView tv_maneuver;
    private Button btn_prev;
    private Button btn_next;
    private ImageView img_maneuver;

    // Maneuvers Overlay
    private DefaultItemizedOverlay maneuverOverlay;

    // Text-to-Speech
    private TextToSpeech tts;

    // Directions API URL query
    private String directions_api_request_url;

    // ImageView that would act as marker been dragged
    private ImageView dragImage = null;

    // Route Manager
    private Duncan_RouteManager duncanRouteManager;

    // MyLocationOverlay: An Overlya to display My Location
    private MyLocationOverlay myLocationOverlay;
    private ProgressDialog myLocation_progressDialog;

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
                    Log.i(TAG, msg);

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
        map.getController().setZoom(12);
        map.getController().setCenter(defaultCenterPoint);
        map.setBuiltInZoomControls(true);

        // Adding myLocationOverlay
        setupMyLocation();

        // Adding draggable marker overlay
        Drawable icon = getResources().getDrawable(R.drawable.dot_marker);
        destinationPoint = defaultEndPoint;         // Destination is by default, the default endPoint
        dragImage = (ImageView) rootView.findViewById(R.id.dragImg);
        map.getOverlays().add(new DraggableOverlay(icon));

        map.invalidate();                           // Re-Draw the map

        // Maneuvers Display and controls
        tv_maneuver = (TextView) rootView.findViewById(R.id.tv_manouver);
        img_maneuver = (ImageView) rootView.findViewById(R.id.im_manouver);

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
        DefaultItemizedOverlay.setAlignment(iconFlag, Overlay.RIGHT | Overlay.BOTTOM);

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
                if (null != duncanRouteManager) {
                    duncanRouteManager.clearRoute();                    // Clear previous route (if any)
                }

                // Request the route (Direction API) from mapquest
                directions_api_request_url = "http://open.mapquestapi.com/directions/v2/route?key=" + MAPQUEST_API_KEY +
                        "&callback=renderAdvancedNarrative&outFormat=json&routeType=fastest&timeType=1&enhancedNarrative=false&shapeFormat=raw&generalize=0" +
                        "&locale=" + Locale.getDefault() +              // Set query with default locale (for narratives)
                        "&unit=m" +
                        "&from=" + myLocationOverlay.getMyLocation().getLatitudeE6() / 1E6 + "," + myLocationOverlay.getMyLocation().getLongitudeE6() / 1E6 +
                        "&to=" + destinationPoint.getLatitudeE6() / 1E6 + "," + destinationPoint.getLongitudeE6() / 1E6 +
                        "&drivingStyle=2&highwayEfficiency=21.0";
                Log.i(TAG, "DIRECTIONS API URL: " + directions_api_request_url);
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

                // As it is the last maneuver, ALWAYS say: "Arrive at your destination"
                maneuver_narrative = getResources().getString(R.string.arrive_destination);

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

            // Maneuver Icon
            String maneuver_icon = "";
            try{
                maneuver_icon = maneuvers.getJSONObject(idx).optString("iconUrl");     // Get the "iconUrl" of the IDX maneuver object
            }catch (JSONException e){
                // TODO Handle problems.
                Log.e(TAG, Log.getStackTraceString(e));
            }

            // Maneuver Text + Icon
            tv_maneuver.setText(maneuver_narrative);        // Set maneuver text

            Log.e(TAG,"Maneuver Icon URL: " + maneuver_icon);
            if (!maneuver_icon.equals("")){
                img_maneuver.setImageDrawable(null);        // Remove Image
                new DownloadImageTask(img_maneuver).execute(maneuver_icon);
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


    /* SET UP MY LOCATION */
    /* ****************** */
    /**
     * Set up a MyLocationOverlay and execute the runnable once a location has
     * been fixed
     */
    private void setupMyLocation() {
        // Check if the GPS is enabled
        if (!((LocationManager) getActivity().getSystemService(getActivity().LOCATION_SERVICE))
                .isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            // Open dialog to inform the user that the GPS is disabled and ask him to enable it
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(getResources().getString(R.string.gps_disabled));
            builder.setMessage(getResources().getString(R.string.improve_location));
            builder.setCancelable(false);
            builder.setPositiveButton(R.string.settings,
                    new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Open the location settings if it is disabled
                            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            startActivity(intent);
                        }
                    });
            builder.setNegativeButton(R.string.cancel,
                    new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Dismiss the dialog
                            dialog.cancel();
                        }
                    });

            // Display the dialog
            AlertDialog dialog = builder.create();
            dialog.show();
        }

        // Create the MyLocationOverlay
        myLocationOverlay = new MyLocationOverlay(getActivity(), map);
        myLocationOverlay.enableMyLocation();
        myLocationOverlay.setMarker(getResources().getDrawable(R.drawable.dot_marker), 0);

        myLocation_progressDialog = new ProgressDialog(getActivity());
        myLocation_progressDialog.setMessage(getResources().getString(R.string.waiting_my_location));
        myLocation_progressDialog.show();

        myLocationOverlay.runOnFirstFix(new Runnable() {

            @Override
            public void run() {
                if (myLocation_progressDialog.isShowing()) {
                    myLocation_progressDialog.dismiss();
                }

                GeoPoint currentLocation = myLocationOverlay.getMyLocation();
                map.getController().animateTo(currentLocation);
                map.getOverlays().add(myLocationOverlay);
                myLocationOverlay.setFollowing(false);  // Don't follow myLocation if I move the map
            }
        });
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
                duncanRouteManager = new Duncan_RouteManager(getActivity().getApplicationContext(), MAPQUEST_API_KEY);

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


/************************************************************************************/
/** DRAGGABLE OVERLAY **/

    public class DraggableOverlay extends ItemizedOverlay<OverlayItem> {
        private List<OverlayItem> items = new ArrayList<OverlayItem>();
        private Drawable marker = null;
        private OverlayItem inDrag = null;

        /** DragImageOffset are the values halfWidth and fullHeight used to position the image.
         * When positioning an image (setting margins) the easiest way is to set a LEFT and TOP margin.
         * But the coordinates we are going to use to position it are relatives to BOTTOM and CENTER,
         * because the marker is boundCenterBottom.
         * So we need to adjust this margin in order to correctly position the ImageView where the
         * marker was. Therefore there's an offset on the image's margins. Those offsets are:
         *      - for Left Margin: half width to the left (to be in the middle) so this value should be subtracted
         *      - for Top Margin: full height to the top (to be in the bottom) so this value should be subtracted
         */
        private int xDragImageOffset = 0;
        private int yDragImageOffset = 0;

        private int xDragTouchOffset = 0;
        private int yDragTouchOffset = 0;

        /* CONSTRUCTOR */
        /* *********** */
        public DraggableOverlay(Drawable marker) {
            super(marker);
            this.marker = marker;


            xDragImageOffset = dragImage.getDrawable().getIntrinsicWidth()/2;
            yDragImageOffset = dragImage.getDrawable().getIntrinsicHeight();


            items.add(new OverlayItem(destinationPoint,"Title", "Snippet"));

            // Populates ItemizedOverlay's internal list. Subclass must provide number of items that
            // must be populate by implementing size(). Each item in the list populated by calling createItem(int).
            populate();
        }


        /* CREATE ITEM */
        /* *********** */
        /** Required method when extending ItemizedOverlay. Returns the item at the given index.
         *
         * @param i index
         * @return item at given index
         */
        @Override
        protected OverlayItem createItem(int i) {
            return(items.get(i));
        }


        /* DRAW */
        /* **** */
        /** Required method when extending ItemizedOverlay. If not present, markers won't be
         * drawn int the map
         */
        @Override
        public void draw(Canvas canvas, MapView mapView, boolean shadow) {
            super.draw(canvas, mapView, shadow);

            boundCenterBottom(marker);
        }


        /* SIZE */
        /* **** */
        /** Required method when extending ItemizedOverlay. Returns the number of items in the overlay.
         *
         * @return number of items in the overlay
         */
        @Override
        public int size() {
            return(items.size());
        }


        /* ON TOUCH EVENT */
        /* ************** */
        /** Handles the touch events for this overlay. This is how we're gone make it draggable
         *
         * @param event type of touch event (MotionEvent) DOWN, MOVE, UP, etc
         * @param mapView mapview where the event ocurr
         * @return TRUE to stop propagation of the event or FALSE to pass the event handler
         */
        @Override
        public boolean onTouchEvent(MotionEvent event, MapView mapView) {
            final int action  = event.getAction();      // Type of Action
            final int x = (int)event.getX();            // X coordinate where the event happened
            final int y = (int)event.getY();            // Y coordinate where the event happened

            // DOWN
            if (action == MotionEvent.ACTION_DOWN) {
                // Sweep all items on the overlay list to test if touch event happened over one
                // of them
                for (OverlayItem item : items) {
                    Point p = new Point();                              // Creates an Android screen point
                    map.getProjection().toPixels(item.getPoint(), p);   // Set point coordinates to be item's coordinates on screen

                    // Check if event (DOWN) happened over an item (marker)
                    if (hitTest(item, marker, x - p.x, y - p.y)) {
                        inDrag = item;                                  // Item being dragged
                        items.remove(inDrag);                           // Remove this dragged item from item list
                        populate();                                     // Update ItemizedOverlay internal list
                        map.postInvalidate();                           // Re-draw the map

                        // As the Touch Event may have happened around the point but not exactly
                        // on it we need to consider this little offset.
                        // Depending on your needs the may not be necessary
                        // This offset is an initial value that won't be changed
                        xDragTouchOffset = x - p.x;
                        yDragTouchOffset = y - p.y;

                        // Now the marker was removed from the list, so it won't exist and won't be shown.
                        // So what we do is to show an ImageView with the same picture as the location
                        // marker, starting from the exact same place,  to make user believe he
                        // is dragging that marker. But that's only an illusion
                        setDragImagePosition(x, y);                     // Set ImageView in the same place the marker was
                        dragImage.setVisibility(View.VISIBLE);          // Show this ImageView

                        break;                                          // Exit FOR, don't keep sweeping
                    }

                    // If dragging and item, the stop progation of tocuh event => return true
                    if (inDrag != null) {
                        return true;
                    }
                }

                // MOVE
            }else if (action == MotionEvent.ACTION_MOVE) {
                // Only move the ImageView if an item is being dragged
                if (inDrag != null) {
                    // Change position of imageView accordly to movement
                    setDragImagePosition(x, y);

                    // If dragging and item, the stop progation of tocuh event => return true
                    return true;
                }

                // UP
            }else if (action == MotionEvent.ACTION_UP) {
                // Only process UP event if an item is being dragged
                if (inDrag != null) {
                    dragImage.setVisibility(View.GONE);     // Hide the ImageView

                    // Now we change the destination to be a GeoPoint from where the UP occurs,
                    // create an OverlayItem from it and add it to the item list so it will be
                    // drawn on the map
                    destinationPoint = map.getProjection().fromPixels(x - xDragTouchOffset,y - yDragTouchOffset);
                    OverlayItem toDrop = new OverlayItem(destinationPoint, inDrag.getTitle(),inDrag.getSnippet());
                    items.add(toDrop);
                    populate();
                    map.postInvalidate();                   // Re-draw the map

                    inDrag = null;                          // There's not an item been dragged anymore

                    // Stop progation of tocuh event => return true
                    return true;
                }
            }

            // Return false to pass the handler of the Event
            return false;
        }

        /* SET DRAG IMAGE POSITION */
        /* *********************** */
        /** Sets the ImageView position on screen. This ImageView is the one that creates the illusion
         * of dragging the marker.
         *
         * @param x X coordinate on screen
         * @param y Y coordinate on screen
         */
        private void setDragImagePosition(int x, int y) {
            RelativeLayout.LayoutParams lp= (RelativeLayout.LayoutParams) dragImage.getLayoutParams();

            // To positioning a View (an ImageView ins this case) on the screen we need to set its margins.
            // We are going to set LEFT and TOP margins, and to correctly positioning the image
            // some offsets need to be taken in account.
            lp.setMargins(x - xDragImageOffset - xDragTouchOffset, y - yDragImageOffset - yDragTouchOffset, 0, 0);
            dragImage.setLayoutParams(lp);
        }
    }

/************************************************************************************/
/** DOWNLOAD IMAGE FROM URL **/

    /** This asynTask will download and image from the given URL and finally set it as the bitmap image
     * of the ImageView passed when creating the task
     *
     * This was taken from here:
     * http://stackoverflow.com/a/25423372/2597775
     */
    private class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
        ImageView bmImage;

        // CONSTRUCTOR
        public DownloadImageTask(ImageView bmImage) {
            this.bmImage = bmImage;
        }

        // DO IN BACKGROUND
        protected Bitmap doInBackground(String... urls) {
            String urldisplay = urls[0];
            Bitmap mIcon11 = null;
            try {
                InputStream in = new java.net.URL(urldisplay).openStream();
                mIcon11 = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                Log.e("Error", e.getMessage());
                e.printStackTrace();
            }
            return mIcon11;
        }

        // ON POST-EXECUTE
        protected void onPostExecute(Bitmap result) {
            bmImage.setImageBitmap(result);
        }
    }

}
