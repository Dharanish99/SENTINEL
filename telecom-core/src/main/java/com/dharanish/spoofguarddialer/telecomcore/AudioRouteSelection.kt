package com.dharanish.spoofguarddialer.telecomcore

object AudioRouteSelection {
    fun resolve(routes: List<AudioRoute>, confirmedId: String?, routeType: Int?): String? =
        routes.firstOrNull { it.id == confirmedId }?.id
            ?: routes.singleOrNull { it.type == routeType }?.id
}
