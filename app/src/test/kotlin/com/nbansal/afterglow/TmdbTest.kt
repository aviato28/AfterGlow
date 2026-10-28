package com.nbansal.afterglow

import com.nbansal.afterglow.showcase.ShowTitle
import com.nbansal.afterglow.showcase.ShowcaseLogic
import com.nbansal.afterglow.showcase.Tmdb
import com.nbansal.afterglow.showcase.TmdbHttpException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TmdbTest {
    private val genresJson = """{"genres":[{"id":28,"name":"Action"},{"id":878,"name":"Science Fiction"},{"id":10759,"name":"Action & Adventure"}]}"""

    private val trending = """
        {"page":1,"results":[
          {"id":693134,"media_type":"movie","title":"Dune: Part Two","backdrop_path":"/dune.jpg","release_date":"2024-02-27","genre_ids":[878,12],"vote_average":8.2345,"overview":"  Paul unites with the Fremen.  "},
          {"id":1,"media_type":"person","name":"Someone","backdrop_path":"/p.jpg"},
          {"id":2,"media_type":"movie","title":"No art","backdrop_path":null,"release_date":"2020-01-01"},
          {"id":3,"media_type":"tv","name":"The Show","backdrop_path":"/show.jpg","first_air_date":"2023-05-01","genre_ids":[10759],"vote_average":0,"overview":""},
          {"id":4,"media_type":"movie","title":"Empty art","backdrop_path":""},
          {"id":5,"media_type":"tv","name":"Unknown genre","backdrop_path":"/u.jpg","first_air_date":"","genre_ids":[99999],"vote_average":7.75}
        ]}
    """.trimIndent()

    private fun genres() = Tmdb.parseGenres(genresJson)

    @Test
    fun genresParsedAndCachedRoundTrip() {
        assertEquals("Science Fiction", genres()[878])
        assertEquals(genres(), Tmdb.genresFromJson(Tmdb.genresToJson(genres())))
    }

    @Test
    fun keepsMovieAndTvWithBackdropOnly() {
        val list = Tmdb.parseTrending(trending, genres())
        assertEquals(listOf(693134, 3, 5), list.map { it.id })
        val dune = list[0]
        assertEquals("movie", dune.type)
        assertEquals("Dune: Part Two", dune.title)
        assertEquals("2024", dune.year)
        assertEquals("Science Fiction", dune.genre)
        assertEquals("8.2", dune.rating)
        assertEquals("Paul unites with the Fremen.", dune.overview)
        assertEquals("/dune.jpg", dune.backdrop)
        val show = list[1]
        assertEquals("tv", show.type)
        assertEquals("The Show", show.title)
        assertEquals("2023", show.year)
        assertEquals("Action & Adventure", show.genre)
        assertEquals("", show.rating)
        assertEquals("", list[2].year)
        assertEquals("", list[2].genre)
    }

    @Test
    fun firstTenOnly() {
        val items = (1..25).joinToString(",") { """{"id":$it,"media_type":"movie","title":"T$it","backdrop_path":"/b$it.jpg"}""" }
        val list = Tmdb.parseTrending("""{"results":[$items]}""", emptyMap())
        assertEquals(10, list.size)
        assertEquals(10, list.last().id)
    }

    @Test
    fun emptyOrMissingResults() {
        assertTrue(Tmdb.parseTrending("""{"page":1}""", emptyMap()).isEmpty())
        assertTrue(Tmdb.parseGenres("""{}""").isEmpty())
    }

    @Test
    fun ratingOneDecimalWithDot() {
        assertEquals("7.8", Tmdb.rating(7.8))
        assertEquals("7.8", Tmdb.rating(7.75))
        assertEquals("10.0", Tmdb.rating(10.0))
        assertEquals("", Tmdb.rating(0.0))
    }

    @Test
    fun metaLineSkipsMissingParts() {
        val t = ShowTitle(1, "movie", "X", "2024", "Sci-Fi", "7.8", "", "/x.jpg")
        assertEquals("2024 · Sci-Fi · ★ 7.8", Tmdb.metaLine(t))
        assertEquals("2024 · ★ 7.8", Tmdb.metaLine(t.copy(genre = "")))
        assertEquals("", Tmdb.metaLine(t.copy(year = "", genre = "", rating = "")))
    }

    @Test
    fun watchUrlByType() {
        assertEquals("https://www.themoviedb.org/movie/693134", Tmdb.watchUrl(ShowTitle(693134, "movie", "D", "", "", "", "", "/d")))
        assertEquals("https://www.themoviedb.org/tv/3", Tmdb.watchUrl(ShowTitle(3, "tv", "S", "", "", "", "", "/s")))
    }

    @Test
    fun v3KeyGoesInTheQueryV4InTheHeader() {
        val v3 = "0123456789abcdef0123456789abcdef"
        assertTrue(Tmdb.isValidKey(v3))
        assertFalse(Tmdb.usesBearer(v3))
        assertNull(Tmdb.bearer(v3))
        assertEquals("https://api.themoviedb.org/3/trending/all/week?language=en-US&api_key=$v3", Tmdb.requestUrl(Tmdb.TRENDING, v3))
        assertEquals("https://api.themoviedb.org/3/configuration?api_key=$v3", Tmdb.requestUrl(Tmdb.CONFIG, v3))

        val v4 = "eyJhbGciOiJIUzI1NiJ9." + "a".repeat(40) + ".sig"
        assertTrue(Tmdb.isValidKey(v4))
        assertEquals("Bearer $v4", Tmdb.bearer(v4))
        assertFalse(Tmdb.requestUrl(Tmdb.TRENDING, v4).contains("eyJ"))

        assertFalse(Tmdb.isValidKey(""))
        assertFalse(Tmdb.isValidKey("not a key"))
        assertFalse(Tmdb.isValidKey("0123"))
    }

    @Test
    fun errorTextNeverContainsKeyOrUrl() {
        val msg = Tmdb.errorText(TmdbHttpException(401))
        assertTrue(msg.contains("401"))
        assertFalse(msg.contains("api_key"))
        assertTrue(Tmdb.errorText(java.net.UnknownHostException("https://x?api_key=SECRET")).let { "SECRET" !in it })
    }

    @Test
    fun titleSizeShrinksForLongNames() {
        assertEquals(96, Tmdb.titleSizeSp("Dune"))
        assertEquals(72, Tmdb.titleSizeSp("Dune: Part Two Ext"))
        assertEquals(56, Tmdb.titleSizeSp("A very long title that will not fit on one line"))
    }

    @Test
    fun ramGate() {
        assertFalse(ShowcaseLogic.ramOk(2_000_000_000L))
        assertFalse(ShowcaseLogic.ramOk(2_799_999_999L))
        assertTrue(ShowcaseLogic.ramOk(2_800_000_000L))
        assertTrue(ShowcaseLogic.ramOk(4_000_000_000L))
    }

    @Test
    fun cacheFreshness() {
        val h = 60 * 60 * 1000L
        assertTrue(ShowcaseLogic.fresh(100 * h, 105 * h, ShowcaseLogic.REFRESH_MS))
        assertFalse(ShowcaseLogic.fresh(100 * h, 106 * h, ShowcaseLogic.REFRESH_MS))
        assertFalse(ShowcaseLogic.fresh(0, 5 * h, ShowcaseLogic.REFRESH_MS))
        assertFalse(ShowcaseLogic.fresh(200 * h, 100 * h, ShowcaseLogic.REFRESH_MS)) // clock went backwards
        assertTrue(ShowcaseLogic.fresh(0 + 1, 29L * 24 * h, ShowcaseLogic.GENRES_MS))
    }

    @Test
    fun sampleSizeAndAverageColour() {
        assertEquals(1, ShowcaseLogic.sampleSize(1280, 1920))
        assertEquals(1, ShowcaseLogic.sampleSize(1280, 1280))
        assertEquals(2, ShowcaseLogic.sampleSize(1280, 640))
        assertEquals(4, ShowcaseLogic.sampleSize(1280, 320))
        assertEquals(0xFF7F4020.toInt(), ShowcaseLogic.averageColor(intArrayOf(0xFF000000.toInt(), 0xFFFF8040.toInt())))
    }
}
