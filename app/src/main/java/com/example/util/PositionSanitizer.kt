package com.example.util

import com.example.data.model.Position

object PositionSanitizer {
    fun sanitize(positions: List<Position>): List<Position> {
        return TelemetrySanitizerService.sanitizeRoute(positions)
    }
}

