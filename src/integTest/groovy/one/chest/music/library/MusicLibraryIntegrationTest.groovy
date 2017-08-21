/*
 * MIT License
 *
 * Copyright (c) 2017 Mikhalev Ruslan
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package one.chest.music.library

import groovy.transform.CompileStatic
import org.junit.Test
import ratpack.groovy.test.GroovyRatpackMainApplicationUnderTest

@CompileStatic
class MusicLibraryIntegrationTest {

    private GroovyRatpackMainApplicationUnderTest app = new GroovyRatpackMainApplicationUnderTest()

    @Test
    void testHealth() {
        assert app.httpClient.getText("health") == "ok"
    }

    @Test
    void testPlaylistCrud() {
        def response = app.httpClient.request("playlist/tracks") {
            it.method "POST"
            it.body {
                it.text "trackId=1&albumId=2"
                it.type "application/x-www-form-urlencoded"
            }
        }
        assert response.body.text.empty && response.status.code == 200

        def tracks = app.httpClient.getText('playlist/tracks')
        assert tracks == '[{"albumId":2,"trackId":1}]'

        response = app.httpClient.request("playlist/tracks") {
            it.method "POST"
            it.body {
                it.text "trackId=3&albumId=4"
                it.type "application/x-www-form-urlencoded"
            }
        }
        assert response.body.text.empty && response.status.code == 200

        tracks = app.httpClient.getText('playlist/tracks')
        assert tracks == '[{"albumId":2,"trackId":1},{"albumId":4,"trackId":3}]'
    }

}
