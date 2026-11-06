package app.tessera.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressPolicyTest {
    private fun open(s: String) = (AddressPolicy.resolve(s) as AddressPolicy.Result.Open).url
    private fun isSearch(s: String) = AddressPolicy.resolve(s) is AddressPolicy.Result.Search

    @Test fun plainDomainGetsHttps() = assertEquals("https://example.com", open("example.com"))
    @Test fun pathAndQueryKept() = assertEquals("https://news.ycombinator.com/item?id=1", open("news.ycombinator.com/item?id=1"))
    @Test fun explicitSchemeKept() = assertEquals("http://example.com/a", open("http://example.com/a"))
    @Test fun localhostUsesHttp() = assertEquals("http://localhost:8080", open("localhost:8080"))
    @Test fun ipUsesHttp() = assertEquals("http://192.168.1.1/admin", open("192.168.1.1/admin"))
    @Test fun wordsSearch() = assertTrue(isSearch("best pizza near me"))
    @Test fun singleWordSearch() = assertTrue(isSearch("weather"))
    @Test fun dottedPhraseWithSpacesSearch() = assertTrue(isSearch("what is example.com"))
    @Test fun javascriptSchemeIsNotOpened() = assertTrue(isSearch("javascript:alert(1)"))
    @Test fun emptyIsEmpty() = assertTrue(AddressPolicy.resolve("   ") is AddressPolicy.Result.Empty)
    @Test fun searchEncodes() = assertEquals("https://www.google.com/search?q=a+%26+b", AddressPolicy.searchUrl("a & b"))
    @Test fun labelStripsWww() = assertEquals("example.com", AddressPolicy.label("https://www.example.com/x?y"))
    @Test fun labelBlank() = assertEquals("", AddressPolicy.label("about:blank"))
}
