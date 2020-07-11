package com.hpcnc.cameraxfaceml

import android.graphics.Rect

data class Prediction( var bbox : Rect, var label : String )
