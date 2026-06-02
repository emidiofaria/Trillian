package com.bmw.drivingcoach.ui.recording

import android.os.Bundle
import androidx.navigation.NavDirections
import com.bmw.drivingcoach.R
import kotlin.Int
import kotlin.Long

public class RecordingFragmentDirections private constructor() {
  private data class ActionRecordingToSessionResult(
    public val sessionId: Long,
  ) : NavDirections {
    public override val actionId: Int = R.id.action_recording_to_session_result

    public override val arguments: Bundle
      get() {
        val result = Bundle()
        result.putLong("sessionId", this.sessionId)
        return result
      }
  }

  public companion object {
    public fun actionRecordingToSessionResult(sessionId: Long): NavDirections =
        ActionRecordingToSessionResult(sessionId)
  }
}
