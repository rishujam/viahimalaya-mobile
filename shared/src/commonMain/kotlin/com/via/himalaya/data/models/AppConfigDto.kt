package com.via.himalaya.data.models

import com.via.himalaya.domain.model.AppBanner
import com.via.himalaya.domain.model.AppConfig
import com.via.himalaya.domain.model.BannerAction
import kotlinx.serialization.Serializable

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
    val banner: BannerDto? = null
)

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
    banner = banner?.toDomain()
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
