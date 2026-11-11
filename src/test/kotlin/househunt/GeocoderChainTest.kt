package househunt

import househunt.geo.ChainedGeocoder
import househunt.geo.Geocoder
import househunt.model.Coordinates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GeocoderChainTest {
    private fun geocoder(name: String, answer: Coordinates?) = object : Geocoder {
        override val serviceName = name
        override fun findCoordinates(query: String) = answer
    }

    private val amersfoort = Coordinates(52.15, 5.38)
    private val rotterdam = Coordinates(51.87, 4.49)
    private val inAmersfoortArea = { point: Coordinates -> point.latitude in 52.05..52.30 && point.longitude in 5.20..5.60 }

    @Test
    fun `an answer far outside the search area is skipped and the next service is used`() {
        val chain = ChainedGeocoder(listOf(geocoder("pdok", rotterdam), geocoder("nominatim", amersfoort)), inAmersfoortArea)
        assertEquals(amersfoort to "nominatim", chain.findCoordinates("Station Amersfoort Schothorst"))
    }

    @Test
    fun `the first plausible service wins`() {
        val chain = ChainedGeocoder(listOf(geocoder("pdok", amersfoort), geocoder("nominatim", Coordinates(52.2, 5.4))), inAmersfoortArea)
        assertEquals(amersfoort to "pdok", chain.findCoordinates("x"))
    }

    @Test
    fun `no plausible answer gives nothing`() {
        val chain = ChainedGeocoder(listOf(geocoder("pdok", rotterdam), geocoder("nominatim", null)), inAmersfoortArea)
        assertNull(chain.findCoordinates("x"))
    }
}
