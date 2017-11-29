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
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber

import javax.inject.Inject
import java.util.concurrent.TimeUnit

import static java.lang.Thread.currentThread

@Slf4j
@CompileStatic
class Player implements Runnable {

    private final Playlist playlist
    private final PlayerConfiguration configuration

    @Inject
    Player(Playlist playlist, PlayerConfiguration configuration) {
        this.playlist = playlist
        this.configuration = configuration
    }

    @Override
    void run() {
        log.info("Player thread started as ${currentThread().name}")
        while (!currentThread().interrupted) {
            playTrack()
        }
        log.info("Thread ${currentThread().name} is interrupted")
    }

    void addTrack(Track track) {
        playlist.addTrack(track)
    }

    Collection<Track> getTracks() {
        return playlist.tracks
    }

    PlayableTrack currentTrack() {
        return playlist.peek()
    }

    Publisher<ByteBuf> broadcast() {
        return { Subscriber<ByteBuf> s ->
            try {
                playlist.deliverer { Queue<PlayableTrack> tracks ->
                    log.debug("Client connected")

                    def subscription = new BroadcastSubscription()
                    s.onSubscribe(subscription)

                    while (!subscription.canceled) {
                        while (!tracks.empty) {
                            PlayableTrack track = tracks.poll()
                            broadcastTrack(s, track, subscription)
                        }
                        if (!configuration.holdConnection) {
                            break
                        }
                        try {
                            log.trace("Park thread. Waiting for adding tracks")
                            playlist.lock.lock()
                            if (!subscription.canceled && tracks.empty)
                                playlist.loadTracks.await(1, TimeUnit.SECONDS)
                        } finally {
                            playlist.lock.unlock()
                        }
                    }
                    log.debug("Client disconnected")
                }
            } catch (Throwable e) {
                s.onError(e)
            } finally {
                s.onComplete()
            }
        } as Publisher<ByteBuf>
    }

    private
    static void broadcastTrack(Subscriber<ByteBuf> s, PlayableTrack playableTrack, BroadcastSubscription subscription) {
        playableTrack.deliverer { Queue<ByteBuf> stack ->
            subscription.buffer = stack
            ByteBuf buf = Unpooled.buffer()
            while (!subscription.canceled && (!playableTrack.loaded || (buf = stack.poll()) != null)) {
                if (buf) {
                    s.onNext(Unpooled.copiedBuffer(buf.array()))
                }
            }
        }

    }

    private void playTrack() {
        try {
            PlayableTrack playableTrack = playlist.fetch()
            playableTrack.play()
            log.info("Playing " + playableTrack.track)
            while (!playableTrack.played) {
                Thread.sleep(16)
            }
            playlist.remove()
        } catch (InterruptedException ignore) {
            currentThread().interrupt()
        }
    }
}
