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
import groovy.util.logging.Slf4j
import one.chest.music.playlist.controller.Track
import one.chest.music.playlist.repository.PlaylistRepository
import one.chest.music.playlist.repository.TrackStorage

import javax.inject.Inject
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

import static java.lang.Thread.currentThread

@Slf4j
@CompileStatic
class Player implements Runnable {

    private final PlaylistRepository playlist
    private final TrackStorage trackStorage

    @Inject
    Player(PlaylistRepository playlist, TrackStorage trackStorage) {
        this.playlist = playlist
        this.trackStorage = trackStorage
    }

    private ReentrantLock lock = new ReentrantLock()
    private Condition condition = lock.newCondition()

    PlayableTrack playableTrack

    @Override
    void run() {
        log.info("Player thread started as ${currentThread().name}")
        while (!currentThread().interrupted) {
            playTrack(fetchTrack())
        }
        log.info("Thread ${currentThread().name} is interrupted")
    }

    private Track fetchTrack() {
        try {
            lock.lock()
            if (playlist.isEmpty()) {
                condition.await()
            }
            return playlist.poll()
        } finally {
            lock.unlock()
        }
    }

    private void playTrack(Track track) {
        try {
            playableTrack = new PlayableTrack(trackStorage, track)
            playableTrack.play()
            log.info("Playing " + track)
            while (!playableTrack.played) {
                Thread.sleep(16)
            }
        } catch (InterruptedException ignore) {
            currentThread().interrupt()
        }
    }

    void addTrack(Track track) {
        try {
            lock.lock()
            playlist.addTrack(track)
            log.info("Track ${track} added to playlist")
            condition.signal()
        } finally {
            lock.unlock()
        }
    }

    Collection<Track> getTracks() {
        return playlist.tracks
    }
}
