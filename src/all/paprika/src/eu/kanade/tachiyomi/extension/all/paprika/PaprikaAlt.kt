package eu.kanade.tachiyomi.extension.all.paprika

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class PaprikaAlt(
    override val name: String,
    override val baseUrl: String,
    override val lang: String
) : Paprika(name, baseUrl, lang) {
    override fun popularMangaSelector() = "div.anipost"

    override fun popularMangaFromElement(element: Element): SManga {
        // Log.d("Paprika", "processing popular element")
        return SManga.create().apply {
            element.select("a:has(h2)").let {
                setUrlWithoutDomain(it.attr("href"))
                title = it.text()
                // Log.d("Paprika", "manga url: $url")
                // Log.d("Paprika", "manga title: $title")
            }
            thumbnail_url = element.select("img").attr("abs:src")
            // Log.d("Paprika", "manga thumb: $thumbnail_url")
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotBlank()) {
            GET("$baseUrl/search?s=$query&post_type=manga&page=$page")
        } else {
            val url = HttpUrl.parse("$baseUrl/genres/")!!.newBuilder()
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> url.addPathSegment(filter.toUriPart())
                    is OrderFilter -> url.addQueryParameter("orderby", filter.toUriPart())
                }
            }
            url.addQueryParameter("page", page.toString())
            GET(url.toString(), headers)
        }
    }

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.select(".animeinfo .rm h1")[0].text()
            thumbnail_url = document.select(".animeinfo .lm  img").attr("abs:src")
            document.select(".listinfo li").forEach {
                it.text().apply {
                    when {
                        this.startsWith("Author") -> author = this.substringAfter(":").trim()
                        this.startsWith("Artist") -> artist = this.substringAfter(":").trim()
                        this.startsWith("Genre") -> genre = this.substringAfter(":").trim().replace(";", ",")
                        this.startsWith("Status") -> status = this.substringAfter(":").trim().toStatus()
                    }
                }
            }
            description = document.select("#noidungm").joinToString("\n") { it.text() }

            // Log.d("Paprika", "mangaDetials")
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaTitle = document.select(".animeinfo .rm h1")[0].text()
        return document.select(chapterListSelector()).map { chapterFromElement(it, mangaTitle) }.distinctBy { it.url }
    }

    override fun chapterListSelector() = ".animeinfo .rm .cl li"

    // changing the signature to pass the manga title in order to trim the title from chapter titles
    override fun chapterFromElement(element: Element, mangaTitle: String): SChapter {
        return SChapter.create().apply {
            element.select(".leftoff").let {
                name = it.text().substringAfter("$mangaTitle ")
                setUrlWithoutDomain(it.select("a").attr("href"))
            }
            date_upload = element.select(".rightoff").firstOrNull()?.text().toDate()
        }
    }
}
