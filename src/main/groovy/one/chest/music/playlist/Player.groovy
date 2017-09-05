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
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import one.chest.music.playlist.controller.Track
import one.chest.music.playlist.repository.NoSuchTrackException
import one.chest.music.playlist.repository.PlaylistRepository
import one.chest.music.playlist.repository.TrackStorage
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

import javax.inject.Inject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

import static java.lang.Thread.currentThread

@Slf4j
@CompileStatic
class Player implements Runnable {

    private final PlaylistRepository playlist
    private final TrackStorage trackStorage
    private final PlayerConfiguration configuration

    @Inject
    Player(PlaylistRepository playlist, TrackStorage trackStorage, PlayerConfiguration configuration) {
        this.playlist = playlist
        this.trackStorage = trackStorage
        this.configuration = configuration
    }

    private ReentrantLock lock = new ReentrantLock()
    private Condition condition = lock.newCondition()

    Queue<PlayableTrack> playableTracks = new ConcurrentLinkedQueue()
    private int playableTracksQueueSize = 3

    @Override
    void run() {
        log.info("Player thread started as ${currentThread().name}")
        while (!currentThread().interrupted) {
            nextTrack()
            playTrack()
        }
        log.info("Thread ${currentThread().name} is interrupted")
    }

    private void nextTrack() {
        PlayableTrack oldTrack = playableTracks.poll()
        if (playableTracks.empty) {
            playableTracks << new PlayableTrack(trackStorage, fetchTrack())
            log.info((oldTrack ? "${oldTrack.track} is done, " : "") + "${playableTracks.peek().track} now playing")
        }
    }

    private void loadTracks() {
        if (!lock.locked) {
            log.debug("Fill playable tracks queue")
            for (int i = 0; i < playableTracksQueueSize && !playlist.empty; i++) {
                Track track = playlist.remove()
                log.debug("Add ${track} to loading")
                PlayableTrack playableTrack = new PlayableTrack(trackStorage, track)
                playableTrack.loadResourceAsync()
                playableTracks << playableTrack
            }
        }
    }

    private Track fetchTrack() {
        try {
            lock.lock()
            if (playlist.isEmpty()) {
                condition.await()
            }
            return playlist.remove()
        } finally {
            lock.unlock()
        }
    }

    Publisher<ByteBuf> broadcast() {
        return { Subscriber<ByteBuf> s ->
            try {
                AtomicBoolean requestCanceled = new AtomicBoolean()
                s.onSubscribe([request: { l -> }, cancel: { requestCanceled.set(true) }] as Subscription)

                Queue<PlayableTrack> tracks = new ConcurrentLinkedQueue(playableTracks)
                while (!requestCanceled.get()) {
                    while (!tracks.empty) {
                        PlayableTrack track = tracks.poll()
                        broadcastTrack(s, track, requestCanceled)
                    }
                    if (!configuration.holdConnection) {
                        break
                    }
                }
            } catch (Throwable e) {
                s.onError(e)
            } finally {
                s.onComplete()
            }
        } as Publisher<ByteBuf>
    }

    private static void broadcastTrack(Subscriber<ByteBuf> s, PlayableTrack playableTrack, AtomicBoolean canceled) {
        Queue<ByteBuf> stack = new ConcurrentLinkedQueue<>(playableTrack.buffer)
        while (!canceled.get() && (!playableTrack.loaded || !stack.empty)) {
            s.onNext(Unpooled.copiedBuffer(stack.poll().array()))
        }
    }

    private void playTrack() {
        try {
            PlayableTrack playableTrack = playableTracks.peek()
            playableTrack.play()
            log.info("Playing " + playableTrack.track)
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
            if (!trackStorage.isTrackExists(track.albumId, track.trackId)) {
                throw new NoSuchTrackException(track.albumId, track.trackId)
            }
            playlist.addTrack(track)
            log.info("Track ${track} added to playlist")
            condition.signal()
            loadTracks()
        } finally {
            lock.unlock()
        }
    }

    Collection<Track> getTracks() {
        return playableTracks*.track + playlist.tracks
    }
}
