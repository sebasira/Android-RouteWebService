package com.sebasira.routewebservice;

import android.content.Context;

import com.mapquest.android.maps.LineOverlay;
import com.mapquest.android.maps.RouteManager;
import com.mapquest.android.maps.RouteResponse;

/**
 * Helper class to expose a couple of protected functions
 * [John Duncan @ http://developer.mapquest.com/widget/web/products/forums/-/message_boards/message/1381991]
 */
public class Duncan_RouteManager extends RouteManager {

    public Duncan_RouteManager(Context context) {
        super(context);
    }

    public Duncan_RouteManager(Context context, String apiKey) {
        super(context, apiKey);
    }

    //Create a route overlay directly from a route response rather than indirectly
    // through createRoute since createRoute is broken....
    public LineOverlay getRouteOverlay(RouteResponse routeResponse) {
        return buildRouteOverlay(routeResponse);
    }

    //Create a route itinerary directly from a route response rather than indirectly
    // through createRoute since createRoute is broken....
    public String getHTMLItinerary(RouteResponse routeResponse) {
        return buildHTMLItinerary(routeResponse);
    }


}
