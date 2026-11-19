package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.export.BackupManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Baddie", appName)
  }

  @Test
  fun `validateBackupJson rejects malformed and foreign payloads`() {
    assertTrue(BackupManager.validateBackupJson("not-json").isFailure)
    assertTrue(BackupManager.validateBackupJson("""{"app":"Other"}""").isFailure)
    assertTrue(BackupManager.validateBackupJson("""{"app":"Baddie","schemaVersion":1}""").isSuccess)
  }
}
