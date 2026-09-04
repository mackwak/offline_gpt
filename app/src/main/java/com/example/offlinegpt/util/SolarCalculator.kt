package com.example.offlinegpt.util

import java.util.*
import kotlin.math.*

data class SunPosition(val azimuth: Double, val elevation: Double)

object SolarCalculator {
    /**
     * Calculates the sun's position (azimuth and elevation) for a given location and time.
     * 
     * @param lat Latitude in degrees (positive for North)
     * @param lon Longitude in degrees (positive for East)
     * @param date Date and time for the calculation
     * @return SunPosition with azimuth (degrees clockwise from North) and elevation (degrees above horizon)
     */
    fun calculateSunPosition(lat: Double, lon: Double, date: Date = Date()): SunPosition {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = date }
        
        // 1. Calculate Julian Day
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR_OF_DAY) + 
                   calendar.get(Calendar.MINUTE) / 60.0 + 
                   calendar.get(Calendar.SECOND) / 3600.0
        
        // Simplified Julian Date formula
        val jd = 367.0 * year - floor(7.0 * (year + floor((month + 9.0) / 12.0)) / 4.0) +
                 floor(275.0 * month / 9.0) + day + 1721013.5 + hour / 24.0
        
        val t = (jd - 2451545.0) / 36525.0 // Julian centuries since J2000.0

        // 2. Sun's Mean Longitude and Anomaly
        var l0 = 280.46646 + t * (36000.76983 + t * 0.0003032)
        l0 %= 360
        
        var m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        m %= 360
        
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        
        // 3. Sun's Equation of Center
        val c = (1.914602 - t * (0.004817 + 0.000014 * t)) * sin(Math.toRadians(m)) +
                (0.019993 - 0.000101 * t) * sin(Math.toRadians(2 * m)) +
                0.000289 * sin(Math.toRadians(3 * m))
        
        val sunTrueLong = l0 + c
        val apparentLong = sunTrueLong - 0.00569 - 0.00478 * sin(Math.toRadians(125.04 - 1934.136 * t))
        val epsilon = 23.439291 - t * (0.013004166) // Obliquity of ecliptic

        // 4. Declination and Equation of Time
        val declination = Math.toDegrees(asin(sin(Math.toRadians(epsilon)) * sin(Math.toRadians(apparentLong))))
        
        val y = tan(Math.toRadians(epsilon / 2.0)).pow(2)
        val eqTime = 4.0 * Math.toDegrees(y * sin(2 * Math.toRadians(l0)) - 
                     2 * e * sin(Math.toRadians(m)) + 
                     4 * e * y * sin(Math.toRadians(m)) * cos(2 * Math.toRadians(l0)) -
                     0.5 * y * y * sin(4 * Math.toRadians(l0)) - 
                     1.25 * e * e * sin(2 * Math.toRadians(m)))

        // 5. Hour Angle
        // Solar time = UTC time + longitude / 15 (approx) + equation of time
        val trueSolarTime = (hour * 60 + calendar.get(Calendar.MINUTE) + calendar.get(Calendar.SECOND) / 60.0 + eqTime + 4 * lon) % 1440
        var ha = trueSolarTime / 4.0 - 180.0
        if (ha < -180) ha += 360

        // 6. Final Azimuth/Elevation Calculation
        val phi = Math.toRadians(lat)
        val delta = Math.toRadians(declination)
        val hRad = Math.toRadians(ha)

        val csz = sin(phi) * sin(delta) + cos(phi) * cos(delta) * cos(hRad)
        val zenith = acos(max(-1.0, min(1.0, csz)))
        val azDenom = cos(phi) * sin(zenith)
        
        var azimuth = if (abs(azDenom) > 0.001) {
            val cosAz = (sin(delta) - sin(phi) * cos(zenith)) / azDenom
            Math.toDegrees(acos(max(-1.0, min(1.0, cosAz))))
        } else {
            if (lat > 0) 180.0 else 0.0
        }
        
        if (ha > 0) azimuth = (360 - azimuth) % 360 else azimuth = (azimuth + 180) % 360

        return SunPosition(azimuth, 90.0 - Math.toDegrees(zenith))
    }
}
