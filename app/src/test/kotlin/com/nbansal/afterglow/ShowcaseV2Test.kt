package com.nbansal.afterglow

import android.graphics.Bitmap
import com.nbansal.afterglow.showcase.ShowDetails
import com.nbansal.afterglow.showcase.ShowTitle
import com.nbansal.afterglow.showcase.ShowcaseLogic
import com.nbansal.afterglow.showcase.Tmdb
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ShowcaseV2Test {
    private val movie = """
        {"id":1,"runtime":166,"release_date":"2024-02-27",
         "genres":[{"id":1,"name":"Science Fiction"},{"id":2,"name":"Adventure"},{"id":3,"name":"Drama"}],
         "images":{"logos":[
           {"file_path":"/fr.png","iso_639_1":"fr","vote_average":9.0},
           {"file_path":"/nul.png","iso_639_1":null,"vote_average":8.0},
           {"file_path":"/en_low.png","iso_639_1":"en","vote_average":3.0},
           {"file_path":"/en_hi.png","iso_639_1":"en","vote_average":5.5},
           {"file_path":"/en.svg","iso_639_1":"en","vote_average":10.0}]},
         "release_dates":{"results":[
           {"iso_3166_1":"US","release_dates":[{"certification":"PG-13"}]},
           {"iso_3166_1":"IN","release_dates":[{"certification":""},{"certification":"UA"}]}]}}
    """.trimIndent()

    private val tv = """
        {"id":2,"episode_run_time":[52,50],"first_air_date":"2023-05-01","genres":[{"id":1,"name":"Drama"}],
         "images":{"logos":[]},
         "content_ratings":{"results":[{"iso_3166_1":"US","rating":"TV-MA"},{"iso_3166_1":"IN","rating":"TV-14"}]}}
    """.trimIndent()

    @Test
    fun movieDetailsAreParsed() {
        val d = Tmdb.parseDetails(movie, "movie")
        assertEquals("/en_hi.png", d.logo)
        assertEquals("UA", d.cert)
        assertEquals(166, d.runtime)
        assertEquals(listOf("Science Fiction", "Adventure"), d.genres)
        assertEquals("2024-02-27", d.released)
    }

    @Test
    fun tvDetailsAreParsed() {
        val d = Tmdb.parseDetails(tv, "tv")
        assertEquals("", d.logo)
        assertEquals("TV-14", d.cert)
        assertEquals(52, d.runtime)
        assertEquals(listOf("Drama"), d.genres)
        assertEquals("2023-05-01", d.released)
    }

    @Test
    fun emptyDetailsGiveDefaults() {
        assertEquals(ShowDetails(), Tmdb.parseDetails("{}", "movie"))
        assertEquals(ShowDetails(), Tmdb.parseDetails("{}", "tv"))
    }

    @Test
    fun logoPrefersEnglishThenNoLanguageThenHighestVote() {
        assertEquals("/en_hi.png", Tmdb.pickLogo(JSONObject(movie).getJSONObject("images").getJSONArray("logos")))
        val noEnglish = JSONArray("""[{"file_path":"/fr.png","iso_639_1":"fr","vote_average":9},{"file_path":"/a.png","iso_639_1":null,"vote_average":1},{"file_path":"/b.png","iso_639_1":null,"vote_average":2}]""")
        assertEquals("/b.png", Tmdb.pickLogo(noEnglish))
        assertEquals("", Tmdb.pickLogo(JSONArray("""[{"file_path":"/fr.png","iso_639_1":"fr","vote_average":9}]""")))
        assertEquals("", Tmdb.pickLogo(null))
    }

    @Test
    fun certificationFallsBackToUs() {
        val noIndia = JSONObject("""{"release_dates":{"results":[{"iso_3166_1":"US","release_dates":[{"certification":"PG-13"}]}]}}""")
        assertEquals("PG-13", Tmdb.pickCert(noIndia, "movie"))
        val emptyIndia = JSONObject("""{"release_dates":{"results":[{"iso_3166_1":"IN","release_dates":[{"certification":""}]},{"iso_3166_1":"US","release_dates":[{"certification":"R"}]}]}}""")
        assertEquals("R", Tmdb.pickCert(emptyIndia, "movie"))
        val tvUs = JSONObject("""{"content_ratings":{"results":[{"iso_3166_1":"US","rating":"TV-MA"}]}}""")
        assertEquals("TV-MA", Tmdb.pickCert(tvUs, "tv"))
        assertEquals("", Tmdb.pickCert(JSONObject("{}"), "tv"))
    }

    @Test
    fun newBadgeIsWithinSixtyDays() {
        val today = LocalDate.of(2024, 4, 30)
        assertTrue(Tmdb.isNew("2024-04-30", today))
        assertTrue(Tmdb.isNew("2024-03-01", today)) // exactly 60 days
        assertFalse(Tmdb.isNew("2024-02-29", today)) // 61 days
        assertFalse(Tmdb.isNew("2024-05-01", today)) // not out yet
        assertFalse(Tmdb.isNew("", today))
        assertFalse(Tmdb.isNew("soon", today))
    }

    @Test
    fun textHelpers() {
        assertEquals("2h 46m", Tmdb.runtimeText(166))
        assertEquals("2h", Tmdb.runtimeText(120))
        assertEquals("45m", Tmdb.runtimeText(45))
        assertEquals("", Tmdb.runtimeText(0))
        assertEquals(82, Tmdb.scorePercent("8.2"))
        assertEquals(0, Tmdb.scorePercent(""))
        val withDetails = ShowTitle(1, "movie", "X", "2024", "Sci-Fi", "7.8", "", "/x.jpg", ShowDetails(genres = listOf("A", "B"), runtime = 125))
        assertEquals(listOf("2024", "A · B", "2h 5m"), Tmdb.infoParts(withDetails))
        assertEquals(listOf("2024", "Sci-Fi"), Tmdb.infoParts(withDetails.copy(details = ShowDetails())))
    }

    @Test
    fun detailsRoundTripThroughJson() {
        val d = Tmdb.parseDetails(movie, "movie")
        assertEquals(d, Tmdb.detailsFromJson(Tmdb.detailsToJson(d)))
    }

    @Test
    fun detailsPathHasOneCallWithAppendedParts() {
        val t = ShowTitle(5, "movie", "X", "", "", "", "", "/x.jpg")
        assertEquals("/movie/5?language=en-US&append_to_response=images,release_dates&include_image_language=en,null", Tmdb.detailsPath(t))
        assertTrue(Tmdb.detailsPath(t.copy(type = "tv")).contains("content_ratings"))
    }

    @Test
    fun accentIsTheMostSaturatedMidBrightPixel() {
        val orange = 0xFFC86414.toInt()
        val px = intArrayOf(0xFF808080.toInt(), 0xFF100000.toInt(), 0xFFFFFFFF.toInt(), 0xFF6080A0.toInt(), orange)
        assertEquals(orange, ShowcaseLogic.pickAccent(px))
    }

    @Test
    fun accentFallsBackToAmber() {
        assertEquals(ShowcaseLogic.DEFAULT_ACCENT, ShowcaseLogic.pickAccent(IntArray(0)))
        assertEquals(ShowcaseLogic.DEFAULT_ACCENT, ShowcaseLogic.pickAccent(intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())))
        assertEquals(0xFFFFB070.toInt(), ShowcaseLogic.DEFAULT_ACCENT)
    }

    @Test
    fun accentFromSyntheticBitmap() {
        val bmp = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(0xFF808080.toInt())
        val teal = 0xFF148C96.toInt()
        bmp.setPixel(3, 4, teal)
        val px = IntArray(16 * 9)
        bmp.getPixels(px, 0, 16, 0, 0, 16, 9)
        assertEquals(teal, ShowcaseLogic.pickAccent(px))
    }

    @Test
    fun nightSlowsTheDrift() {
        assertEquals(15_000, ShowcaseLogic.driftMs(false))
        assertEquals(30_000, ShowcaseLogic.driftMs(true))
        assertEquals(24, ShowcaseLogic.MAX_IMAGE_FILES)
    }
}
