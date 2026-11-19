package com.example.wastenot.data.dummy

import com.example.wastenot.data.network.ApiFailure
import com.example.wastenot.data.network.ApiFailureKind
import com.example.wastenot.data.network.ApiResult
import com.example.wastenot.data.network.PageInfo
import com.example.wastenot.data.repository.GuideStore
import com.example.wastenot.model.DisposalMethod
import com.example.wastenot.model.WasteCategory
import com.example.wastenot.model.WasteGuide

class DummyGuideStore(private val articles: List<WasteGuide> = fixtures) : GuideStore {
    override suspend fun search(query: String, category: WasteCategory?, cursor: String?): ApiResult<List<WasteGuide>> {
        val term = query.trim()
        if (term.codePointCount(0, term.length) > 100) return failure(ApiFailureKind.VALIDATION)
        val offset = if (cursor == null) 0 else cursor.toIntOrNull()?.takeIf { it >= 0 }
            ?: return failure(ApiFailureKind.INVALID_REQUEST)
        val found = articles.filter { article ->
            (category == null || article.category == category) &&
                (article.name.contains(term, ignoreCase = true) || article.keywords.any { it.contains(term, ignoreCase = true) })
        }
        if (offset > found.size) return failure(ApiFailureKind.INVALID_REQUEST)
        val page = found.drop(offset).take(20)
        val next = (offset + page.size).takeIf { it < found.size }
        return ApiResult.Success(page, PageInfo(next?.toString(), next != null))
    }

    override suspend fun get(id: String): ApiResult<WasteGuide> =
        articles.find { it.id == id }?.let { ApiResult.Success(it) } ?: failure(ApiFailureKind.NOT_FOUND)

    private fun failure(kind: ApiFailureKind) = ApiResult.Failure(ApiFailure(kind))

    companion object {
        // Editorial fixtures from the kit retain their missing review metadata.
        val fixtures = listOf(
            WasteGuide("1b681a96-36e1-42c5-95ab-803d951da101", "Plastic bottles", WasteCategory.PLASTIC, DisposalMethod.RECYCLE,
                listOf("bottle", "plastic", "packaging"), "Identify the material and check what the facility accepts.",
                listOf("Check the material type on the packaging.", "Keep it apart from other materials, as the facility asks.",
                    "Confirm the condition and acceptance with the collector."),
                emptyList()),
            WasteGuide("1b681a96-36e1-42c5-95ab-803d951da102", "Glass containers", WasteCategory.GLASS, DisposalMethod.REUSE,
                listOf("glass", "container", "jar"), "Consider reusing it, depending on the container's condition.",
                listOf("Check the container's condition before using it.", "Make sure the next use suits this kind of container.",
                    "If it can't be used, ask your local facility how to handle it."),
                emptyList()),
            WasteGuide("1b681a96-36e1-42c5-95ab-803d951da103", "Paper and cardboard", WasteCategory.PAPER, DisposalMethod.RECYCLE,
                listOf("paper", "cardboard", "carton"), "Check the material's condition before sorting.",
                listOf("Separate paper from mixed materials.", "Check which material conditions are accepted.",
                    "Confirm acceptance with your local facility."),
                emptyList()),
        )
    }
}
