package com.bmw.drivingcoach.ui.session

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavArgs
import java.lang.IllegalArgumentException
import kotlin.Long
import kotlin.jvm.JvmStatic

public data class SessionResultFragmentArgs(
  public val sessionId: Long,
) : NavArgs {
  public fun toBundle(): Bundle {
    val result = Bundle()
    result.putLong("sessionId", this.sessionId)
    return result
  }

  public fun toSavedStateHandle(): SavedStateHandle {
    val result = SavedStateHandle()
    result.set("sessionId", this.sessionId)
    return result
  }

  public companion object {
    @JvmStatic
    public fun fromBundle(bundle: Bundle): SessionResultFragmentArgs {
      bundle.setClassLoader(SessionResultFragmentArgs::class.java.classLoader)
      val __sessionId : Long
      if (bundle.containsKey("sessionId")) {
        __sessionId = bundle.getLong("sessionId")
      } else {
        throw IllegalArgumentException("Required argument \"sessionId\" is missing and does not have an android:defaultValue")
      }
      return SessionResultFragmentArgs(__sessionId)
    }

    @JvmStatic
    public fun fromSavedStateHandle(savedStateHandle: SavedStateHandle): SessionResultFragmentArgs {
      val __sessionId : Long?
      if (savedStateHandle.contains("sessionId")) {
        __sessionId = savedStateHandle["sessionId"]
        if (__sessionId == null) {
          throw IllegalArgumentException("Argument \"sessionId\" of type long does not support null values")
        }
      } else {
        throw IllegalArgumentException("Required argument \"sessionId\" is missing and does not have an android:defaultValue")
      }
      return SessionResultFragmentArgs(__sessionId)
    }
  }
}
