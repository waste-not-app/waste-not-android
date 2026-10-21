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
        if (term.length > 100) return failure(ApiFailureKind.VALIDATION)
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
            WasteGuide("1b681a96-36e1-42c5-95ab-803d951da101", "Botol plastik", WasteCategory.PLASTIC, DisposalMethod.RECYCLE,
                listOf("botol", "plastik", "kemasan"), "Kenali material dan periksa penerimaan fasilitas.",
                listOf("Periksa jenis material pada kemasan.", "Pisahkan dari material lain sesuai ketentuan fasilitas.",
                    "Konfirmasikan kondisi dan penerimaan kepada pengelola."),
                emptyList()),
            WasteGuide("1b681a96-36e1-42c5-95ab-803d951da102", "Wadah kaca", WasteCategory.GLASS, DisposalMethod.REUSE,
                listOf("kaca", "wadah", "toples"), "Pertimbangkan penggunaan kembali sesuai kondisi wadah.",
                listOf("Periksa kondisi wadah sebelum digunakan.", "Pastikan penggunaan berikutnya sesuai jenis wadah.",
                    "Jika tidak layak dipakai, tanyakan penanganan ke fasilitas setempat."),
                emptyList()),
            WasteGuide("1b681a96-36e1-42c5-95ab-803d951da103", "Kertas dan kardus", WasteCategory.PAPER, DisposalMethod.RECYCLE,
                listOf("kertas", "kardus", "karton"), "Cek kondisi material sebelum memilah.",
                listOf("Pisahkan kertas dari bahan campuran.", "Periksa ketentuan kondisi material yang diterima.",
                    "Konfirmasikan penerimaan ke fasilitas setempat."),
                emptyList()),
        )
    }
}
