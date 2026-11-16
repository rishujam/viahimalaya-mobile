package com.via.himalaya.data.models

import com.via.himalaya.domain.model.AppBanner
import com.via.himalaya.domain.model.AppConfig
import com.via.himalaya.domain.model.AppUpdate
import com.via.himalaya.domain.model.BannerAction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Wire shape of /api/app-config's `data` object.
 *
 * Every field is nullable with a default, on purpose. A section the server has
 * not sent, has disabled, or has sent half-populated must degrade to "off"
 * rather than throw - this response is parsed at launch, and an exception here
 * would take out config for the whole session.
 */
@Serializable
data class AppConfigDto(
    val banner: BannerDto? = null,
    /**
     * Raw JSON, deliberately not Map<String, Boolean>. A typed map would throw on
     * the first value that is not a boolean - `"false"` typed as a string in
     * Neon, say - and take the banner and every other section down with it.
     * [toFeatureMap] keeps the well-formed switches and drops the rest.
     */
    val features: JsonElement? = null,
    /** Raw JSON for the same reason as [features]; see [toAppUpdate]. */
    @SerialName("app_update")
    val appUpdate: JsonElement? = null
)

/**
 * Reads `{"min_version": 14, "latest_version": 15, "message": "..."}`. A version
 * that is not a whole number is dropped rather than failing the parse, and a
 * row with no usable version at all is null - so a typo in Neon can at worst
 * fail to ask for an update, never block anyone by mistake.
 */
internal fun JsonElement?.toAppUpdate(): AppUpdate? {
    val obj = this as? JsonObject ?: return null
    fun version(key: String): Int? {
        val p = obj[key] as? JsonPrimitive ?: return null
        if (p.isString) return null
        return p.intOrNull?.takeIf { it > 0 }
    }
    val min = version("min_version")
    val latest = version("latest_version")
    if (min == null && latest == null) return null
    val message = (obj["message"] as? JsonPrimitive)
        ?.takeIf { it.isString }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    return AppUpdate(minVersion = min, latestVersion = latest, message = message)
}

/**
 * The switches that are real booleans. Anything else - a string, a number, a
 * nested object, or `features` not being an object at all - is skipped, so that
 * key falls back to the feature's in-app default.
 */
internal fun JsonElement?.toFeatureMap(): Map<String, Boolean> {
    val obj = this as? JsonObject ?: return emptyMap()
    return obj.mapNotNull { (key, value) ->
        val primitive = value as? JsonPrimitive ?: return@mapNotNull null
        if (primitive.isString) return@mapNotNull null
        primitive.booleanOrNull?.let { key to it }
    }.toMap()
}

@Serializable
data class BannerDto(
    val title: String? = null,
    val description: String? = null,
    /**
     * Plain string, deliberately not [BannerAction]. See that type for why
     * decoding an enum straight off the wire is a trap.
     */
    val action: String? = null
)

fun AppConfigDto.toDomain(): AppConfig = AppConfig(
    banner = banner?.toDomain(),
    features = features.toFeatureMap(),
    update = appUpdate.toAppUpdate()
)

/**
 * Fails closed: null unless the banner is complete and its action is one this
 * build can actually perform.
 *
 * All three fields are required rather than defaulted. The server controls all
 * three and always sends them, so a missing one is a mistake upstream - and
 * rendering a banner with a blank line or a dead tap target hides that mistake
 * instead of surfacing it.
 */
fun BannerDto.toDomain(): AppBanner? {
    val resolvedTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val resolvedDescription = description?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val resolvedAction = BannerAction.from(action) ?: return null

    return AppBanner(
        title = resolvedTitle,
        description = resolvedDescription,
        action = resolvedAction
    )
}
