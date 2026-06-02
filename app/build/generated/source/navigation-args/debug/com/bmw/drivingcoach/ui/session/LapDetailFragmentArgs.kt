package com.bmw.drivingcoach.ui.session

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavArgs
import java.lang.IllegalArgumentException
import kotlin.Long
import kotlin.jvm.JvmStatic

public data class LapDetailFragmentArgs(
  public val lapId: Long,
) : NavArgs {
  public fun toBundle(): Bundle {
    val result = Bundle()
    result.putLong("lapId", this.lapId)
    return result
  }

  public fun toSavedStateHandle(): SavedStateHandle {
    val result = SavedStateHandle()
    result.set("lapId", this.lapId)
    return result
  }

  public companion object {
    @JvmStatic
    public fun fromBundle(bundle: Bundle): LapDetailFragmentArgs {
      bundle.setClassLoader(LapDetailFragmentArgs::class.java.classLoader)
      val __lapId : Long
      if (bundle.containsKey("lapId")) {
        __lapId = bundle.getLong("lapId")
      } else {
        throw IllegalArgumentException("Required argument \"lapId\" is missing and does not have an android:defaultValue")
      }
      return LapDetailFragmentArgs(__lapId)
    }

    @JvmStatic
    public fun fromSavedStateHandle(savedStateHandle: SavedStateHandle): LapDetailFragmentArgs {
      val __lapId : Long?
      if (savedStateHandle.contains("lapId")) {
        __lapId = savedStateHandle["lapId"]
        if (__lapId == null) {
          throw IllegalArgumentException("Argument \"lapId\" of type long does not support null values")
        }
      } else {
        throw IllegalArgumentException("Required argument \"lapId\" is missing and does not have an android:defaultValue")
      }
      return LapDetailFragmentArgs(__lapId)
    }
  }
}
