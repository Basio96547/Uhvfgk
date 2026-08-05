package com.basel.ai.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scraping is the most brittle part of search, so it gets the most tests.
 * The sample below mirrors the shape of a real html.duckduckgo.com response,
 * including the click-tracking redirect and an ad block.
 */
class HtmlExtractTest {

    private val sample = """
        <div class="result results_links">
          <h2 class="result__title">
            <a rel="nofollow" class="result__a"
               href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fkotlinlang.org%2Fdocs%2Fcoroutines.html&amp;rut=abc">Coroutines &amp; Flow</a>
          </h2>
          <a class="result__snippet">Kotlin <b>coroutines</b> let you write async code&hellip;</a>
        </div>
        <div class="result results_links">
          <a rel="nofollow" class="result__a"
             href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.org%2Fb">Second &#39;result&#39;</a>
          <a class="result__snippet">Snippet <i>two</i> here</a>
        </div>
        <div class="result result--ad">
          <a class="result__a" href="//duckduckgo.com/y.js?ad=1">An advert</a>
          <a class="result__snippet">Buy things</a>
        </div>
    """.trimIndent()

    @Test
    fun `extracts organic results and skips ads`() {
        val results = HtmlExtract.parseDuckDuckGoResults(sample)
        assertEquals(2, results.size)
        assertTrue(results.none { it.title == "An advert" })
    }

    @Test
    fun `unwraps the click-tracking redirect`() {
        val results = HtmlExtract.parseDuckDuckGoResults(sample)
        assertEquals("https://kotlinlang.org/docs/coroutines.html", results[0].url)
    }

    @Test
    fun `decodes entities and strips markup from titles and snippets`() {
        val results = HtmlExtract.parseDuckDuckGoResults(sample)
        assertEquals("Coroutines & Flow", results[0].title)
        assertEquals("Kotlin coroutines let you write async code…", results[0].snippet)
        assertEquals("Second 'result'", results[1].title)
    }

    @Test
    fun `pairs each snippet with its own result`() {
        // Pairing by position survives markup changes that name-based lookup
        // would not.
        val results = HtmlExtract.parseDuckDuckGoResults(sample)
        assertEquals("Snippet two here", results[1].snippet)
    }

    /** The same page with the advert on top, which is where they actually are. */
    private val adFirst = """
        <div class="result result--ad">
          <a class="result__a" href="//duckduckgo.com/y.js?ad=1">An advert</a>
          <a class="result__snippet">Buy things</a>
        </div>
        <div class="result results_links">
          <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.org%2Fa">First</a>
          <a class="result__snippet">Snippet for first</a>
        </div>
        <div class="result results_links">
          <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.org%2Fb">Second</a>
          <a class="result__snippet">Snippet for second</a>
        </div>
    """.trimIndent()

    @Test
    fun `a skipped ad does not shift every snippet onto the wrong result`() {
        // Snippets are found by where they sit in the document, not by counting
        // matches: the ad's anchor is dropped but its snippet is not, so index
        // pairing handed "Buy things" to the first real result and shifted the
        // rest down by one.
        val results = HtmlExtract.parseDuckDuckGoResults(adFirst)
        assertEquals(2, results.size)
        assertEquals("First", results[0].title)
        assertEquals("Snippet for first", results[0].snippet)
        assertEquals("Second", results[1].title)
        assertEquals("Snippet for second", results[1].snippet)
    }

    @Test
    fun `a result with no snippet does not borrow the next one's`() {
        val html = """
            <a class="result__a" href="https://a.example/1">Bare</a>
            <a class="result__a" href="https://a.example/2">Has one</a>
            <a class="result__snippet">Belongs to the second</a>
        """.trimIndent()
        val results = HtmlExtract.parseDuckDuckGoResults(html)
        assertEquals(2, results.size)
        assertEquals("", results[0].snippet)
        assertEquals("Belongs to the second", results[1].snippet)
    }

    /**
     * DuckDuckGo's lite frontend, verbatim in shape: single-quoted classes,
     * and `href` written *before* `class`.
     */
    private val liteMarkup = """
        <table>
          <tr><td>1.&nbsp;</td><td>
            <a rel="nofollow" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fa.example%2F1" class='result-link'>First lite result</a>
          </td></tr>
          <tr><td class='result-snippet'>Lite snippet one</td></tr>
          <tr><td>2.&nbsp;</td><td>
            <a rel="nofollow" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fb.example%2F2" class='result-link'>Second lite result</a>
          </td></tr>
          <tr><td class='result-snippet'>Lite snippet two</td></tr>
        </table>
    """.trimIndent()

    @Test
    fun `the lite frontend parses too`() {
        // The old pattern required class before href and double quotes, and
        // lite gives neither — so it matched nothing on a page full of results.
        val results = HtmlExtract.parseDuckDuckGoResults(liteMarkup)
        assertEquals(2, results.size)
        assertEquals("First lite result", results[0].title)
        assertEquals("https://a.example/1", results[0].url)
        assertEquals("Lite snippet one", results[0].snippet)
        assertEquals("Lite snippet two", results[1].snippet)
    }

    @Test
    fun `attributes are read whatever they are quoted with`() {
        assertEquals("x", HtmlExtract.attribute(" class=\"x\" ", "class"))
        assertEquals("x", HtmlExtract.attribute(" class='x' ", "class"))
        assertEquals("x", HtmlExtract.attribute(" class=x ", "class"))
        assertNull(HtmlExtract.attribute(" id='y' ", "class"))
    }

    @Test
    fun `attribute order does not matter`() {
        val tag = """ rel="nofollow" href="https://a.example" class='result-link' """
        assertEquals("https://a.example", HtmlExtract.attribute(tag, "href"))
        assertTrue(HtmlExtract.hasClass(tag, listOf("result-link")))
        assertFalse(HtmlExtract.hasClass(tag, listOf("result__snippet")))
    }

    @Test
    fun `both endpoints carry the query and the region`() {
        assertTrue(HtmlExtract.duckDuckGoLiteUrl("kotlin", "xa-ar").contains("lite.duckduckgo"))
        assertTrue(HtmlExtract.duckDuckGoLiteUrl("kotlin", "xa-ar").contains("kl=xa-ar"))
        val body = HtmlExtract.duckDuckGoFormBody("android 16", "xa-ar")
        assertTrue(body.startsWith("q="))
        assertTrue(body.contains("kl=xa-ar"))
        assertFalse("a form body is not a query string", body.contains("?"))
    }

    @Test
    fun `resolves usable URLs and rejects the rest`() {
        assertEquals("https://a.com/x", HtmlExtract.resolveUrl("https://a.com/x"))
        assertEquals("https://a.com/x", HtmlExtract.resolveUrl("//a.com/x"))
        assertNull(HtmlExtract.resolveUrl("/settings"))
        assertNull(HtmlExtract.resolveUrl("  "))
        assertNull(HtmlExtract.resolveUrl("//duckduckgo.com/y.js?x=1"))
    }

    @Test
    fun `decodes named, decimal and hex entities`() {
        assertEquals(
            "a & b < c > d \" e ' f",
            HtmlExtract.decodeEntities("a &amp; b &lt; c &gt; d &quot; e &#39; f"),
        )
        assertEquals("A", HtmlExtract.decodeEntities("&#65;"))
        assertEquals("A", HtmlExtract.decodeEntities("&#x41;"))
        assertEquals("م", HtmlExtract.decodeEntities("&#1605;"))
    }

    @Test
    fun `leaves an out-of-range entity alone rather than throwing`() {
        assertEquals("&#99999999;", HtmlExtract.decodeEntities("&#99999999;"))
    }

    private val page = """
        <html><head><style>body{color:red}</style>
        <script>var x=1;alert('hi')</script></head>
        <body><h1>Title</h1><p>First para.</p><p>Second para.</p>
        <div>Third</div><script>tracker()</script></body></html>
    """.trimIndent()

    @Test
    fun `article text drops scripts and styles`() {
        // Without removing these first, their source survives tag stripping
        // and swamps the extracted text.
        val text = HtmlExtract.articleText(page)
        assertTrue(!text.contains("alert") && !text.contains("tracker"))
        assertTrue(!text.contains("color:red"))
    }

    @Test
    fun `article text keeps prose and separates blocks`() {
        val text = HtmlExtract.articleText(page)
        assertTrue(text.contains("First para."))
        assertTrue(text.contains("Second para."))
        assertTrue(text.contains("\n"))
        assertTrue(!text.contains("\n\n\n"))
    }

    @Test
    fun `article text truncates and marks the cut`() {
        val long = (1..500).joinToString(" ") { "word$it" }
        val cut = HtmlExtract.articleText("<p>$long</p>", maxChars = 200)
        assertTrue(cut.length <= 201)
        assertTrue(cut.endsWith("…"))
    }

    @Test
    fun `handles empty and malformed input`() {
        assertEquals(0, HtmlExtract.parseDuckDuckGoResults("").size)
        assertEquals(0, HtmlExtract.parseDuckDuckGoResults("<<<>>not html<<").size)
        assertEquals("", HtmlExtract.articleText(""))
    }

    @Test
    fun `respects the result limit`() {
        assertEquals(1, HtmlExtract.parseDuckDuckGoResults(sample, limit = 1).size)
    }
}
