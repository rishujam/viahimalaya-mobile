package com.via.himalaya.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable(with = TrekGeometrySerializer::class)
data class TrekGeometry(
    val type: String, // "LineString" or "MultiLineString"
    val coordinates: List<List<List<Double>>> // Normalized to MultiLineString format internally
)

/**
 * Custom serializer to handle both LineString and MultiLineString coordinate formats
 */
object TrekGeometrySerializer : KSerializer<TrekGeometry> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("TrekGeometry") {
        element<String>("type")
        element<JsonElement>("coordinates")
    }

    override fun deserialize(decoder: Decoder): TrekGeometry {
        require(decoder is JsonDecoder)
        val element = decoder.decodeJsonElement()
        require(element is JsonObject)
        
        val type = element["type"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing type field")
        val coordinatesElement = element["coordinates"]?.jsonArray ?: throw IllegalArgumentException("Missing coordinates field")
        
        val normalizedCoordinates = when (type) {
            "LineString" -> {
                // LineString: [[lon, lat], [lon, lat], ...]
                // Convert to MultiLineString format: [[[lon, lat], [lon, lat], ...]]
                val lineStringCoords = coordinatesElement.map { pointElement ->
                    pointElement.jsonArray.map { it.jsonPrimitive.double }
                }
                listOf(lineStringCoords)
            }
            "MultiLineString" -> {
                // MultiLineString: [[[lon, lat], [lon, lat], ...], [[lon, lat], ...]]
                // Already in the correct format
                coordinatesElement.map { lineElement ->
                    lineElement.jsonArray.map { pointElement ->
                        pointElement.jsonArray.map { it.jsonPrimitive.double }
                    }
                }
            }
            else -> throw IllegalArgumentException("Unsupported geometry type: $type")
        }
        
        return TrekGeometry(type, normalizedCoordinates)
    }

    override fun serialize(encoder: Encoder, value: TrekGeometry) {
        require(encoder is JsonEncoder)
        val jsonObject = buildJsonObject {
            put("type", value.type)
            
            // Serialize based on original type
            val coordinatesArray = when (value.type) {
                "LineString" -> {
                    // Convert back to LineString format: [[lon, lat], [lon, lat], ...]
                    JsonArray(
                        value.coordinates.firstOrNull()?.map { point ->
                            JsonArray(point.map { kotlinx.serialization.json.JsonPrimitive(it) })
                        } ?: emptyList()
                    )
                }
                "MultiLineString" -> {
                    // Keep MultiLineString format: [[[lon, lat], ...], [[lon, lat], ...]]
                    JsonArray(
                        value.coordinates.map { line ->
                            JsonArray(
                                line.map { point ->
                                    JsonArray(point.map { kotlinx.serialization.json.JsonPrimitive(it) })
                                }
                            )
                        }
                    )
                }
                else -> JsonArray(emptyList())
            }
            
            put("coordinates", coordinatesArray)
        }
        encoder.encodeJsonElement(jsonObject)
    }
}

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
 * GeoJSON for the Mapbox line layer.
 *
 * Keeps a MultiLineString as a MultiLineString rather than flattening it. A flat
 * list has no notion of where one path stops and the next begins, so Mapbox drew
 * a single continuous line through every part - on Beas Kund that produced a 2.7
 * km straight edge across the map between two unconnected sections of trail, and
 * every multi-segment trek had smaller versions of the same thing.
 *
 * The geometry from OSM is passed through untouched; only how it is described to
 * Mapbox changes.
 */
fun TrekGeometry.toGeoJsonString(): String {
    val isMulti = type == "MultiLineString" && coordinates.size > 1
    val geometryJson = if (isMulti) {
        """
                    "type": "MultiLineString",
                    "coordinates": ${segmentsToJsonArray(coordinates)}
        """.trimIndent()
    } else {
        """
                    "type": "LineString",
                    "coordinates": ${coordinatesToJsonArray(getFlattenedCoordinates())}
        """.trimIndent()
    }

    return """
    {
        "type": "FeatureCollection",
        "features": [
            {
                "type": "Feature",
                "properties": {},
                "geometry": {
                    $geometryJson
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

/** One nesting level up from [coordinatesToJsonArray]: a list of line segments. */
private fun segmentsToJsonArray(segments: List<List<List<Double>>>): String {
    return segments.joinToString(
        prefix = "[",
        postfix = "]",
        separator = ","
    ) { segment -> coordinatesToJsonArray(segment) }
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