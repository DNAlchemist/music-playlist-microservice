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
import one.chest.music.playlist.repository.FileSystemTrackStorage
import one.chest.music.playlist.repository.TrackStorage
import ratpack.groovy.test.GroovyRatpackMainApplicationUnderTest
import ratpack.guice.BindingsImposition
import ratpack.impose.ImpositionsSpec

import java.nio.file.Path

@CompileStatic
class MusicPlaylistApplicationUnderTest extends GroovyRatpackMainApplicationUnderTest {

    private Path temporaryFolder

    final PlayerConfiguration playerConfiguration = [
            holdConnection: true
    ] as PlayerConfiguration

    MusicPlaylistApplicationUnderTest(Path temporaryFolder) {
        this.temporaryFolder = temporaryFolder
    }

    @Override
    protected void addImpositions(ImpositionsSpec impositions) {
        impositions.add(BindingsImposition.of({
            it.bindInstance(TrackStorage, new FileSystemTrackStorage(temporaryFolder))
            it.bindInstance(PlayerConfiguration, playerConfiguration)
        }))
    }

    def releaseStreamConnectionAfter(long mills) {
        synchronized (playerConfiguration) {
            Thread.sleep(mills)
            playerConfiguration.holdConnection = false
        }
    }

}