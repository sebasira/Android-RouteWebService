package com.sebasira.routewebservice;

import android.app.Fragment;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.mapquest.android.maps.DefaultItemizedOverlay;
import com.mapquest.android.maps.GeoPoint;
import com.mapquest.android.maps.LineOverlay;
import com.mapquest.android.maps.MapView;
import com.mapquest.android.maps.Overlay;
import com.mapquest.android.maps.OverlayItem;
import com.mapquest.android.maps.RouteResponse;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.ClientProtocolException;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

/**
 * MapFragment
 */
public class MapFragment extends Fragment {
    // Marker Icon
    Drawable icon;

    // Mapquest Map View
    private MapView map;

    // Mapquest API KEY
    private String MAPQUEST_API_KEY;

/************************************************************************************/
/** LIFE CYLCE **/

	/* ON CREATE */
	/* ********* */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        map.getController().setZoom(16);
        map.getController().setCenter(new GeoPoint(-32.9476917,-60.6304694));
        map.setBuiltInZoomControls(true);

        // Adding default Markers Overlays
        icon = getResources().getDrawable(R.drawable.location_marker);
        DefaultItemizedOverlay marker_overlay = new DefaultItemizedOverlay(icon);

        OverlayItem beginMarker = new OverlayItem(new GeoPoint(-32.947444,-60.630304),"Start Here","The trip will start here");
        marker_overlay.addItem(beginMarker);

        OverlayItem endMarker = new OverlayItem(new GeoPoint(-32.947048,-60.631699),"Stop Here","The trip will end here");
        marker_overlay.addItem(endMarker);

        map.getOverlays().add(marker_overlay);

        map.invalidate();                           // Re-Draw the map

        new GetRouteTask().execute("http://open.mapquestapi.com/directions/v2/route?key=" + MAPQUEST_API_KEY + "&callback=renderAdvancedNarrative&outFormat=json&routeType=fastest&timeType=1&enhancedNarrative=false&shapeFormat=raw&generalize=0&locale=en_US&unit=m&from=-32.947444,-60.630304&to=-32.947048,-60.631699&drivingStyle=2&highwayEfficiency=21.0");

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
        super.onDestroy();

        // Possible solution to some leaks
        // http://developer.mapquest.com/web/products/forums/-/message_boards/view_message/744551
        if (null != map){
            map.destroy();
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

            } catch (ClientProtocolException e) {
                // TODO Handle problems..

            } catch (IOException e) {
                // TODO Handle problems..

            } catch (JSONException e) {
                // TODO Handle problems..

            }finally {
                try{
                    input.close();
                } catch (IOException e) {
                    // TODO Handle problems..
                }
            }

            return jsonResponse;
        }

        // POST EXECUTE
        protected void onPostExecute(JSONObject jsonResponse) {
            // Create the RouteResponse from JSON Responso, aka from Web Service
            RouteResponse response = new RouteResponse(jsonResponse);

            // Create a RouteManager as John Duncan suggested using your API KEY
            Duncan_RouteManager duncanRouteManager = new Duncan_RouteManager(getActivity().getApplicationContext(), MAPQUEST_API_KEY);

            // Get the route from the response
            LineOverlay routeLine = duncanRouteManager.getRouteOverlay(response);
            map.getOverlays().add(routeLine);
            map.postInvalidate();

            //String itinerary = duncanRouteManager.getHTMLItinerary(response);
        }
    }

}
