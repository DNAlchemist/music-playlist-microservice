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
package one.chest.music.playlist.repository

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.nio.file.Path

@Slf4j
@CompileStatic
class FileSystemTrackStorage implements TrackStorage {

    Path directory

    FileSystemTrackStorage(Path directory) {
        assert directory.toFile().exists()
        log.info("Initialize tracks storage in directory: ${directory.toAbsolutePath()}")
        this.directory = directory
    }

    @Override
    public boolean isTrackExists(int albumId, int trackId) {
        return directory.resolve("${albumId}.${trackId}").toFile().exists()
    }

    @Override
    public InputStream getTrackInputStream(int albumId, int trackId) {
        log.debug("Load track from path ${directory.resolve("${albumId}.${trackId}")}")
        checkTrackExists(albumId, trackId)
        return directory.resolve("${albumId}.${trackId}").newInputStream()
    }

    private void checkTrackExists(int albumId, int trackId) {
        if (!isTrackExists(albumId, trackId)) {
            throw new NoSuchTrackException(albumId, trackId)
        }
    }
}
