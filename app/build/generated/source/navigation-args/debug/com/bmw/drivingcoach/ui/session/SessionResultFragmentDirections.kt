package com.bmw.drivingcoach.ui.session

import android.os.Bundle
import androidx.navigation.NavDirections
import com.bmw.drivingcoach.R
import kotlin.Int
import kotlin.Long

public class SessionResultFragmentDirections private constructor() {
  private data class ActionSessionResultToLapDetail(
    public val lapId: Long,
  ) : NavDirections {
    public override val actionId: Int = R.id.action_session_result_to_lap_detail

    public override val arguments: Bundle
      get() {
        val result = Bundle()
        result.putLong("lapId", this.lapId)
        return result
      }
  }

  public companion object {
    public fun actionSessionResultToLapDetail(lapId: Long): NavDirections =
        ActionSessionResultToLapDetail(lapId)
  }
}
