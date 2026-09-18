package com.odys.mototriptracker.ui.components.map

val DARK_MAP_STYLE_JSON = """
[
  { "elementType": "geometry",        "stylers": [{ "color": "#1a1a2e" }] },
  { "elementType": "labels.text.fill","stylers": [{ "color": "#6b6b8d" }] },
  { "elementType": "labels.text.stroke","stylers": [{ "color": "#0e0e14" }] },
  { "featureType": "road",            "elementType": "geometry",       "stylers": [{ "color": "#2a2a42" }] },
  { "featureType": "road.highway",    "elementType": "geometry",       "stylers": [{ "color": "#3a3a58" }] },
  { "featureType": "road",            "elementType": "labels.text.fill","stylers": [{ "color": "#4a4a6a" }] },
  { "featureType": "water",           "elementType": "geometry",       "stylers": [{ "color": "#0d1b2a" }] },
  { "featureType": "poi",             "elementType": "geometry",       "stylers": [{ "color": "#16162a" }] },
  { "featureType": "poi",             "elementType": "labels",         "stylers": [{ "visibility": "off" }] },
  { "featureType": "transit",         "stylers": [{ "visibility": "off" }] },
  { "featureType": "administrative",  "elementType": "geometry",       "stylers": [{ "color": "#2a2a42" }] }
]
""".trimIndent()

/** Live tracker dark style — same palette but POI labels stay visible for map place taps. */
val LIVE_DARK_MAP_STYLE_JSON = """
[
  { "elementType": "geometry",        "stylers": [{ "color": "#1a1a2e" }] },
  { "elementType": "labels.text.fill","stylers": [{ "color": "#6b6b8d" }] },
  { "elementType": "labels.text.stroke","stylers": [{ "color": "#0e0e14" }] },
  { "featureType": "road",            "elementType": "geometry",       "stylers": [{ "color": "#2a2a42" }] },
  { "featureType": "road.highway",    "elementType": "geometry",       "stylers": [{ "color": "#3a3a58" }] },
  { "featureType": "road",            "elementType": "labels.text.fill","stylers": [{ "color": "#4a4a6a" }] },
  { "featureType": "water",           "elementType": "geometry",       "stylers": [{ "color": "#0d1b2a" }] },
  { "featureType": "poi",             "elementType": "geometry",       "stylers": [{ "color": "#16162a" }] },
  { "featureType": "transit",         "stylers": [{ "visibility": "off" }] },
  { "featureType": "administrative",  "elementType": "geometry",       "stylers": [{ "color": "#2a2a42" }] }
]
""".trimIndent()
