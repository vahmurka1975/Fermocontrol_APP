package ru.fermocontrol.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun decimal(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)
fun grams(value: Double): String = decimal(value)
fun dateTime(value: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(value))
