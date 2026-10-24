package com.blacklaunch.launcher.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val soundMode: String,
    val notificationMode: String,
    val scheduleEnabled: Boolean,
    /** Порядок профілів у списку та для свайпу вліво/вправо. */
    val position: Int,
)

@Entity(
    tableName = "pinned_apps",
    primaryKeys = ["profileId", "appKey"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PinnedAppEntity(
    val profileId: String,
    val appKey: String,
    val position: Int,
)

@Entity(
    tableName = "notification_rules",
    primaryKeys = ["profileId", "packageName"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("packageName")],
)
data class NotificationRuleEntity(
    val profileId: String,
    val packageName: String,
)
