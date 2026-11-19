package com.example

import com.example.data.domain.BaddieDomainLogic
import com.example.data.model.BaddieTask
import com.example.data.model.RoutineBlock
import com.example.data.model.TuitionSession
import com.example.data.model.TuitionStatus
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun detectRoutineAndTuitionConflicts_accuratelyIdentifiesOverlaps() {
        val routines = listOf(
            RoutineBlock(
                id = "r1",
                title = "Morning Physics",
                startTime = "08:00",
                endTime = "09:30",
                durationMinutes = 90,
                repeatDays = listOf("SUN", "TUE")
            )
        )
        val routineConflicts = BaddieDomainLogic.detectRoutineConflicts(
            startTime = "09:00",
            endTime = "10:00",
            repeatDays = listOf("SUN"),
            existingBlocks = routines
        )
        assertEquals(1, routineConflicts.size)

        val tuitions = listOf(
            TuitionSession(
                id = "t1",
                subject = "Mathematics",
                sessionDate = "2026-11-22",
                startTime = "16:00",
                durationMinutes = 90,
                status = TuitionStatus.SCHEDULED.name
            )
        )
        val tuitionConflicts = BaddieDomainLogic.detectTuitionConflicts(
            dateIso = "2026-11-22",
            startTime = "16:30",
            durationMinutes = 60,
            sessions = tuitions,
            routineBlocks = routines
        )
        assertEquals(1, tuitionConflicts.size)
    }

    @Test
    fun computeBehaviourProfile_doesNotInventDataWhenLogsSparse() {
        val profile = BaddieDomainLogic.computeBehaviourProfile(
            tasks = listOf(BaddieTask(title = "Single task")),
            studySessions = emptyList(),
            habits = emptyList(),
            tuitionSessions = emptyList(),
            sleepLogs = emptyList()
        )
        assertFalse(profile.hasSufficientData)
        assertNull(profile.typicalWakeTime)
        assertNull(profile.averageStudyMinutesPerDay)
    }
}
