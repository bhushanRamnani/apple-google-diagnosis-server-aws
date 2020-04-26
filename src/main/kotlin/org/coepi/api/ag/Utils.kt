package org.coepi.api.ag

import org.apache.commons.lang3.Validate
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

object Utils {
    val minEpoch = LocalDateTime.of(2019, 11, 1, 0, 0)
            .toEpochSecond(ZoneOffset.UTC)

    fun getDayNumber(epochSeconds: Long): Long {
        return (epochSeconds / (24 * 60 * 60))
    }

    fun getIntervalNumber(epochSeconds: Long): Int {
        return ((epochSeconds % (24 * 60 * 60)) / DiagnosisServerHandler.INTERVAL_LENGTH_S).toInt()
    }

    fun validateDayNumber(dayNumber: Long) {
        val epochNow = Instant.now().epochSecond
        val todayNumber = getDayNumber(epochNow)
        val minDayNumber = getDayNumber(minEpoch)
        Validate.inclusiveBetween(minDayNumber, todayNumber, dayNumber,
                "dayNumber has to be between $minDayNumber and $todayNumber, inclusive,"
                        + " but the dayNumber provided was: $dayNumber")
    }
}