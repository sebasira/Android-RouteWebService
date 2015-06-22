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
- Add drag marker capability [pull-request #1](https://github.com/sebasira/Android-RouteWebService/pull/1)
- Change Route destination (drag marker) [pull-request #1](https://github.com/sebasira/Android-RouteWebService/pull/1)
- Use MyLocation as Origin for the route [pull-request #2](https://github.com/sebasira/Android-RouteWebService/pull/2)
- Added maneuver icon to the maneuver detail
- Change route type (walk, drive, bike) [pull-request #3](https://github.com/sebasira/Android-RouteWebService/pull/3)
 
### What would be done:
- Real-Time Navigation as we move
- Re-Calculate route when drifting from it
- Add Markers by touching
- Add waypoints to the route
 

Some part's of the code (even some ideas) where taken from different places and a mention to the user who point it out and the place where it was taken from are mentioned, to give credit to them.
