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
import one.chest.music.playlist.controller.Track
import one.chest.music.playlist.repository.TrackStorage
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import java.util.concurrent.TimeUnit

@CompileStatic
class PlayableTrackTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder()

    @Test
    void loadTrackSync() {
        byte[] content = 0..1025 as byte[]

        PlayableTrack playableTrack = createPlayableTrack(content)
        playableTrack.loadResource()

        assert playableTrack.loadComplete

        assert playableTrack.buffer.size() == 2

        byte[] array1kb = content[0..1023]
        assert playableTrack.buffer[0].array() == array1kb

        assert playableTrack.buffer[1].array() == ([0b0, 0b1] + ([0b0] * 1022)) as byte[]
    }

    @Test
    void loadTrackAsync() {
        byte[] content = 0..1025 as byte[]

        PlayableTrack playableTrack = createPlayableTrack(content)
        playableTrack.loadResourceAsync()

        assert withTimeout(500, TimeUnit.MILLISECONDS, playableTrack.&isLoaded)

        assert playableTrack.buffer.size() == 2

        byte[] array1kb = content[0..1023]
        assert playableTrack.buffer[0].array() == array1kb

        assert playableTrack.buffer[1].array() == ([0, 1] + ([0] * 1022)) as byte[]
    }

    private static PlayableTrack createPlayableTrack(byte[] content) {
        TrackStorage storage = [
                isTrackExists      : { a, t -> true },
                getTrackInputStream: { a, t -> new ByteArrayInputStream(content) }
        ] as TrackStorage

        return new PlayableTrack(storage, new Track(albumId: 1, trackId: 2, duration: 50L), 1024)
    }

    private static boolean withTimeout(int timeout, TimeUnit timeUnit, Closure<Boolean> condition) {
        if (condition()) {
            return true
        }
        while (timeout-- > 0) {
            timeUnit.sleep(1)
            if (condition()) {
                return true
            }
        }
        return false
    }
}