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
package one.chest.music.playlist

import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ratpack.groovy.test.GroovyRatpackMainApplicationUnderTest
import ratpack.http.client.ReceivedResponse

import java.util.concurrent.TimeUnit

@CompileStatic
class MusicLibraryIntegrationTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder()

    GroovyRatpackMainApplicationUnderTest app

    @Before
    void setUp() {
        app = new MusicPlaylistApplicationUnderTest(temporaryFolder.root.toPath())
        temporaryFolder.newFile('1.2').bytes = 0..1025 as byte[]
        temporaryFolder.newFile('3.4')
        temporaryFolder.newFile('5.6')
    }

    @Test
    void testHealth() {
        assert app.httpClient.getText("health") == "ok"
    }

    ReceivedResponse addTrack(int albumId, int trackId, int duration) {
        app.httpClient.request("playlist/tracks") {
            it.method "POST"
            it.body {
                it.text "albumId=${albumId}&trackId=${trackId}&duration=${duration}"
                it.type "application/x-www-form-urlencoded"
            }
        }
    }

    String getTracks() {
        app.httpClient.getText('playlist/tracks')
    }

    String getCurrentTrack() {
        app.httpClient.getText('playlist/tracks/current')
    }

    @Test
    void testPlaylistCrudAndPlayTrack() {
        def response = addTrack(1, 2, 1000)
        assert response.body.text.empty && response.status.code == 201

        assert tracks == '[{"albumId":1,"trackId":2,"duration":1000}]'

        response = addTrack(3, 4, 5000)
        assert response.body.text.empty && response.status.code == 201

        assert tracks == '[{"albumId":1,"trackId":2,"duration":1000},{"albumId":3,"trackId":4,"duration":5000}]'

        response = addTrack(5, 6, 3000)
        assert response.body.text.empty && response.status.code == 201

        assert tracks == '[{"albumId":1,"trackId":2,"duration":1000},{"albumId":3,"trackId":4,"duration":5000},{"albumId":5,"trackId":6,"duration":3000}]'
    }

    @Test
    void testPlayTrack() {
        assert [
                addTrack(1, 2, 500),
                addTrack(3, 4, 5000),
                addTrack(5, 6, 3000)
        ]*.statusCode == [201] * 3

        assert tracks == '[{"albumId":1,"trackId":2,"duration":500},{"albumId":3,"trackId":4,"duration":5000},{"albumId":5,"trackId":6,"duration":3000}]'

        TimeUnit.SECONDS.sleep(1)
        assert tracks == '[{"albumId":3,"trackId":4,"duration":5000},{"albumId":5,"trackId":6,"duration":3000}]'
    }

    @Test
    void testAddTrackValidation() {
        def response = app.httpClient.request("playlist/tracks") {
            it.method "POST"
            it.body {
                it.text "albumId=1&trackId=2"
                it.type "application/x-www-form-urlencoded"
            }
        }
        assert response.statusCode == 422
    }

    @Test
    void testCheckCurrentTrack() {
        assert [
                addTrack(1, 2, 500),
                addTrack(3, 4, 5000),
                addTrack(5, 6, 3000)
        ]*.statusCode == [201] * 3

        assert tracks == '[{"albumId":1,"trackId":2,"duration":500},{"albumId":3,"trackId":4,"duration":5000},{"albumId":5,"trackId":6,"duration":3000}]'

        TimeUnit.SECONDS.sleep(1)
        assert currentTrack ==~ /\{"track":\{"albumId":3,"trackId":4,"duration":5000\},"position":\d{1,3}\}/
    }

    @Test
    void testStream() {
        assert addTrack(1, 2, 5000).statusCode == 201
        def response = app.httpClient.get("playlist/tracks/stream")
        assert response.statusCode == 200

        def was = response.body.bytes
        assert was.length > 1025

        def expected = 0..1025 as byte[]

        assert was[0..1025] as byte[] == expected
    }


}
