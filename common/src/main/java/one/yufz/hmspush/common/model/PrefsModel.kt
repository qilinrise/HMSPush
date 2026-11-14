package one.yufz.hmspush.common.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import one.yufz.hmspush.common.content.ContentModel
import one.yufz.hmspush.common.content.ContentProperties

@Parcelize
data class PrefsModel constructor(
    var disableSignature: Boolean,
    var groupMessageById: Boolean,
    var useCustomIcon: Boolean,
    var tintIconColor: Boolean,
    var keepAlive: Boolean,
    var hideAppIcon: Boolean,
    var fixNotificationIcon: Boolean,
) : ContentModel, Parcelable {

    constructor() : this(
        disableSignature = false,
        groupMessageById = true,
        useCustomIcon = false,
        tintIconColor = true,
        keepAlive = false,
        hideAppIcon = false,
        // Added later: missing key in old preference files keeps this default.
        fixNotificationIcon = false,
    )

    companion object {
        @JvmField
        val PROPERTIES = ContentProperties.Builder<PrefsModel>()
            .property("disableSignature", PrefsModel::disableSignature)
            .property("groupMessageById", PrefsModel::groupMessageById)
            .property("useCustomIcon", PrefsModel::useCustomIcon)
            .property("tintIconColor", PrefsModel::tintIconColor)
            .property("keepAlive", PrefsModel::keepAlive)
            .property("hideAppIcon", PrefsModel::hideAppIcon)
            .property("fixNotificationIcon", PrefsModel::fixNotificationIcon)
            .build()
    }
}
