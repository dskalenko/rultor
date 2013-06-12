/**
 * Copyright (c) 2009-2013, rultor.com
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met: 1) Redistributions of source code must retain the above
 * copyright notice, this list of conditions and the following
 * disclaimer. 2) Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided
 * with the distribution. 3) Neither the name of the rultor.com nor
 * the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.rultor.conveyer;

import com.jcabi.aspects.Loggable;
import com.jcabi.log.Logger;
import com.rultor.spi.Instance;
import com.rultor.spi.Pulse;
import com.rultor.spi.State;
import com.rultor.spi.Work;
import java.util.Date;
import javax.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.commons.lang3.time.DateFormatUtils;

/**
 * Logged instance.
 *
 * @author Yegor Bugayenko (yegor@tpc2.com)
 * @version $Id$
 * @since 1.0
 */
@ToString
@EqualsAndHashCode(of = "origin")
@Loggable(Loggable.DEBUG)
final class LoggedInstance implements Instance {

    /**
     * Origin instance.
     */
    private final transient Instance origin;

    /**
     * Work we're doing.
     */
    private final transient Work work;

    /**
     * Log appender.
     */
    private final transient ConveyerAppender appender;

    /**
     * Public ctor.
     * @param instance Original one
     * @param wrk Work
     * @param appr Appender
     */
    protected LoggedInstance(final Instance instance, final Work wrk,
        final ConveyerAppender appr) {
        this.origin = instance;
        this.work = wrk;
        this.appender = appr;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pulse(@NotNull final State state) {
        final ThreadGroup group = Thread.currentThread().getThreadGroup();
        this.appender.register(group, this.work);
        this.meta(
            "started",
            DateFormatUtils.formatUTC(new Date(), "yyyy-MM-dd'T'HH:mm'Z'")
        );
        this.meta("owner", this.work.owner().toString());
        this.meta("unit", this.work.name());
        this.meta("spec", this.work.spec().toString());
        try {
            this.origin.pulse(state);
        } finally {
            this.appender.unregister(group);
        }
    }

    /**
     * Log meta information.
     * @param name Name of the key
     * @param value Value of it
     */
    private void meta(final String name, final String value) {
        Logger.info(this, "%s", new Pulse.Signal(name, value).toString());
    }

}
