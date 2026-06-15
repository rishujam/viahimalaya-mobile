package com.via.himalaya.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TrekGeometry(
    val type: String, // "LineString" or "MultiLineString"
    val coordinates: List<List<List<Double>>> // Flexible structure to handle both types
)

@Serializable
data class TrekGeoData(
    val id: String,
    val name: String,
    val geometry: TrekGeometry
)

/**
 * Extension function to flatten coordinates based on geometry type
 */
fun TrekGeometry.getFlattenedCoordinates(): List<List<Double>> {
    return when (type) {
        "LineString" -> {
            // For LineString, coordinates is List<List<Double>> but stored as List<List<List<Double>>>
            // So we take the first element which contains the actual coordinates
            if (coordinates.isNotEmpty()) coordinates[0] else emptyList()
        }
        "MultiLineString" -> {
            // For MultiLineString, we need to flatten all coordinate arrays
            coordinates.flatten()
        }
        else -> emptyList()
    }
}

/**
 * Extension function to convert TrekGeometry to GeoJSON string for Mapbox
 */
fun TrekGeometry.toGeoJsonString(): String {
    val flattenedCoordinates = getFlattenedCoordinates()
    
    return """
    {
        "type": "FeatureCollection",
        "features": [
            {
                "type": "Feature",
                "properties": {},
                "geometry": {
                    "type": "LineString",
                    "coordinates": ${coordinatesToJsonArray(flattenedCoordinates)}
                }
            }
        ]
    }
    """.trimIndent()
}

/**
 * Helper function to convert coordinates list to JSON array string
 */
private fun coordinatesToJsonArray(coordinates: List<List<Double>>): String {
    return coordinates.joinToString(
        prefix = "[",
        postfix = "]",
        separator = ","
    ) { coordinate ->
        "[${coordinate.joinToString(",")}]"
    }
}

/**
 * Extension function to calculate bounding box from coordinates
 */
fun TrekGeometry.calculateBoundingBox(): List<Double> {
    val flattenedCoordinates = getFlattenedCoordinates()
    if (flattenedCoordinates.isEmpty()) return emptyList()
    
    var minLng = Double.MAX_VALUE
    var minLat = Double.MAX_VALUE
    var maxLng = Double.MIN_VALUE
    var maxLat = Double.MIN_VALUE
    
    flattenedCoordinates.forEach { coordinate ->
        if (coordinate.size >= 2) {
            val lng = coordinate[0]
            val lat = coordinate[1]
            
            minLng = minOf(minLng, lng)
            maxLng = maxOf(maxLng, lng)
            minLat = minOf(minLat, lat)
            maxLat = maxOf(maxLat, lat)
        }
    }
    
    return listOf(minLng, minLat, maxLng, maxLat)
}

/**
 * Extension function to get center point from coordinates
 */
fun TrekGeometry.getCenterPoint(): Pair<Double, Double>? {
    val boundingBox = calculateBoundingBox()
    if (boundingBox.size < 4) return null
    
    val centerLng = (boundingBox[0] + boundingBox[2]) / 2
    val centerLat = (boundingBox[1] + boundingBox[3]) / 2
    
    return Pair(centerLng, centerLat)
}