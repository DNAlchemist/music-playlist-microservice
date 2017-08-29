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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.rules.TemporaryFolder

@CompileStatic
class FileSystemTrackStorageTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder()

    @Rule
    public ExpectedException expectedException = ExpectedException.none()


    @Test
    void getTrackInputStream() {
        def storage = new FileSystemTrackStorage(temporaryFolder.root.toPath())
        temporaryFolder.newFile("1.2").text = "Hello, world!"
        def result = storage
                .getTrackInputStream(1, 2)
                .withCloseable({ is -> is.text })

        assert result == "Hello, world!"
    }

    @Test
    void getTrackInputStreamTrackNotExists() {
        def storage = new FileSystemTrackStorage(temporaryFolder.root.toPath())

        expectedException.expect(NoSuchTrackException)
        expectedException.expectMessage("Track[albumId: 1, trackId: 2] not found.")
        storage.getTrackInputStream(1, 2)
    }

    @Test
    void isTrackExists() {
        def storage = new FileSystemTrackStorage(temporaryFolder.root.toPath())
        assert !storage.isTrackExists(1, 2)
        temporaryFolder.newFile("1.2").text = "Hello, world!"
        assert storage.isTrackExists(1, 2)
        assert !storage.isTrackExists(2, 3)
    }

}