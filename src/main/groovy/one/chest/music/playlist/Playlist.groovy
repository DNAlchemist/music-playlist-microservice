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
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import groovy.util.logging.Slf4j
import one.chest.music.playlist.controller.Track
import one.chest.music.playlist.repository.NoSuchTrackException
import one.chest.music.playlist.repository.PlaylistRepository
import one.chest.music.playlist.repository.TrackStorage

import javax.inject.Inject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

@Slf4j
@CompileStatic
class Playlist {

    private final PlaylistRepository playlistRepository
    private final TrackStorage trackStorage

    @Inject
    Playlist(PlaylistRepository playlistRepository, TrackStorage trackStorage) {
        this.playlistRepository = playlistRepository
        this.trackStorage = trackStorage
    }

    ReentrantLock lock = new ReentrantLock()
    Condition loadTracks = lock.newCondition()

    Queue<PlayableTrack> playableTracks = new ConcurrentLinkedQueue()
    private int playableTracksQueueSize = 3

    private final Map<Object, Closure<?>> onLoadTrack = new ConcurrentHashMap<>()

    PlayableTrack fetch() {
        try {
            lock.lock()
            while (playableTracks.empty) {
                log.info("Track list is empty. Standing by")
                loadTracks.await()
                log.trace("Signal to receive new track")
            }
            return peek()
        } finally {
            lock.unlock()
        }
    }

    PlayableTrack remove() {
        return playableTracks.remove()
    }

    PlayableTrack peek() {
        if (playableTracks.empty && playlistRepository.empty) {
            log.error("BUG! Playable tracks is empty, but playlist repository is not!")
        }
        return playableTracks.peek()
    }

    void addTrack(Track track) {
        if (!trackStorage.isTrackExists(track.albumId, track.trackId)) {
            throw new NoSuchTrackException(track.albumId, track.trackId)
        }
        try {
            lock.lock()
            playlistRepository.addTrack(track)
            log.info("Track ${track} added to playlist")
            loadTracks()
        } finally {
            lock.unlock()
        }
    }

    Collection<Track> getTracks() {
        return playableTracks*.track + playlistRepository.tracks
    }

    void deliverer(@ClosureParams(
            value = SimpleType.class,
            options = "java.util.Queue") Closure<?> c) {
        Deliverer<PlayableTrack> deliverer = new Deliverer<>(playableTracks, onLoadTrack)
        c(deliverer.pack)
    }

    Deliverer<PlayableTrack> deliverer() {
        return new Deliverer<>(playableTracks, onLoadTrack)
    }

    private void loadTracks() {
        log.debug("Refresh playable track list")
        Track track = null
        for (int i = 0; i < playableTracksQueueSize && (track = playlistRepository.poll()); i++) {
            log.trace("Add ${track} to loading")
            loadTrack(track)
        }
        loadTracks.signal()
    }

    private void loadTrack(Track track) {
        PlayableTrack playableTrack = new PlayableTrack(trackStorage, track)
        playableTrack.loadResourceAsync()
        playableTracks << playableTrack
        sendToSubscribers(playableTrack)
    }

    private void sendToSubscribers(PlayableTrack playableTrack) {
        onLoadTrack.each { k, func ->
            try {
                func(playableTrack)
            } catch (e) {
                log.error("Error while sending track to subscriber", e)
            }
        }
    }
}
