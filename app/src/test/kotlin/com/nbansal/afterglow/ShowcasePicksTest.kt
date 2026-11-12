package com.nbansal.afterglow

import android.graphics.Bitmap
import com.nbansal.afterglow.showcase.Picks
import com.nbansal.afterglow.showcase.ShowTitle
import com.nbansal.afterglow.showcase.ShowcaseLogic
import com.nbansal.afterglow.showcase.Taste
import com.nbansal.afterglow.showcase.Tmdb
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShowcasePicksTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100L * day

    private fun title(id: Int, type: String = "movie", via: String = "", genres: List<Int> = emptyList(), lang: String = "") =
        ShowTitle(id, type, "T$id", "2024", "", "7.0", "", "/b$id.jpg", via = via, genreIds = genres, lang = lang)

    private fun titles(n: Int) = (1..n).map { title(it) }

    // ---- logo with a solid box ----

    private val opaque = 0xFF336699.toInt()

    private fun image(w: Int, h: Int, fill: (Int, Int) -> Int): IntArray = IntArray(w * h) { fill(it % w, it / w) }

    @Test
    fun solidLogoIsRejected() {
        assertTrue(ShowcaseLogic.logoIsBoxed(image(100, 50) { _, _ -> opaque }, 100, 50))
    }

    @Test
    fun transparentLogoWithCentredTextIsKept() {
        val px = image(100, 50) { x, y -> if (x in 20..79 && y in 10..39) opaque else 0 }
        assertFalse(ShowcaseLogic.logoIsBoxed(px, 100, 50))
        assertFalse(ShowcaseLogic.logoIsBoxed(IntArray(100 * 50), 100, 50))
    }

    @Test
    fun opaqueRingAroundTransparentMiddleIsRejected() {
        val px = image(100, 50) { x, y -> if (x < 4 || x >= 96 || y < 2 || y >= 48) opaque else 0 }
        assertTrue(ShowcaseLogic.logoIsBoxed(px, 100, 50))
    }

    @Test
    fun onlyAFewBorderPixelsOpaqueIsKept() {
        // The left 4 columns only: 200 of the 768 border pixels (26%).
        val px = image(100, 50) { x, _ -> if (x < 4) opaque else 0 }
        assertFalse(ShowcaseLogic.logoIsBoxed(px, 100, 50))
    }

    @Test
    fun alphaMustBeAbove200() {
        assertFalse(ShowcaseLogic.logoIsBoxed(image(100, 50) { _, _ -> 0xC8336699.toInt() }, 100, 50)) // alpha 200
        assertTrue(ShowcaseLogic.logoIsBoxed(image(100, 50) { _, _ -> 0xC9336699.toInt() }, 100, 50)) // alpha 201
    }

    @Test
    fun syntheticBitmaps() {
        val bmp = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
        for (x in 20..79) for (y in 10..39) bmp.setPixel(x, y, opaque)
        val px = IntArray(100 * 50)
        bmp.getPixels(px, 0, 100, 0, 0, 100, 50)
        assertFalse(ShowcaseLogic.logoIsBoxed(px, 100, 50))
        for (x in 0 until 100) for (y in 0 until 50) bmp.setPixel(x, y, opaque)
        bmp.getPixels(px, 0, 100, 0, 0, 100, 50)
        assertTrue(ShowcaseLogic.logoIsBoxed(px, 100, 50))
    }

    @Test
    fun badSizesAreNotBoxed() {
        assertFalse(ShowcaseLogic.logoIsBoxed(IntArray(0), 0, 0))
        assertFalse(ShowcaseLogic.logoIsBoxed(IntArray(3), 10, 10))
    }

    @Test
    fun logoCandidatesAreKeptInOrder() {
        val json = """{"images":{"logos":[
            {"file_path":"/a.png","iso_639_1":"en","vote_average":1.0},
            {"file_path":"/b.png","iso_639_1":"en","vote_average":5.0},
            {"file_path":"/c.png","iso_639_1":null,"vote_average":9.0}]}}"""
        val d = Tmdb.parseDetails(json, "movie")
        assertEquals(listOf("/b.png", "/a.png", "/c.png"), d.logos)
        assertEquals("/b.png", d.logo)
        assertEquals(d, Tmdb.detailsFromJson(Tmdb.detailsToJson(d)))
    }

    // ---- pool ----

    @Test
    fun dedupeKeepsFirstAndMergesService() {
        val a = title(1)
        val b = title(2)
        val aNetflix = title(1, via = "Netflix")
        val tvSameId = title(1, type = "tv")
        val out = Picks.dedupe(listOf(a, b, aNetflix, tvSameId))
        assertEquals(listOf("movie:1", "movie:2", "tv:1"), out.map { Picks.key(it) })
        assertEquals("Netflix", out[0].via)
        assertEquals("", out[1].via)
    }

    @Test
    fun poolIsDeterministicForASeedAndCappedAt50() {
        val list = titles(80)
        val a = Picks.pool(list, Taste(), emptySet(), emptyMap(), now, 7L)
        val b = Picks.pool(list, Taste(), emptySet(), emptyMap(), now, 7L)
        assertEquals(a.map { it.id }, b.map { it.id })
        assertEquals(50, a.size)
        assertEquals(50, a.map { it.id }.toSet().size)
        val c = Picks.pool(list, Taste(), emptySet(), emptyMap(), now, 8L)
        assertNotEquals(a.map { it.id }, c.map { it.id })
    }

    @Test
    fun poolDropsDuplicatesTitlesWithoutBackdropAndHidden() {
        val list = titles(30) + title(5) + title(40).copy(backdrop = "")
        val out = Picks.pool(list, Taste(), setOf("movie:7"), emptyMap(), now, 1L)
        assertEquals(29, out.size)
        assertFalse(out.any { it.id == 7 })
        assertFalse(out.any { it.id == 40 })
        assertEquals(1, out.count { it.id == 5 })
    }

    @Test
    fun titlesShownInTheLastThreeDaysAreSkipped() {
        val shown = mapOf(
            "movie:1" to now - day, "movie:2" to now - 2 * day, "movie:3" to now - 3 * day + 1000,
            "movie:4" to now - 3 * day, "movie:5" to now - 5 * day,
        )
        val ids = Picks.pool(titles(30), Taste(), emptySet(), shown, now, 3L).map { it.id }.toSet()
        assertFalse(1 in ids)
        assertFalse(2 in ids)
        assertFalse(3 in ids)
        assertTrue(4 in ids) // exactly 3 days ago is no longer "recent"
        assertTrue(5 in ids)
        assertEquals(27, ids.size)
    }

    @Test
    fun smallPoolIsFilledWithTheLeastRecentlyShown() {
        val list = titles(10)
        val shown = list.associate { Picks.key(it) to now - day }
        assertEquals(10, Picks.pool(list, Taste(), emptySet(), shown, now, 3L).size)
        // 25 titles, 10 of them shown yesterday: 15 unseen is under 20, so 5 of the shown ones come back, the oldest first.
        val more = titles(25)
        val shown2 = (1..10).associate { "movie:$it" to now - it * 1000L }
        val out = Picks.pool(more, Taste(), emptySet(), shown2, now, 3L).map { it.id }.toSet()
        assertEquals(20, out.size)
        assertTrue((6..10).all { it in out })
        assertFalse((1..5).any { it in out })
    }

    @Test
    fun clockGoingBackwardsIsNotRecent() {
        assertFalse(Picks.recentlyShown(now + day, now))
        assertTrue(Picks.recentlyShown(now - day, now))
        assertFalse(Picks.recentlyShown(null, now))
    }

    @Test
    fun shownEntriesOverSevenDaysAreForgotten() {
        val kept = Picks.pruneShown(mapOf("a" to now - 7 * day, "b" to now - 7 * day - 1, "c" to now), now)
        assertEquals(setOf("a", "c"), kept.keys)
    }

    @Test
    fun dailySeedChangesOncePerDay() {
        assertEquals(Picks.dailySeed(5 * day), Picks.dailySeed(5 * day + day - 1))
        assertNotEquals(Picks.dailySeed(5 * day), Picks.dailySeed(6 * day))
    }

    // ---- providers ----

    private val providers = mapOf(
        8 to "Netflix", 1796 to "Netflix basic with Ads", 119 to "Amazon Prime Video", 10 to "Amazon Video",
        122 to "JioHotstar", 315 to "Hotstar", 237 to "Sony LIV", 232 to "Zee5", 2 to "Apple TV",
    )

    @Test
    fun installedAppsMapToProviderIds() {
        val installed = listOf(
            "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "in.startv.hotstar", "com.sonyliv", "com.graymatrix.did", "com.other.app",
        )
        assertEquals(setOf(8, 119, 122, 315, 237, 232), Picks.matchProviders(installed, providers).keys)
    }

    @Test
    fun onlyInstalledAppsCountAndMissingProvidersAreIgnored() {
        assertEquals(setOf(8), Picks.matchProviders(listOf("com.netflix.ninja"), providers).keys)
        assertTrue(Picks.matchProviders(listOf("com.example.player"), providers).isEmpty())
        assertTrue(Picks.matchProviders(listOf("com.sonyliv"), mapOf(8 to "Netflix")).isEmpty())
        assertTrue(Picks.matchProviders(emptyList(), providers).isEmpty())
    }

    @Test
    fun providerListIsParsed() {
        val json = """{"results":[{"provider_id":8,"provider_name":"Netflix"},{"provider_id":-5,"provider_name":"x"},{"provider_id":9,"provider_name":""}]}"""
        assertEquals(mapOf(8 to "Netflix"), Picks.parseProviders(json))
        assertTrue(Picks.parseProviders("{}").isEmpty())
    }

    @Test
    fun attributionNamesTheFirstServiceThatHasTheTitle() {
        val out = Picks.attribution(
            listOf("Netflix" to listOf(title(1)), "Zee5" to listOf(title(1), title(2))),
            listOf(title(1), title(2), title(3), title(4, via = "Hotstar")),
        )
        assertEquals(listOf("Netflix", "Zee5", "", "Hotstar"), out.map { it.via })
    }

    // ---- genre and language parameters ----

    @Test
    fun discoverPathHasProvidersGenresAndLanguage() {
        val taste = Taste(more = setOf(35, 28), less = setOf(27), languages = setOf("hi"))
        assertEquals(
            "/discover/movie?language=en-US&sort_by=popularity.desc&page=2&watch_region=IN&with_watch_providers=8%7C119" +
                "&with_genres=28%7C35&without_genres=27&with_original_language=hi",
            Picks.discoverPath("movie", 2, listOf(119, 8), taste, "hi"),
        )
        assertEquals("/discover/tv?language=en-US&sort_by=popularity.desc&page=1", Picks.discoverPath("tv", 1, emptyList(), Taste(), null))
    }

    @Test
    fun withProvidersThePoolIsDiscoverPlusTrending() {
        val q = Picks.queries(mapOf(8 to "Netflix"), Taste())
        assertEquals(6, q.size)
        assertEquals(listOf("movie", "movie", "tv", "tv", "", ""), q.map { it.type })
        assertTrue(q.take(4).all { it.path.contains("with_watch_providers=8") && it.via == "Netflix" })
        assertEquals(listOf(Picks.TRENDING_DAY, Picks.TRENDING_WEEK), q.drop(4).map { it.path })
        // Several providers: no single name per query (attribution does it later).
        assertTrue(Picks.queries(mapOf(8 to "Netflix", 232 to "Zee5"), Taste()).all { it.via.isEmpty() })
    }

    @Test
    fun withoutProvidersThePoolIsTrendingPopularNowPlayingTopRated() {
        val paths = Picks.queries(emptyMap(), Taste()).map { it.path }
        assertEquals(7, paths.size)
        assertTrue(paths.none { it.startsWith("/discover") })
        assertTrue(paths.any { it.startsWith("/movie/now_playing") && it.contains("region=IN") })
        assertTrue(paths.any { it.startsWith("/movie/top_rated") })
        assertTrue(paths.any { it.startsWith("/movie/popular") })
        assertEquals(Picks.TRENDING_DAY, paths[0])
    }

    @Test
    fun languagesGetOneDiscoverQueryEach() {
        val q = Picks.queries(emptyMap(), Taste(languages = setOf("ko", "hi")))
        val discover = q.filter { it.path.startsWith("/discover") }
        assertEquals(8, discover.size) // 2 types x 2 languages x 2 pages
        assertEquals(2, discover.count { it.path.contains("with_original_language=hi") && it.type == "movie" })
        val many = Picks.queries(emptyMap(), Taste(languages = setOf("en", "hi", "ko")))
        assertEquals(6, many.count { it.path.startsWith("/discover") }) // 1 page each
    }

    // ---- taste ----

    @Test
    fun tastePostFilter() {
        val drama = title(1, genres = listOf(18), lang = "en")
        val horror = title(2, genres = listOf(27, 18), lang = "en")
        val hindi = title(3, genres = listOf(35), lang = "hi")
        val unknownLang = title(4, genres = listOf(35), lang = "")
        assertTrue(Taste().accepts(horror))
        assertFalse(Taste(less = setOf(27)).accepts(horror))
        assertTrue(Taste(less = setOf(27)).accepts(drama))
        assertTrue(Taste(more = setOf(18)).accepts(drama))
        assertFalse(Taste(more = setOf(35)).accepts(drama))
        assertTrue(Taste(languages = setOf("hi")).accepts(hindi))
        assertFalse(Taste(languages = setOf("hi")).accepts(drama))
        assertTrue(Taste(languages = setOf("hi")).accepts(unknownLang))
    }

    @Test
    fun tasteFiltersThePoolButNeverEmptiesIt() {
        val list = listOf(title(1, lang = "en"), title(2, lang = "hi"), title(3, lang = "hi"))
        val hindi = Picks.pool(list, Taste(languages = setOf("hi")), emptySet(), emptyMap(), now, 1L).map { it.id }.toSet()
        assertEquals(setOf(2, 3), hindi)
        val korean = Picks.pool(list, Taste(languages = setOf("ko")), emptySet(), emptyMap(), now, 1L).map { it.id }.toSet()
        assertEquals(setOf(1, 2, 3), korean)
    }

    @Test
    fun togglesKeepMoreAndLessApart() {
        val t = Taste().toggleMore(18).toggleLess(27).toggleLanguage("hi")
        assertEquals(Taste(setOf(18), setOf(27), setOf("hi")), t)
        assertEquals(Taste(emptySet(), setOf(18, 27), setOf("hi")), t.toggleLess(18))
        assertEquals(Taste(setOf(18), setOf(27), emptySet()), t.toggleLanguage("hi"))
    }

    @Test
    fun tasteAndTitlesRoundTripThroughJson() {
        val t = Taste(setOf(18, 35), setOf(27), setOf("hi", "ko"))
        assertEquals(t, Taste.fromJson(JSONObject(t.toJson().toString())))
        assertEquals(Taste(), Taste.fromJson(null))
        val list = listOf(title(1, via = "Netflix", genres = listOf(1, 2), lang = "hi"), title(2, type = "tv"))
        assertEquals(list, Picks.poolFromJson(Picks.poolToJson(list)))
    }

    @Test
    fun discoverItemsWithoutMediaTypeUseTheListType() {
        val json = """{"results":[{"id":9,"title":"X","backdrop_path":"/x.jpg","genre_ids":[18,35],"original_language":"hi"}]}"""
        assertTrue(Tmdb.parseTrending(json, emptyMap()).isEmpty())
        val out = Tmdb.parseTrending(json, emptyMap(), 100, "movie")
        assertEquals(1, out.size)
        assertEquals("movie", out[0].type)
        assertEquals(listOf(18, 35), out[0].genreIds)
        assertEquals("hi", out[0].lang)
    }
}
