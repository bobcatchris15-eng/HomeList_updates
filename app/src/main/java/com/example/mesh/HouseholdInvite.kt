package com.example.mesh

import android.net.Uri

data class HouseholdInvite(
    val endpointId: String,
    val householdId: String,
    val pairingCode: String,
    val householdName: String = ""
) {
    fun toUri(): Uri = Uri.Builder()
        .scheme("homelist")
        .authority("join")
        .appendQueryParameter("ep", endpointId)
        .appendQueryParameter("hid", householdId)
        .appendQueryParameter("code", pairingCode)
        .apply { if (householdName.isNotBlank()) appendQueryParameter("name", householdName) }
        .build()

    companion object {
        fun parse(raw: String): HouseholdInvite? {
            val value = raw.trim()
            if (value.length > 4096) return null
            val uri = Uri.parse(value)
            if (!uri.isHierarchical || uri.scheme != "homelist" || uri.authority != "join" ||
                !uri.path.isNullOrEmpty() || uri.fragment != null
            ) return null
            val endpoint = uri.getQueryParameters("ep").singleOrNull()?.takeIf {
                it.length in 52..64 && it.all { char -> char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' }
            } ?: return null
            val id = uri.getQueryParameters("hid").singleOrNull()?.takeIf {
                it.matches(Regex("[A-Za-z0-9_-]{1,128}"))
            } ?: return null
            val code = uri.getQueryParameters("code").singleOrNull()?.trim()?.uppercase()
                ?.takeIf { it.length in 4..128 && it.all { char -> char in 'A'..'Z' || char in '0'..'9' } } ?: return null
            return HouseholdInvite(endpoint, id, code, uri.getQueryParameter("name").orEmpty())
        }
    }
}
