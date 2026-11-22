package me.viktorbarzin.portalframe.projectivy

import org.junit.Assert.assertEquals
import org.junit.Test

class FramePhotosTest {
    private val base = "https://frame.example"

    @Test
    fun eachImageBecomesItsImageUrl() {
        val json = """[
            {"id":"a1","type":"image","exifInfo":{"city":"Sofia","country":"Bulgaria"}},
            {"id":"b2","type":"image","exifInfo":null}
        ]"""
        assertEquals(
            listOf(
                FramePhoto("https://frame.example/api/Asset/a1/Image", "Sofia, Bulgaria"),
                FramePhoto("https://frame.example/api/Asset/b2/Image", null),
            ),
            FramePhotos.parse(base, json),
        )
    }

    @Test
    fun videosAndEntriesWithoutAnIdAreLeftOut() {
        val json = """[
            {"id":"v1","type":"video"},
            {"type":"image"},
            {"id":"c3","type":"image"}
        ]"""
        assertEquals(
            listOf(FramePhoto("https://frame.example/api/Asset/c3/Image", null)),
            FramePhotos.parse(base, json),
        )
    }

    @Test
    fun aTrailingSlashOnTheServerUrlIsDropped() {
        val json = """[{"id":"d4","type":"image"}]"""
        assertEquals(
            "https://frame.example/api/Asset/d4/Image",
            FramePhotos.parse("https://frame.example/", json).single().imageUrl,
        )
    }

    @Test
    fun aReplyThatIsNotAListGivesNoPhotos() {
        assertEquals(emptyList<FramePhoto>(), FramePhotos.parse(base, "<html>Forbidden</html>"))
        assertEquals(emptyList<FramePhoto>(), FramePhotos.parse(base, """{"error":"x"}"""))
    }

    @Test
    fun onlyTheCityOrOnlyTheCountryIsStillATitle() {
        val json = """[
            {"id":"e5","type":"image","exifInfo":{"city":null,"country":"Greece"}},
            {"id":"f6","type":"image","exifInfo":{"city":"London","country":""}}
        ]"""
        assertEquals(
            listOf("Greece", "London"),
            FramePhotos.parse(base, json).map { it.title },
        )
    }
}
