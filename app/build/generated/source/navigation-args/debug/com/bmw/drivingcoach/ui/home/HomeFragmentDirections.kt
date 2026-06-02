package com.bmw.drivingcoach.ui.home

import android.os.Bundle
import androidx.navigation.ActionOnlyNavDirections
import androidx.navigation.NavDirections
import com.bmw.drivingcoach.R
import kotlin.Int
import kotlin.Long

public class HomeFragmentDirections private constructor() {
  private data class ActionHomeToRecording(
    public val sessionId: Long,
  ) : NavDirections {
    public override val actionId: Int = R.id.action_home_to_recording

    public override val arguments: Bundle
      get() {
        val result = Bundle()
        result.putLong("sessionId", this.sessionId)
        return result
      }
  }

  private data class ActionHomeToSessionResult(
    public val sessionId: Long,
  ) : NavDirections {
    public override val actionId: Int = R.id.action_home_to_session_result

    public override val arguments: Bundle
      get() {
        val result = Bundle()
        result.putLong("sessionId", this.sessionId)
        return result
      }
  }

  public companion object {
    public fun actionHomeToRecording(sessionId: Long): NavDirections =
        ActionHomeToRecording(sessionId)

    public fun actionHomeToProfile(): NavDirections =
        ActionOnlyNavDirections(R.id.action_home_to_profile)

    public fun actionHomeToSessionResult(sessionId: Long): NavDirections =
        ActionHomeToSessionResult(sessionId)
  }
}
