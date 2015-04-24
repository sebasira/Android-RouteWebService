# Android-RouteWebService

This project uses MapQuest Directions API (Open Data) to get a route, instead of using the MapQuest Android SDK v1.0.5, because this requieres an Enterprise Key (Licensed Data).

Within the response of the Directions API we also have narratives used in Guidance. At the end this would be a full complex project starting from a basic map and then adding functionallities.

On the different releases you'll find different functionallities as this app evolves. I invite you to browse to the [releases](https://github.com/sebasira/Android-RouteWebService/releases) to see what's new.

### What's already done:
- Get the route response from MapQuest Directions API (Open Data)
- Draw the route over the map
- Get default Android Locale and use on the route query
- Get maneuvers from route response
- Show and navigate to maneuvers
- Add a marker on each maneuver startPoint when navigating through maneuvers
- Use TTS service to speak out the maneuvers narrative in the defalut Locale Languaje

### What would be done:
- Add drag marker capability
- Change Route destination (drag marker)
- Use MyLocation as Origin for the route
- Real-Time Navigation as we move
- Re-Calculate route when drifting from it
- Change route type (walk, drive, bike)
- Add Markers by touching
- Add waypoints to the route
