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
package one.chest.music.library.controller

import groovy.transform.CompileStatic
import one.chest.music.library.service.PlaylistService
import ratpack.form.Form
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler
import ratpack.jackson.Jackson

import javax.inject.Inject

@CompileStatic
class TracksHandler extends GroovyHandler {

    @Inject
    private PlaylistService playlist

    private GroovyContext ctx

    @Override
    protected void handle(GroovyContext ctx) {
        this.ctx = ctx;
        ctx.byMethod {
            get(this.&doGet)
            post(this.&doPost)
        }
    }

    private void doGet() {
        try {
            ctx.render Jackson.json(playlist.tracks)
            ctx.response.send()
        } catch (e) {
            ctx.response.status 500
            ctx.response.send e.message
        }
    }

    private void doPost() {
        ctx.parse(Form).then {
            try {
                playlist.addTrack(it as Track)
                ctx.response.status 201
                ctx.response.send()
            } catch (e) {
                ctx.response.status 500
                ctx.response.send e.message
            }
        }
    }

}

