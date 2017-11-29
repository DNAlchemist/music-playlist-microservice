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
import one.chest.music.playlist.repository.TrackStorage

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@Slf4j
@CompileStatic
class PlayableTrack {

    final Track track
    final AtomicLong startTime = new AtomicLong()

    private TrackStorage storage

    Queue<ByteBuf> buffer = new ConcurrentLinkedQueue<>()
    private int bufferEntrySize = 1024

    private Thread downloader
    final AtomicBoolean loadComplete = new AtomicBoolean()

    private final Map<Object, Closure<?>> onLoadBuf = new ConcurrentHashMap<>()

    PlayableTrack(TrackStorage trackStorage, Track track) {
        this.storage = trackStorage
        this.track = Objects.requireNonNull(track)
    }

    PlayableTrack(TrackStorage storage, Track track, int bufferEntrySize) {
        this.storage = storage
        this.track = track
        this.bufferEntrySize = bufferEntrySize
    }

    void loadResourceAsync() {
        if (!loaded)
            this.downloader = Thread.start(this.&loadResource)
    }

    void play() {
        loadResourceAsync()
        startTime.set(System.currentTimeMillis())
    }

    boolean isLoaded() {
        return loadComplete.get()
    }

    boolean isPlayed() {
        timePosition > track.duration
    }

    long getTimePosition() {
        return System.currentTimeMillis() - startTime
    }

    Deliverer<ByteBuf> deliverer() {
        return new Deliverer<ByteBuf>(buffer, onLoadBuf)
    }

    void loadResource() {
        try {
            if (loaded)
                return

            synchronized (loadComplete) {
                if (!loaded) {
                    log.info("Track ${track}: downloading started")
                    storage
                            .getTrackInputStream(track.albumId, track.trackId)
                            .withCloseable { is ->
                        byte[] bytes = new byte[bufferEntrySize]
                        while (is.read(bytes) != -1) {
                            def part = Unpooled.copiedBuffer(bytes)
                            buffer << part
                            sendToSubscribers(part)
                            bytes = new byte[bufferEntrySize]
                        }
                    }
                    loadComplete.compareAndSet(false, true)
                    log.info("Track ${track}: downloading completed")
                }
            }
        } catch (NoSuchTrackException e) {
            log.error(e.message, e)
        }
    }

    private void sendToSubscribers(ByteBuf byteBuf) {
        onLoadBuf.each { k, func ->
            try {
                func(byteBuf)
            } catch (e) {
                log.error("Error while sending buffer to subscriber", e)
            }
        }
    }
}
