package com.example.wastenot.data.network

import com.example.wastenot.data.network.dto.toRequestFields
import com.example.wastenot.data.repository.ActivityInput
import com.example.wastenot.data.repository.CoreRepository
import com.example.wastenot.model.AchievementRequirement
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.Quantity
import com.example.wastenot.model.WasteCategory
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AchievementsAndPhotosNetworkTest {
    private lateinit var server: MockWebServer
    private lateinit var network: WasteNotNetwork
    private lateinit var repository: CoreRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        network = requireNotNull(
            WasteNotNetwork.create(
                baseUrl = server.url("/v1/").toString(),
                accessToken = { "private-access-token" },
                allowLocalHttp = true,
            ),
        )
        repository = CoreRepository(network)
    }

    @After
    fun tearDown() {
        network.client.dispatcher.executorService.shutdown()
        network.client.connectionPool.evictAll()
        server.shutdown()
    }

    @Test
    fun achievementsParseLockedAndUnlockedItemsAndRejectMalformedData() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"data":[
                  {
                    "id":"00000000-0000-4000-8000-000000000101",
                    "code":"FIRST_STEP",
                    "title":"Langkah pertama",
                    "description":"Catat minimal 1 aktivitas.",
                    "requirement_type":"ACTIVE_LOG_COUNT",
                    "current_value":1,
                    "target_value":1,
                    "is_unlocked":true,
                    "unlocked_at":"2026-11-13T07:00:00Z"
                  },
                  {
                    "id":"00000000-0000-4000-8000-000000000102",
                    "code":"STREAK_3",
                    "title":"Konsisten 3 hari",
                    "description":"Capai streak terbaik 3 hari.",
                    "requirement_type":"BEST_STREAK",
                    "current_value":2,
                    "target_value":3,
                    "is_unlocked":false,
                    "unlocked_at":null
                  }
                ]}
                """.trimIndent(),
            ),
        )

        val result = repository.achievements() as ApiResult.Success
        assertEquals(2, result.value.size)
        assertEquals(AchievementRequirement.ACTIVE_LOG_COUNT, result.value[0].requirementType)
        assertTrue(result.value[0].isUnlocked)
        assertEquals(Instant.parse("2026-11-13T07:00:00Z"), result.value[0].unlockedAt)
        assertEquals(AchievementRequirement.BEST_STREAK, result.value[1].requirementType)
        assertFalse(result.value[1].isUnlocked)
        assertNull(result.value[1].unlockedAt)
        assertEquals("/v1/achievements", server.takeRequest().path)

        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":[{"id":"not-a-uuid","code":"X","title":"T","description":"D","requirement_type":"ACTIVE_LOG_COUNT","current_value":0,"target_value":1,"is_unlocked":false,"unlocked_at":null}]}""",
            ),
        )
        assertEquals(ApiFailureKind.MALFORMED_RESPONSE, (repository.achievements() as ApiResult.Failure).error.kind)
    }

    @Test
    fun uploadDownloadAndDeletePhotoEnforceContracts() = runTest {
        val sampleBytes = byteArrayOf(1, 2, 3, 4)
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """
                {"data":{
                  "id":"00000000-0000-4000-8000-000000000201",
                  "status":"STAGED",
                  "mime_type":"image/jpeg",
                  "size_bytes":4,
                  "preview_url":"/v1/photos/00000000-0000-4000-8000-000000000201",
                  "created_at":"2026-11-13T08:00:00Z"
                }}
                """.trimIndent(),
            ),
        )
        val uploaded = repository.uploadPhoto("image/jpeg", sampleBytes, "scan.jpg") as ApiResult.Success
        assertEquals("00000000-0000-4000-8000-000000000201", uploaded.value.id)
        val uploadReq = server.takeRequest()
        assertEquals("POST", uploadReq.method)
        assertEquals("/v1/photos", uploadReq.path)
        assertTrue(uploadReq.getHeader("Content-Type")?.startsWith("multipart/form-data") == true)

        assertEquals(
            ApiFailureKind.INVALID_REQUEST,
            (repository.uploadPhoto("image/gif", sampleBytes, "anim.gif") as ApiResult.Failure).error.kind,
        )

        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(sampleBytes)))
        val downloaded = repository.downloadPhoto("00000000-0000-4000-8000-000000000201") as ApiResult.Success
        assertArrayEquals(sampleBytes, downloaded.value)
        val downloadReq = server.takeRequest()
        assertEquals("GET", downloadReq.method)
        assertEquals("/v1/photos/00000000-0000-4000-8000-000000000201", downloadReq.path)

        server.enqueue(
            MockResponse().setResponseCode(409)
                .setBody("""{"error":{"code":"PHOTO_IN_USE","message":"used"}}"""),
        )
        val conflict = repository.deletePhoto("00000000-0000-4000-8000-000000000201") as ApiResult.Failure
        assertEquals(ApiFailureKind.PHOTO_IN_USE, conflict.error.kind)
    }

    @Test
    fun activityInputSerializesPhotoIdAndClearPhotoAccurately() {
        val base = ActivityInput(
            category = WasteCategory.PLASTIC,
            method = DisposalMethod.RECYCLE,
            quantity = Quantity.Count.of(1),
            date = LocalDate.of(2026, 11, 13),
            notes = "",
        )
        assertFalse("photo_id" in base.toRequestFields())
        val attached = base.copy(photoId = "00000000-0000-4000-8000-000000000201")
        assertEquals(
            "00000000-0000-4000-8000-000000000201",
            attached.toRequestFields()["photo_id"]?.jsonPrimitive?.content,
        )
        val cleared = base.copy(clearPhoto = true)
        assertEquals(JsonNull, cleared.toRequestFields()["photo_id"])
    }
}
